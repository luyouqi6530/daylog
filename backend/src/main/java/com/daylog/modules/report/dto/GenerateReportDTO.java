package com.daylog.modules.report.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 手动生成周报入参
 */
@Data
public class GenerateReportDTO {

    /** 必须为周一日期 */
    @NotNull(message = "周起始日期不能为空")
    private LocalDate weekStartDate;
}
