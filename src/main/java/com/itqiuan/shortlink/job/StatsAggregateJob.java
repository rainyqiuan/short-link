package com.itqiuan.shortlink.job;

import com.itqiuan.shortlink.config.ShortLinkProperties;
import com.itqiuan.shortlink.service.StatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StatsAggregateJob {

    private final StatsService statsService;
    private final ShortLinkProperties shortLinkProperties;


    public StatsAggregateJob(StatsService statsService, ShortLinkProperties shortLinkProperties) {
        this.statsService = statsService;
        this.shortLinkProperties = shortLinkProperties;
    }

    @Scheduled(cron="${short-link.stats-cron}", zone = "Asia/Shanghai")
    public void aggregateStats() {
        try {
            statsService.aggregate(shortLinkProperties.getStatsLookbackDays());
        } catch (DataAccessException e) {
            log.warn("聚合统计数据时发生异常", e);
        }
    }
}
