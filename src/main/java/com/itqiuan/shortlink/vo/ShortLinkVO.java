package com.itqiuan.shortlink.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "短链信息")
public class ShortLinkVO {

    @Schema(description = "短链码")
    private String shortCode;

    @Schema(description = "短链URL")
    private String shortUrl;

    @Schema(description = "原始URL")
    private String originUrl;

    @Schema(description = "过期时间")
    private String expireTime;

    @Schema(description = "创建时间")
    private String createTime;

}
