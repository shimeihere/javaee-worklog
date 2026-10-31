package com.worklog.dto;

import lombok.Data;

/**
 * 用户分页查询条件。
 *
 * <p>作为 GET 查询参数绑定：{@code /sys/users?page=1&size=10&role=EMPLOYEE}。</p>
 */
@Data
public class UserQuery {

    /** 页码，从 1 开始 */
    private long page = 1;

    /** 每页条数，服务端会限制上限 */
    private long size = 10;

    /** 关键字：账号或姓名，模糊匹配 */
    private String keyword;

    /** 部门筛选 */
    private Long deptId;

    /** 角色筛选：EMPLOYEE / DEPT_LEADER / ADMIN */
    private String role;

    /** 状态筛选：0 正常 / 1 禁用 */
    private Integer status;
}
