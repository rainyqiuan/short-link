package com.itqiuan.shortlink.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;

/**
 * 创建短链的入参。
 * <p>
 * 校验注解集中放在 DTO 上，而不是散在 Service 里逐个 if 判断 —— 这样：
 * ① 校验规则和字段定义在一起，改字段时顺手就能看到规则；
 * ② Controller 上的 {@code @Valid} 自动触发，省掉手写校验代码。
 * 校验失败会由 GlobalExceptionHandler 翻译成业务错误码。
 */
@Data
@Schema(description = "短链创建参数")
public class ShortLinkCreateDTO {

    /** 原始 URL：非空 + 必须 http/https 开头 + 长度上限 2048。 */
    @NotBlank(message = "原始URL不能为空")
    @URL(regexp = "^(http|https)://.+$", message = "原始URL格式不正确")
    @Size(max = 2048, message = "原始URL长度不能超过2048")
    @Schema(description = "原始URL")
    private String originUrl;

    /**
     * 自定义短码，<b>可以不填</b>。正则 {@code ^$|^[0-9a-zA-Z]{4,16}$} 里的 {@code ^$}
     * 就是用来放行"空串"的（不填时前端可能传空串而不是 null）。
     */
    @Pattern(
            regexp = "^$|^[0-9a-zA-Z]{4,16}$",
            message = "短码如果填写，必须为4-16位，且只能包含大小写字母和数字"
    )
    @Schema(description = "自定义短码")
    private String customCode;

    /** 过期时间，可以不填（不填表示永不过期）；填了必须是未来时间。 */
    //时间格式
    @Future(message = "过期时间必须是未来的时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

}
