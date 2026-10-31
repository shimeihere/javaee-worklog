package com.worklog.controller;

import com.worklog.common.PageResult;
import com.worklog.common.Result;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.ProjectCreateRequest;
import com.worklog.dto.ProjectQuery;
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
