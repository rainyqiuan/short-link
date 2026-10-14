package com.itqiuan.shortlink.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 摘要工具。
 * <p>
 * 本项目里的用途：对 originUrl 取摘要存进 {@code origin_url_md5} 列，
 * 配合该列上的唯一索引做创建幂等 —— 同一个 URL 重复提交时不会再建一条，
 * 而是直接返回已有的那条短链。
 * <p>
 * <b>注意不是拿它做安全用途</b>：MD5 早已不适合密码等安全场景，这里只是当"稳定的定长指纹"用。
 */
public class Md5Util {

    /**
     * 计算字符串的 MD5，返回 32 位小写十六进制。
     *
     * @param input 待摘要字符串；null 直接抛（避免把 null 摘要成一个固定值而无人察觉）
     */
    public static String toMd5(String input) {
        if(input==null)
        {
            throw new IllegalArgumentException("Input string cannot be null");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前JVM不支持MD5算法", e);
        }

    }

}
