package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.worklog.common.PageResult;
import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.constant.AuditAction;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.ProjectCreateRequest;
import com.worklog.dto.ProjectQuery;
import com.worklog.dto.ProjectUpdateRequest;
import com.worklog.entity.Project;
import com.worklog.entity.SysDept;
import com.worklog.entity.SysUser;
import com.worklog.mapper.ProjectMapper;
import com.worklog.mapper.SysDeptMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import com.worklog.service.AuditService;
import com.worklog.service.DataScopeService;
import com.worklog.service.MessageService;
import com.worklog.service.ProjectService;
import com.worklog.util.ProjectTime;
import com.worklog.vo.ProjectVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private static final long MAX_PAGE_SIZE = 100;

    /** 账号状态：禁用 */
    private static final int USER_STATUS_DISABLED = 1;

    /** 项目状态：进行中 */
    private static final int PROJECT_STATUS_ACTIVE = 0;

    /** 项目状态：已归档 */
    private static final int PROJECT_STATUS_ARCHIVED = 1;

    private final ProjectMapper projectMapper;
    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final DataScopeService dataScopeService;
    private final MessageService messageService;
    private final AuditService auditService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO create(ProjectCreateRequest request) {
        LoginUser operator = UserContext.require();
        Long deptId = request.getDeptId();

        // 部门负责人只能为本部门建项目；管理员可跨部门
        if (RoleConstant.DEPT_LEADER.equals(operator.role()) && !Objects.equals(deptId, operator.deptId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "部门负责人只能为本部门创建项目");
        }

        SysDept dept = sysDeptMapper.selectById(deptId);
        if (dept == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "所选部门不存在");
        }

        SysUser leader = sysUserMapper.selectById(request.getLeaderId());
        if (leader == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "指定的项目负责人不存在");
        }
        if (Integer.valueOf(USER_STATUS_DISABLED).equals(leader.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "项目负责人账号已被禁用，请更换负责人");
        }

        Project project = new Project();
        project.setProjectName(request.getProjectName().trim());
        project.setLeaderId(leader.getId());
        project.setDeptId(dept.getId());
        project.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        if (request.getPlannedEndDate() != null && request.getPlannedEndDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "计划完成日期不能早于今天");
        }
        project.setPlannedEndDate(request.getPlannedEndDate());
        project.setStatus(PROJECT_STATUS_ACTIVE);
        projectMapper.insert(project);

        log.info("创建项目：id={}, name={}, leaderId={}, deptId={}, operator={}",
                project.getId(), project.getProjectName(), leader.getId(), dept.getId(), operator.username());

        // 系统通知：告诉负责人这个项目交给他了
        messageService.notifySystem(leader.getId(), "你被指派为项目负责人",
                "项目「" + project.getProjectName() + "」已创建，负责人是你（所属部门："
                        + dept.getDeptName() + "），请关注项目进度与成员填报。");

        auditService.record(AuditAction.CREATE_PROJECT, "PROJECT", project.getId(),
                "新建项目「" + project.getProjectName() + "」，部门：" + dept.getDeptName()
                        + "，负责人：" + leader.getRealName()
                        + (project.getPlannedEndDate() == null ? "，未设完成日"
                        : "，计划完成：" + project.getPlannedEndDate()));

        return toVO(project, leader.getRealName(), dept.getDeptName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO changeLeader(Long id, Long leaderId) {
        LoginUser operator = UserContext.require();
        Project project = projectMapper.selectById(id);
        if (project == null || !dataScopeService.canViewProject(project)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "项目不存在或无权访问");
        }
        if (RoleConstant.DEPT_LEADER.equals(operator.role())
                && !Objects.equals(project.getDeptId(), operator.deptId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能变更本部门项目的负责人");
        }

        SysUser leader = sysUserMapper.selectById(leaderId);
        if (leader == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "指定的项目负责人不存在");
        }
        if (Integer.valueOf(USER_STATUS_DISABLED).equals(leader.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "项目负责人账号已被禁用，请更换负责人");
        }

        Long oldLeaderId = project.getLeaderId();
        if (Objects.equals(oldLeaderId, leaderId)) {
            return toVOList(List.of(project)).get(0);
        }

        project.setLeaderId(leader.getId());
        projectMapper.updateById(project);

        // 系统通知：新负责人收指派通知，旧负责人收移交通知
        messageService.notifySystem(leader.getId(), "你被指派为项目负责人",
                "项目「" + project.getProjectName() + "」的负责人已变更为你，请关注项目进度与成员填报。");
        if (oldLeaderId != null) {
            messageService.notifySystem(oldLeaderId, "项目负责人已变更",
                    "项目「" + project.getProjectName() + "」的负责人已移交给 " + leader.getRealName() + "。");
        }

        log.info("变更项目负责人：id={}, {} -> {}, operator={}",
                id, oldLeaderId, leader.getId(), operator.username());
        auditService.record(AuditAction.CHANGE_PROJECT_LEADER, "PROJECT", id,
                "项目「" + project.getProjectName() + "」负责人变更为 " + leader.getRealName());
        return toVOList(List.of(project)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO update(Long id, ProjectUpdateRequest request) {
        LoginUser operator = UserContext.require();
        Project project = projectMapper.selectById(id);
        if (project == null || !dataScopeService.canViewProject(project)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "项目不存在或无权访问");
        }
        if (RoleConstant.DEPT_LEADER.equals(operator.role())
                && !Objects.equals(project.getDeptId(), operator.deptId())
                && !Objects.equals(project.getLeaderId(), operator.id())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能编辑本部门或本人负责的项目");
        }

        int oldStatus = project.getStatus() == null ? PROJECT_STATUS_ACTIVE : project.getStatus();
        int status = request.getStatus() == null ? oldStatus : request.getStatus();
        if (status != PROJECT_STATUS_ACTIVE && status != PROJECT_STATUS_ARCHIVED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "项目状态只能是 0（进行中）或 1（已归档）");
        }

        LocalDate start = ProjectTime.startDate(project.getCreateTime());
        if (request.getPlannedEndDate() != null && request.getPlannedEndDate().isBefore(start)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "计划完成日期不能早于立项日期");
        }
        LocalDate oldPlannedEnd = project.getPlannedEndDate();

        project.setProjectName(request.getProjectName().trim());
        // 描述允许清空：MyBatis-Plus 的 not_null 策略会跳过 null，这里用空串表示"清空"
        project.setDescription(StringUtils.hasText(request.getDescription())
                ? request.getDescription().trim() : "");
        project.setPlannedEndDate(request.getPlannedEndDate());
        project.setStatus(status);
        projectMapper.updateById(project);

        log.info("编辑项目：id={}, name={}, status={} -> {}, operator={}",
                id, project.getProjectName(), oldStatus, status, operator.username());

        List<String> changes = new ArrayList<>();
        if (oldStatus != status) {
            changes.add("状态 " + (oldStatus == PROJECT_STATUS_ACTIVE ? "进行中" : "已归档")
                    + " → " + (status == PROJECT_STATUS_ACTIVE ? "进行中" : "已归档"));
        }
        if (!Objects.equals(oldPlannedEnd, project.getPlannedEndDate())) {
            changes.add("计划完成日 " + (oldPlannedEnd == null ? "未设置" : oldPlannedEnd)
                    + " → " + (project.getPlannedEndDate() == null ? "未设置" : project.getPlannedEndDate()));
        }
        if (changes.isEmpty()) {
            changes.add("更新名称/描述");
        }
        auditService.record(AuditAction.UPDATE_PROJECT, "PROJECT", id,
                "编辑项目「" + project.getProjectName() + "」：" + String.join("；", changes));

        if (status == PROJECT_STATUS_ARCHIVED && oldStatus != PROJECT_STATUS_ARCHIVED
                && project.getLeaderId() != null) {
            messageService.notifySystem(project.getLeaderId(), "项目已归档",
                    "项目「" + project.getProjectName() + "」已被 " + operator.username()
                            + " 归档，如需继续填报请先恢复为「进行中」。");
        }

        return toVOList(List.of(projectMapper.selectById(id))).get(0);
    }

    @Override
    public PageResult<ProjectVO> page(ProjectQuery query) {
        long page = Math.max(query.getPage(), 1);
        long size = Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(Project::getProjectName, query.getKeyword().trim());
        }
        if (query.getDeptId() != null) {
            wrapper.eq(Project::getDeptId, query.getDeptId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Project::getStatus, query.getStatus());
        }

        dataScopeService.applyProjectScope(wrapper);
        wrapper.orderByDesc(Project::getId);

        Page<Project> result = projectMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result, toVOList(result.getRecords()));
    }

    @Override
    public ProjectVO detail(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null || !dataScopeService.canViewProject(project)) {
            // 越权与不存在返回同一提示，避免通过 id 探测其他部门项目
            throw new BusinessException(ResultCode.NOT_FOUND, "项目不存在或无权访问");
        }
        return toVOList(List.of(project)).get(0);
    }

    /** 批量补齐负责人姓名与部门名称，避免逐行查库 */
    private List<ProjectVO> toVOList(List<Project> projects) {
        if (projects.isEmpty()) {
            return List.of();
        }
        Set<Long> leaderIds = projects.stream()
                .map(Project::getLeaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> deptIds = projects.stream()
                .map(Project::getDeptId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> leaderNames = leaderIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(leaderIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : sysDeptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));

        return projects.stream()
                .map(project -> toVO(project,
                        project.getLeaderId() == null ? null : leaderNames.get(project.getLeaderId()),
                        project.getDeptId() == null ? null : deptNames.get(project.getDeptId())))
                .toList();
    }

    private ProjectVO toVO(Project project, String leaderName, String deptName) {
        LocalDate today = LocalDate.now();
        LocalDate start = ProjectTime.startDate(project.getCreateTime());
        return ProjectVO.builder()
                .id(project.getId())
                .projectName(project.getProjectName())
                .leaderId(project.getLeaderId())
                .leaderName(leaderName)
                .deptId(project.getDeptId())
                .deptName(deptName)
                .description(project.getDescription())
                .status(project.getStatus())
                .startDate(start)
                .plannedEndDate(project.getPlannedEndDate())
                .elapsedDays(ProjectTime.elapsedDays(start, today))
                .plannedDays(ProjectTime.plannedDays(start, project.getPlannedEndDate()))
                .remainingDays(ProjectTime.remainingDays(project.getPlannedEndDate(), today))
                .timeProgress(ProjectTime.timeProgress(start, project.getPlannedEndDate(), today))
                .overdue(ProjectTime.overdue(project.getPlannedEndDate(), today))
                .createTime(project.getCreateTime())
                .build();
    }
}
