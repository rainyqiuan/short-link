package com.itqiuan.shortlink.service;

public interface ExpireCleanService {
    void cleanExpired(int batchSize, int maxRound);
}
