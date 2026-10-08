package com.itqiuan.shortlink.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itqiuan.shortlink.bo.AccessLogBO;
import com.itqiuan.shortlink.common.constant.RedisKeyConstant;
import com.itqiuan.shortlink.config.ShortLinkProperties;
import com.itqiuan.shortlink.entity.LinkAccessLog;
import com.itqiuan.shortlink.mapper.LinkAccessLogMapper;
import com.itqiuan.shortlink.service.AccessLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccessLogServiceImpl extends ServiceImpl<LinkAccessLogMapper, LinkAccessLog> implements AccessLogService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ShortLinkProperties shortLinkProperties;

    private static final int MAX = 512;

    @Override
    public void record(String shortCode) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            log.debug("非请求线程，跳过访问记录");
            return;
        }
        HttpServletRequest request = sra.getRequest();
        AccessLogBO accessLogBO = new AccessLogBO();
        LocalDateTime now = LocalDateTime.now();
        accessLogBO.setShortCode(shortCode);
        accessLogBO.setClientIp(request.getRemoteAddr());
        accessLogBO.setUserAgent(truncate(request.getHeader("User-Agent"), MAX));
        accessLogBO.setReferer(truncate(request.getHeader("Referer"), MAX));
        accessLogBO.setAccessTime(now);
        accessLogBO.setStatDate(now.toLocalDate());
        try {
            String accessLogBOJson = objectMapper.writeValueAsString(accessLogBO);
            stringRedisTemplate.opsForList().rightPush(RedisKeyConstant.ACCESS_QUEUE, accessLogBOJson);
        } catch (JsonProcessingException e) {
            log.error("json序列化失败", e);
        } catch (DataAccessException e) {
            log.warn("redis连接异常", e);
        }
    }

    @Override
    public void flush() {
        int maxFetch = shortLinkProperties.getFlushBatchSize();
        List<String> range = stringRedisTemplate.opsForList().range(RedisKeyConstant.ACCESS_QUEUE, 0, maxFetch - 1);
        long fetched = range.size();
        //全部反序列化
        List<AccessLogBO> accessLogBOList = range.stream().map(item -> {
            try {
                return objectMapper.readValue(item, AccessLogBO.class);
            } catch (JsonProcessingException e) {
                log.error("json反序列化失败", e);
            }
            return null;
        }).filter(accessLogBO -> accessLogBO != null).collect(Collectors.toList());
        List<LinkAccessLog> linkAccessLogList = accessLogBOList.stream().map(AccessLogServiceImpl::toEntity).collect(Collectors.toList());
        transactionTemplate.execute(status -> {
            saveBatch(linkAccessLogList);
            return null;
        });
        stringRedisTemplate.opsForList().trim(RedisKeyConstant.ACCESS_QUEUE, fetched, -1L);

    }

    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max));
    }

    private static LinkAccessLog toEntity(AccessLogBO accessLogBO) {
        LinkAccessLog linkAccessLog = new LinkAccessLog();
        linkAccessLog.setShortCode(accessLogBO.getShortCode());
        linkAccessLog.setClientIp(accessLogBO.getClientIp());
        linkAccessLog.setUserAgent(accessLogBO.getUserAgent());
        linkAccessLog.setReferer(accessLogBO.getReferer());
        linkAccessLog.setAccessTime(accessLogBO.getAccessTime());
        linkAccessLog.setStatDate(accessLogBO.getStatDate());
        return linkAccessLog;
    }
}
