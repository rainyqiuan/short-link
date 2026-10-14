package com.itqiuan.shortlink.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体：所有接口的返回值最终都是这个形态
 * {@code { "code": 0, "message": "成功", "data": {...}, "traceId": "..." }}。
 * <p>
 * 两条约定：
 * <ul>
 *   <li>业务成败看 {@code code}（0 为成功），<b>不看 HTTP 状态码</b> —— 业务失败也是 HTTP 200；</li>
 *   <li>{@code data}/{@code traceId} 为 null 时被 Jackson 略过（{@code @JsonInclude(NON_NULL)}），
 *       所以成功响应里通常看不到 traceId —— 它是 TraceIdAdvice 在序列化前回填的。</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)   // data/traceId 为 null 时不输出该字段
public class Result<T> {

    @Schema(description = "状态码")
    private int code;
    @Schema(description = "提示信息")
    private String message;
    @Schema(description = "数据")
    private T data;
    @Schema(description = "追踪信息")
    private String traceId;

    private Result(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    /** 成功（traceId 留空，由 TraceIdAdvice 回填）—— 常规成功响应走这个。 */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, null);
    }

    /** 成功，并显式带上 traceId。 */
    public static <T> Result<T> success(T data, String traceId) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, traceId);
    }

    /** 失败，取错误码枚举里的默认描述。 */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null, null);
    }

    /** 复用枚举的 code，但用自定义 message 覆盖（全局异常处理器常用） */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, null);
    }

    /** 失败，并显式带上 traceId。 */
    public static <T> Result<T> fail(ResultCode resultCode, String traceId) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null, traceId);
    }

    /** 失败，但响应里要带一部分数据（如校验失败时回吐出错字段）。 */
    public static <T> Result<T> fail(ResultCode resultCode, T data, String traceId) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), data, traceId);
    }
}
