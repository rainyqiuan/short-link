package com.itqiuan.shortlink.service.impl;

import com.itqiuan.shortlink.common.constant.RedisKeyConstant;
import com.itqiuan.shortlink.common.exception.BusinessException;
import com.itqiuan.shortlink.common.result.ResultCode;
import com.itqiuan.shortlink.service.SeqGenerator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 发号器实现：用 Redis INCR 做全局自增。
 * <p>
 * 选它而不是数据库自增 ID 的原因：Redis 单线程执行，INCR 天然原子，
 * 且不占用数据库连接、不用和业务事务绑在一起。
 */
@Service
public class SeqGeneratorImpl implements SeqGenerator {

    private final StringRedisTemplate stringRedisTemplate;

    public SeqGeneratorImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    /**
     * 取一个序号。
     * <p>
     * INCR 返回 null 只可能是连接/序列化层面的异常，属于依赖故障，直接抛 50001。
     */
    @Override
    public Long generateSeqNo() {
        // 1. 执行 INCR，获取自增ID
        Long seq = stringRedisTemplate.opsForValue().increment(RedisKeyConstant.SEQ);

        if (seq == null) {
            throw new BusinessException(ResultCode.DEPENDENCY_ERROR, "redis 获取自增ID失败");
        }
        return seq;
    }
}
