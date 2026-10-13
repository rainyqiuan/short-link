package com.itqiuan.shortlink.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;

@Data
@Schema(description = "短链创建参数")
public class ShortLinkCreateDTO {

    @NotBlank(message = "原始URL不能为空")
    @URL(regexp = "^(http|https)://.+$", message = "原始URL格式不正确")
    @Size(max = 2048, message = "原始URL长度不能超过2048")
    @Schema(description = "原始URL")
    private String originUrl;

    @Pattern(
            regexp = "^$|^[0-9a-zA-Z]{4,16}$",
            message = "短码如果填写，必须为4-16位，且只能包含大小写字母和数字"
    )
    @Schema(description = "自定义短码")
    private String customCode;

    //时间格式
    @Future(message = "过期时间必须是未来的时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

}
