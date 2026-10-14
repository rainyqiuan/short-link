package com.itqiuan.shortlink.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Documented;

/**
 * 限流标记注解：贴在需要限流的接口方法上，由 {@code RateLimitInterceptor}
 * 在请求进入业务逻辑之前读取并执行令牌桶判定。
 * <p>
 * 之所以做成注解、而不是把"哪些接口要限流"写死在拦截器里，是为了让限流规则
 * 显式落在接口定义处 —— 看接口就知道它受限，不用去翻配置。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    /**
     * 路由标识，是限流桶 key 的维度之一（完整 key = {@code shortlink:limit:{route}:{ip}}）。
     * <p>
     * <b>必须填路由"模板"而非真实路径</b>：例如 {@code /{code}}，而不是 {@code /abc123}。
     * 若填真实路径，每个短码都会各占一个桶，同一个接口的额度被无限切分，限流就形同虚设。
     */
    String route();
}
