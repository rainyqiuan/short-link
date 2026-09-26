package com.itqiuan.shortlink.controller;

import com.itqiuan.shortlink.common.result.Result;
import com.itqiuan.shortlink.dto.ShortLinkCreateDTO;
import com.itqiuan.shortlink.service.ShortLinkService;
import com.itqiuan.shortlink.vo.ShortLinkVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/link")
@Tag(name = "短链管理")
public class ShortLinkController {

    @Autowired
    private ShortLinkService shortLinkService;

    @PostMapping
    @Operation(summary = "创建短链")
    public Result<ShortLinkVO> createShortLink(@Valid @RequestBody ShortLinkCreateDTO shortLinkCreateDTO) {
        return Result.success(shortLinkService.createShortLink(shortLinkCreateDTO));
    }
}
