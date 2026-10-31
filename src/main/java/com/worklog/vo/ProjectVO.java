package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
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

    private LocalDateTime createTime;
}
