package com.daylog.modules.diary.vo;

import com.daylog.modules.file.vo.AttachmentVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 日记详情视图对象（比列表多图片明细）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DiaryDetailVO extends DiaryVO {

    private List<AttachmentVO> images = new ArrayList<>();
}
