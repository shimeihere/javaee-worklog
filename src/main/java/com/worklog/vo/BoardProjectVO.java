package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 看板右侧"按项目"排行的一行。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardProjectVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long projectId;

    private String projectName;

    private String leaderName;

    private String deptName;

    /** 区间内该项目收到的已提交日报数 */
    private int submitted;

    /** 参与人数（在该项目填报过的人） */
    private int participants;

    private int rate;

    /** 立项日期 */
    private LocalDate startDate;

    /** 计划完成日期（可空） */
    private LocalDate plannedEndDate;

    /** 已进行天数（立项当天 = 第 1 天） */
    private Integer elapsedDays;

    /** 计划工期（含首尾） */
    private Integer plannedDays;

    /** 剩余天数，负数表示超期 */
    private Integer remainingDays;

    /** 时间进度 0-100；没设计划完成日为空 */
    private Integer timeProgress;

    /** 是否超期 */
    private boolean overdue;
}
