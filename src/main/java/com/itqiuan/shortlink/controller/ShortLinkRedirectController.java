package com.itqiuan.shortlink.controller;

import com.itqiuan.shortlink.bo.ShortLinkCheckResult;
import com.itqiuan.shortlink.common.enums.LinkUsabilityEnum;
import com.itqiuan.shortlink.common.result.Result;
import com.itqiuan.shortlink.common.result.ResultCode;
import com.itqiuan.shortlink.service.RedirectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Slf4j
@RestController
public class ShortLinkRedirectController {

    @Autowired
    private RedirectService redirectService;

    @GetMapping("/{code:[0-9a-zA-Z]{4,16}}")
    public ResponseEntity<Result<Object>> redirect(@PathVariable("code") String shortCode) {
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
