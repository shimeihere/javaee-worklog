package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 部门提交率对比项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardDeptVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long deptId;

    private String deptName;

    private int memberCount;

    /** 已提交人次 */
    private int submitted;

    /** 应提交人次 = 人数 × 工作日数 */
    private int expected;

    private int rate;
}
