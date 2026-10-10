package com.itqiuan.shortlink.common.constant;

import java.time.LocalDate;

public class RedisKeyConstant {
    public static final String SEQ = "shortlink:seq";

    public static final String BUFFER = "shortlink:code:";

    public static final String BLOOM = "shortlink:bloom";

    public static final String BLOOM_READY = "shortlink:bloom:ready";

    public static final String ACCESS_QUEUE = "shortlink:access:queue";

    public static final String LIMIT = "shortlink:limit:";

    public static final String TEST_INIT = "shortlink:test:init:key";

    public static final String UV = "shortlink:uv:";

    public static String uvKey(String code, LocalDate date) {
        return UV + code + ":" + date.toString();
    }

    private RedisKeyConstant() {
    }
}
