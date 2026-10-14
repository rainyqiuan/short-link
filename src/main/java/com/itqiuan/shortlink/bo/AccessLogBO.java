package com.itqiuan.shortlink.bo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 访问日志的缓冲对象：跳转时构造、序列化成 JSON 塞进 Redis 队列，定时任务再取出来落库。
 * <p>
 * 为什么要单独一个 BO 而不直接用 entity：写入队列的那一刻 linkId 还没确定（跳转链路不查库），
 * 而且塞进 Redis 的对象应是"可序列化的纯数据"，拿 entity 容易日后被人误加关联逻辑。
 */
@Data
@NoArgsConstructor
public class AccessLogBO {

    /** 短码。 */
    private String shortCode;

    /** 关联的短链 id（入队时可能为 null，落库前补全）。 */
    private Long linkId;

    /** 访问日期。 */
    private LocalDate statDate;

    /** 访问时刻。 */
    private LocalDateTime accessTime;

    /** 客户端 IP。 */
    private String clientIp;

    /** 浏览器 User-Agent。 */
    private String userAgent;

    /** 来源页（Referer）。 */
    private String referer;

}
