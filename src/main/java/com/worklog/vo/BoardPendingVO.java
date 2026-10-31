package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 待提醒成员。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardPendingVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;

    private String realName;

    private String deptName;

    private String roleName;

    /** 范围内缺报天数 */
    private int missingDays;

    /** 范围内最后一次提交日期，从未提交为 null */
    private LocalDate lastSubmitDate;

    /** 今日是否已提交 */
    private boolean todaySubmitted;
}
