package com.worklog.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 汇总中心查询条件（四维共用）。
 */
@Data
public class SummaryQuery {

    /** 维度：user 人 / project 项目 / dept 部门 / date 时间 */
    private String dimension = "user";

    private long page = 1;

    private long size = 10;

    /** 起始日期，缺省为当月 1 号 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 结束日期，缺省为今天 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private Long deptId;

    private Long projectId;

    /** 关键字：对人名/项目名/部门名做模糊匹配 */
    private String keyword;
}
