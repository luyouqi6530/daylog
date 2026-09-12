package com.daylog.modules.file.controller;

import com.daylog.common.result.Result;
import com.daylog.modules.file.service.AttachmentService;
import com.daylog.modules.file.vo.AttachmentVO;
import com.daylog.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件接口
 */
@Tag(name = "文件模块", description = "图片上传（先上传后关联）")
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final AttachmentService attachmentService;

    @Operation(summary = "上传图片")
    @PostMapping("/upload")
    public Result<AttachmentVO> upload(@RequestPart("file") MultipartFile file) {
        return Result.success(attachmentService.upload(file, SecurityUtils.getCurrentUserId()));
    }
}
