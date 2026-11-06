package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.worklog.common.PageResult;
import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.constant.AuditAction;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.BatchUserCreateRequest;
import com.worklog.dto.UserCreateRequest;
import com.worklog.dto.UserQuery;
import com.worklog.dto.UserUpdateRequest;
import com.worklog.entity.DailyReport;
import com.worklog.entity.Project;
import com.worklog.entity.ProjectMember;
import com.worklog.entity.SysDept;
import com.worklog.entity.SysUser;
import com.worklog.mapper.DailyReportMapper;
import com.worklog.mapper.ProjectMapper;
import com.worklog.mapper.ProjectMemberMapper;
import com.worklog.mapper.SysDeptMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import com.worklog.service.AuditService;
import com.worklog.service.DataScopeService;
import com.worklog.service.MessageService;
import com.worklog.service.SysUserService;
import com.worklog.vo.BatchUserResultVO;
import com.worklog.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

    /** 单个用户最多同时参与的项目数，防止误点全选把关系表塞满 */
    private static final int MAX_PROJECTS_PER_USER = 50;

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final DailyReportMapper dailyReportMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final DataScopeService dataScopeService;
    private final PasswordEncoder passwordEncoder;
    private final MessageService messageService;
    private final AuditService auditService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO create(UserCreateRequest request) {
        String username = request.getUsername().trim();
        String role = normalizeRole(request.getRole());
        assertUsernameAvailable(username);
        SysDept dept = requireDept(request.getDeptId());

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

        // 系统通知：把初始密码与角色告知本人
        notifyCreated(user, dept, request.getPassword());

        auditService.record(AuditAction.CREATE_USER, "USER", user.getId(),
                "新增用户 " + username + "（" + user.getRealName() + " · "
                        + RoleConstant.displayName(role) + " · " + dept.getDeptName() + "）");

        return toVOList(List.of(user)).get(0);
    }

    @Override
    public BatchUserResultVO batchCreate(BatchUserCreateRequest request) {
        List<BatchUserCreateRequest.Item> items = request.getUsers();
        // 默认角色没填时按员工处理，避免整批被一个空值卡住
        String defaultRole = StringUtils.hasText(request.getRole())
                ? normalizeRole(request.getRole()) : RoleConstant.EMPLOYEE;
        SysDept defaultDept = request.getDeptId() == null ? null : requireDept(request.getDeptId());

        BatchUserResultVO result = BatchUserResultVO.builder()
                .total(items.size())
                .createdCount(0)
                .failedCount(0)
                .build();

        // 同一批里同密码只做一次 BCrypt（BCrypt 单次约 80ms，100 行会白等 8 秒）
        Map<String, String> encodedCache = new HashMap<>();
        Set<String> seenUsernames = new HashSet<>();

        for (int index = 0; index < items.size(); index++) {
            BatchUserCreateRequest.Item item = items.get(index);
            String username = item.getUsername() == null ? "" : item.getUsername().trim();
            String label = "第 " + (index + 1) + " 行「" + (username.isEmpty() ? "空账号" : username) + "」";

            try {
                if (username.isEmpty()) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "登录账号不能为空");
                }
                if (item.getRealName() == null || item.getRealName().trim().isEmpty()) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "真实姓名不能为空");
                }
                if (!seenUsernames.add(username)) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "本次提交里账号重复");
                }
                assertUsernameAvailable(username);

                String role = StringUtils.hasText(item.getRole())
                        ? normalizeRole(item.getRole()) : defaultRole;
                SysDept dept = item.getDeptId() != null ? requireDept(item.getDeptId()) : defaultDept;
                if (dept == null) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "请为这一行选择部门");
                }

                String rawPassword = StringUtils.hasText(item.getPassword())
                        ? item.getPassword() : request.getPassword();
                if (rawPassword == null || rawPassword.length() < 6 || rawPassword.length() > 32) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "初始密码长度需为 6-32 位");
                }

                SysUser user = new SysUser();
                user.setUsername(username);
                user.setPassword(encodedCache.computeIfAbsent(rawPassword, passwordEncoder::encode));
                user.setRealName(item.getRealName().trim());
                user.setRole(role);
                user.setDeptId(dept.getId());
                user.setStatus(STATUS_NORMAL);
                sysUserMapper.insert(user);

                notifyCreated(user, dept, rawPassword);

                result.getCreatedUsernames().add(username);
                result.setCreatedCount(result.getCreatedCount() + 1);
                log.info("批量新增用户：id={}, username={}, role={}, deptId={}",
                        user.getId(), username, role, dept.getId());
            } catch (BusinessException e) {
                result.getFailures().add(label + "：" + e.getMessage());
                result.setFailedCount(result.getFailedCount() + 1);
            } catch (Exception e) {
                // 单行写入异常不影响其他行
                log.warn("批量新增用户失败：username={}", username, e);
                result.getFailures().add(label + "：写入失败，请稍后重试");
                result.setFailedCount(result.getFailedCount() + 1);
            }
        }

        log.info("批量新增用户完成：total={}, created={}, failed={}",
                result.getTotal(), result.getCreatedCount(), result.getFailedCount());
        if (result.getCreatedCount() > 0) {
            String names = result.getCreatedUsernames().stream().limit(10)
                    .collect(Collectors.joining("、"))
                    + (result.getCreatedUsernames().size() > 10 ? " 等" : "");
            auditService.record(AuditAction.BATCH_CREATE_USER, "USER", null,
                    "批量新增 " + result.getCreatedCount() + " 人：" + names
                            + (result.getFailedCount() > 0 ? "；失败 " + result.getFailedCount() + " 行" : ""));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO update(Long userId, UserUpdateRequest request) {
        LoginUser operator = UserContext.require();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        String role = normalizeRole(request.getRole());
        SysDept dept = requireDept(request.getDeptId());

        boolean roleChanged = !Objects.equals(user.getRole(), role);
        boolean deptChanged = !Objects.equals(user.getDeptId(), dept.getId());
        boolean nameChanged = !Objects.equals(user.getRealName(), request.getRealName().trim());

        if (Objects.equals(operator.id(), userId) && roleChanged) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "不能修改自己的角色，请让另一位管理员操作");
        }
        // 与禁用逻辑一致：系统里必须留至少一个可用管理员
        if (RoleConstant.ADMIN.equals(user.getRole()) && !RoleConstant.ADMIN.equals(role)) {
            long otherActiveAdmins = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getRole, RoleConstant.ADMIN)
                    .eq(SysUser::getStatus, STATUS_NORMAL)
                    .ne(SysUser::getId, userId));
            if (otherActiveAdmins == 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "系统必须保留至少一个可用的管理员");
            }
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setRealName(request.getRealName().trim());
        update.setRole(role);
        update.setDeptId(dept.getId());
        sysUserMapper.updateById(update);

        // projectIds 为 null 表示"项目不动"，空数组表示"退出全部项目"
        List<String> projectNames = null;
        if (request.getProjectIds() != null) {
            projectNames = syncUserProjects(userId, request.getProjectIds());
        }

        if (nameChanged || roleChanged || deptChanged || projectNames != null) {
            StringBuilder content = new StringBuilder("你的账号信息已被管理员更新。");
            if (nameChanged) {
                content.append("\n姓名：").append(user.getRealName()).append(" → ")
                        .append(request.getRealName().trim());
            }
            if (roleChanged) {
                content.append("\n角色：").append(RoleConstant.displayName(user.getRole()))
                        .append(" → ").append(RoleConstant.displayName(role));
            }
            if (deptChanged) {
                content.append("\n部门：").append(deptNameOf(user.getDeptId()))
                        .append(" → ").append(dept.getDeptName());
            }
            if (projectNames != null) {
                content.append("\n参与项目：")
                        .append(projectNames.isEmpty() ? "（已全部移出）" : String.join("、", projectNames));
            }
            messageService.notifySystem(userId, "账号信息已更新", content.toString());
        }

        log.info("编辑用户：id={}, username={}, role={}, deptId={}, projects={}, operator={}",
                userId, user.getUsername(), role, dept.getId(), projectNames, operator.username());

        List<String> changes = new ArrayList<>();
        if (nameChanged) {
            changes.add("姓名 " + user.getRealName() + " → " + request.getRealName().trim());
        }
        if (roleChanged) {
            changes.add("角色 " + RoleConstant.displayName(user.getRole())
                    + " → " + RoleConstant.displayName(role));
        }
        if (deptChanged) {
            changes.add("部门 " + deptNameOf(user.getDeptId()) + " → " + dept.getDeptName());
        }
        if (projectNames != null) {
            changes.add("参与项目 " + (projectNames.isEmpty() ? "清空" : "→ " + String.join("、", projectNames)));
        }
        if (!changes.isEmpty()) {
            auditService.record(AuditAction.UPDATE_USER, "USER", userId,
                    "编辑用户 " + user.getUsername() + "：" + String.join("；", changes));
        }

        return toVOList(List.of(sysUserMapper.selectById(userId))).get(0);
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
        auditService.record(AuditAction.USER_STATUS, "USER", userId,
                (status == STATUS_DISABLED ? "禁用" : "启用") + "账号 " + user.getUsername()
                        + "（" + user.getRealName() + "）");
        return message;
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 角色编码校验并归一化（大小写不敏感），非法直接抛 400 */
    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase();
        if (!RoleConstant.ADMIN.equals(normalized)
                && !RoleConstant.EXECUTIVE.equals(normalized)
                && !RoleConstant.DEPT_LEADER.equals(normalized)
                && !RoleConstant.EMPLOYEE.equals(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "角色只能是 EMPLOYEE / DEPT_LEADER / EXECUTIVE / ADMIN");
        }
        return normalized;
    }

    private SysDept requireDept(Long deptId) {
        SysDept dept = deptId == null ? null : sysDeptMapper.selectById(deptId);
        if (dept == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "所选部门不存在");
        }
        return dept;
    }

    private void assertUsernameAvailable(String username) {
        long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (exists > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "登录账号已存在：" + username);
        }
    }

    private void notifyCreated(SysUser user, SysDept dept, String rawPassword) {
        messageService.notifySystem(user.getId(), "账号已开通",
                "你的账号「" + user.getUsername() + "」已创建，角色："
                        + RoleConstant.displayName(user.getRole())
                        + "，所属部门：" + dept.getDeptName()
                        + "。初始密码：" + rawPassword + "，请登录后到「个人中心」修改密码。");
    }

    /** 覆盖式同步用户参与的项目，返回最终的项目名称列表 */
    private List<String> syncUserProjects(Long userId, List<Long> projectIds) {
        List<Long> wanted = projectIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (wanted.size() > MAX_PROJECTS_PER_USER) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "单个用户最多同时参与 " + MAX_PROJECTS_PER_USER + " 个项目");
        }

        List<Project> projects = wanted.isEmpty() ? List.of() : projectMapper.selectBatchIds(wanted);
        if (projects.size() != wanted.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "所选项目中有不存在的记录，请刷新后重试");
        }

        projectMemberMapper.delete(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getUserId, userId));
        for (Project project : projects) {
            ProjectMember member = new ProjectMember();
            member.setProjectId(project.getId());
            member.setUserId(userId);
            projectMemberMapper.insert(member);
        }

        return projects.stream()
                .sorted(Comparator.comparing(Project::getId))
                .map(Project::getProjectName)
                .toList();
    }

    private String deptNameOf(Long deptId) {
        if (deptId == null) {
            return "未分配";
        }
        SysDept dept = sysDeptMapper.selectById(deptId);
        return dept == null ? "未分配" : dept.getDeptName();
    }

    /** 批量补齐部门名称与参与项目，避免逐行查库 */
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

        // 一次查出本页所有用户的参与项目，避免 10 行 10 次查询
        Set<Long> userIds = users.stream().map(SysUser::getId).collect(Collectors.toSet());
        Map<Long, List<Long>> projectIdsByUser = new HashMap<>();
        for (ProjectMember member : projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                .in(ProjectMember::getUserId, userIds))) {
            projectIdsByUser.computeIfAbsent(member.getUserId(), key -> new ArrayList<>())
                    .add(member.getProjectId());
        }
        Set<Long> allProjectIds = projectIdsByUser.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        Map<Long, String> projectNames = allProjectIds.isEmpty() ? Map.of()
                : projectMapper.selectBatchIds(allProjectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getProjectName, (a, b) -> a));

        return users.stream()
                .map(user -> {
                    List<Long> mine = projectIdsByUser.getOrDefault(user.getId(), List.of()).stream()
                            .sorted()
                            .toList();
                    return UserVO.builder()
                            .id(user.getId())
                            .username(user.getUsername())
                            .realName(user.getRealName())
                            .role(user.getRole())
                            .roleName(RoleConstant.displayName(user.getRole()))
                            .deptId(user.getDeptId())
                            .deptName(user.getDeptId() == null ? null : deptNames.get(user.getDeptId()))
                            .status(user.getStatus())
                            .projectIds(mine)
                            .projectNames(mine.stream()
                                    .map(projectNames::get)
                                    .filter(Objects::nonNull)
                                    .toList())
                            .createTime(user.getCreateTime())
                            .build();
                })
                .toList();
    }
}
