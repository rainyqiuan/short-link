package com.itqiuan.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_link_access_log")
public class LinkAccessLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String shortCode;
    private Long linkId;
    private LocalDate statDate;
    private LocalDateTime accessTime;
    private String clientIp;
    private String userAgent;
    private String referer;
}
