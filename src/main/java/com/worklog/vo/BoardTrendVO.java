package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 近 7 日提交趋势中的一天。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardTrendVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private LocalDate date;

    /** 当日已提交人数 */
    private int submitted;

    /** 当日应提交人数（周末为 0） */
    private int expected;

    /** 当日提交率 0-100 */
    private int rate;

    private boolean workday;
}
