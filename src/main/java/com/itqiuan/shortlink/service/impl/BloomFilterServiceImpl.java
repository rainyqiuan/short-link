package com.itqiuan.shortlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itqiuan.shortlink.common.constant.RedisKeyConstant;
import com.itqiuan.shortlink.entity.ShortLink;
import com.itqiuan.shortlink.mapper.ShortLinkMapper;
import com.itqiuan.shortlink.service.BloomFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 布隆过滤器实现：拦截"一定不存在"的短码，挡住缓存穿透。
 * <p>
 * 参数：位数组 m = 9_580_000 位、hash 个数 k = 7 —— 按预期数据量与 1% 误判率算出来的。
 * 两个 hash 由一次 MD5 摘要的前 8 字节 + 后 8 字节派生，再用线性组合凑出 k 个下标，
 * 省掉引额外 hash 库。
 * <p>
 * 三类操作的降级：add 失败只记日志；mightContain 失败一律返回 true 放行；
 * rebuild 失败降级 —— 绝不因为布隆挂了就让正常请求查不到。
 */
@Service
@Slf4j
public class BloomFilterServiceImpl implements BloomFilterService {

    /** 位数组长度 m（单位：位）。 */
    private static final long BIT_SIZE = 9_580_000L;

    /** hash 个数 k（一个元素映射 k 个位）。 */
    private static final int HASH_COUNT = 7;

    /** 重建时每页扫描的行数。 */
    private static final int PAGE_SIZE = 1000;

    private final StringRedisTemplate stringRedisTemplate;
    private final ShortLinkMapper shortLinkMapper;

    public BloomFilterServiceImpl(StringRedisTemplate stringRedisTemplate, ShortLinkMapper shortLinkMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.shortLinkMapper = shortLinkMapper;
    }


    /**
     * 加一个短码。
     * <p>
     * setBits 会抛 DataAccessException，这里包一层降级：漏加最多让这个短码
     * 首次访问多走一次库，不影响正确性。
     */
    @Override
    public void addToBloomFilter(String shortCode) {
        try {
            setBits(shortCode);
        } catch (DataAccessException e) {
            log.warn("添加到布隆过滤器失败", e);
        }
    }

    /**
     * 判断短码"可能存在"。
     * <p>
     * 顺序很关键：<b>先看 READY 标志</b> —— 位图还没建好时直接放行，
     * 否则会把所有短码都判成"不存在"（位图是空的，任何下标查出来都是 0）。
     * <p>
     * 一次 BITFIELD 批量取 k 个位（而不是 k 次 GETBIT），把往返从 k 次压到 1 次。
     */
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
                long index = indexAt(indices, i);
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

    /**
     * 全量重建：先删位图和 READY 标志，再按主键游标<b>分页</b>扫全表逐条加位，最后才置回 READY。
     * <p>
     * 用"主键游标"而不是 OFFSET 分页：OFFSET 深分页会越翻越慢，主键游标翻页代价恒定。
     */
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

    /** @param shortCode 短码；把它的 k 个下标位全置 1 */
    private void setBits(String shortCode) {
        long[] indices = indices(shortCode);
        for (int i = 0; i < HASH_COUNT; i++) {
            long index = indexAt(indices, i);
            stringRedisTemplate.opsForValue().setBit(RedisKeyConstant.BLOOM, index, true);
        }
    }

    /**
     * 应用就绪后自动重建：只有位图缺失（比如 Redis 被清过）时才补齐，
     * 已就绪则跳过，避免每次重启都白扫一遍全表。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildBloomFilterOnStartup() {
        try {
            if (stringRedisTemplate.hasKey(RedisKeyConstant.BLOOM_READY)) {
                log.info("布隆过滤器已就绪，跳过重建");
                return;
            }
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，跳过布隆过滤器就绪检查", e);
        }
        log.info("布隆过滤器未就绪，开始重建");
        rebuildBloomFilter();
    }


    /**
     * 由短码派生两个 64 位 hash：取 MD5 摘要（16 字节）的前 8 / 后 8 字节。
     */
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

    /**
     * 第 i 个下标 = (h1 + i*h2) mod m —— 双 hash 线性组合出 k 个位置。
     * <p>
     * 用 Math.floorMod 而不是 %：h1 与 i*h2 相加后可能超出 int/long 范围、也可能为负，
     * 直接用 % 在参数为负时会得到负下标，floorMod 保证结果恒为非负。
     */
    private long indexAt(long[] indices, int i) {
        return Math.floorMod(Math.floorMod(indices[0], BIT_SIZE)
                + i * Math.floorMod(indices[1], BIT_SIZE), BIT_SIZE);
    }


}
