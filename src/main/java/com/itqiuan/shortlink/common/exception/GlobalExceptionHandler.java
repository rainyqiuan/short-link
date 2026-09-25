package com.itqiuan.shortlink.common.exception;

import com.itqiuan.shortlink.common.result.Result;
import com.itqiuan.shortlink.common.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    //业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        // 有自定义 message 就用自定义的，否则退回枚举里的默认描述
        String message = StringUtils.hasText(e.getMessage())
                ? e.getMessage()
                : e.getResultCode().getMessage();
        log.warn("业务异常: code={}, message={}", e.getResultCode().getCode(), message);
        return Result.fail(e.getResultCode().getCode(), message);
    }

    //@Valid @RequestBody校验失败(body)异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // 1. 获取第一条字段校验失败的 FieldError 对象（保留原代码的 Stream 写法）
        FieldError fieldError = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .orElse(null);

        // 2. 兜底逻辑：如果意外没有拿到 FieldError，返回默认错误
        if (fieldError == null) {
            log.warn("参数校验失败 (body): 未知参数错误");
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), ResultCode.PARAM_ERROR.getMessage());
        }

        // 3. 提取出错字段和错误提示
        String field = fieldError.getField();           // 获取出错字段名，如 expireTime
        String message = fieldError.getDefaultMessage(); // 获取注解上的提示语
        log.warn("参数校验失败 (body): 字段[{}], 提示[{}]", field, message);

        // 4. 按字段分流错误码
        int errorCode = switch (field) {
            case "expireTime" -> ResultCode.EXPIRE_TIME_INVALID.getCode();
            case "customCode" -> ResultCode.SHORT_CODE_FORMAT_ERROR.getCode();
            default -> ResultCode.PARAM_ERROR.getCode(); // 默认 40001（复用你原本的枚举）
        };

        // 5. 返回统一结果
        return Result.fail(errorCode, message);
    }

    //@Validated 在方法级校验失败（@RequestParam/@PathVariable）
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        log.warn("参数校验失败(param): {}", message);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("参数格式错误", e);
        return Result.fail(ResultCode.PARAM_FORMAT_ERROR.getCode(), ResultCode.PARAM_FORMAT_ERROR.getMessage());
    }

    //其他所有（空指针，数据库异常）（兜底）
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);    // 堆栈只进日志，不进响应体
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }
}
