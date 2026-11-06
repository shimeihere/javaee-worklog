package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

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

    /** 参与的项目ID（编辑用户时回填多选框） */
    private List<Long> projectIds;

    /** 参与的项目名称（列表直接展示，不用再逐行查库） */
    private List<String> projectNames;

    private LocalDateTime createTime;
}
