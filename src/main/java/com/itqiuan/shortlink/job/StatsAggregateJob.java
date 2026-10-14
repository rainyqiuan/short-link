package com.itqiuan.shortlink.job;

import com.itqiuan.shortlink.config.ShortLinkProperties;
import com.itqiuan.shortlink.service.StatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 统计聚合任务：定时重算最近若干天的 PV/UV 并写入汇总表。
 * <p>
 * 与搬运任务一样，只对数据访问异常兜底 —— 聚合失败下一轮会重算（回看多天就是为了兜底）。
 */
@Slf4j
@Component
public class StatsAggregateJob {

    private final StatsService statsService;
    private final ShortLinkProperties shortLinkProperties;


    public StatsAggregateJob(StatsService statsService, ShortLinkProperties shortLinkProperties) {
        this.statsService = statsService;
        this.shortLinkProperties = shortLinkProperties;
    }

    /**
     * 按 stats-cron 触发；回看天数来自配置。
     */
    @Scheduled(cron="${short-link.stats-cron}", zone = "Asia/Shanghai")
    public void aggregateStats() {
        try {
            statsService.aggregate(shortLinkProperties.getStatsLookbackDays());
        } catch (DataAccessException e) {
            log.warn("聚合统计数据时发生异常", e);
        }
    }
}
