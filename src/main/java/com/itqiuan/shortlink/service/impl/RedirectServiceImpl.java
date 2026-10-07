package com.itqiuan.shortlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itqiuan.shortlink.bo.ShortLinkCacheBO;
import com.itqiuan.shortlink.bo.ShortLinkCheckResult;
import com.itqiuan.shortlink.common.constant.RedisKeyConstant;
import com.itqiuan.shortlink.common.enums.LinkUsabilityEnum;
import com.itqiuan.shortlink.entity.ShortLink;
import com.itqiuan.shortlink.mapper.ShortLinkMapper;
import com.itqiuan.shortlink.service.RedirectService;
import com.itqiuan.shortlink.service.support.ShortLinkClassifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class RedirectServiceImpl implements RedirectService {

    @Autowired
    private ShortLinkMapper shortLinkMapper;
    @Autowired
    private ShortLinkClassifier shortLinkClassifier;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    // 基准 TTL：24 小时
    private static final long BASE_TTL_SECONDS = 86400L;
    // 抖动幅度：最多提前 1 小时
    private static final long JITTER_SECONDS = 3600L;


    @Override
    public ShortLinkCheckResult redirect(String shortCode) {
        String key = RedisKeyConstant.BUFFER + shortCode;
        ShortLinkCacheBO shortLinkCacheBO = null;
        boolean cacheReadOk = true;
        try {
            String jsonValue = stringRedisTemplate.opsForValue().get(key);
            if (jsonValue == null) {
                // 缓存未命中，跳过解析，继续走
                log.debug("缓存未命中，key: {}", key);
            } else {
                // 只有非 null 时才解析
                shortLinkCacheBO = objectMapper.readValue(jsonValue, ShortLinkCacheBO.class);
            }
        } catch (Exception e) {
            cacheReadOk = false;
            log.warn("读缓存失败，降级查库", e);
        }
        if (shortLinkCacheBO == null) {
            try {
                ShortLink shortLink = shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, shortCode));
                if (shortLink == null || shortLink.getOriginUrl() == null || shortLink.getStatus() == null) {
                    return ShortLinkCheckResult.fail(LinkUsabilityEnum.NOT_FOUND);
                }
                shortLinkCacheBO = new ShortLinkCacheBO(shortLink.getOriginUrl(), shortLink.getStatus(), shortLink.getExpireTime());
                Long ttlSeconds = calcTtlSeconds(shortLink.getExpireTime());
                if (cacheReadOk && ttlSeconds != null) {
                    stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(shortLinkCacheBO), ttlSeconds, TimeUnit.SECONDS);
                }
            } catch (JsonProcessingException e) {
                log.error("写缓存序列化失败",e);
            } catch (DataAccessException e) {
                log.error("写缓存失败",e);
            }
        }
        return toCheckResult(shortLinkCacheBO);
    }

    private ShortLinkCheckResult toCheckResult(ShortLinkCacheBO shortLinkCacheBO) {
        return switch (shortLinkClassifier.classify(shortLinkCacheBO.getStatus(), shortLinkCacheBO.getExpireTime())) {
            case REUSABLE -> ShortLinkCheckResult.success(shortLinkCacheBO.getOriginUrl());
            case DISABLED -> ShortLinkCheckResult.fail(LinkUsabilityEnum.DISABLED);
            case EXPIRED -> ShortLinkCheckResult.fail(LinkUsabilityEnum.EXPIRED);
            case NOT_FOUND -> ShortLinkCheckResult.fail(LinkUsabilityEnum.NOT_FOUND);
            default -> ShortLinkCheckResult.fail(LinkUsabilityEnum.UNKNOWN);
        };
    }

    /** 计算缓存 TTL（秒）。返回 null 表示"这次不写缓存"。 */
    private Long calcTtlSeconds(LocalDateTime expireTime) {
        if (expireTime == null) {
            // "永不过期"：基准值 + 抖动（不存在活过 expire_time 的问题，方向随意）
            return BASE_TTL_SECONDS + ThreadLocalRandom.current().nextLong(JITTER_SECONDS);
        }
        long remaining = Duration.between(LocalDateTime.now(), expireTime).getSeconds();
        if (remaining <= 0) {
            // 已经过期：不写缓存（写了也立刻判 EXPIRED，且 TTL<=0 会被 Redis 拒绝）
            return null;
        }
        long ttl = Math.min(BASE_TTL_SECONDS, remaining)
                - ThreadLocalRandom.current().nextLong(JITTER_SECONDS);
        // 下限保护：抖动可能把 ttl 拉到 <= 0
        return Math.max(ttl, 1L);
    }


}
