package com.itqiuan.shortlink.job;

import com.itqiuan.shortlink.service.AccessLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AccessLogFlushJob {

    private final AccessLogService accessLogService;

    public AccessLogFlushJob(AccessLogService accessLogService) {
        this.accessLogService = accessLogService;
    }

    @Scheduled(cron = "${short-link.flush-cron}", zone = "Asia/Shanghai")
    public void flushAccessLog() {
        log.info("搬运开始");
        try {
            accessLogService.flush();
        } catch (DataAccessException e) {
            log.warn("搬运数据时发生异常", e);
        }
    }
}
