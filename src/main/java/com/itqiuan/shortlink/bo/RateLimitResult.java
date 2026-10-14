package com.itqiuan.shortlink.bo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 令牌桶的判定结果。
 * <p>
 * 把"放不放行"和"被限时要等多久"打包成一个对象返回，让调用方（拦截器）
 * 一次拿到全部所需信息，不用再回查 Redis。
 */
@Data
@AllArgsConstructor
public class RateLimitResult {

    /** 是否放行（true = 桶里还有令牌）。 */
    private boolean allowed;

    /** 被限流时建议的重试等待秒数（写进 429 响应的 Retry-After 头；放行时为 0）。 */
    private long retryAfterSeconds;
}
