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


/**
 * 限流拦截器：在请求进入 Controller 之前，对带 {@code @RateLimit} 的方法做令牌桶判定。
 * <p>
 * 几个关键点：
 * <ul>
 *   <li>只对 {@code HandlerMethod} 看注解，静态资源等直接放行；</li>
 *   <li>直接把 429 写进 response，而<b>不抛异常</b> —— 抛异常会被全局异常处理器包成 200，
 *       那样限流就骗过了客户端的重试逻辑；</li>
 *   <li>限流维度 = route + ip，其中 ip 取自 {@code getRemoteAddr()}；</li>
 *   <li>Redis 不可用时由 RateLimitService 内部 fail-open（放行）—— 限流只该"让快的变慢"，
 *       不该"让能用的不能用"。</li>
 * </ul>
 */
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

    /**
     * 前置拦截：返回 false 表示已自行写出响应、不再往下走。
     */
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
        //⚠️ 容器部署（Docker Desktop 端口映射）下这里恒为网桥网关（如 172.18.0.1）
        //   → "限 IP" 会退化成"限全站"；这是端口映射丢源 IP 的固有代价，非代码 bug
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
