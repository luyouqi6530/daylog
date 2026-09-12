package com.daylog.modules.file.service;

import com.daylog.modules.file.entity.Attachment;
import com.daylog.modules.file.vo.AttachmentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 附件服务：上传、关联回填、清理
 */
public interface AttachmentService {

    /**
     * 上传图片：UUID 重命名落盘 + 落库（diary_id 为空，等日记保存时回填）
     */
    AttachmentVO upload(MultipartFile file, Long userId);

    /**
     * 日记保存时回填：把一批附件归属到日记（校验归属用户）
     */
    void linkToDiary(List<Long> attachmentIds, Long diaryId, Long userId);

    /**
     * 日记更新时解除不再引用的附件（diary_id 置空，重新等待关联或被清理）
     */
    void unlinkNotIn(Long diaryId, List<Long> keepAttachmentIds);

    /**
     * 查询日记的图片列表（详情页用）
     */
    List<AttachmentVO> getByDiaryId(Long diaryId);

    /**
     * 批量统计一组日记的配图数（列表页用，避免 N+1）
     *
     * @return diaryId -> 图片数
     */
    Map<Long, Long> countByDiaryIds(List<Long> diaryIds);

    /**
     * 删除日记时：删除附件记录 + 物理文件
     */
    void deleteByDiaryId(Long diaryId);

    /**
     * 清理孤儿附件：上传超过 24 小时仍未关联任何日记的（定时任务调用）
     *
     * @return 清理数量
     */
    int cleanOrphans();

    /**
     * 相对路径转访问 URL（静态资源映射前缀 /files/）
     */
    String toUrl(String filePath);
}
