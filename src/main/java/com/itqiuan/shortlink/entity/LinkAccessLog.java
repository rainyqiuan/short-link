package com.itqiuan.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 访问明细实体，对应表 {@code t_link_access_log}。
 * <p>
 * 这是"流水"表：每次成功跳转产生一条，由定时任务从 Redis 队列<b>异步批量</b>搬运落库，
 * 不在跳转链路上同步写 —— 详见 AccessLogServiceImpl。
 */
@Data
@TableName("t_link_access_log")
public class LinkAccessLog {

    /** 主键，数据库自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 短码。 */
    private String shortCode;

    /** 关联的短链 id。 */
    private Long linkId;

    /** 访问日期（从访问时刻派生，用于按天聚合）。 */
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
