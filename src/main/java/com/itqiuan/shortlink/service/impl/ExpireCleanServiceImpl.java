package com.itqiuan.shortlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itqiuan.shortlink.entity.ShortLink;
import com.itqiuan.shortlink.mapper.ShortLinkMapper;
import com.itqiuan.shortlink.service.ExpireCleanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * @author qiuan
 */
@Slf4j
@Service
public class ExpireCleanServiceImpl implements ExpireCleanService {

    @Autowired
    private ShortLinkMapper shortLinkMapper;

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
