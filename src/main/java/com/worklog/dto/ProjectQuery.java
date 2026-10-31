package com.worklog.dto;

import lombok.Data;

/**
 * 项目分页查询条件（数据范围由 Service 层统一追加，前端无法绕过）。
 */
@Data
public class ProjectQuery {

    /** 页码，从 1 开始 */
    private long page = 1;

    /** 每页条数，服务端会限制上限 */
    private long size = 10;

    /** 关键字：项目名称模糊匹配 */
    private String keyword;

    /** 部门筛选 */
    private Long deptId;

    /** 状态筛选：0 进行中 / 1 已归档 */
    private Integer status;
}
