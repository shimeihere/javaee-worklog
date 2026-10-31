package com.worklog.controller;

import com.worklog.common.Result;
import com.worklog.dto.ChangePasswordRequest;
import com.worklog.dto.LoginRequest;
import com.worklog.service.AuthService;
import com.worklog.vo.LoginVO;
import com.worklog.vo.UserInfoVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>完整路径前缀为 /api（server.servlet.context-path）。</p>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录：POST /api/auth/login
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok("登录成功", authService.login(request));
    }

    /**
     * 当前登录用户信息：GET /api/auth/me
     */
    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        return Result.ok(authService.currentUserInfo());
    }

    /**
     * 修改密码：PUT /api/auth/password
     *
     * <p>需要校验原密码；修改后旧令牌仍有效（JWT 无状态），前端提示重新登录即可。</p>
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return Result.ok("密码已修改，请用新密码重新登录", null);
    }

    /**
     * 登出：POST /api/auth/logout
     *
     * <p>JWT 无状态，服务端不维护会话，登出由前端丢弃令牌完成；
     * 预留该接口便于后续接入令牌黑名单（Redis）。</p>
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }
}
