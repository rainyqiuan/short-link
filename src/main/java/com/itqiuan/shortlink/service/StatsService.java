package com.itqiuan.shortlink.service;

import com.itqiuan.shortlink.vo.LinkStatsVO;
import com.itqiuan.shortlink.vo.StatsItemVO;

import java.time.LocalDate;

public interface StatsService {
    void aggregate(int lookbackDays);

    //queryStats(...)：校验 + 查 + 汇总
    LinkStatsVO queryStats(String code, LocalDate start, LocalDate end);
}
