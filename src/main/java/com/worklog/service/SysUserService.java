package com.worklog.service;

import com.worklog.common.PageResult;
import com.worklog.dto.UserCreateRequest;
import com.worklog.dto.UserQuery;
import com.worklog.vo.UserVO;

/**
 * 用户管理服务。
 */
public interface SysUserService {

    /**
     * 分页查询用户，自动应用数据范围（部门负责人仅本部门）。
     */
    PageResult<UserVO> page(UserQuery query);

    /**
     * 新增用户（仅管理员），密码用 BCrypt 加密存储。
     */
    UserVO create(UserCreateRequest request);

    /**
     * 启用/禁用用户。
     *
     * @return 面向管理员的提示语（禁用时若存在未提交日报会附加提醒）
     */
    String updateStatus(Long userId, Integer status);
}
