package com.itqiuan.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itqiuan.shortlink.entity.LinkStatsDaily;
import com.itqiuan.shortlink.vo.StatsItemVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface LinkStatsDailyMapper extends BaseMapper<LinkStatsDaily> {
    int upsertBatch(@Param("list") List<LinkStatsDaily> list);

    List<StatsItemVO> selectByCodeAndRange(
            @Param("code") String code,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );
}
