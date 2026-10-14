package com.itqiuan.shortlink.common.enums;

/**
 * 短链"可用性"分类结果：跳转前给一条短链做体检，得出它属于哪种情况。
 * <p>
 * 之所以用枚举返回值而不是直接抛异常，是因为读路径要按分类走不同分支
 * （例如只有 REUSABLE 才记录访问日志），用返回值控制流程比用异常更清楚。
 */
public enum LinkUsabilityEnum {

    /** 可用，可以跳转。 */
    REUSABLE,

    /** 查无此短码（DB 里没有，或被布隆过滤器判定为"一定不存在"）。 */
    NOT_FOUND,

    /** 已禁用。 */
    DISABLED,

    /** 已过期。 */
    EXPIRED,

    /** 其他未知情况（兜底，避免遗漏分支）。 */
    UNKNOWN;
}
