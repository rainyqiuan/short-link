package com.itqiuan.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_link_stats_daily")
public class LinkStatsDaily {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String shortCode;
    private LocalDate statDate;
    private Long pv;
    private Long uv;
    private LocalDateTime updateTime;

}
