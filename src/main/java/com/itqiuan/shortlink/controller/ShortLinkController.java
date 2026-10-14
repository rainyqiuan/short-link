package com.itqiuan.shortlink.controller;

import com.itqiuan.shortlink.common.result.Result;
import com.itqiuan.shortlink.dto.ShortLinkCreateDTO;
import com.itqiuan.shortlink.service.ShortLinkService;
import com.itqiuan.shortlink.service.StatsService;
import com.itqiuan.shortlink.vo.LinkStatsVO;
import com.itqiuan.shortlink.vo.ShortLinkVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 面向开发者的短链管理接口（JSON，前缀 {@code /api/link}）。
 * <p>
 * 与跳转入口 {@code ShortLinkRedirectController} 的区别：这里的方法一律返回 {@code Result}，
 * 业务失败也走 HTTP 200，不需要真实 3xx/4xx 状态码。
 */
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

    /**
     * 创建短链。入参由 {@code @Valid} 触发 Bean Validation 校验，
     * 校验失败由 GlobalExceptionHandler 翻译成对应业务错误码。
     */
    @PostMapping
    @Operation(summary = "创建短链")
    public Result<ShortLinkVO> createShortLink(@Valid @RequestBody ShortLinkCreateDTO shortLinkCreateDTO) {
        return Result.success(shortLinkService.createShortLink(shortLinkCreateDTO));
    }

    /**
     * 查询某条短链在给定日期区间内的统计。
     * 日期用 ISO 格式（yyyy-MM-dd）；格式不对会由 MethodArgumentTypeMismatchException 兜成 40002。
     */
    @GetMapping("/{code}/stats")
    @Operation(summary = "获取短链统计信息")
    public Result<LinkStatsVO> getStats(@Parameter(description = "短链码") @PathVariable String code,
                                        @Parameter(description = "开始日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                        @Parameter(description = "结束日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(statsService.queryStats(code, startDate, endDate));
    }

}
