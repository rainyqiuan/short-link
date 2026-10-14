package com.itqiuan.shortlink.common.enums;

/**
 * 短链状态，对应 short_link.status 列。
 * <p>
 * 注意：这里存的是<b>数据库列的值</b>（0/1），不是接口返回的业务错误码 —— 两者不要混。
 */
public enum LinkStatusEnum {

    /** 正常可用。 */
    NORMAL(1),

    /** 已被禁用（短码仍被占用，但跳转会被拒绝）。 */
    DISABLE(0);

    private final int code;

    LinkStatusEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

}
