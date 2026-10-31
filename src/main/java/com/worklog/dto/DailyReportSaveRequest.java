package com.worklog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日报保存请求（存草稿与编辑草稿共用）。
 */
@Data
public class DailyReportSaveRequest {

    /** 日报日期；新增时必填，编辑时以原记录日期为准、本字段忽略 */
    private LocalDate reportDate;

    /** 关联项目ID */
    private Long projectId;

    /** 今日完成内容 */
    @Size(max = 4000, message = "完成事项不能超过 4000 字")
    private String contentDone;

    /** 明日计划 */
    @Size(max = 4000, message = "明日计划不能超过 4000 字")
    private String contentPlan;

    /** 工时（小时） */
    @DecimalMin(value = "0", message = "工时不能为负数")
    @DecimalMax(value = "24", message = "单日工时不能超过 24 小时")
    private BigDecimal hours;
}
