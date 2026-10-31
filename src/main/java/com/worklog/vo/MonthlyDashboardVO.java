package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 本月填报统计（口径同 {@link WeeklyDashboardVO}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyDashboardVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 本月已过去的工作日数 */
    private int workdays;

    private int submittedDays;

    private int overdueDays;

    private int submitRate;
}
