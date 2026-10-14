package com.itqiuan.shortlink.common.util;

/**
 * Base62 编解码：把发号序号转成短码字符串，以及反向解析回数字。
 * <p>
 * ⚠️ 字符表用的是<b>标准序</b>（0-9 → A-Z → a-z），<b>没有打乱</b>。这意味着
 * 短码是<b>可预测</b>的：seq 1/2/3 连续，编码出来就是连号的短码。
 * 这是"发号 + 固定偏移"方案的固有代价（换来的是零冲突）；若业务要求强防枚举，
 * 应改用"随机码 + 去重"或"发号 + 可逆加密"。
 */
public class Base62Util {

    /** 62 进制字符表（10 数字 + 26 大写 + 26 小写）。顺序即编码顺序，一旦改动，已发出的短码会全部对不上。 */
    public static final String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /**
     * 数字 → Base62 字符串。
     * <p>
     * 返回的是<b>最短表示</b>（不带前导补位）：1 编出来是 {@code "1"} 而不是 {@code "000001"}。
     * 短码的"定长 6 位"是上层靠固定偏移量（62^5）保证的，不是这个方法做的。
     *
     * @param number 非负整数；负数直接抛（序号不可能为负）
     */
    public static String toBase62(long number) {
        if (number < 0) {
            throw new IllegalArgumentException("Number must be positive");
        }
        if (number == 0) {
            return "0";
        }
        StringBuilder result = new StringBuilder();
        while (number > 0) {
            result.append(BASE62_CHARS.charAt((int) (number % 62)));
            number /= 62;
        }
        return result.reverse().toString();
    }

    /**
     * Base62 字符串 → 数字。
     *
     * @param base62 只允许字符表内的字符；出现非法字符直接抛异常（宁可报错，也不要算出一个错的数）
     */
    public static long toNumber(String base62) {
        long result = 0;
        for (int i = 0; i < base62.length(); i++) {
            if (BASE62_CHARS.indexOf(base62.charAt(i)) == -1) {
                throw new IllegalArgumentException("存在非法字符：" + base62.charAt(i));
            }
            result = result * 62 + BASE62_CHARS.indexOf(base62.charAt(i));
        }
        return result;
    }

}
