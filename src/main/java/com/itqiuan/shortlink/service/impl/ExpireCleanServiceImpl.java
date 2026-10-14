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
 * @author qiuan
 */
/**
 * 过期短链清理实现：按"过期时间 < 当前时刻"分批删除。
 * <p>
 * 设计要点：
 * ① 分批（batchSize）+ 限轮（maxRound）—— 一次删太多会长时间锁行、拖垮数据库；
 * ② 用 Status 枚举记录退出原因（删完 / 达上限 / 异常），任务结束后统一按原因打日志，
 *    而不是在各分支里散着打 —— 出问题时一眼能看出"是删完了还是被上限截断了"。
 * <p>
 * 注意这里用的是<b>物理删除</b>（真 delete），不是逻辑删除：过期数据已无价值，留着只占空间。
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
