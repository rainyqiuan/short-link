package com.itqiuan.shortlink.controller;

import com.itqiuan.shortlink.bo.ShortLinkCheckResult;
import com.itqiuan.shortlink.common.annotation.RateLimit;
import com.itqiuan.shortlink.common.enums.LinkUsabilityEnum;
import com.itqiuan.shortlink.common.result.Result;
import com.itqiuan.shortlink.common.result.ResultCode;
import com.itqiuan.shortlink.service.RedirectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Slf4j
@RestController
@Tag(name = "短链重定向")
public class ShortLinkRedirectController {

    private final RedirectService redirectService;

    public ShortLinkRedirectController(RedirectService redirectService) {
        this.redirectService = redirectService;
    }

    @RateLimit(route = "redirect")
    @GetMapping("/{code:[0-9a-zA-Z]{4,16}}")
    @Operation(summary = "短链跳转", description = "浏览器跳转入口，不是开发者接口，勿在文档中点")
    public ResponseEntity<Result<Object>> redirect(@Parameter(description = "短链码") @PathVariable("code") String shortCode) {
        ShortLinkCheckResult redirect = redirectService.redirect(shortCode);
        LinkUsabilityEnum linkUsabilityEnum = redirect.getLinkUsabilityEnum();
        return switch (linkUsabilityEnum) {
            case REUSABLE ->
                    ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirect.getOriginUrl())).build();
            case NOT_FOUND -> {
                log.warn("短链不存在 {}", shortCode);
                yield ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.fail(ResultCode.SHORT_CODE_NOT_EXIST));
            }
            case DISABLED -> {
                log.warn("短链已禁用 {}", shortCode);
                yield ResponseEntity.status(HttpStatus.GONE).body(Result.fail(ResultCode.SHORT_LINK_DISABLED));
            }
            case EXPIRED -> {
                log.warn("短链已过期 {}", shortCode);
                yield ResponseEntity.status(HttpStatus.GONE).body(Result.fail(ResultCode.SHORT_LINK_EXPIRED));
            }
            case UNKNOWN -> {
                log.warn("短链状态未知 {}", shortCode);
                yield ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(ResultCode.SYSTEM_ERROR));
            }
            default ->
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(ResultCode.SYSTEM_ERROR));
        };
    }
}
