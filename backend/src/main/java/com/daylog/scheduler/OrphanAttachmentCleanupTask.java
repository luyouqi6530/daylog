package com.daylog.scheduler;

import com.daylog.modules.file.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 孤儿附件清理任务
 *
 * <p>附件设计为"先上传后关联"：编辑器里传了图但最终没保存日记，
 * 这些附件 24 小时内未关联任何日记即成为孤儿，每天凌晨 3 点清理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanAttachmentCleanupTask {

    private final AttachmentService attachmentService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanOrphanAttachments() {
        try {
            int cleaned = attachmentService.cleanOrphans();
            if (cleaned > 0) {
                log.info("孤儿附件清理完成，共清理 {} 个", cleaned);
            }
        } catch (Exception e) {
            // 定时任务必须自己兜异常，否则异常抛出后本次调度静默终止
            log.error("孤儿附件清理任务执行失败", e);
        }
    }
}
