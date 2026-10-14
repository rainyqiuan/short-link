package com.itqiuan.shortlink.service;

/**
 * 布隆过滤器服务：拦截"一定不存在"的短码，挡住缓存穿透。
 * <p>
 * 降级原则（见下方注释）：读方法失败一律放行、写方法失败只记日志、rebuild 失败必须降级 ——
 * 核心是「降级只能变慢，不能变错」。
 */
//读方法失败一律放行、写方法失败只记日志、rebuild 失败必须降级。
public interface BloomFilterService {

    /**
     * 把一个新短码加入布隆（创建成功后调用）。
     * 失败只记日志：漏加最多让这个短码首次访问多走一次库，不影响正确性。
     */
    void addToBloomFilter(String shortCode);

    /**
     * 判断短码"可能存在"。
     * <p>
     * 返回 false 表示<b>一定不存在</b>，可以直接拒；返回 true 只是"可能存在"（有假阳性），
     * 还要继续查缓存/库。
     */
    boolean mightContain(String shortCode);

    /**
     * 从数据库全量重建布隆位图（启动时或位图丢失后调用）。
     */
    void rebuildBloomFilter();
}
