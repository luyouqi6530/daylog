package com.daylog.modules.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.daylog.modules.report.entity.WeeklyReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 周报 Mapper
 */
@Mapper
public interface WeeklyReportMapper extends BaseMapper<WeeklyReport> {
}
