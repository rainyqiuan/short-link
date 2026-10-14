package com.itqiuan.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itqiuan.shortlink.entity.LinkStatsDaily;
import com.itqiuan.shortlink.vo.StatsItemVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 按天汇总 Mapper。
 * <p>
 * 两个自定义方法都写在 {@code resources/mapper/LinkStatsDailyMapper.xml}：
 * upsert 与聚合查询用注解写法会很乱，放 XML 更好读。
 */
public interface LinkStatsDailyMapper extends BaseMapper<LinkStatsDaily> {

    /**
     * 批量 upsert（按"短码 + 日期"唯一键插入或更新）。
     * <p>
     * 用一条 SQL 完成"有则更新、无则插入"，避免"先查后写"的竞态，也省往返。
     *
     * @param list 待写入的汇总行
     * @return 受影响行数
     */
    int upsertBatch(@Param("list") List<LinkStatsDaily> list);

    /**
     * 查某条短码在日期区间内的每日统计（直接返回 VO，省一层手工搬运）。
     */
    List<StatsItemVO> selectByCodeAndRange(
            @Param("code") String code,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );
}
