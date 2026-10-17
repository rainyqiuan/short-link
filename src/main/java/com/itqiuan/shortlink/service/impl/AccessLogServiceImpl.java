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

    /**
     * 因容量封顶被裁掉的日志条数（累计）：脚本返回 {@code >0} 时 {@code add(dropped)}。
     * <p>
     * ⭐ 用 {@link LongAdder} 而不是 {@code AtomicLong}：{@link #record(String)} 由 Tomcat 工作线程
     * 并发调用（实测同一批请求落在 {@code exec-5/6/7} 三个线程上），{@code AtomicLong} 的高并发自增
     * 是<b>单点 CAS 竞争</b>，{@code LongAdder} 分散到 Cell 数组分段累加。代价是 {@link LongAdder#sum()}
     * <b>不是原子快照</b>，并发下可能略偏 —— 这两个数只用于告警，不参与任何业务判断，偏一点无所谓。
     * <p>
     * ⚠️ 它也<b>不能</b>注册成 Spring Bean、不该走构造器注入：无状态 JDK 工具类型，没有配置、
     * 没有生命周期、不会被替换，进容器只会多一层无意义的间接，还把它变成全局单例。
     * 曾经把这两个参数写进构造器（想走依赖注入）→ 应用直接起不来：
     * {@code required a bean of type 'java.util.concurrent.atomic.LongAdder' that could not be found}。
     * 正解就是这里 —— <b>字段声明处 new</b>。
     */
    private final LongAdder droppedByCapTotal = new LongAdder();

    /**
     * 入队失败次数（累计）：只在 {@code catch (DataAccessException)} 里 {@code increment()}。
     * <p>
     * 与 {@link #droppedByCapTotal} 同为 {@link LongAdder}，理由见上。
     * 注意它<b>不统计</b> JSON 序列化失败 —— 那是代码/数据问题，不是环境问题，混进来会让排障时分不清
     * "是 Redis 挂了还是 BO 写坏了"。
     */
    private final LongAdder enqueueFailureTotal = new LongAdder();

    /**
     * 上一次在 flush 周期里已经上报出去的两个累计值；{@code delta = sum() - lastReported}。
     * <p>
     * ⚠️⭐ 这两个字段就是"增量上报窗口连续"的全部实现，<b>别退化成"在 flush 开头读基线"</b>：
     * flush 本身只跑几十~几百毫秒，而两次 flush 之间隔着一整分钟。落在
     * "上次 flush 结束 → 本次 flush 开始"这段空隙里的增量<b>不属于任何窗口</b>，会被永久吞掉。
     * 实测过：cap 压到 5 连发 8 跳，计数器到 3，而 flush 在开头读到的基线<b>也是 3</b> → delta = 0 → 一条告警都没有。
     * 记住"上次报过的值"，每个增量才有且只有一个归属窗口。
     * <p>
     * 不加锁、不加 {@code volatile}：{@code @Scheduled} 默认<b>单线程</b>（日志里恒为 {@code [scheduling-1]}），
     * 这两个字段只被那一个线程读写。而 {@code record()} 是多线程，才需要上面的 {@code LongAdder} ——
     * <b>两个工具各自对应各自的实际并发模型，不是"越重越安全"</b>。
     */
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
            // cap 每跳读一次配置（Properties 是内存对象，代价可忽略）。不缓存成字段，
            // 是为了不给自己造第二个真源 —— 队列上限的唯一出处就是 application.yml。
            int accessQueueMaxSize = shortLinkProperties.getAccessQueueMaxSize();
            String accessLogBOJson = objectMapper.writeValueAsString(accessLogBO);
            // ⚠️ cap 必须 String.valueOf 成字符串：StringRedisTemplate 的 value 序列化器是
            //    StringRedisSerializer，只吃 String —— 直接传 int 会在序列化阶段就抛异常。
            long dropped = stringRedisTemplate.execute(script, List.of(RedisKeyConstant.ACCESS_QUEUE), accessLogBOJson, String.valueOf(accessQueueMaxSize));
            // 脚本返回值是「三态」，不是一个布尔。"0 什么都不做"必须显式存在，见下。
            if (dropped == 0) {
                // 常态：队列没到上限，什么都没裁。
                // 刻意保留这个「空分支」：让三态在代码结构上完整可见。若收成 `if (dropped > 0)`，
                // 后来人会以为返回值只有两种可能，将来脚本加第四种返回值时没人会回来改这里。
                // 也绝不能在这里打日志 —— 这是跳转热路径，每跳一次都会跑。
            } else if (dropped > 0) {
                droppedByCapTotal.add(dropped);
                // debug 级：灌爆场景要能看见"确实裁了"，但正常流量不该被刷屏（默认 info 不输出）。
                log.debug("入队成功，队列超限裁掉最旧 {} 条", dropped);
            } else {
                // 只剩 -1：cap 非法（非数字 / <= 0），脚本「既没入队也没裁剪」→ 本条日志已丢。
                // 生产路径走不到（ShortLinkProperties 上 @Min(1) + @Validated，配置坏了应用根本起不来），
                // 但万一走到就是"丢全部新日志"，所以是 error 不是 debug。
                log.error("入队失败，cap 参数非法，值为{}", accessQueueMaxSize);
            }
        } catch (JsonProcessingException e) {
            // 序列化失败意味着 BO 写坏了，是代码问题 —— 刻意「不」计入 enqueueFailureTotal，
            // 否则"Redis 挂了"和"BO 写坏了"会混进同一个数字，排障时分不清该往哪查。
            log.error("json序列化失败", e);
        } catch (DataAccessException e) {
            // 连接失败 / 超时 / 服务端报错都归到这里（LettuceExceptionConverter 统一转成 DataAccessException）。
            // 只计数、不往外抛：丢一条日志只是统计少一次，不能让"记录"把"跳转"搞挂。
            // ⚠️ RedisSystemException（脚本语法错 / WRONGTYPE / 参数类型不对）也是 DataAccessException 的子类，
            //    会被这条 catch 吞掉并伪装成"连接异常" —— 所以脚本本身的问题不能指望这里暴露，
            //    必须由 initScript() 在启动期拦死（见该方法注释）。
            //    日志文案沿用"redis连接异常"（历史命名，实际也涵盖服务端报错），见 docs/05 三之六十 §4。
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

                // trim(fetched, -1)：保留 [fetched, 末尾] 这个区间，等于删掉刚搬走的这 fetched 条。
                // ⚠️ 它在 if (!empty) 之外、无条件执行 —— 这是「游标推进」，不是「落库确认」：
                //    反序列化失败的坏条目本来也写不进库，若不推进，下一轮还会取到同一批坏数据，
                //    flush 会永远卡在这里。真正的"落库成功才裁"由「代码位置」保证：
                //    saveBatch 抛异常时走不到这一行（异常直接跳到 catch）。
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
            // ---------- 增量上报：把"丢了多少"从静默变成每分钟最多一行 warn ----------
            // 位置：finally 里、switch 「之后」—— ① 三种 transactionStatus（CLEARED / LIMIT / ABORTED）
            // 都会走到，放进某个 case 就会漏报；② flush 每 60 秒一次，它自己就是节流阀，
            // 放到 record() 里会被一波流量刷爆日志、还拖慢跳转。
            // ⚠️ 先 sum() 到局部变量再用：LongAdder.sum()「不是原子快照」，同一个表达式里连调两次，
            //    中间被并发更新就会写出"本轮新增 3 条（累计 4）"这种自相矛盾的数字。
            long droppedNow = droppedByCapTotal.sum();
            long droppedDelta = droppedNow - lastReportedDropped;
            // ⚠️ 这行必须在 if 外面无条件执行：delta 为 0 时也要把基线同步到当前值，
            //    否则下次上报会把"已经报过的"再算一遍。
            lastReportedDropped = droppedNow;
            if (droppedDelta > 0) {
                log.warn("本轮新增丢弃 {} 条（累计 {}）", droppedDelta, droppedNow);
            }

            // 两个 if 分开写、「不合并」：丢弃和入队失败语义不同，合成一行会变成"任一非零就一起报"，
            // 读日志时反而分不清是哪一件事、发生了多少。
            long failedNow = enqueueFailureTotal.sum();
            long enqueueFailureDelta = failedNow - lastReportedFailed;
            lastReportedFailed = failedNow;
            if (enqueueFailureDelta > 0) {
                log.warn("本轮新增入队失败 {} 次（累计 {} 次）", enqueueFailureDelta, failedNow);
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
     * <p>
     * 🔴 <b>自检的 KEYS[1] 必须是独立常量 {@link RedisKeyConstant#TEST_INIT_ACCESS}，绝不能是
     * {@link RedisKeyConstant#ACCESS_QUEUE}。</b>
     * 最初从限流自检抄这段代码时，把 key 写成了<b>真队列</b>，而 cap 又照抄了限流的 {@code "1"}。
     * 于是启动瞬间发生的是 {@code RPUSH 真队列 + LTRIM -1 -1} —— <b>把队列裁到只剩 1 条</b>。
     * <p>
     * 实测复现：先灌 124 条真实日志再启动 → {@code LLEN} 从 <b>124 变成 1</b>，内容变成自检塞的占位串
     * {@code "1"}；而日志里只有一句"自检通过，加载成功"。应用启动成功、跳转照常 302、
     * 定时任务照常搬运 —— 只是<b>每次重启都静默清空当时队列里的真实访问日志</b>。
     * 而 {@link #record(String)} 一直在塞、{@link #flush()} 一分钟才搬一次，
     * "重启时队列非空"是<b>常态</b>而不是边角情况。
     * <p>
     * 另一个教训：判断"自检有没有碰生产数据"，<b>只看启动前后两个点会漏掉中间态</b>，
     * 必须采 {@code LLEN} 的<b>时间序列</b>（覆盖整个启动窗口）。
     */
    @PostConstruct
    public void initScript() {
        try {
            // 参数顺序照脚本契约：ARGV[1] = 日志 JSON（自检给个占位串），ARGV[2] = cap。
            // ⚠️ 只能传这两个 —— 限流脚本要 3 个 ARGV，从它那儿抄代码时很容易顺手多带一个 "0"；
            //   本脚本从不读 ARGV[3]，多传不会报错，但会让后来人以为脚本有第 3 个参数。
            // 🔴 KEYS[1] 是独立的 TEST_INIT_ACCESS，不是 ACCESS_QUEUE —— 写错这一行的后果见方法注释。
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
