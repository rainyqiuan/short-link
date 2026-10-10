package com.itqiuan.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itqiuan.shortlink.entity.LinkAccessLog;
import com.itqiuan.shortlink.entity.LinkStatsDaily;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface LinkAccessLogMapper extends BaseMapper<LinkAccessLog> {

    List<LinkStatsDaily> selectPvByDateRange(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );
}
