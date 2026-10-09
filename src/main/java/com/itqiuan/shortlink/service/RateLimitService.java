package com.itqiuan.shortlink.service;

import com.itqiuan.shortlink.bo.RateLimitResult;

public interface RateLimitService {
    RateLimitResult tryAcquire(String key, int capacity, int refillRate);
}
