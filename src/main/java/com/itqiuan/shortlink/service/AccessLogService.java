package com.itqiuan.shortlink.service;

/**
 * 访问日志服务：写入缓冲、异步落库。
 */
public interface AccessLogService {

    /**
     * 记录一次成功跳转（只入 Redis 队列，不写库）。
     * 调用方需保证只在真正可跳转时才调它。
     */
    void record(String shortCode);

    /**
     * 把队列里积压的日志批量搬运落库（由定时任务调用）。
     * 只有落库事务提交成功后才裁剪队列；失败则保留原样、下轮重试。
     */
    void flush();
}
