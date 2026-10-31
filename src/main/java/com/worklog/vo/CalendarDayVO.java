package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日历单日状态。
 *
 * <p>status：0 未填 / 1 已提交 / 2 已逾期（工作日应提交而未提交，含仅存草稿的情况）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarDayVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private LocalDate date;

    /** 0 未填 / 1 已提交 / 2 已逾期 */
    private Integer status;

    /** 当天日报ID，未填时为 null */
    private Long reportId;

    /** 当天工时，未填时为 null */
    private BigDecimal hours;

    /** 是否为工作日（前端可据此区分周末底色） */
    private Boolean workday;
}
