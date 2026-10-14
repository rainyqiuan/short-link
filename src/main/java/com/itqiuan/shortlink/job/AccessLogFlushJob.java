package com.itqiuan.shortlink.job;

import com.itqiuan.shortlink.service.AccessLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 访问日志搬运任务：定时把 Redis 队列里的日志批量落库。
 * <p>
 * 为什么在 job 里 try/catch：定时任务抛出的异常没人接，只会刷一堆堆栈；
 * 这里只对"数据访问异常"兜底记日志，下一轮照常重试（数据仍在队列里，不会丢）。
 */
@Slf4j
@Component
public class AccessLogFlushJob {

    private final AccessLogService accessLogService;

    public AccessLogFlushJob(AccessLogService accessLogService) {
        this.accessLogService = accessLogService;
    }

    /**
     * 按 flush-cron 触发；zone 固定 Asia/Shanghai，避免跟随机器时区漂移。
     */
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
