package com.itqiuan.shortlink.common.exception;

import com.itqiuan.shortlink.common.result.ResultCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException{
    private ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
