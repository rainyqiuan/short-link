package com.itqiuan.shortlink.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 缓存的短链快照：真正存进 Redis 的内容（序列化成 JSON）。
 * <p>
 * 只挑了跳转链路需要的三个字段（目标地址 / 状态 / 过期时间）——
 * 缓存不该把整张表都搬进去：字段越少，序列化越省、语义越清楚。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShortLinkCacheBO {

    /** 目标原始地址。 */
    private String originUrl;

    /** 状态（1 正常 / 0 禁用）。 */
    private Integer status;

    /** 过期时间，null 表示永不过期。 */
    private LocalDateTime expireTime;

}
