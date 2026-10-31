package com.worklog.security;

import java.io.Serializable;

/**
 * 当前登录用户（从 JWT 解析 + 数据库实时状态合并而来）。
 *
 * @param id       用户ID
 * @param username 登录账号
 * @param realName 真实姓名
 * @param role     角色编码
 * @param deptId   所属部门ID
 */
public record LoginUser(Long id, String username, String realName, String role, Long deptId)
        implements Serializable {
}
