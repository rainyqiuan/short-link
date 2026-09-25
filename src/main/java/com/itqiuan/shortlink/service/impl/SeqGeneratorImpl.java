package com.itqiuan.shortlink.service.impl;

import com.itqiuan.shortlink.common.constant.RedisKeyConstant;
import com.itqiuan.shortlink.common.exception.BusinessException;
import com.itqiuan.shortlink.common.result.ResultCode;
import com.itqiuan.shortlink.service.SeqGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SeqGeneratorImpl implements SeqGenerator {

    @Autowired
    StringRedisTemplate stringRedisTemplate;


    @Override
    public Long generateSeqNo() {
        // 1. 执行 INCR，获取自增ID
        Long seq = stringRedisTemplate.opsForValue().increment(RedisKeyConstant.SEQ);

        if(seq == null){
            throw new BusinessException(ResultCode.DEPENDENCY_ERROR,"redis 获取自增ID失败");
        }
        return seq;
    }
}
