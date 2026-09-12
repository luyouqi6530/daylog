package com.daylog.modules.diary.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.daylog.common.result.Result;
import com.daylog.modules.diary.dto.DiaryQueryDTO;
import com.daylog.modules.diary.dto.DiarySaveDTO;
import com.daylog.modules.diary.service.DiaryService;
import com.daylog.modules.diary.vo.CalendarDayVO;
import com.daylog.modules.diary.vo.DiaryDetailVO;
import com.daylog.modules.diary.vo.DiaryVO;
import com.daylog.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 日记接口
 */
@Tag(name = "日记模块", description = "日记 CRUD / 日历视图")
@RestController
@RequestMapping("/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @Operation(summary = "日记列表（分页+多条件）")
    @GetMapping
    public Result<Page<DiaryVO>> page(DiaryQueryDTO query) {
        return Result.success(diaryService.page(query, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "日记详情")
    @GetMapping("/{id}")
    public Result<DiaryDetailVO> getById(@PathVariable Long id) {
        return Result.success(diaryService.getById(id, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "查询指定日期日记（编辑今天场景，无记录 data 为 null）")
    @GetMapping("/date/{recordDate}")
    public Result<DiaryDetailVO> getByDate(@PathVariable String recordDate) {
        return Result.success(diaryService.getByDate(recordDate, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "创建日记")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody DiarySaveDTO dto) {
        return Result.success(diaryService.create(dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "更新日记")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DiarySaveDTO dto) {
        diaryService.update(id, dto, SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @Operation(summary = "删除日记")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        diaryService.delete(id, SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @Operation(summary = "日历视图")
    @GetMapping("/calendar")
    public Result<List<CalendarDayVO>> calendar(@RequestParam int year, @RequestParam int month) {
        return Result.success(diaryService.calendar(year, month, SecurityUtils.getCurrentUserId()));
    }
}
