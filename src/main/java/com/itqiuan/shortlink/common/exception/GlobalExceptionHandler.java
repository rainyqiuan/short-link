package com.itqiuan.shortlink.common.exception;

import com.itqiuan.shortlink.common.result.Result;
import com.itqiuan.shortlink.common.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
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
        // 取第一条字段校验失败的提示，比笼统的“参数错误”更有用
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        log.warn("参数校验失败(body): {}", message);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), message);
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

    //其他所有（空指针，数据库异常）（兜底）
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);   // 堆栈只进日志，不进响应体
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }
}
