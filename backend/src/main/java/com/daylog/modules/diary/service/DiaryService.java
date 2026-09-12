package com.daylog.modules.diary.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.daylog.modules.diary.dto.DiaryQueryDTO;
import com.daylog.modules.diary.dto.DiarySaveDTO;
import com.daylog.modules.diary.vo.CalendarDayVO;
import com.daylog.modules.diary.vo.DiaryDetailVO;
import com.daylog.modules.diary.vo.DiaryVO;

/**
 * 日记服务
 */
public interface DiaryService {

    /** 分页 + 多条件列表 */
    Page<DiaryVO> page(DiaryQueryDTO query, Long userId);

    /** 详情（校验归属） */
    DiaryDetailVO getById(Long id, Long userId);

    /** 查询指定日期日记（"编辑今天"场景），无记录返回 null */
    DiaryDetailVO getByDate(String recordDate, Long userId);

    /** 创建（一天一篇约束 + 标签关联 + 附件回填） */
    Long create(DiarySaveDTO dto, Long userId);

    /** 更新 */
    void update(Long id, DiarySaveDTO dto, Long userId);

    /** 删除（物理删除，级联清理关联与附件） */
    void delete(Long id, Long userId);

    /** 日历视图：某年某月有记录的日期与心情分 */
    java.util.List<CalendarDayVO> calendar(int year, int month, Long userId);
}
