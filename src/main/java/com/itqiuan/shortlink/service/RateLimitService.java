package com.itqiuan.shortlink.service;

import com.itqiuan.shortlink.bo.RateLimitResult;

/**
 * 限流服务：基于 Redis + Lua 的令牌桶。
 */
public interface RateLimitService {

    /**
     * 尝试取一个令牌。
     * <p>
     * 判定与扣减必须原子（放在同一段 Lua 里），否则并发下会超发。
     *
     * @param key        桶的标识（已含 route 与 ip）
     * @param capacity   桶容量（突发上限）
     * @param refillRate 每秒补充的令牌数（长期平均 QPS 上限）
     * @return 放行与否 + 建议等待秒数
     */
    RateLimitResult tryAcquire(String key, int capacity, int refillRate);
}
