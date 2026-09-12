package com.daylog.modules.report.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.daylog.common.result.Result;
import com.daylog.modules.report.dto.GenerateReportDTO;
import com.daylog.modules.report.service.WeeklyReportService;
import com.daylog.modules.report.vo.WeeklyReportVO;
import com.daylog.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 周报接口
 */
@Tag(name = "AI 周报模块", description = "AI 周报生成与查询")
@RestController
@RequestMapping("/ai/reports")
@RequiredArgsConstructor
public class WeeklyReportController {

    private final WeeklyReportService weeklyReportService;

    @Operation(summary = "手动生成周报（同周重复生成覆盖更新）")
    @PostMapping("/generate")
    public Result<Long> generate(@Valid @RequestBody GenerateReportDTO dto) {
        return Result.success(weeklyReportService.generate(SecurityUtils.getCurrentUserId(), dto));
    }

    @Operation(summary = "周报列表")
    @GetMapping
    public Result<Page<WeeklyReportVO>> page(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return Result.success(weeklyReportService.page(SecurityUtils.getCurrentUserId(), page, size));
    }

    @Operation(summary = "周报详情")
    @GetMapping("/{id}")
    public Result<WeeklyReportVO> getById(@PathVariable Long id) {
        return Result.success(weeklyReportService.getById(id, SecurityUtils.getCurrentUserId()));
    }
}
