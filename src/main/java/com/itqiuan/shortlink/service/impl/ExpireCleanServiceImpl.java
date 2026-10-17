package com.itqiuan.shortlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itqiuan.shortlink.entity.ShortLink;
import com.itqiuan.shortlink.mapper.ShortLinkMapper;
import com.itqiuan.shortlink.service.ExpireCleanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 过期短链清理实现：按"过期时间 < 当前时刻"分批删除。
 * <p>
 * 设计要点：
 * ① 分批（batchSize）+ 限轮（maxRound）—— 一次删太多会长时间锁行、拖垮数据库；
 * ② 用 Status 枚举记录退出原因（删完 / 达上限 / 异常），任务结束后统一按原因打日志，
 *    而不是在各分支里散着打 —— 出问题时一眼能看出"是删完了还是被上限截断了"。
 * <p>
 * ⚠️ <b>这里是逻辑删除，不是物理删除</b> —— 尽管方法名叫 delete、代码里也找不到半句
 * {@code SET del_flag=1}。真正发生的是 MyBatis-Plus 的全局配置
 * （{@code mybatis-plus.global-config.db-config.logic-delete-field: delFlag}）把
 * {@code mapper.delete(wrapper)} 改写成了：
 * <pre>
 * UPDATE t_short_link SET del_flag=1, update_time=... WHERE del_flag=0 AND (expire_time &lt; ?) limit 500
 * </pre>
 * 所以"删了 1200 条"之后表里那 1200 行仍然在，只是 {@code del_flag=1}。
 * <p>
 * ⭐ 为什么<b>必须</b>是逻辑删除：这张表上两个唯一索引各管一件事，物理删会把两件事一起破坏。
 * <table>
 *   <caption>两种删除对两个唯一索引的影响</caption>
 *   <tr><th>索引</th><th>逻辑删（现状）</th><th>物理删</th></tr>
 *   <tr>
 *     <td>{@code uk_short_code}（普通唯一索引，只认 short_code 一列）</td>
 *     <td>行还在 → <b>短码继续被占位 → 永不复用</b> ✅</td>
 *     <td>行没了 → 短码被发号器/自定义码重新发出 → <b>原短码的访问者被跳到别人的链接</b> ❌</td>
 *   </tr>
 *   <tr>
 *     <td>{@code uk_origin_url_md5}（函数索引 {@code IF(del_flag=0, origin_url_md5, NULL)}）</td>
 *     <td>{@code del_flag=1} 后索引键变 {@code NULL} → <b>幂等位自动释放</b>，同一长链可重建 ✅</td>
 *     <td>行没了 → 索引里也没这条了，幂等位同样释放（结果与逻辑删一样）—— 但代价是上面那一行</td>
 *   </tr>
 * </table>
 * 一句话：<b>物理删让"短码"变成可复用的，逻辑删只让"幂等位"变成可复用的。</b>
 * 前者是安全事故，后者是产品功能。（出处：{@code docs/02-数据库设计.sql} 要点 5、
 * {@code docs/03-接口设计.md} 唯一索引一节）
 */
@Slf4j
@Service
public class ExpireCleanServiceImpl implements ExpireCleanService {

    private final ShortLinkMapper shortLinkMapper;

    public ExpireCleanServiceImpl(ShortLinkMapper shortLinkMapper) {
        this.shortLinkMapper = shortLinkMapper;
    }

    /**
     * 分批清理：每批删 batchSize 行；某批不足 batchSize 说明已删完，提前退出。
     */
    @Override
    public void cleanExpired(int batchSize, int maxRound) {
        if (batchSize <= 0 || maxRound <= 0) {
            throw new IllegalArgumentException("batchSize和maxRound必须大于0");
        }
        LocalDateTime cutoff = LocalDateTime.now();
        LocalDateTime startedAt = LocalDateTime.now();
        int deletedCount = 0;
        Status transactionStatus = Status.LIMIT;
        try {
            for (int i = 0; i < maxRound; i++) {
                try {
                    int size = shortLinkMapper.delete(new LambdaQueryWrapper<ShortLink>()
                            .lt(ShortLink::getExpireTime, cutoff).last("limit " + batchSize));
                    deletedCount += size;
                    if (size < batchSize) {
                        transactionStatus = Status.CLEARED;
                        break;
                    }
                } catch (DataAccessException e) {
                    transactionStatus = Status.ABORTED;
                    log.error("清理过期数据时发生异常", e);
                    break;
                }
            }
        } finally {
            long costMs = Duration.between(startedAt, LocalDateTime.now()).toMillis();
            switch (transactionStatus) {
                case CLEARED:
                    log.info("已删除 {} 条过期数据，耗时 {} ms", deletedCount, costMs);
                    break;
                case LIMIT:
                    log.info("已删除 {} 条过期数据，达到轮次上限，可能还有剩余，耗时 {} ms", deletedCount, costMs);
                    break;
                case ABORTED:
                    log.warn("清理过期数据时发生异常，已删除 {} 条过期数据，耗时 {} ms", deletedCount, costMs);
                    break;
            }

        }
    }
    private enum Status {
        // 清理完成
        CLEARED,
        // 达到轮次上限
        LIMIT,
        // 中断
        ABORTED
    }


}
