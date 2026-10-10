package com.itqiuan.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itqiuan.shortlink.entity.LinkStatsDaily;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LinkStatsDailyMapper extends BaseMapper<LinkStatsDaily> {
    int upsertBatch(@Param("list") List<LinkStatsDaily> list);
}
