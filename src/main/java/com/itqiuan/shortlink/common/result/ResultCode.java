package com.itqiuan.shortlink.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码。
 *
 * 分段规则（与 HTTP 状态码解耦，业务码 ≠ HTTP 码）：
 *   0         成功
 *   400xx     客户端问题：参数、短码、状态冲突
 *   500xx     服务端问题：系统异常、依赖不可用
 *
 * 之所以不复用 HTTP 码：业务码会越加越多（"短码格式非法"、"自定义短码已被占用"
 * 都属于 400 语义，但需要前端区分提示），同号会很快不够用；而且同一个业务码
 * 在不同接口可能对应不同 HTTP 码，绑死在一起会互相牵制。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ===== 成功 =====
    SUCCESS(0, "成功"),

    // ===== 400xx 客户端问题 =====
    PARAM_ERROR(40001, "参数错误"),
    PARAM_FORMAT_ERROR(40002, "参数格式错误"),
    SHORT_CODE_FORMAT_ERROR(40003, "短码格式非法"),
    EXPIRE_TIME_INVALID(40004, "过期时间必须晚于当前时间"),
    SHORT_CODE_NOT_EXIST(40005, "短码不存在"),
    SHORT_CODE_ALREADY_EXIST(40006, "短码已被占用"),
    SHORT_LINK_DISABLED(40007, "短链已被禁用"),
    SHORT_LINK_EXPIRED(40008, "短链已过期"),
    TRIGGER_RATE_LIMIT(40009, "触发限流"),
    RESOURCE_NOT_EXIST(40010, "资源不存在"),
    REQUEST_METHOD_NOT_SUPPORTED(40011, "请求方法不支持"),
    CONTENT_TYPE_NOT_SUPPORTED(40012, "内容类型不支持"),

    // ===== 500xx 服务端问题 =====
    SYSTEM_ERROR(50000, "系统异常"),
    DEPENDENCY_ERROR(50001, "依赖服务不可用");

    private final int code;
    private final String message;

}
