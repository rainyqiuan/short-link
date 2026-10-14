package com.itqiuan.shortlink.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 创建短链后返回给调用方的信息。
 * <p>
 * 注意时间字段是<b>字符串</b>（已格式化），不是时间类型 —— 这是对外的呈现约定。
 */
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
