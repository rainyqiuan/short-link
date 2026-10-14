package com.itqiuan.shortlink.service;

/**
 * 发号器：提供全局唯一的自增序号（短码的序号来源）。
 */
public interface SeqGenerator {

    /**
     * 取一个序号。
     * @return 单调递增、全局唯一的序号
     */
    //取一个序号
    Long generateSeqNo();

}
