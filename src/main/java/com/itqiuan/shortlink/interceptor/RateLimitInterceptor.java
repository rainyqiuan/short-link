package com.itqiuan.shortlink.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itqiuan.shortlink.bo.RateLimitResult;
import com.itqiuan.shortlink.common.annotation.RateLimit;
import com.itqiuan.shortlink.common.result.Result;
import com.itqiuan.shortlink.common.result.ResultCode;
import com.itqiuan.shortlink.config.ShortLinkProperties;
import com.itqiuan.shortlink.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;


@Component
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ShortLinkProperties shortLinkProperties;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(ShortLinkProperties shortLinkProperties, RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.shortLinkProperties = shortLinkProperties;
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit rl = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rl == null) {
            return true;
        }

        //将来上反代要改读 X-Forwarded-For，且要防伪造
        String ip = request.getRemoteAddr();
        String key = rl.route() + ":" + ip;
        RateLimitResult r = rateLimitService.tryAcquire(key, shortLinkProperties.getLimitCapacity(), shortLinkProperties.getLimitRefillRate());
        if (r.isAllowed()) {
            return true;
        }
        log.debug("触发限流 {}", request.getRequestURI());
        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Retry-After", String.valueOf(r.getRetryAfterSeconds()));
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.TRIGGER_RATE_LIMIT)));
        return false;
    }
}
