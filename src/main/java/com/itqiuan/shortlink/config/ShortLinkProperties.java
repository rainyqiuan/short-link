package com.itqiuan.shortlink.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 自定义配置：绑定 application.yml 里 {@code short-link.*} 前缀的值。
 * <p>
 * {@code @Validated + @Min} 的作用是<b>启动即失败</b>：配置漏了或给了 0/负数，应用直接起不来，
 * 而不是等运行到那行代码才炸 —— 配置错误的暴露时机越早越好。
 * <p>
 * 注：几个 cron（clean/flush/stats-cron）没有在这里绑定，是 job 里用
 * {@code @Scheduled(cron="${short-link.xxx-cron}")} 直接读的，所以不在此列。
 */
@Component
@Validated
@Data
@ConfigurationProperties(prefix = "short-link")
public class ShortLinkProperties {
    /** 过期清理每批删除的行数（分批删，避免一次锁太多行、事务过大）。 */
    @Min(value = 1, message = "batchSize 必须大于 0")
    private int batchSize;

    /** 单个任务一轮执行内最多循环几次（过期清理与日志搬运共用这个上限）。 */
    @Min(value = 1, message = "maxRound 必须大于 0")
    private int maxRound;

    /** 日志搬运每轮从队列取出处理的条数。 */
    @Min(value = 1, message = "flushBatchSize 必须大于 0")
    private int flushBatchSize;

    /** 令牌桶容量：桶里最多存多少个令牌（决定一次性可放行的突发请求数）。 */
    @Min(value = 1, message = "limitCapacity 必须大于 0")
    private int limitCapacity;

    /** 令牌补充速率：每秒补多少个（决定长期平均 QPS 上限）。 */
    @Min(value = 1, message = "limitRefillRate 必须大于 0")
    private int limitRefillRate;

    /** 统计聚合回看天数：每次重算最近 N 天，兜住迟到或补跑的数据。 */
    @Min(value = 1, message = "statsLookbackDays 必须大于 0")
    private int statsLookbackDays;

    /** UV（HyperLogLog）key 的存活时长；过期后该天的 UV 需要重新统计。 */
    @Min(value = 1, message = "uvTtlSeconds 必须大于 0")
    private int uvTtlSeconds;

    /** 访问日志队列的封顶长度：超了就裁掉最旧的（宁可丢最早日志，也不让队列无限涨）。 */
    @Min(value = 1, message = "accessQueueMaxSize 必须大于 0")
    private int accessQueueMaxSize;


}
