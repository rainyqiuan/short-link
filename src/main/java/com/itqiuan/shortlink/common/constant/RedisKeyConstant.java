package com.itqiuan.shortlink.common.constant;

import lombok.NoArgsConstructor;

public class RedisKeyConstant {
    public static final String SEQ = "shortlink:seq";

    public static final String BUFFER = "shortlink:code:{shortCode}";

    public static final String LIMIT="shortlink:limit:{ip}";

    private RedisKeyConstant() {}
}
