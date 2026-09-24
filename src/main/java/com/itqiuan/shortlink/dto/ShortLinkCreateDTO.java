package com.itqiuan.shortlink.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data

public class ShortLinkCreateDTO {

    @NotEmpty
    @URL(message = "URL格式不正确")
    @Pattern(regexp = "^(http|https)://.*$", message = "只支持 http 或 https 协议")
    private String originUrl;

    private String customCode;

    private String expireTime;

}
