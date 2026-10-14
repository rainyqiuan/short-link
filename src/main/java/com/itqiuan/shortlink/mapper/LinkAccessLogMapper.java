package com.itqiuan.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itqiuan.shortlink.entity.LinkAccessLog;
import com.itqiuan.shortlink.entity.LinkStatsDaily;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 访问明细 Mapper。
 * <p>
 * 除了 MP 内置方法，这里有一个自定义查询（{@code selectPvByDateRange}），
 * SQL 写在 {@code resources/mapper/LinkAccessLogMapper.xml} 里 —— 聚合查询用 Lambda 表达不了，
 * 但依旧不写成 {@code @Select} 注解：SQL 放在 XML 里能整体看、能复用片段，也不污染 Java 文件。
 */
public interface LinkAccessLogMapper extends BaseMapper<LinkAccessLog> {

    /**
     * 按日期区间统计每条短码的 PV（统计聚合的初始数据来源）。
     *
     * @param start 起始日期（含）
     * @param end   结束日期（含）
     * @return 每条短码一行，带 PV（uv 此时可能为 null，后续再由 HLL 补上）
     */
    List<LinkStatsDaily> selectPvByDateRange(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );
}
