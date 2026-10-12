package com.itqiuan.shortlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itqiuan.shortlink.common.constant.RedisKeyConstant;
import com.itqiuan.shortlink.entity.ShortLink;
import com.itqiuan.shortlink.mapper.ShortLinkMapper;
import com.itqiuan.shortlink.service.BloomFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Service
@Slf4j
public class BloomFilterServiceImpl implements BloomFilterService {
    // m
    private static final long BIT_SIZE = 9_580_000L;
    // k
    private static final int HASH_COUNT = 7;

    private static final int PAGE_SIZE = 1000;

    private final StringRedisTemplate stringRedisTemplate;
    private final ShortLinkMapper shortLinkMapper;

    public BloomFilterServiceImpl(StringRedisTemplate stringRedisTemplate, ShortLinkMapper shortLinkMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.shortLinkMapper = shortLinkMapper;
    }


    @Override
    public void addToBloomFilter(String shortCode) {
        try {
            setBits(shortCode);
        } catch (DataAccessException e) {
            log.warn("添加到布隆过滤器失败", e);
        }
    }

    @Override
    public boolean mightContain(String shortCode) {
        try {
            //先查这个标志；标志不存在 → 直接返回 true（放行）
            if (!stringRedisTemplate.hasKey(RedisKeyConstant.BLOOM_READY)) {
                return true;
            }
            long[] indices = indices(shortCode);
            BitFieldSubCommands commands = BitFieldSubCommands.create();
            for (int i = 0; i < HASH_COUNT; i++) {
                long index = Math.floorMod((indices[0] + i * indices[1]), BIT_SIZE);
                BitFieldSubCommands bitFieldSubCommands = commands.get(BitFieldSubCommands.BitFieldType.unsigned(1)).valueAt(index);
                commands = bitFieldSubCommands;
            }
            List<Long> results = stringRedisTemplate.opsForValue()
                    .bitField(RedisKeyConstant.BLOOM, commands);
            if (results == null || results.size() != HASH_COUNT) {
                log.warn("布隆过滤器检查结果异常");
                return true;
            }
            for (Long bit : results) {
                if (bit == null || bit == 0L) {
                    return false;
                }
            }
            return true;
        } catch (DataAccessException e) {
            log.warn("检查布隆过滤器失败", e);
            return true;
        }
    }

    @Override
    public void rebuildBloomFilter() {
        try {
            //清空布隆过滤器
            stringRedisTemplate.delete(RedisKeyConstant.BLOOM_READY);
            stringRedisTemplate.delete(RedisKeyConstant.BLOOM);
            // 循环外：游标从 0 开始（id 是自增主键，从 1 起）
            long lastId = 0L;
            while (true) {
                List<ShortLink> shortLinks = shortLinkMapper.selectList(new LambdaQueryWrapper<ShortLink>()
                        .select(ShortLink::getId, ShortLink::getShortCode)
                        .gt(ShortLink::getId, lastId)
                        .orderByAsc(ShortLink::getId)
                        .last("limit " + PAGE_SIZE));
                if (shortLinks.isEmpty()) {
                    break;
                }
                for (ShortLink shortLink : shortLinks) {
                    setBits(shortLink.getShortCode());
                }
                lastId = shortLinks.get(shortLinks.size() - 1).getId();
                if (shortLinks.size() < PAGE_SIZE) {
                    break;
                }
            }
            stringRedisTemplate.opsForValue().set(RedisKeyConstant.BLOOM_READY, "1");
        } catch (DataAccessException e) {
            log.warn("重建布隆过滤器失败", e);
        }
    }

    private void setBits(String shortCode) {
        long[] indices = indices(shortCode);
        for (int i = 0; i < HASH_COUNT; i++) {
            long index = Math.floorMod((indices[0] + i * indices[1]), BIT_SIZE);
            stringRedisTemplate.opsForValue().setBit(RedisKeyConstant.BLOOM, index, true);
        }
    }

    private long[] indices(String shortCode) {
        long[] indices = new long[2];
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(shortCode.getBytes(StandardCharsets.UTF_8));
            // digest 有 16 字节，取前 8 字节拼成 long
            ByteBuffer buffer = ByteBuffer.wrap(digest);
            indices[0] = buffer.getLong();
            indices[1] = buffer.getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 MD5 算法（这是极其严重的系统错误）", e);
        }
        return indices;
    }
}
