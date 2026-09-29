package com.itqiuan.shortlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itqiuan.shortlink.entity.ShortLink;
import com.itqiuan.shortlink.mapper.ShortLinkMapper;
import com.itqiuan.shortlink.service.ExpireCleanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ExpireCleanServiceImpl implements ExpireCleanService {

    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ShortLinkMapper shortLinkMapper;

    @Override
    public void cleanExpired(int batchSize, int maxRound) {
        if (batchSize <= 0 || maxRound <= 0){
            throw new IllegalArgumentException("batchSize和maxRound必须大于0");
        }
        LocalDateTime cutoff = LocalDateTime.now();
        LocalDateTime startedAt = LocalDateTime.now();
        int deletedCount = 0;
        for (int batch = 0; batch < maxRound; batch++) {
            try {
                List<Long> expireIds = shortLinkMapper.selectList(new LambdaQueryWrapper<ShortLink>()
                                .select(ShortLink::getId).orderByAsc(ShortLink::getId)
                                .lt(ShortLink::getExpireTime, cutoff).last("limit " + batchSize))
                        .stream()
                        .map(ShortLink::getId)
                        .toList();
                int result = transactionTemplate.execute(status -> {
                    return shortLinkMapper.deleteByIds(expireIds);
                });
                deletedCount += result;
                if (expireIds.size() < batchSize) {
                    break;
                }
            } catch (Exception e) {
                log.error("清理过期数据时发生异常", e);
            }
        }
        log.info("已删除 {} 条过期数据,耗时 {} ms", deletedCount, Duration.between(startedAt, LocalDateTime.now()).toMillis());
    }
}
