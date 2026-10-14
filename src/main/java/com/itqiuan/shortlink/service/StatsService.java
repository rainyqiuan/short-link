package com.itqiuan.shortlink.service;

import com.itqiuan.shortlink.vo.LinkStatsVO;
import com.itqiuan.shortlink.vo.StatsItemVO;

import java.time.LocalDate;

/**
 * 统计服务：聚合落库 + 查询。
 */
public interface StatsService {

    /**
     * 重算最近 lookbackDays 天的 PV/UV 并写入汇总表（由定时任务调用）。
     * 回看若干天是为了兜住迟到或补跑的数据（而不是只算"今天"）。
     *
     * @param lookbackDays 回看天数
     */
    void aggregate(int lookbackDays);

    /**
     * 查询某条短码在日期区间内的统计：校验 + 查 + 汇总。
     */
    //queryStats(...)：校验 + 查 + 汇总
    LinkStatsVO queryStats(String code, LocalDate start, LocalDate end);
}
