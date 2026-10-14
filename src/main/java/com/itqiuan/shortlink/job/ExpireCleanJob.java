package com.itqiuan.shortlink.job;

import com.itqiuan.shortlink.config.ShortLinkProperties;
import com.itqiuan.shortlink.service.ExpireCleanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 过期短链清理任务：定时分批删除已过期数据。
 */
@Slf4j
@Component
public class ExpireCleanJob {

    private final ExpireCleanService expireCleanService;
    private final ShortLinkProperties properties;

    public ExpireCleanJob(ShortLinkProperties properties, ExpireCleanService expireCleanService) {
        this.properties = properties;
        this.expireCleanService = expireCleanService;
    }

    /**
     * 按 clean-cron 触发；批大小与轮次上限都来自配置（见 ShortLinkProperties）。
     */
    @Scheduled(cron = "${short-link.clean-cron}", zone = "Asia/Shanghai")
    public void cleanExpireShortLink() {
        log.info("调度开始");
        expireCleanService.cleanExpired(properties.getBatchSize(), properties.getMaxRound());
    }

}
