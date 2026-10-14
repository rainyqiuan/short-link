package com.itqiuan.shortlink.common.constant;

import java.util.regex.Pattern;

/**
 * 全链路追踪相关的常量。
 * <p>
 * traceId 的职责是"把一个请求在所有日志里串起来"：入口过滤器从请求头透传（没有就新生成）、
 * 写进 MDC 供日志模板取用，再回写到响应头 —— 调用方拿着它就能去查这次请求的完整链路。
 */
public class TraceConstant {

    /** MDC 里的 key，日志模板用 %X{traceId} 取值。 */
    public static final String MDC_KEY = "traceId";

    /** 透传用的请求/响应头名。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** traceId 的长度上限，超长的一律丢弃并重新生成（防止外部塞超长字符串污染日志）。 */
    public static final int TRACE_ID_MAX_LENGTH = 64;

    /** traceId 允许的字符集：只接受字母、数字、连字符 —— 挡掉换行等可能伪造日志行的字符。 */
    public static final String TRACE_ID_REGEX = "[0-9a-zA-Z-]{1,64}";

    /** 预编译好的校验器：校验发生在每个请求上，预编译避免每次调用都重新编译正则。 */
    public static final Pattern TRACE_ID_PATTERN = Pattern.compile(TRACE_ID_REGEX);

    /** 纯常量类，禁止实例化。 */
    private TraceConstant(){
    }
}
