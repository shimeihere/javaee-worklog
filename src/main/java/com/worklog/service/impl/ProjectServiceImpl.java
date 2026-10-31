package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.worklog.common.PageResult;
import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.ProjectCreateRequest;
import com.worklog.dto.ProjectQuery;
import com.worklog.entity.Project;
import com.worklog.entity.SysDept;
import com.worklog.entity.SysUser;
import com.worklog.mapper.ProjectMapper;
import com.worklog.mapper.SysDeptMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import com.worklog.service.DataScopeService;
import com.worklog.service.ProjectService;
import com.worklog.vo.ProjectVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    private final ProjectMapper projectMapper;
    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final DataScopeService dataScopeService;

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
        project.setStatus(PROJECT_STATUS_ACTIVE);
        projectMapper.insert(project);

        log.info("创建项目：id={}, name={}, leaderId={}, deptId={}, operator={}",
                project.getId(), project.getProjectName(), leader.getId(), dept.getId(), operator.username());

        return toVO(project, leader.getRealName(), dept.getDeptName());
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
        return ProjectVO.builder()
                .id(project.getId())
                .projectName(project.getProjectName())
                .leaderId(project.getLeaderId())
                .leaderName(leaderName)
                .deptId(project.getDeptId())
                .deptName(deptName)
                .description(project.getDescription())
                .status(project.getStatus())
                .createTime(project.getCreateTime())
                .build();
    }
}
