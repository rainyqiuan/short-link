package com.itqiuan.shortlink.common.util;

public class Base62Util {

    public static final String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

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
