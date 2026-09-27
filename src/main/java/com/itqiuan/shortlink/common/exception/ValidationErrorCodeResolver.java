package com.itqiuan.shortlink.common.exception;

import com.itqiuan.shortlink.common.exception.constant.ValidationFieldNames;
import com.itqiuan.shortlink.common.result.ResultCode;

public class ValidationErrorCodeResolver {
    /**
     * 根据字段路径解析业务错误码
     *
     * @param fieldPath 原始字段路径，如 "expireTime" 或 "createShortLink.shortLinkCreateDTO.expireTime"
     * @return 对应的业务错误码
     */
    public static int resolve(String fieldPath) {
        if (fieldPath == null || fieldPath.isBlank()) {
            return ResultCode.PARAM_ERROR.getCode(); // 默认 40001
        }

        // 核心逻辑：剥掉前缀，只取最后一段真实的字段名
        // 如"createShortLink.shortLinkCreateDTO.expireTime" -> "expireTime"
        String pureField = fieldPath;
        if (fieldPath.contains(".")) {
            String[] parts = fieldPath.split("\\.");
            pureField = parts[parts.length - 1];
        }

        // 统一映射规则
        return switch (pureField) {
            case ValidationFieldNames.EXPIRE_TIME -> ResultCode.EXPIRE_TIME_INVALID.getCode();
            case ValidationFieldNames.CUSTOM_CODE -> ResultCode.SHORT_CODE_FORMAT_ERROR.getCode();
            default -> ResultCode.PARAM_ERROR.getCode(); // 40001
        };
    }

    private ValidationErrorCodeResolver() {
    }
}
