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