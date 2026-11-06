package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 项目列表/详情视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String projectName;

    /** 项目负责人ID */
    private Long leaderId;

    /** 项目负责人姓名（前端直接展示） */
    private String leaderName;

    private Long deptId;

    private String deptName;

    private String description;

    /** 0 进行中 / 1 已归档 */
    private Integer status;

    /** 立项日期（create_time 的日期） */
    private LocalDate startDate;

    /** 计划完成日期，可空 */
    private LocalDate plannedEndDate;

    /** 已进行天数（立项当天 = 第 1 天） */
    private Integer elapsedDays;

    /** 计划工期（含首尾），未设计划完成日时为空 */
    private Integer plannedDays;

    /** 剩余天数，负数表示已超期 */
    private Integer remainingDays;

    /** 时间进度 0-100，未设计划完成日时为空 */
    private Integer timeProgress;

    /** 是否已超过计划完成日 */
    private boolean overdue;

    private LocalDateTime createTime;
}
