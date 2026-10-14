package com.itqiuan.shortlink.common.exception.constant;

/**
 * 校验字段名的字面量常量。
 * <p>
 * 这些值必须与 {@code ShortLinkCreateDTO} 的字段名严格一致 —— Bean Validation 报错时
 * 给的就是字段名字符串，这里靠字符串匹配来分流错误码。
 * <b>改 DTO 字段名时必须同步改这里</b>，否则错误码会静默退回 40001（编译期不会报错）。
 */
public class ValidationFieldNames {
    //与 ShortLinkCreateDTO 字段名强耦合，改 DTO 要同步改这里
    public static final String EXPIRE_TIME = "expireTime";
    public static final String CUSTOM_CODE = "customCode";
}
