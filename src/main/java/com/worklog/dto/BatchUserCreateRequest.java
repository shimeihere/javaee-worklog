package com.worklog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量新增用户请求（仅 ADMIN）。
 *
 * <p>适用于"一次招了多个人"的场景：外层给统一默认值（初始密码 / 角色 / 部门），
 * 每个用户行可以覆盖角色与部门。整批逐行校验，成功的立即生效，失败的按行返回原因，
 * 不会因为某一行账号重复就整批回滚。</p>
 */
@Data
public class BatchUserCreateRequest {

    /** 统一初始密码（行内 password 可覆盖） */
    @NotBlank(message = "请填写初始密码")
    @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
    private String password;

    /** 默认角色，行内 role 可覆盖；不填按 EMPLOYEE */
    private String role = "EMPLOYEE";

    /** 默认部门，行内 deptId 可覆盖 */
    private Long deptId;

    @NotEmpty(message = "请至少填写一个用户")
    @Size(max = 100, message = "一次最多批量新增 100 个用户")
    @Valid
    private List<Item> users;

    /**
     * 单个待新增用户。
     */
    @Data
    public static class Item {

        @NotBlank(message = "登录账号不能为空")
        @Size(max = 50, message = "账号不能超过 50 个字符")
        private String username;

        @NotBlank(message = "真实姓名不能为空")
        @Size(max = 50, message = "姓名不能超过 50 个字符")
        private String realName;

        /** 可选：覆盖外层默认角色 */
        private String role;

        /** 可选：覆盖外层默认部门 */
        private Long deptId;

        /** 可选：覆盖外层统一密码 */
        private String password;
    }
}
