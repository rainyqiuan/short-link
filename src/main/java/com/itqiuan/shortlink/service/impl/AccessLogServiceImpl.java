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
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 访问日志实现：跳转链路上只入队、不在链路上写库；由定时任务批量搬运落库。
 * <p>
 * 为什么要缓冲：跳转是最热、最不能被拖慢的一环 —— 每次跳转写一条日志，写压力会直接压在同一张表上。
 * 改成"先 RPUSH 进 Redis List、定时批量落库"后，跳转链路<b>零写库</b>，写压力与读路径解耦。
 * <p>
 * 队列还有上限（accessQueueMaxSize）：超了就从头部裁掉最旧的 —— 宁可丢最早的历史日志，
 * 也不让队列无限涨把 Redis 内存吃穿。
 */
@Slf4j
@Service
public class AccessLogServiceImpl extends ServiceImpl<LinkAccessLogMapper, LinkAccessLog> implements AccessLogService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ShortLinkProperties shortLinkProperties;

    /** UA / Referer 的截断长度：这两个字段可能很长，截断避免撑爆库里的列。 */
    private static final int MAX = 512;

    public AccessLogServiceImpl(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper, TransactionTemplate transactionTemplate, ShortLinkProperties shortLinkProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.shortLinkProperties = shortLinkProperties;
    }

    /**
     * 记录一次跳转：把日志序列化后 RPUSH 进队列，紧接着 LTRIM 保留最后 N 条。
     * <p>
     * ① 取请求信息走 {@code RequestContextHolder}：可能有异步场景拿不到请求上下文，
     *   拿到就跳过（不能让统计把跳转搞挂）；
     * ② 入队失败（序列化/连接异常）只记日志 —— 丢一条日志只是统计少一次，不影响跳转本身。
     */
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
            int accessQueueMaxSize = shortLinkProperties.getAccessQueueMaxSize();
            String accessLogBOJson = objectMapper.writeValueAsString(accessLogBO);
            stringRedisTemplate.opsForList().rightPush(RedisKeyConstant.ACCESS_QUEUE, accessLogBOJson);
            stringRedisTemplate.opsForList().trim(RedisKeyConstant.ACCESS_QUEUE,  -accessQueueMaxSize, -1);
        } catch (JsonProcessingException e) {
            log.error("json序列化失败", e);
        } catch (DataAccessException e) {
            log.warn("redis连接异常", e);
        }

    }

    /**
     * 批量将访问日志写入数据库。
     * <p>
     * 循环 maxRound 轮，每轮：从队列取 flushBatchSize 条 → 反序列化 → 累计 UV（HLL）
     * → 落库 → 裁队列。
     * <p>
     * ⚠️ 顺序是这套机制的关键：<b>先落库成功、再裁队列</b>。若先裁后写，中途失败就真丢数据；
     * 反过来最坏只是"重复搬运一次"（下一轮还会看到同一批），重复远好过丢失。
     * （UV 的 HLL 写入在落库前，但 HLL 自带去重，重复 PFADD 不会重复计数。）
     */
    @Override
    public void flush() {
        int maxFetch = shortLinkProperties.getFlushBatchSize();
        int maxRound = shortLinkProperties.getMaxRound();

        long startedAt = System.currentTimeMillis();
        int totalFlushed = 0;
        Status transactionStatus = Status.LIMIT;

        try {
            for (int i = 0; i < maxRound; i++) {
                List<String> range = stringRedisTemplate.opsForList().range(RedisKeyConstant.ACCESS_QUEUE, 0, maxFetch - 1);
                long fetched = range.size();

                if (fetched == 0) {
                    transactionStatus = Status.CLEARED;
                    break;
                }

                //全部反序列化
                List<AccessLogBO> accessLogBOList = range.stream().map(item -> {
                    try {
                        return objectMapper.readValue(item, AccessLogBO.class);
                    } catch (JsonProcessingException e) {
                        log.error("json反序列化失败", e);
                    }
                    return null;
                }).filter(accessLogBO -> accessLogBO != null).collect(Collectors.toList());

                accessLogBOList.stream()
                        // 1. 分组：用逗号或者特定的对象作为分组键
                        .collect(Collectors.groupingBy(item -> item.getShortCode() + "_" + item.getStatDate()))
                        .forEach((groupKey, value) -> {
                            // 2. 从 list 中提取真实的业务字段
                            AccessLogBO firstItem = value.get(0);
                            String shortCode = firstItem.getShortCode();
                            // 3. 使用常量的唯一函数生成 Redis key
                            String redisKey = RedisKeyConstant.uvKey(shortCode, firstItem.getStatDate());
                            // 4. 写入 HLL
                            stringRedisTemplate.opsForHyperLogLog()
                                    .add(redisKey, value.stream().map(AccessLogBO::getClientIp).distinct().toArray(String[]::new));
                            stringRedisTemplate.expire(redisKey, Duration.ofSeconds(shortLinkProperties.getUvTtlSeconds()));

                        });

                List<LinkAccessLog> linkAccessLogList = accessLogBOList.stream().map(AccessLogServiceImpl::toEntity).collect(Collectors.toList());
                if (!linkAccessLogList.isEmpty()) {
                    transactionTemplate.execute(status -> {
                        saveBatch(linkAccessLogList);
                        return null;
                    });
                    // 累加搬运条数
                    totalFlushed += linkAccessLogList.size();
                }

                stringRedisTemplate.opsForList().trim(RedisKeyConstant.ACCESS_QUEUE, fetched, -1L);
                if (fetched < maxFetch) {
                    transactionStatus = Status.CLEARED;
                    break;
                }

            }
        }catch (DataAccessException e){
            transactionStatus = Status.ABORTED;
            log.warn("数据访问失败", e);
        } catch (RuntimeException e) {
            transactionStatus = Status.ABORTED;
            log.error("访问日志搬运过程中发生异常", e);
            throw e;
        } finally {
            long costMs = System.currentTimeMillis() - startedAt;
            switch (transactionStatus) {
                case CLEARED:{
                    if(totalFlushed==0){
                        log.debug("访问日志搬运完成，但无数据入库，耗时 {} ms", costMs);
                    }else{
                        log.info("访问日志搬运完成，入库 {} 条，耗时 {} ms", totalFlushed, costMs);
                    }

                }

                    break;
                case LIMIT:
                    log.info("访问日志搬运达到轮次上限，入库 {} 条，可能还有剩余，耗时 {} ms", totalFlushed, costMs);
                    break;
                case ABORTED:
                    log.warn("访问日志搬运异常中断，入库 {} 条，耗时 {} ms", totalFlushed, costMs);
                    break;
            }
        }
    }

    /** 截断到 max 长度；null 视为空串。 */
    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max));
    }

    /** BO → entity（落库前的字段搬运）。 */
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

    private enum Status {
        // 清理完成
        CLEARED,
        // 达到轮次上限
        LIMIT,
        // 中断
        ABORTED
    }

}
