package com.itqiuan.shortlink.service;

public interface StatsService {
    void aggregate(int lookbackDays);
}
