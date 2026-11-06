package com.worklog.controller;

import com.worklog.common.PageResult;
import com.worklog.common.Result;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.ProjectCreateRequest;
import com.worklog.dto.ProjectLeaderRequest;
import com.worklog.dto.ProjectQuery;
import com.worklog.dto.ProjectUpdateRequest;
import com.worklog.security.RequireRole;
import com.worklog.service.MemberService;
import com.worklog.service.ProjectService;
import com.worklog.vo.MemberVO;
import com.worklog.vo.ProjectVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目管理接口（M2）。
 */
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final MemberService memberService;

    /**
     * 创建项目：POST /api/projects
     *
     * <p>管理员与部门负责人可创建；部门负责人只能为本部门创建。</p>
     */
    @PostMapping
    @RequireRole({RoleConstant.ADMIN, RoleConstant.DEPT_LEADER})
    public Result<ProjectVO> create(@Valid @RequestBody ProjectCreateRequest request) {
        return Result.ok("项目创建成功", projectService.create(request));
    }

    /**
     * 项目列表：GET /api/projects?page=1&size=10&keyword=看板&deptId=2
     *
     * <p>按登录人角色自动过滤数据范围（ADMIN 全部 / DEPT_LEADER 本部门 / EMPLOYEE 负责或参与）。</p>
     */
    @GetMapping
    public Result<PageResult<ProjectVO>> page(ProjectQuery query) {
        return Result.ok(projectService.page(query));
    }

    /**
     * 项目详情：GET /api/projects/{id}
     */
    @GetMapping("/{id}")
    public Result<ProjectVO> detail(@PathVariable Long id) {
        return Result.ok(projectService.detail(id));
    }

    /**
     * 变更项目负责人：PUT /api/projects/{id}/leader
     *
     * <p>管理员与部门负责人（限本部门）可用；会给新旧负责人各写一条系统通知。</p>
     */
    @PutMapping("/{id}/leader")
    @RequireRole({RoleConstant.ADMIN, RoleConstant.DEPT_LEADER})
    public Result<ProjectVO> changeLeader(@PathVariable Long id,
                                          @Valid @RequestBody ProjectLeaderRequest request) {
        return Result.ok("负责人已变更", projectService.changeLeader(id, request.getLeaderId()));
    }

    /**
     * 编辑项目：PUT /api/projects/{id}
     *
     * <p>可改名称、描述与状态（进行中 / 已归档）；部门负责人只能改本部门或本人负责的项目。</p>
     */
    @PutMapping("/{id}")
    @RequireRole({RoleConstant.ADMIN, RoleConstant.DEPT_LEADER})
    public Result<ProjectVO> update(@PathVariable Long id,
                                    @Valid @RequestBody ProjectUpdateRequest request) {
        return Result.ok("项目已更新", projectService.update(id, request));
    }

    /**
     * 项目成员名单：GET /api/projects/{id}/members
     *
     * <p>只返回姓名与角色；管理员/高管可看任意项目，负责人仅本部门或其负责的项目。</p>
     */
    @GetMapping("/{id}/members")
    @RequireRole({RoleConstant.ADMIN, RoleConstant.EXECUTIVE, RoleConstant.DEPT_LEADER})
    public Result<List<MemberVO>> members(@PathVariable Long id) {
        return Result.ok(memberService.projectMembers(id));
    }
}
