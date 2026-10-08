package com.itqiuan.shortlink.service;

public interface AccessLogService {

    void record(String shortCode);

    void flush();
}
