package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 团队今日提交情况（仅部门负责人 / 管理员可见）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamDashboardVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 团队人数（管理员=全公司，负责人=本部门） */
    private int memberCount;

    /** 今日已提交人数 */
    private int submittedToday;

    /** 今日提交率（0-100 整数） */
    private int submitRate;
}
