package com.itqiuan.shortlink.service.impl;

import com.itqiuan.shortlink.bo.RateLimitResult;
import com.itqiuan.shortlink.common.constant.RedisKeyConstant;
import com.itqiuan.shortlink.service.RateLimitService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class RateLimitServiceImpl implements RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> script;

    public RateLimitServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("lua/token_bucket.lua"));
        this.script.setResultType(Long.class);
    }

    @Override
    public RateLimitResult tryAcquire(String key, int capacity, int refillRate) {
        String redisKey = RedisKeyConstant.LIMIT + key;
        // 获取当前时间戳（秒级）
        double now = System.currentTimeMillis() / 1000.0;
        RateLimitResult rateLimitResult;
        try {
            long result = stringRedisTemplate.execute(script, List.of(redisKey), String.valueOf(capacity), String.valueOf(refillRate), String.valueOf(now));
            rateLimitResult = buildResult(result);
        } catch (RedisSystemException e) {
            log.error("Redis 限流异常，触发 fail-open 降级，请求 key: {}", key, e);
            rateLimitResult = new RateLimitResult(true, 0);
        } catch (DataAccessException e) {
            log.warn("Redis 限流异常，触发 fail-open 降级，请求 key: {}", key, e);
            rateLimitResult = new RateLimitResult(true, 0);
        }
        return rateLimitResult;
    }

    @PostConstruct
    public void initScript() {
        try {
            // 用一个临时无用的 key 和参数，试跑一次脚本
            // 如果 Lua 语法有错，或者类型不对，这里会立刻抛出 RedisSystemException
            stringRedisTemplate.execute(script, List.of(RedisKeyConstant.TEST_INIT), "1", "1", "0");
            stringRedisTemplate.execute(script, List.of(RedisKeyConstant.TEST_INIT), "1", "1", "0");
            log.info("Lua 限流脚本自检通过，加载成功");
        } catch (RedisSystemException e) {
            // 在启动时抛出异常，直接阻止 Spring 容器启动
            throw new RuntimeException("【致命】Lua 限流脚本初始化失败，请检查脚本逻辑！", e);
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，跳过 Lua 脚本自检", e);
        } finally {
            // 清理可能产生的临时数据（其实不清理也行，因为是假 key，会自己过期）
            try {
                stringRedisTemplate.delete(RedisKeyConstant.TEST_INIT);
            } catch (Exception e) {
                log.warn("Redis 限流脚本初始化失败，清理临时数据时出错", e);
            }
        }
    }

    private RateLimitResult buildResult(long result) {
        if (result == 0) {
            return new RateLimitResult(true, result);
        }
        return new RateLimitResult(false, result);
    }
}
