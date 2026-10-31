package com.worklog.service;

import com.worklog.dto.ChangePasswordRequest;
import com.worklog.dto.LoginRequest;
import com.worklog.vo.LoginVO;
import com.worklog.vo.UserInfoVO;

/**
 * 认证服务。
 */
public interface AuthService {

    /**
     * 账号密码登录：BCrypt 校验 + 账号状态拦截 + 签发 JWT。
     *
     * @param request 登录请求
     * @return 令牌与用户信息
     */
    LoginVO login(LoginRequest request);

    /**
     * 获取当前登录用户信息（/auth/me）。
     */
    UserInfoVO currentUserInfo();

    /**
     * 修改密码（个人中心），需校验原密码。
     */
    void changePassword(ChangePasswordRequest request);
}
