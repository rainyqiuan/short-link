package com.itqiuan.shortlink.bo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class AccessLogBO {

    private String shortCode;
    private Long linkId;
    private LocalDate statDate;
    private LocalDateTime accessTime;
    private String clientIp;
    private String userAgent;
    private String referer;

}
