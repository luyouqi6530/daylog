package com.daylog.modules.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.daylog.common.exception.BusinessException;
import com.daylog.common.result.ResultCode;
import com.daylog.modules.file.entity.Attachment;
import com.daylog.modules.file.mapper.AttachmentMapper;
import com.daylog.modules.file.service.AttachmentService;
import com.daylog.modules.file.vo.AttachmentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 附件服务实现
 *
 * <p>存储策略：本地磁盘按 年/月 分目录，文件名 UUID 重命名防冲突与路径穿越。
 * 生产可切换 OSS：只需替换本实现，接口不动（面向接口编程的收益）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private static final String URL_PREFIX = "/files/";

    private final AttachmentMapper attachmentMapper;

    @Value("${daylog.upload.dir}")
    private String uploadDir;

    @Value("${daylog.upload.allowed-extensions}")
    private String allowedExtensions;

    @Override
    public AttachmentVO upload(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上传文件不能为空");
        }
        String originalName = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = getExtension(originalName);

        // 白名单校验扩展名（后缀不可信，前端校验只是体验优化，安全必须后端做）
        Set<String> allowed = Arrays.stream(allowedExtensions.split(","))
                .map(String::trim).map(String::toLowerCase).collect(Collectors.toSet());
        if (!allowed.contains(extension.toLowerCase())) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED,
                    "仅支持 " + String.join("/", allowed) + " 格式");
        }

        // 目录：uploads/2026/09/  文件名：uuid.png
        String subdir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String relativePath = subdir + "/" + fileName;

        try {
            Path target = Paths.get(uploadDir, relativePath);
            Files.createDirectories(target.getParent());
            file.transferTo(target.toAbsolutePath());
        } catch (IOException e) {
            log.error("文件保存失败: {}", originalName, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED);
        }

        Attachment attachment = new Attachment();
        attachment.setUserId(userId);
        attachment.setOriginalName(originalName);
        attachment.setFilePath(relativePath);
        attachment.setFileSize(file.getSize());
        attachment.setMimeType(file.getContentType());
        attachmentMapper.insert(attachment);

        AttachmentVO vo = new AttachmentVO();
        vo.setId(attachment.getId());
        vo.setUrl(toUrl(relativePath));
        vo.setOriginalName(originalName);
        return vo;
    }

    @Override
    public void linkToDiary(List<Long> attachmentIds, Long diaryId, Long userId) {
        if (CollectionUtils.isEmpty(attachmentIds)) {
            return;
        }
        // 只回填属于当前用户的附件，防止拿别人的附件ID关联到自己的日记
        attachmentMapper.update(null, new LambdaUpdateWrapper<Attachment>()
                .set(Attachment::getDiaryId, diaryId)
                .in(Attachment::getId, attachmentIds)
                .eq(Attachment::getUserId, userId));
    }

    @Override
    public void unlinkNotIn(Long diaryId, List<Long> keepAttachmentIds) {
        LambdaUpdateWrapper<Attachment> wrapper = new LambdaUpdateWrapper<Attachment>()
                .set(Attachment::getDiaryId, null)
                .eq(Attachment::getDiaryId, diaryId);
        if (!CollectionUtils.isEmpty(keepAttachmentIds)) {
            wrapper.notIn(Attachment::getId, keepAttachmentIds);
        }
        attachmentMapper.update(null, wrapper);
    }

    @Override
    public List<AttachmentVO> getByDiaryId(Long diaryId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                        .eq(Attachment::getDiaryId, diaryId)
                        .orderByAsc(Attachment::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public Map<Long, Long> countByDiaryIds(List<Long> diaryIds) {
        if (CollectionUtils.isEmpty(diaryIds)) {
            return Map.of();
        }
        return attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                        .select(Attachment::getId, Attachment::getDiaryId)
                        .in(Attachment::getDiaryId, diaryIds))
                .stream().collect(Collectors.groupingBy(Attachment::getDiaryId, Collectors.counting()));
    }

    @Override
    public void deleteByDiaryId(Long diaryId) {
        List<Attachment> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<Attachment>().eq(Attachment::getDiaryId, diaryId));
        if (attachments.isEmpty()) {
            return;
        }
        for (Attachment attachment : attachments) {
            try {
                Files.deleteIfExists(Paths.get(uploadDir, attachment.getFilePath()));
            } catch (IOException e) {
                // 文件删不掉只记日志，不能阻断删除流程（记录已删，残留文件等人工处理）
                log.warn("附件文件删除失败: {}", attachment.getFilePath());
            }
        }
        attachmentMapper.delete(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getDiaryId, diaryId));
    }

    @Override
    public int cleanOrphans() {
        List<Attachment> orphans = attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                .isNull(Attachment::getDiaryId)
                .lt(Attachment::getCreatedAt, LocalDateTime.now().minusHours(24)));
        if (orphans.isEmpty()) {
            return 0;
        }
        for (Attachment attachment : orphans) {
            try {
                Files.deleteIfExists(Paths.get(uploadDir, attachment.getFilePath()));
            } catch (IOException e) {
                log.warn("孤儿附件文件删除失败: {}", attachment.getFilePath());
            }
        }
        attachmentMapper.deleteByIds(orphans.stream().map(Attachment::getId).toList());
        log.info("清理孤儿附件 {} 个", orphans.size());
        return orphans.size();
    }

    @Override
    public String toUrl(String filePath) {
        return URL_PREFIX + filePath;
    }

    private AttachmentVO toVO(Attachment attachment) {
        AttachmentVO vo = new AttachmentVO();
        vo.setId(attachment.getId());
        vo.setUrl(toUrl(attachment.getFilePath()));
        vo.setOriginalName(attachment.getOriginalName());
        return vo;
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        return filename.substring(dot + 1);
    }
}
