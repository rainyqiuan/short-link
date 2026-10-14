package com.itqiuan.shortlink.common.exception;

import com.itqiuan.shortlink.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：表示"业务规则不满足"这类<b>可预期</b>的失败（如短码已存在）。
 * <p>
 * 继承 RuntimeException 是为了不给自己找麻烦 —— 受检异常会逼着每一层都写 try/catch，
 * 而上层其实只需要一个地方（GlobalExceptionHandler）统一把它翻译成响应体。
 * <p>
 * 注意：业务失败最终仍是 <b>HTTP 200 + body 里的错误码</b>，不是 4xx/5xx。
 */
@Getter
public class BusinessException extends RuntimeException{

    /** 对应的业务错误码，由全局异常处理器取出并翻译成 Result。 */
    private ResultCode resultCode;

    /** 用错误码自带的默认描述。 */
    public BusinessException(ResultCode resultCode) {
        this.resultCode = resultCode;
    }

    /** 用自定义描述覆盖默认描述（只在需要补充具体上下文时才用，如"短码 xxx 已存在"）。 */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
