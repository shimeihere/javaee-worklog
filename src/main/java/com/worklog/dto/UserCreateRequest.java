package com.worklog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增用户请求（仅 ADMIN）。
 */
@Data
public class UserCreateRequest {

    @NotBlank(message = "请填写登录账号")
    @Size(max = 50, message = "账号不能超过 50 个字符")
    private String username;

    @NotBlank(message = "请填写初始密码")
    @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
    private String password;

    @NotBlank(message = "请填写真实姓名")
    @Size(max = 50, message = "姓名不能超过 50 个字符")
    private String realName;

    /** EMPLOYEE / DEPT_LEADER / ADMIN */
    @NotBlank(message = "请选择角色")
    private String role;

    @NotNull(message = "请选择所属部门")
    private Long deptId;
}
