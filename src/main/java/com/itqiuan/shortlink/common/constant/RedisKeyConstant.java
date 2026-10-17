package com.itqiuan.shortlink.common.constant;

import java.time.LocalDate;

/**
 * Redis key 的统一出口：所有 key 都带 {@code shortlink:} 前缀，落在同一个"命名空间"里，
 * 避免和同一 Redis 实例上的其他应用抢 key。
 * <p>
 * 集中定义的意义在于：key 一旦散落到各个 Service 里，"改前缀 / 查有没有撞 key" 就无从下手了。
 */
public class RedisKeyConstant {

    /** 全局自增发号器（Redis INCR），短码序号的唯一来源。 */
    public static final String SEQ = "shortlink:seq";

    /** 短链缓存的 key 前缀，完整 key 形如 {@code shortlink:code:{shortCode}}，存的是 ShortLinkCacheBO 的 JSON。 */
    public static final String BUFFER = "shortlink:code:";

    /** 布隆过滤器位图本体（判断短码"可能存在"）。 */
    public static final String BLOOM = "shortlink:bloom";

    /** 布隆过滤器是否已就绪的标记。未就绪时读路径一律放行，不拿布隆做拦截（否则会把全部请求误杀）。 */
    public static final String BLOOM_READY = "shortlink:bloom:ready";

    /** 访问日志缓冲队列（Redis List）：跳转时 RPUSH，定时任务批量搬运落库后再裁剪。 */
    public static final String ACCESS_QUEUE = "shortlink:access:queue";

    /** 限流令牌桶前缀，完整 key 形如 {@code shortlink:limit:{route}:{ip}}。 */
    public static final String LIMIT = "shortlink:limit:";

    /** Lua 限流脚本自检时用的占位 key（启动时拿它试跑一次脚本，用完即删）。 */
    public static final String TEST_INIT_LIMIT = "shortlink:test:init:limit";

    /**
     * Lua 日志脚本自检时用的占位 key（启动时拿它试跑一次脚本，用完即删）。
     * <p>
     * 🔴 <b>绝不能换成 {@link #ACCESS_QUEUE}</b>。自检传的 cap 是 {@code "1"}，拿真队列试跑就是
     * {@code RPUSH 真队列} + {@code LTRIM -1 -1} → <b>把真实访问日志裁到只剩 1 条</b>。
     * 实测过：灌 124 条后启动 → {@code LLEN} = 1，而日志里只有"自检通过"。
     * 每次重启静默清空一次，且不报任何错。
     * <p>
     * 也不需要带随机后缀：一个 key 只有一种类型，按脚本各分一个 key 之后，即使多实例并发自检，
     * 也只是"对同一个 key 做同类型操作"，不可能 {@code WRONGTYPE}。
     */
    public static final String TEST_INIT_ACCESS = "shortlink:test:init:access";

    /** 按天 UV 去重前缀（HyperLogLog），完整 key 形如 {@code shortlink:uv:{code}:{date}}。 */
    public static final String UV = "shortlink:uv:";

    /**
     * 拼某条短链在某天的 UV key（对应一个 HyperLogLog，用来估算当天去重访客数）。
     * <p>
     * 按天切分的好处：跨天天然分桶，不用额外做过期淘汰逻辑。
     *
     * @param code 短码
     * @param date 统计日期
     */
    public static String uvKey(String code, LocalDate date) {
        return UV + code + ":" + date.toString();
    }

    /** 纯常量类，禁止实例化。 */
    private RedisKeyConstant() {
    }
}
