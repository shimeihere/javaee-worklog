package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.worklog.common.PageResult;
import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.UserCreateRequest;
import com.worklog.dto.UserQuery;
import com.worklog.entity.DailyReport;
import com.worklog.entity.SysDept;
import com.worklog.entity.SysUser;
import com.worklog.mapper.DailyReportMapper;
import com.worklog.mapper.SysDeptMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import com.worklog.service.DataScopeService;
import com.worklog.service.SysUserService;
import com.worklog.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    /** 每页最大条数，防止前端传超大 size 拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 100;

    /** 账号状态：禁用 */
    private static final int STATUS_DISABLED = 1;

    /** 账号状态：正常 */
    private static final int STATUS_NORMAL = 0;

    /** 日报状态：草稿（未提交） */
    private static final int REPORT_STATUS_DRAFT = 0;

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final DailyReportMapper dailyReportMapper;
    private final DataScopeService dataScopeService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO create(UserCreateRequest request) {
        String username = request.getUsername().trim();
        String role = request.getRole() == null ? "" : request.getRole().trim().toUpperCase();
        if (!RoleConstant.ADMIN.equals(role)
                && !RoleConstant.EXECUTIVE.equals(role)
                && !RoleConstant.DEPT_LEADER.equals(role)
                && !RoleConstant.EMPLOYEE.equals(role)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "角色只能是 EMPLOYEE / DEPT_LEADER / EXECUTIVE / ADMIN");
        }

        long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (exists > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "登录账号已存在：" + username);
        }

        SysDept dept = sysDeptMapper.selectById(request.getDeptId());
        if (dept == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "所选部门不存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        // 初始密码同登录校验，统一 BCrypt 存储
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName().trim());
        user.setRole(role);
        user.setDeptId(dept.getId());
        user.setStatus(STATUS_NORMAL);
        sysUserMapper.insert(user);

        log.info("新增用户：id={}, username={}, role={}, deptId={}",
                user.getId(), username, role, dept.getId());
        return toVOList(List.of(user)).get(0);
    }

    @Override
    public PageResult<UserVO> page(UserQuery query) {
        long page = Math.max(query.getPage(), 1);
        long size = Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getRealName, keyword));
        }
        if (query.getDeptId() != null) {
            wrapper.eq(SysUser::getDeptId, query.getDeptId());
        }
        if (StringUtils.hasText(query.getRole())) {
            wrapper.eq(SysUser::getRole, query.getRole().trim().toUpperCase());
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, query.getStatus());
        }

        // 数据范围：ADMIN 全部、DEPT_LEADER 本部门、EMPLOYEE 仅本人
        dataScopeService.applyUserScope(wrapper);
        wrapper.orderByAsc(SysUser::getId);

        Page<SysUser> result = sysUserMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result, toVOList(result.getRecords()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateStatus(Long userId, Integer status) {
        if (status == null || (status != STATUS_NORMAL && status != STATUS_DISABLED)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "状态值只能是 0（正常）或 1（禁用）");
        }
        LoginUser operator = UserContext.require();
        if (Objects.equals(operator.id(), userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能修改自己的账号状态");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (Objects.equals(user.getStatus(), status)) {
            return "账号状态未发生变化";
        }

        // 防止把系统里最后一个可用管理员禁掉，导致没人能管理系统
        if (status == STATUS_DISABLED && RoleConstant.ADMIN.equals(user.getRole())) {
            long otherActiveAdmins = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getRole, RoleConstant.ADMIN)
                    .eq(SysUser::getStatus, STATUS_NORMAL)
                    .ne(SysUser::getId, userId));
            if (otherActiveAdmins == 0) {
                log.warn("拒绝禁用最后一个可用管理员：userId={}, operator={}", userId, operator.username());
                throw new BusinessException(ResultCode.BAD_REQUEST, "系统必须保留至少一个可用的管理员");
            }
        }

        String message = status == STATUS_DISABLED ? "账号已禁用" : "账号已启用";

        // 禁用前先统计未提交的日报草稿，给管理员一个明确提示（不阻断操作）
        if (status == STATUS_DISABLED) {
            long draftCount = dailyReportMapper.selectCount(new LambdaQueryWrapper<DailyReport>()
                    .eq(DailyReport::getUserId, userId)
                    .eq(DailyReport::getStatus, REPORT_STATUS_DRAFT));
            if (draftCount > 0) {
                message = "账号已禁用。该用户还有 " + draftCount + " 份未提交的日报草稿，建议先提醒其提交。";
            }
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setStatus(status);
        sysUserMapper.updateById(update);

        log.info("用户状态变更：userId={}, username={}, status={}, operator={}",
                userId, user.getUsername(), status, operator.username());
        return message;
    }

    /** 批量补齐部门名称，避免逐行查库 */
    private List<UserVO> toVOList(List<SysUser> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        Set<Long> deptIds = users.stream()
                .map(SysUser::getDeptId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : sysDeptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));

        return users.stream()
                .map(user -> UserVO.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .realName(user.getRealName())
                        .role(user.getRole())
                        .roleName(RoleConstant.displayName(user.getRole()))
                        .deptId(user.getDeptId())
                        .deptName(user.getDeptId() == null ? null : deptNames.get(user.getDeptId()))
                        .status(user.getStatus())
                        .createTime(user.getCreateTime())
                        .build())
                .toList();
    }
}
