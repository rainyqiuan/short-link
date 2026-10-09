package com.itqiuan.shortlink.config;

import com.itqiuan.shortlink.interceptor.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册全路径，实际限流范围由 @RateLimit 注解决定（避免把 /{code} 的正则抄成第二处真相）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**");
    }
}
