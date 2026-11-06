package com.worklog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 编辑用户请求（仅 ADMIN）。
 *
 * <p>登录账号不允许修改（历史日报/周报、站内信都以用户ID关联，账号是登录凭据）；
 * 可改的是姓名、角色、所属部门，以及参与的项目。</p>
 */
@Data
public class UserUpdateRequest {

    @NotBlank(message = "请填写真实姓名")
    @Size(max = 50, message = "姓名不能超过 50 个字符")
    private String realName;

    /** EMPLOYEE / DEPT_LEADER / EXECUTIVE / ADMIN */
    @NotBlank(message = "请选择角色")
    private String role;

    @NotNull(message = "请选择所属部门")
    private Long deptId;

    /**
     * 参与的项目ID集合。
     *
     * <p>null 表示"不改项目"；传空数组表示"退出全部项目"。</p>
     */
    private List<Long> projectIds;
}
