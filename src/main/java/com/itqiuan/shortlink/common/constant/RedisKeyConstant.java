package com.itqiuan.shortlink.common.constant;

public class RedisKeyConstant {
    public static final String SEQ = "shortlink:seq";

    public static final String BUFFER = "shortlink:code:";

    public static final String BLOOM = "shortlink:bloom";

    public static final String BLOOM_READY = "shortlink:bloom:ready";

    public static final String ACCESS_QUEUE = "shortlink:access:queue";

    private RedisKeyConstant() {}
}
