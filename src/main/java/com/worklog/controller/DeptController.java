package com.worklog.controller;

import com.worklog.common.Result;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.DeptSaveRequest;
import com.worklog.security.RequireRole;
import com.worklog.service.DeptService;
import com.worklog.service.MemberService;
import com.worklog.vo.DeptTreeVO;
import com.worklog.vo.MemberVO;
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
 * 部门管理接口。
 *
 * <p>部门树属于组织架构公共信息，登录后各角色均需使用（表单选择、筛选器），
 * 因此查询不限制角色；新增/编辑仅管理员可操作。</p>
 */
@RestController
@RequestMapping("/sys/depts")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;
    private final MemberService memberService;

    /**
     * 两级部门树：GET /api/sys/depts/tree
     */
    @GetMapping("/tree")
    public Result<List<DeptTreeVO>> tree() {
        return Result.ok(deptService.tree());
    }

    /**
     * 新增部门：POST /api/sys/depts（仅 ADMIN）
     */
    @PostMapping
    @RequireRole(RoleConstant.ADMIN)
    public Result<DeptTreeVO> create(@Valid @RequestBody DeptSaveRequest request) {
        return Result.ok("部门已创建", deptService.create(request));
    }

    /**
     * 编辑部门：PUT /api/sys/depts/{id}（仅 ADMIN）
     */
    @PutMapping("/{id}")
    @RequireRole(RoleConstant.ADMIN)
    public Result<DeptTreeVO> update(@PathVariable Long id,
                                     @Valid @RequestBody DeptSaveRequest request) {
        return Result.ok("部门已更新", deptService.update(id, request));
    }

    /**
     * 部门成员名单：GET /api/sys/depts/{id}/members
     *
     * <p>只返回姓名与角色；管理员/高管可看任意部门，负责人仅本部门。</p>
     */
    @GetMapping("/{id}/members")
    @RequireRole({RoleConstant.ADMIN, RoleConstant.EXECUTIVE, RoleConstant.DEPT_LEADER})
    public Result<List<MemberVO>> members(@PathVariable Long id) {
        return Result.ok(memberService.deptMembers(id));
    }
}
