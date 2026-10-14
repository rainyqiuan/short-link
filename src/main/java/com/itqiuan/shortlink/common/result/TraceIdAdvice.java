package com.itqiuan.shortlink.common.result;

import com.itqiuan.shortlink.common.constant.TraceConstant;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 把 traceId 回填进响应体。
 * <p>
 * 机制：实现 {@code ResponseBodyAdvice}，在 Controller 返回的对象被<b>序列化之前</b>拦一道，
 * 若它是 {@code Result} 就把当前线程 MDC 里的 traceId 塞进去。
 * <p>
 * 为什么用 Advice 而不是每个 Controller 手动 set：traceId 的取值时机（MDC）
 * 和写入位置（响应体）都很统一，集中处理才能保证"不漏、不重"。
 */
@ControllerAdvice
public class TraceIdAdvice implements ResponseBodyAdvice<Object> {

    /**
     * 恒返回 true —— 这里拿不到 body 的类型，无法过滤；
     * 真正"要不要处理"的判断下沉到 {@code beforeBodyWrite} 里。
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /**
     * 只处理 {@code Result}：其他返回值（错误页、文件流等）原样放行。
     * 用 {@code instanceof} 而不是泛型判断 —— 泛型在运行期已被擦除，只能按实际类型判。
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof Result<?>) {
            Result<?> result = (Result<?>) body;
            result.setTraceId(MDC.get(TraceConstant.MDC_KEY));
        }
        return body;
    }
}
