package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 成员花名册条目（部门/项目下钻名单用，只给姓名与角色）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String realName;

    private String roleName;

    private String deptName;
}
