package com.itqiuan.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 按天汇总实体，对应表 {@code t_link_stats_daily}。
 * <p>
 * 存储粒度是"一条短链 + 一天"一行，PV/UV 由统计任务定期重算并 upsert ——
 * 查询统计时直接读它，不用去扫明细表。
 */
@Data
@TableName("t_link_stats_daily")
public class LinkStatsDaily {

    /** 主键，数据库自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 短码。 */
    private String shortCode;

    /** 统计日期。 */
    private LocalDate statDate;

    /** 当日访问量（PV）。 */
    private Long pv;

    /** 当日益客去重数（UV，来源于 HyperLogLog 估算值）。 */
    private Long uv;

    /** 本行最后一次被聚合任务刷新的时刻。 */
    private LocalDateTime updateTime;

}
