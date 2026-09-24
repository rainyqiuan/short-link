package com.itqiuan.shortlink.vo;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShortLinkVO {

    private String shortCode;

    private String shortUrl;

    private String originUrl;

    private String expireTime;

    private String createTime;

}
