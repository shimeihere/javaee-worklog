package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.ChangePasswordRequest;
import com.worklog.dto.LoginRequest;
import com.worklog.entity.SysDept;
import com.worklog.entity.SysUser;
import com.worklog.mapper.SysDeptMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.security.JwtUtil;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import com.worklog.service.AuthService;
import com.worklog.vo.LoginVO;
import com.worklog.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 账号状态：禁用 */
    private static final int STATUS_DISABLED = 1;

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginVO login(LoginRequest request) {
        String username = request.getUsername().trim();
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));

        // 账号不存在与密码错误返回同一提示，避免账号枚举
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("登录失败（账号或密码错误）：{}", username);
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }

        // 密码正确后再校验状态，避免"禁用提示"泄露账号存在性
        if (Integer.valueOf(STATUS_DISABLED).equals(user.getStatus())) {
            log.warn("登录被拦截（账号已禁用）：{}", username);
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        String token = jwtUtil.generateToken(user);
        log.info("登录成功：{}（{}）", user.getUsername(), user.getRealName());

        return LoginVO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpiresInSeconds())
                .user(toUserInfo(user))
                .build();
    }

    @Override
    public UserInfoVO currentUserInfo() {
        LoginUser loginUser = UserContext.require();
        SysUser user = sysUserMapper.selectById(loginUser.id());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (Integer.valueOf(STATUS_DISABLED).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        return toUserInfo(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        Long userId = UserContext.requireUserId();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "原密码不正确");
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码不能与原密码相同");
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        sysUserMapper.updateById(update);

        log.info("修改密码成功：userId={}, username={}", userId, user.getUsername());
    }

    /** 实体 -> 视图对象，顺带补齐角色中文名与部门名称 */
    private UserInfoVO toUserInfo(SysUser user) {
        String deptName = null;
        if (user.getDeptId() != null) {
            SysDept dept = sysDeptMapper.selectById(user.getDeptId());
            if (dept != null) {
                deptName = dept.getDeptName();
            }
        }
        return UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRole())
                .roleName(RoleConstant.displayName(user.getRole()))
                .deptId(user.getDeptId())
                .deptName(deptName)
                .status(user.getStatus())
                .build();
    }
}
