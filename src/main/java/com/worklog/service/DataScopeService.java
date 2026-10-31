package com.worklog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.worklog.constant.RoleConstant;
import com.worklog.entity.DailyReport;
import com.worklog.entity.Project;
import com.worklog.entity.SysUser;
import com.worklog.entity.WeeklyReport;
import com.worklog.mapper.DailyReportMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.mapper.WeeklyReportMapper;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据范围（DataScope）统一过滤。
 *
 * <p>三类角色的可见范围：</p>
 * <ul>
 *   <li><b>ADMIN</b>：全部数据，不追加任何条件。</li>
 *   <li><b>DEPT_LEADER</b>：本部门全部项目 + 本人负责的项目（跨部门兼任场景）。</li>
 *   <li><b>EMPLOYEE</b>：本人负责的项目 + 本人填报过日报/周报的项目（即"参与过"）。</li>
 * </ul>
 *
 * <p><b>为什么"参与"要用日报反查：</b>project 表只有 leader_id 与 dept_id，
 * 没有成员关系表，员工与项目的唯一客观关联就是他在这两个项目上填过的日报/周报。
 * 后续若引入 project_member 表，只需改本类 {@link #participatedProjectIds(Long)} 一处。</p>
 *
 * <p>该过滤器必须只在 Service 层追加（Controller 不暴露 deptId 之外的绕过口），
 * 前端传参无法突破该范围。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final DailyReportMapper dailyReportMapper;
    private final WeeklyReportMapper weeklyReportMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 给项目查询追加数据范围条件。
     */
    public void applyProjectScope(LambdaQueryWrapper<Project> wrapper) {
        LoginUser user = UserContext.require();
        String role = user.role() == null ? "" : user.role();

        switch (role) {
            case RoleConstant.ADMIN, RoleConstant.EXECUTIVE -> {
                // 全局可见，不追加条件
            }
            case RoleConstant.DEPT_LEADER -> {
                Long deptId = user.deptId();
                wrapper.and(w -> w.eq(Project::getLeaderId, user.id())
                        .or(deptId != null, x -> x.eq(Project::getDeptId, deptId)));
            }
            default -> {
                Set<Long> participated = participatedProjectIds(user.id());
                wrapper.and(w -> w.eq(Project::getLeaderId, user.id())
                        .or(!participated.isEmpty(), x -> x.in(Project::getId, participated)));
            }
        }
        log.debug("项目数据范围：user={}, role={}", user.username(), role);
    }

    /**
     * 给用户查询追加数据范围条件（部门负责人只能看本部门成员）。
     */
    public void applyUserScope(LambdaQueryWrapper<SysUser> wrapper) {
        LoginUser user = UserContext.require();
        String role = user.role() == null ? "" : user.role();

        switch (role) {
            case RoleConstant.ADMIN, RoleConstant.EXECUTIVE -> {
                // 全局可见
            }
            case RoleConstant.DEPT_LEADER -> {
                if (user.deptId() == null) {
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.eq(SysUser::getDeptId, user.deptId());
                }
            }
            default -> wrapper.eq(SysUser::getId, user.id());
        }
    }

    /**
     * 单条项目是否对当前登录人可见（详情接口用）。
     */
    public boolean canViewProject(Project project) {
        LoginUser user = UserContext.require();
        if (project == null) {
            return false;
        }
        if (RoleConstant.isGlobal(user.role())) {
            return true;
        }
        if (Objects.equals(project.getLeaderId(), user.id())) {
            return true;
        }
        if (RoleConstant.DEPT_LEADER.equals(user.role())) {
            return user.deptId() != null && Objects.equals(project.getDeptId(), user.deptId());
        }
        return participatedProjectIds(user.id()).contains(project.getId());
    }

    /**
     * 本人"参与过"的项目ID集合：由日报/周报反查得出。
     */
    public Set<Long> participatedProjectIds(Long userId) {
        Set<Long> ids = new HashSet<>();

        dailyReportMapper.selectList(new LambdaQueryWrapper<DailyReport>()
                        .select(DailyReport::getProjectId)
                        .eq(DailyReport::getUserId, userId)
                        .isNotNull(DailyReport::getProjectId))
                .stream()
                .map(DailyReport::getProjectId)
                .filter(Objects::nonNull)
                .forEach(ids::add);

        weeklyReportMapper.selectList(new LambdaQueryWrapper<WeeklyReport>()
                        .select(WeeklyReport::getProjectId)
                        .eq(WeeklyReport::getUserId, userId)
                        .isNotNull(WeeklyReport::getProjectId))
                .stream()
                .map(WeeklyReport::getProjectId)
                .filter(Objects::nonNull)
                .forEach(ids::add);

        return ids;
    }

    // ==================== 填报模块（M3 / M4） ====================

    /**
     * 日报数据范围：ADMIN 全部 / DEPT_LEADER 本部门成员 / EMPLOYEE 仅本人。
     */
    public void applyDailyReportScope(LambdaQueryWrapper<DailyReport> wrapper) {
        applyReportScope(wrapper, DailyReport::getUserId);
    }

    /**
     * 周报数据范围，规则与日报一致。
     */
    public void applyWeeklyReportScope(LambdaQueryWrapper<WeeklyReport> wrapper) {
        applyReportScope(wrapper, WeeklyReport::getUserId);
    }

    /**
     * 单份单据是否对当前登录人可见（详情接口用）。
     */
    public boolean canViewReportOf(Long ownerUserId) {
        LoginUser user = UserContext.require();
        if (RoleConstant.isGlobal(user.role())) {
            return true;
        }
        if (Objects.equals(user.id(), ownerUserId)) {
            return true;
        }
        if (RoleConstant.DEPT_LEADER.equals(user.role()) && user.deptId() != null) {
            SysUser owner = sysUserMapper.selectById(ownerUserId);
            return owner != null && Objects.equals(owner.getDeptId(), user.deptId());
        }
        return false;
    }

    /** 日报/周报共用的取数范围逻辑：按 user_id 列过滤 */
    private <T> void applyReportScope(LambdaQueryWrapper<T> wrapper, SFunction<T, Long> userIdColumn) {
        LoginUser user = UserContext.require();
        String role = user.role() == null ? "" : user.role();

        switch (role) {
            case RoleConstant.ADMIN, RoleConstant.EXECUTIVE -> {
                // 全局可见
            }
            case RoleConstant.DEPT_LEADER -> {
                Set<Long> memberIds = deptMemberIds(user.deptId());
                if (memberIds.isEmpty()) {
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.in(userIdColumn, memberIds);
                }
            }
            default -> wrapper.eq(userIdColumn, user.id());
        }
    }

    /** 某部门下的成员ID集合（供按部门过滤填报数据） */
    private Set<Long> deptMemberIds(Long deptId) {
        if (deptId == null) {
            return Set.of();
        }
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getId)
                        .eq(SysUser::getDeptId, deptId))
                .stream()
                .map(SysUser::getId)
                .collect(Collectors.toSet());
    }
}
