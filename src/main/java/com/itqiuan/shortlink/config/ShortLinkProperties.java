package com.itqiuan.shortlink.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@Data
@ConfigurationProperties(prefix = "short-link")
public class ShortLinkProperties {
    @Min(value = 1, message = "batchSize 必须大于 0")
    private int batchSize;

    @Min(value = 1, message = "maxRound 必须大于 0")
    private int maxRound;

    @Min(value = 1, message = "flushBatchSize 必须大于 0")
    private int flushBatchSize;

    @Min(value = 1, message = "limitCapacity 必须大于 0")
    private int limitCapacity;

    @Min(value = 1, message = "limitRefillRate 必须大于 0")
    private int limitRefillRate;

    @Min(value = 1, message = "statsLookbackDays 必须大于 0")
    private int statsLookbackDays;

    @Min(value = 1, message = "uvTtlSeconds 必须大于 0")
    private int uvTtlSeconds;

    @Min(value = 1, message = "accessQueueMaxSize 必须大于 0")
    private int accessQueueMaxSize;


}
