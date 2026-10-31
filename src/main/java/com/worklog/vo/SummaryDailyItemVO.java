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
 * 汇总中心"查看明细"抽屉里的单条日报。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryDailyItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private LocalDate reportDate;

    private BigDecimal hours;

    /** 0 草稿 / 1 已提交 */
    private Integer status;

    private String projectName;

    private String contentDone;

    private String contentPlan;
}
