package com.itqiuan.shortlink.filter;

import com.itqiuan.shortlink.common.constant.TraceConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * traceId 入口过滤器：整条链路最早的一环。
 * <p>
 * 职责（按执行顺序）：从请求头读 X-Trace-Id → 校验（长度 + 字符集，挡伪造/污染）→
 * 不合法就用 UUID 重新生成 → 写进 MDC（供日志模板取用）→ 回写响应头 → finally 里清 MDC。
 * <p>
 * 两个关键点：
 * ① {@code @Order(HIGHEST_PRECEDENCE)} 保证它比限流拦截器等都早执行，后续环节都能拿到 traceId；
 * ② {@code OncePerRequestFilter} 保证一次请求只跑一遍（forward/include 不会重复生成）。
 * <p>
 * ⚠️ MDC 底层是 ThreadLocal：Tomcat 下"同一条连接 ≠ 同一个线程"（keep-alive 会换线程），
 * 所以 finally 里必须 clear，否则线程被复用时会把上个请求的 traceId 带进来。
 */
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        //读 X-Trace-Id
        String traceId = request.getHeader(TraceConstant.TRACE_ID_HEADER);
        if (traceId == null || traceId.length() > TraceConstant.TRACE_ID_MAX_LENGTH || !TraceConstant.TRACE_ID_PATTERN.matcher(traceId).matches()) {
            UUID uuid = UUID.randomUUID();
            traceId = uuid.toString().replace("-", "");
        }
        MDC.put(TraceConstant.MDC_KEY, traceId);
        try {
            response.setHeader(TraceConstant.TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}