package com.itqiuan.shortlink.service;

/**
 * 过期短链清理服务。
 */
public interface ExpireCleanService {

    /**
     * 分批清理已过期的短链（分批 + 限轮次，避免一次删太多把数据库拖住）。
     *
     * @param batchSize 每批删除行数
     * @param maxRound  一轮最多循环几次
     */
    void cleanExpired(int batchSize, int maxRound);
}
