package com.itqiuan.shortlink.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)   // data/traceId 为 null 时不输出该字段
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    private Result(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, null);
    }

    public static <T> Result<T> success(T data, String traceId) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, traceId);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null, null);
    }

    /** 复用枚举的 code，但用自定义 message 覆盖（全局异常处理器常用） */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, null);
    }

    public static <T> Result<T> fail(ResultCode resultCode, String traceId) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null, traceId);
    }

    public static <T> Result<T> fail(ResultCode resultCode, T data, String traceId) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), data, traceId);
    }
}
