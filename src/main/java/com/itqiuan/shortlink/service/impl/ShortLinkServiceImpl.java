package com.itqiuan.shortlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itqiuan.shortlink.common.enums.LinkUsabilityEnum;
import com.itqiuan.shortlink.common.enums.LinkStatusEnum;
import com.itqiuan.shortlink.common.exception.BusinessException;
import com.itqiuan.shortlink.common.result.ResultCode;
import com.itqiuan.shortlink.common.util.Base62Util;
import com.itqiuan.shortlink.common.util.Md5Util;
import com.itqiuan.shortlink.converter.ShortLinkConverter;
import com.itqiuan.shortlink.dto.ShortLinkCreateDTO;
import com.itqiuan.shortlink.entity.ShortLink;
import com.itqiuan.shortlink.mapper.ShortLinkMapper;
import com.itqiuan.shortlink.service.SeqGenerator;
import com.itqiuan.shortlink.service.ShortLinkService;
import com.itqiuan.shortlink.service.support.ShortLinkClassifier;
import com.itqiuan.shortlink.vo.ShortLinkVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
public class ShortLinkServiceImpl implements ShortLinkService {

    // 62^5 = 916132832
    private static final long SEQ_OFFSET = 916132832L;
    // 总尝试次数
    private static final int MAX_ATTEMPTS = 3;

    // 去重用的唯一索引名：归因靠从异常 message 里读它
    private static final String UK_ORIGIN_URL_MD5 = "uk_origin_url_md5";

    @Autowired
    private ShortLinkConverter shortLinkConverter;
    @Autowired
    private ShortLinkMapper shortLinkMapper;
    @Autowired
    private SeqGenerator seqGenerator;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ShortLinkClassifier shortLinkClassifier;

    /**
     * 创建短链。
     *
     * <p>本方法<b>故意不加 @Transactional</b>：它要负责"失败重试"，而重试必须发生在事务之外
     * ——死锁时 MySQL 已经把整条事务回滚了（Deadlock found ... try restarting transaction），
     * 在同一个事务里 continue 重试的是一个"已经死掉的事务"。
     *
     * <p>真正的事务边界在内层 {@code transactionTemplate.execute(...)}：
     * <b>一轮尝试 = 一个全新事务</b>。失败就干净地回滚退出，下一轮重新开始。
     */
    @Override
    public ShortLinkVO createShortLink(ShortLinkCreateDTO shortLinkCreateDTO) {

        // ---------- 0. service 层兜底校验（controller 的 @Valid 管不到内部调用）----------
        String originUrl = shortLinkCreateDTO.getOriginUrl();
        if (!StringUtils.hasText(originUrl)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "原始地址不能为空");
        }
        String md5 = Md5Util.toMd5(originUrl);
        boolean hasCustomCode = StringUtils.hasText(shortLinkCreateDTO.getCustomCode());

        // ---------- 1. 幂等快路径：事务外查，读到的一定是最新已提交数据 ----------
        ShortLink existShortLink = selectByMd5(md5);
        if (existShortLink != null) {
            return resolveExisting(existShortLink);
        }

        // ---------- 2. 写入：每一轮尝试都是一个独立事务 ----------
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            // 组装放在事务外：事务里只留数据库操作，事务越短越好
            ShortLink toInsert = buildForInsert(shortLinkCreateDTO, md5, hasCustomCode);
            try {
                return transactionTemplate.execute(
                        status -> shortLinkConverter.toShortLinkVO(persist(toInsert)));
            } catch (DuplicateKeyException e) {
                // 归因不看库、只看异常里的索引名（RR 下同事务回查看不见并发赢家）
                if (isOriginUrlMd5Conflict(e)) {
                    // 撞 md5：赢家此时一定已经提交了
                    // （插入撞到未提交的唯一键会先阻塞等锁，等到赢家提交才会报 duplicate）
                    ShortLink winner = selectByMd5(md5);
                    if (winner != null) {
                        return resolveExisting(winner);
                    }
                    // 赢家回滚了：这一轮白撞，重来
                    log.warn("原始URL唯一键冲突但查不到记录，第 {} 次尝试", attempt + 1);
                    continue;
                }
                // 没撞 md5 就是撞短码索引（表上只有这两个唯一索引）
                if (hasCustomCode) {
                    // 幂等优先：也可能只是"同一个 URL 被并发提交"，而不是这个短码真被别人占了
                    ShortLink sameUrl = selectByMd5(md5);
                    if (sameUrl != null) {
                        return resolveExisting(sameUrl);
                    }
                    throw new BusinessException(ResultCode.SHORT_CODE_ALREADY_EXIST,
                            "短码\"" + shortLinkCreateDTO.getCustomCode() + "\"已被占用");
                }
                log.warn("短码撞号，第 {} 次尝试，准备换号重试", attempt + 1);
                continue;
            } catch (ConcurrencyFailureException e) {
                // 死锁 / 锁等待超时：整条事务已被 MySQL 回滚，只能在新事务里重来
                log.warn("并发冲突（死锁或锁等待超时），第 {} 次尝试失败", attempt + 1, e);
            }
        }
        throw new BusinessException(ResultCode.SYSTEM_ERROR, "短链创建失败，请稍后重试");
    }

    /**
     * 组装待插入的实体（事务外执行：发号走 Redis，不参与数据库事务）。
     */
    private ShortLink buildForInsert(ShortLinkCreateDTO dto, String md5, boolean hasCustomCode) {
        ShortLink shortLink = shortLinkConverter.toShortLink(dto);
        shortLink.setOriginUrlMd5(md5);
        shortLink.setStatus(LinkStatusEnum.NORMAL.getCode());
        if (!hasCustomCode) {
            long seqNo = seqGenerator.generateSeqNo();
            shortLink.setSeqNo(seqNo);
            shortLink.setShortCode(Base62Util.toBase62(seqNo + SEQ_OFFSET));
        }
        return shortLink;
    }

    /**
     * 把"库里已经存在的那条记录"翻译成最终结果：能复用就复用，不能复用就抛对应业务码。
     * 调用方保证入参非 null。
     */
    private ShortLinkVO resolveExisting(ShortLink shortLink) {
        switch (shortLinkClassifier.classify(shortLink)) {
            case REUSABLE:
                return shortLinkConverter.toShortLinkVO(shortLink);
            case DISABLED:
                throw new BusinessException(ResultCode.SHORT_LINK_DISABLED, "短链已被禁用");
            case EXPIRED:
                throw new BusinessException(ResultCode.SHORT_LINK_EXPIRED, "短链已过期");
            case NOT_FOUND:
            case UNKNOWN:
            default:
                // 不该走到这里：入参非 null 且 classify 已穷举
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "短链状态异常");
        }
    }

    /**
     * 从异常 message 里读索引名，判断撞的是不是 md5 唯一索引。
     * MySQL 8 的原文形如：Duplicate entry 'xxx' for key 't_short_link.uk_origin_url_md5'
     * 比"回查数据库"可靠：不受隔离级别影响，也不用额外查询。
     */
    private boolean isOriginUrlMd5Conflict(DuplicateKeyException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(UK_ORIGIN_URL_MD5);
    }

    private ShortLink selectByMd5(String md5) {
        return shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getOriginUrlMd5, md5));
    }

    private ShortLink selectByShortCode(String code) {
        return shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, code));
    }

    private ShortLink persist(ShortLink shortLink) {
        shortLinkMapper.insert(shortLink);
        return shortLinkMapper.selectById(shortLink.getId());
    }

}
