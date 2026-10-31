package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前用户信息（登录响应与 /auth/me 共用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 角色编码：ADMIN / DEPT_LEADER / EMPLOYEE */
    private String role;

    /** 角色中文名，前端直接展示 */
    private String roleName;

    /** 所属部门ID */
    private Long deptId;

    /** 所属部门名称 */
    private String deptName;

    /** 状态：0 正常 / 1 禁用 */
    private Integer status;
}
