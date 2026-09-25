package com.itqiuan.shortlink.common.enums;

public enum LinkStatusEnum {
    NORMAL(1),
    DISABLE(0);

    private final int code;

    LinkStatusEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

}
