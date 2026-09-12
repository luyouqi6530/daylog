package com.daylog.modules.tag.controller;

import com.daylog.common.result.Result;
import com.daylog.modules.tag.dto.TagSaveDTO;
import com.daylog.modules.tag.service.TagService;
import com.daylog.modules.tag.vo.TagVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签接口
 */
@Tag(name = "标签模块", description = "标签管理")
@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @Operation(summary = "标签列表（含使用次数）")
    @GetMapping
    public Result<List<TagVO>> list() {
        return Result.success(tagService.list(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "创建标签")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TagSaveDTO dto) {
        return Result.success(tagService.create(dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "更新标签")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TagSaveDTO dto) {
        tagService.update(id, dto, SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.delete(id, SecurityUtils.getCurrentUserId());
        return Result.success();
    }
}
