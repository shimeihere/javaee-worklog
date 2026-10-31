package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.constant.ReportConstant;
import com.worklog.constant.RoleConstant;
import com.worklog.entity.DailyReport;
import com.worklog.entity.Project;
import com.worklog.entity.SysDept;
import com.worklog.entity.SysUser;
import com.worklog.entity.WeeklyReport;
import com.worklog.mapper.DailyReportMapper;
import com.worklog.mapper.ProjectMapper;
import com.worklog.mapper.SysDeptMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.mapper.WeeklyReportMapper;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import com.worklog.service.MemberService;
import com.worklog.vo.MemberVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 成员花名册实现。
 *
 * <p>权限：管理员/高管可看任意部门与项目；部门负责人只能看本部门（项目须属于本部门或本人负责）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final ProjectMapper projectMapper;
    private final DailyReportMapper dailyReportMapper;
    private final WeeklyReportMapper weeklyReportMapper;

    @Override
    public List<MemberVO> deptMembers(Long deptId) {
        LoginUser me = UserContext.require();
        boolean global = RoleConstant.isGlobal(me.role());

        if (!global) {
            if (!RoleConstant.DEPT_LEADER.equals(me.role())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权查看人员名单");
            }
            if (me.deptId() == null || !Objects.equals(me.deptId(), deptId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能查看本部门成员");
            }
        }

        SysDept dept = sysDeptMapper.selectById(deptId);
        if (dept == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "部门不存在");
        }

        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, deptId)
                .ne(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getRole)
                .orderByAsc(SysUser::getId));

        return users.stream()
                .map(user -> toVO(user, dept.getDeptName()))
                .toList();
    }

    @Override
    public List<MemberVO> projectMembers(Long projectId) {
        LoginUser me = UserContext.require();
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "项目不存在");
        }

        boolean global = RoleConstant.isGlobal(me.role());
        if (!global) {
            if (!RoleConstant.DEPT_LEADER.equals(me.role())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权查看人员名单");
            }
            boolean sameDept = me.deptId() != null && Objects.equals(me.deptId(), project.getDeptId());
            boolean isLeader = Objects.equals(me.id(), project.getLeaderId());
            if (!sameDept && !isLeader) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能查看本部门项目成员");
            }
        }

        // 负责人 + 在该项目填报过日报/周报的成员
        Set<Long> userIds = new LinkedHashSet<>();
        if (project.getLeaderId() != null) {
            userIds.add(project.getLeaderId());
        }
        dailyReportMapper.selectList(new LambdaQueryWrapper<DailyReport>()
                        .select(DailyReport::getUserId)
                        .eq(DailyReport::getProjectId, projectId)
                        .eq(DailyReport::getStatus, ReportConstant.STATUS_SUBMITTED))
                .stream().map(DailyReport::getUserId).filter(Objects::nonNull).forEach(userIds::add);
        weeklyReportMapper.selectList(new LambdaQueryWrapper<WeeklyReport>()
                        .select(WeeklyReport::getUserId)
                        .eq(WeeklyReport::getProjectId, projectId)
                        .eq(WeeklyReport::getStatus, ReportConstant.STATUS_SUBMITTED))
                .stream().map(WeeklyReport::getUserId).filter(Objects::nonNull).forEach(userIds::add);

        if (userIds.isEmpty()) {
            return List.of();
        }

        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        Map<Long, String> deptNames = deptNames(users);

        List<MemberVO> members = new ArrayList<>();
        for (SysUser user : users) {
            if (Integer.valueOf(1).equals(user.getStatus())) {
                continue;
            }
            members.add(toVO(user, user.getDeptId() == null ? null : deptNames.get(user.getDeptId())));
        }

        // 负责人排最前，其余按角色、姓名
        members.sort(Comparator
                .comparing((MemberVO member) -> !Objects.equals(member.getId(), project.getLeaderId()))
                .thenComparing(MemberVO::getRoleName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MemberVO::getRealName, Comparator.nullsLast(Comparator.naturalOrder())));
        return members;
    }

    private MemberVO toVO(SysUser user, String deptName) {
        return MemberVO.builder()
                .id(user.getId())
                .realName(user.getRealName())
                .roleName(RoleConstant.displayName(user.getRole()))
                .deptName(deptName)
                .build();
    }

    private Map<Long, String> deptNames(List<SysUser> users) {
        Set<Long> ids = users.stream().map(SysUser::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sysDeptMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a,
                        LinkedHashMap::new));
    }
}
