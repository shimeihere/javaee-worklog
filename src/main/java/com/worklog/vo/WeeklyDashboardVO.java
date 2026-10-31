package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 本周填报统计。
 *
 * <p>分母是"本周已过去的工作日（含今天）"，避免把未来还没到的时间算成未提交。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyDashboardVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 本周已过去的工作日数 */
    private int workdays;

    /** 已提交天数 */
    private int submittedDays;

    /** 逾期天数（应提交未提交） */
    private int overdueDays;

    /** 提交率（0-100 整数） */
    private int submitRate;
}
