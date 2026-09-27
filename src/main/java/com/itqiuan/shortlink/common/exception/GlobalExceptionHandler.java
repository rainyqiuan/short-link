package com.itqiuan.shortlink.common.exception;

import com.itqiuan.shortlink.common.result.Result;
import com.itqiuan.shortlink.common.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
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
        int errorCode = ValidationErrorCodeResolver.resolve(field);

        // 5. 返回统一结果
        return Result.fail(errorCode, message);
    }

    //@Validated 在方法级校验失败（@RequestParam/@PathVariable）
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        // 1. 获取第一条校验失败的 ConstraintViolation
        ConstraintViolation<?> violation = e.getConstraintViolations().stream()
                .findFirst()
                .orElse(null);

        // 2. 兜底
        if (violation == null) {
            log.warn("参数校验失败 (param): 未知参数错误");
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), ResultCode.PARAM_ERROR.getMessage());
        }

        // 3. 提取真实的字段路径 和 错误提示
        String propertyPath = violation.getPropertyPath().toString(); // 可能是 "createShortLink.shortLinkCreateDTO.expireTime"
        String message = violation.getMessage();

        // 4. 核心：调用公共方法解析错误码
        int errorCode = ValidationErrorCodeResolver.resolve(propertyPath);

        log.warn("参数校验失败 (param): 路径[{}], 提示[{}], 分配错误码[{}]", propertyPath, message, errorCode);

        return Result.fail(errorCode, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("参数格式错误：{}", e.getMostSpecificCause().getMessage());
        return Result.fail(ResultCode.PARAM_FORMAT_ERROR.getCode(), ResultCode.PARAM_FORMAT_ERROR.getMessage());
    }

    //其他所有（空指针，数据库异常）（兜底）
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        if (e instanceof ErrorResponse er) {
            int status = er.getStatusCode().value();   // ← 不是 (HttpStatus)
            switch (status) {
                case 404 -> {
                    /* 保 404 + 40010，log 不带堆栈 */
                    log.warn("资源不存在");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.fail(ResultCode.RESOURCE_NOT_EXIST));
                }
                case 405 -> {
                    /* 保 405 + 40011，log 带堆栈 */
                    log.warn("请求方法不支持", e);
                    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Result.fail(ResultCode.REQUEST_METHOD_NOT_SUPPORTED));
                }
                case 415 -> {
                    /* 保 415 + 40012，log 带堆栈 */
                    log.warn("内容类型不支持", e);
                    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Result.fail(ResultCode.CONTENT_TYPE_NOT_SUPPORTED));
                }
                default -> {
                    if (er.getStatusCode().is4xxClientError()){
                        log.warn("客户端错误",e);
                        return ResponseEntity.status(status).body(Result.fail(ResultCode.PARAM_ERROR));
                    }else {
                        log.error("系统异常", e);
                        return ResponseEntity.status(status).body(Result.fail(ResultCode.SYSTEM_ERROR));
                    }
                }
            }
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(ResultCode.SYSTEM_ERROR));
    }
}
