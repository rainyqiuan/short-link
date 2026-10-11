package com.itqiuan.shortlink.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatsItemVO {
    private LocalDate statDate;
    private Long pv;
    private Long uv;

}
