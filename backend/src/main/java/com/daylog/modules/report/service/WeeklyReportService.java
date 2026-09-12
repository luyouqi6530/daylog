package com.daylog.modules.report.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.daylog.modules.report.dto.GenerateReportDTO;
import com.daylog.modules.report.vo.WeeklyReportVO;

/**
 * AI 周报服务
 */
public interface WeeklyReportService {

    /** 为指定用户生成某周周报（同周重复生成 upsert 覆盖），返回周报ID */
    Long generate(Long userId, GenerateReportDTO dto);

    /** 周报分页列表 */
    Page<WeeklyReportVO> page(Long userId, int page, int size);

    /** 周报详情（校验归属） */
    WeeklyReportVO getById(Long id, Long userId);
}
