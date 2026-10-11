package com.itqiuan.shortlink.controller;

import com.itqiuan.shortlink.common.result.Result;
import com.itqiuan.shortlink.dto.ShortLinkCreateDTO;
import com.itqiuan.shortlink.service.ShortLinkService;
import com.itqiuan.shortlink.service.StatsService;
import com.itqiuan.shortlink.vo.LinkStatsVO;
import com.itqiuan.shortlink.vo.ShortLinkVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/link")
@Tag(name = "短链管理")
public class ShortLinkController {

    private final ShortLinkService shortLinkService;
    private final StatsService statsService;

    public ShortLinkController(ShortLinkService shortLinkService, StatsService statsService) {
        this.shortLinkService = shortLinkService;
        this.statsService = statsService;
    }

    @PostMapping
    @Operation(summary = "创建短链")
    public Result<ShortLinkVO> createShortLink(@Valid @RequestBody ShortLinkCreateDTO shortLinkCreateDTO) {
        return Result.success(shortLinkService.createShortLink(shortLinkCreateDTO));
    }

    @GetMapping("/{code}/stats")
    @Operation(summary = "获取短链统计信息")
    public Result<LinkStatsVO> getStats(@PathVariable String code, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(statsService.queryStats(code, startDate, endDate));
    }

}
