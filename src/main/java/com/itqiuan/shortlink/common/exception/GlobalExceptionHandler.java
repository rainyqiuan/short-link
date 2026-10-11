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
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
        // 获取出错字段名，如 expireTime
        String field = fieldError.getField();
        // 获取注解上的提示语
        String message = fieldError.getDefaultMessage();
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
        // 可能是 "createShortLink.shortLinkCreateDTO.expireTime"
        String propertyPath = violation.getPropertyPath().toString();
        String message = violation.getMessage();

        // 4. 核心：调用公共方法解析错误码
        int errorCode = ValidationErrorCodeResolver.resolve(propertyPath);

        log.warn("参数校验失败 (param): 路径[{}], 提示[{}], 分配错误码[{}]", propertyPath, message, errorCode);

        return Result.fail(errorCode, message);
    }

    //MethodArgumentTypeMismatchException	PARAM_FORMAT_ERROR(40002) —— "日期格式应为 yyyy-MM-dd"
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        // 1. 动态获取参数名（比如 "startDate" 或 "pageNum"）
        String paramName = e.getName();

        // 2. 动态获取期望的目标类型（比如 "LocalDate" 或 "Integer"）
        // 判空是因为某些极特殊情况下 e.getRequiredType() 可能为 null
        String requiredType = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知类型";

        // 3. 拼接通用的错误提示
        String message = String.format("参数 '%s' 类型错误，期望类型为 %s", paramName, requiredType);

        // 4. 记录日志（根据之前的要求，使用 log.warn 并打印完整堆栈）
        log.warn("参数类型转换失败: {}", paramName, e);

        // 5. 返回统一的失败结果（保持 40002 错误码）
        return Result.fail(ResultCode.PARAM_FORMAT_ERROR.getCode(), message);
    }

    //MissingServletRequestParameterException	PARAM_ERROR(40001) —— 指明缺哪个参数
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数：{}", e.getParameterName());
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "缺少必填参数：" + e.getParameterName());
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
        log.error("未分类异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(ResultCode.SYSTEM_ERROR));
    }
}
