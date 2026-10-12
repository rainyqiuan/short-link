package com.itqiuan.shortlink.common.constant;

import java.util.regex.Pattern;

public class TraceConstant {

    public static final String MDC_KEY = "traceId";

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    public static final int TRACE_ID_MAX_LENGTH = 64;

    public static final String TRACE_ID_REGEX = "[0-9a-zA-Z-]{1,64}";

    public static final Pattern TRACE_ID_PATTERN = Pattern.compile(TRACE_ID_REGEX);

    private TraceConstant(){
    }
}
