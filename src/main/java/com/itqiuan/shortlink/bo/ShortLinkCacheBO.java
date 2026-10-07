package com.itqiuan.shortlink.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShortLinkCacheBO {

    private String originUrl;
    private Integer status;
    private LocalDateTime expireTime;

}
