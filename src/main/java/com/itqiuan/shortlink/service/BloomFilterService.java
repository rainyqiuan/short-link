package com.itqiuan.shortlink.service;

//读方法失败一律放行、写方法失败只记日志、rebuild 失败必须降级。
public interface BloomFilterService {
    void addToBloomFilter(String shortCode);

    boolean mightContain(String shortCode);

    void rebuildBloomFilter();
}
