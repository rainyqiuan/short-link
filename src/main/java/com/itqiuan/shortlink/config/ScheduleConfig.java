package com.itqiuan.shortlink.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关：开启 {@code @Scheduled} 支持。
 * <p>
 * 项目里的定时任务（访问日志搬运、统计聚合、过期清理）分散在 job/ 包下，
 * 统一靠这里的 {@code @EnableScheduling} 生效。
 */
@Configuration
@EnableScheduling
public class ScheduleConfig {


}
