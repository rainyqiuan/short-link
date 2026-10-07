package com.itqiuan.shortlink.common.constant;

public class RedisKeyConstant {
    public static final String SEQ = "shortlink:seq";

    public static final String BUFFER = "shortlink:code:";

    public static final String LIMIT="shortlink:limit:{ip}";

    private RedisKeyConstant() {}
}
