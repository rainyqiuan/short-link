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

/**
 * 令牌桶限流实现：判定 + 扣减放在一段 Lua 里原子执行。
 * <p>
 * 关键设计：
 * <ul>
 *   <li>用 {@code DefaultRedisScript} 加载 lua/token_bucket.lua，一次 EVAL 完成
 *       "补令牌 + 扣令牌 + 返回剩余"；</li>
 *   <li><b>fail-open</b>：Redis 出问题时一律放行（只记日志）—— 限流是保护手段，
 *       不能因为"保护器坏了"就把正常请求也拒了（降级只能变慢，不能变错）；</li>
 *   <li>{@code @PostConstruct} 自检：启动时试跑一次脚本，Lua 语法/类型错了就直接阻止容器启动
 *       （脚本错误属于确定性故障，越早暴露越好）。</li>
 * </ul>
 */
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

    /**
     * 尝试取一个令牌。
     * <p>
     * 注意脚本参数都以<b>字符串</b>传入（Redis 的 ARGV 只能是字符串），Lua 里再 tonumber 转回数字。
     */
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

    /**
     * 启动自检：拿一个临时 key 试跑两次脚本。
     * <p>
     * 跑两次是为了覆盖"首次创建桶"和"桶已存在"两条分支 —— 只跑一次证明不了后者；
     * 脚本语法/类型错误会抛 RedisSystemException，这里直接把它升级成启动失败。
     * Redis 本身不可用（DataAccessException）则只 warn 跳过 —— 那是环境问题，不是脚本问题。
     */
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
