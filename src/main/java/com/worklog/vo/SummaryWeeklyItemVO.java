package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 汇总中心"查看明细"抽屉里的单条周报。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryWeeklyItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 本周一 */
    private LocalDate startDate;

    /** 本周日 */
    private LocalDate endDate;

    private String projectName;

    private String summary;

    private String plan;

    /** 0 草稿 / 1 已提交 */
    private Integer status;
}
