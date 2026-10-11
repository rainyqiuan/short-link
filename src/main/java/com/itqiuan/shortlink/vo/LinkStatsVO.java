package com.itqiuan.shortlink.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LinkStatsVO {

    private String shortCode;
    private long totalPv;
    // 人日合计：各天 UV 之和，不等于区间去重人数
    private long totalUv;
    private List<StatsItemVO> items;


}
