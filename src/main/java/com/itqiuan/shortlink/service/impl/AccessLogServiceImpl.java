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
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * 访问日志实现：跳转链路上只入队、不在链路上写库；由定时任务批量搬运落库。
 * <p>
 * 为什么要缓冲：跳转是最热、最不能被拖慢的一环 —— 每次跳转写一条日志，写压力会直接压在同一张表上。
 * 改成"先 RPUSH 进 Redis List、定时批量落库"后，跳转链路<b>零写库</b>，写压力与读路径解耦。
 * <p>
 * 队列还有上限（accessQueueMaxSize）：超了就从头部裁掉最旧的 —— 宁可丢最早的历史日志，
 * 也不让队列无限涨把 Redis 内存吃穿。
 * <p>
 * "入队 + 容量裁剪"在<b>同一段 Lua 里原子完成</b>（一次 EVAL / EVALSHA），而不是发两条命令：
 * 既省一次往返，也消掉"入队"与"裁剪"之间被别的客户端插队的窗口。
 * 脚本契约（KEYS/ARGV/返回值三态）见 {@code resources/lua/access_log_enqueue.lua} 头部注释。
 */
@Slf4j
@Service
public class AccessLogServiceImpl extends ServiceImpl<LinkAccessLogMapper, LinkAccessLog> implements AccessLogService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ShortLinkProperties shortLinkProperties;
    private final DefaultRedisScript<Long> script;
    private final LongAdder droppedByCapTotal = new LongAdder();
    private final LongAdder enqueueFailureTotal = new LongAdder();
    private long lastReportedDropped;
    private long lastReportedFailed;
    /**
     * UA / Referer 的截断长度：这两个字段可能很长，截断避免撑爆库里的列。
     */
    private static final int MAX = 512;

    public AccessLogServiceImpl(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper, TransactionTemplate transactionTemplate, ShortLinkProperties shortLinkProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.shortLinkProperties = shortLinkProperties;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("lua/access_log_enqueue.lua"));
        this.script.setResultType(Long.class);
    }

    /**
     * 记录一次跳转：把日志交给 Lua 脚本入队（<b>一次往返</b>完成"入队 + 容量裁剪"）。
     * <p>
     * ① 取请求信息走 {@code RequestContextHolder}：异步 / 非请求线程<b>拿不到</b>请求上下文，
     * 拿不到就跳过（不能让统计把跳转搞挂）。
     * ⚠️ 也正因如此，本方法<b>不能直接挪进线程池（@Async）执行</b>：换了线程就恒定走这个跳过分支，
     * 日志会全部静默消失、而接口照常 200。真要异步，必须先在当前线程把 ip / UA / Referer 取出来传进去；
     * ② 入队失败（序列化 / 连接异常）只记日志 —— 丢一条日志只是统计少一次，不影响跳转本身；
     * ③ 脚本返回三态：0 = 正常；&gt;0 = 本次被裁掉的条数；-1 = cap 参数非法（见脚本注释）。
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
            long dropped = stringRedisTemplate.execute(script, List.of(RedisKeyConstant.ACCESS_QUEUE), accessLogBOJson, String.valueOf(accessQueueMaxSize));
            if (dropped == 0) {

            } else if (dropped > 0) {
                droppedByCapTotal.add(dropped);
                log.debug("入队成功，队列超限裁掉最旧 {} 条", dropped);
            } else {
                log.error("入队失败，cap 参数非法，值为{}", accessQueueMaxSize);
            }
        } catch (JsonProcessingException e) {
            log.error("json序列化失败", e);
        } catch (DataAccessException e) {
            enqueueFailureTotal.increment();
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
        } catch (DataAccessException e) {
            transactionStatus = Status.ABORTED;
            log.warn("数据访问失败", e);
        } catch (RuntimeException e) {
            transactionStatus = Status.ABORTED;
            log.error("访问日志搬运过程中发生异常", e);
            throw e;
        } finally {
            long costMs = System.currentTimeMillis() - startedAt;
            switch (transactionStatus) {
                case CLEARED: {
                    if (totalFlushed == 0) {
                        log.debug("访问日志搬运完成，但无数据入库，耗时 {} ms", costMs);
                    } else {
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
            long droppedDelta = droppedByCapTotal.sum() - lastReportedDropped;
            lastReportedDropped = droppedByCapTotal.sum();
            if (droppedDelta > 0) {
                log.warn("本轮新增丢弃 {} 条（累计 {}）", droppedDelta, droppedByCapTotal.sum());
            }
            long enqueueFailureDelta = enqueueFailureTotal.sum() - lastReportedFailed;
            lastReportedFailed = enqueueFailureTotal.sum();
            if (enqueueFailureDelta > 0) {
                log.warn("本轮新增入队失败 {} 次（累计 {} 次）", enqueueFailureDelta, enqueueFailureTotal.sum());
            }
        }
    }

    /**
     * 启动自检：拿临时 key 试跑两次入队脚本。两个作用：
     * <ol>
     *   <li><b>尽早暴露脚本错误</b>：Lua 语法 / 类型错误会在第一次 execute 时抛
     *       {@link RedisSystemException}。这里把它升级成启动失败 —— 否则它会被
     *       {@link #record(String)} 里的 {@code catch (DataAccessException)} 吞掉
     *       （{@code RedisSystemException} 正是其子类），还被记成"redis连接异常"，
     *       排障时会往网络方向白查半天；</li>
     *   <li><b>预热 Redis 脚本缓存</b>：Spring 的执行顺序是"先 EVALSHA、遇 NOSCRIPT 才回退
     *       带脚本体的 EVAL"，而 Redis 的脚本缓存<b>不持久化</b>（重启即空）。启动时跑一次，
     *       第一个真实请求就能直接命中 EVALSHA，否则它要付两次往返 + 一次异常构造。</li>
     * </ol>
     * 跑两次是为了覆盖"队列不存在（新建）"和"队列已存在"两条分支 —— 只跑一次证明不了后者；
     * 参数刻意给 cap=1，第二次会踩到"超限裁剪"分支，顺带把那条路也走到。
     * Redis 本身不可用（DataAccessException）只 warn 跳过 —— 那是环境问题，不是脚本问题。
     */
    @PostConstruct
    public void initScript() {
        try {
            // 参数顺序照脚本契约：ARGV[1] = 日志 JSON（自检给个占位串），ARGV[2] = cap。
            // ⚠️ 只能传这两个 —— 限流脚本要 3 个 ARGV，从它那儿抄代码时很容易顺手多带一个 "0"；
            //   本脚本从不读 ARGV[3]，多传不会报错，但会让后来人以为脚本有第 3 个参数。
            stringRedisTemplate.execute(script, List.of(RedisKeyConstant.TEST_INIT_ACCESS), "1", "1");
            stringRedisTemplate.execute(script, List.of(RedisKeyConstant.TEST_INIT_ACCESS), "1", "1");
            log.info("Lua 访问日志入队脚本自检通过，加载成功");
        } catch (RedisSystemException e) {
            // 在启动时抛出异常，直接阻止 Spring 容器启动
            throw new RuntimeException("【致命】Lua 访问日志入队脚本初始化失败，请检查脚本逻辑！", e);
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，跳过 Lua 脚本自检", e);
        } finally {
            // 清理可能产生的临时数据（其实不清理也行，因为是假 key，会自己过期）
            try {
                stringRedisTemplate.delete(RedisKeyConstant.TEST_INIT_ACCESS);
            } catch (Exception e) {
                log.warn("Lua 访问日志入队脚本自检后，清理临时数据时出错", e);
            }
        }
    }


    /**
     * 截断到 max 长度；null 视为空串。
     */
    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max));
    }

    /**
     * BO → entity（落库前的字段搬运）。
     */
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
