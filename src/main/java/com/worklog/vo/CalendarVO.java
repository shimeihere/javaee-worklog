package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 月历视图：当前用户某个月每天的填报状态 + 汇总数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 形如 2026-10 */
    private String yearMonth;

    /** 当月总天数 */
    private int totalDays;

    /** 当月工作日数（周一至周五） */
    private int workdays;

    /** 已提交天数 */
    private int submittedDays;

    /** 逾期天数 */
    private int overdueDays;

    /** 未填天数（工作日中尚未到期的） */
    private int notFilledDays;

    /** 当月提交率（0-100 的整数，便于直接展示） */
    private int submitRate;

    @Builder.Default
    private List<CalendarDayVO> days = new ArrayList<>();
}
