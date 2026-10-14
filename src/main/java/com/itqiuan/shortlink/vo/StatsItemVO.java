package com.itqiuan.shortlink.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 统计明细中的"某一天"：该日的访问量（PV）与独立访客数（UV）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "统计信息")
public class StatsItemVO {

    @Schema(description = "统计日期")
    private LocalDate statDate;

    @Schema(description = "访问量")
    private Long pv;

    @Schema(description = "独立用户数")
    private Long uv;

}
