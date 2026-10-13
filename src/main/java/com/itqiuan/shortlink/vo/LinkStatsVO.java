package com.itqiuan.shortlink.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "短链统计信息")
public class LinkStatsVO {

    @Schema(description = "短链码")
    private String shortCode;

    @Schema(description = "总访问量")
    private long totalPv;

    @Schema(description = "人日合计：各天 UV 之和，不等于区间去重人数")
    private long totalUv;

    @Schema(description = "统计明细")
    private List<StatsItemVO> items;


}
