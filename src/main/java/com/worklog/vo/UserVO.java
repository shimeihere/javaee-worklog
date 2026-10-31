package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户管理列表行（管理端使用，不含密码）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String realName;

    private String role;

    private String roleName;

    private Long deptId;

    private String deptName;

    /** 0 正常 / 1 禁用 */
    private Integer status;

    private LocalDateTime createTime;
}
