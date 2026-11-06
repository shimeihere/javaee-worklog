package com.worklog.controller;

import com.worklog.common.PageResult;
import com.worklog.common.Result;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.BatchUserCreateRequest;
import com.worklog.dto.UserCreateRequest;
import com.worklog.dto.UserQuery;
import com.worklog.dto.UserUpdateRequest;
import com.worklog.security.RequireRole;
import com.worklog.service.SysUserService;
import com.worklog.vo.BatchUserResultVO;
import com.worklog.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口（M2）。
 */
@RestController
@RequestMapping("/sys/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    /**
     * 分页查询用户：GET /api/sys/users?page=1&size=10&deptId=2&role=EMPLOYEE&keyword=王
     *
     * <p>管理员看全部；部门负责人仅本部门（数据范围在 Service 层强制，前端传参无法绕过）。</p>
     */
    @GetMapping
    @RequireRole({RoleConstant.ADMIN, RoleConstant.DEPT_LEADER})
    public Result<PageResult<UserVO>> page(UserQuery query) {
        return Result.ok(sysUserService.page(query));
    }

    /**
     * 新增用户：POST /api/sys/users（仅 ADMIN）
     */
    @PostMapping
    @RequireRole(RoleConstant.ADMIN)
    public Result<UserVO> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok("用户已创建，初始密码请告知本人", sysUserService.create(request));
    }

    /**
     * 批量新增用户：POST /api/sys/users/batch（仅 ADMIN）
     *
     * <p>外层给统一密码/角色/部门，行内可覆盖；返回逐行成功与失败原因。</p>
     */
    @PostMapping("/batch")
    @RequireRole(RoleConstant.ADMIN)
    public Result<BatchUserResultVO> batchCreate(@Valid @RequestBody BatchUserCreateRequest request) {
        BatchUserResultVO result = sysUserService.batchCreate(request);
        String message = result.getFailedCount() == 0
                ? "已批量创建 " + result.getCreatedCount() + " 个用户"
                : "成功 " + result.getCreatedCount() + " 个，失败 " + result.getFailedCount() + " 个";
        return Result.ok(message, result);
    }

    /**
     * 编辑用户：PUT /api/sys/users/{id}（仅 ADMIN）
     *
     * <p>可改姓名、角色、所属部门与参与项目；登录账号不可改。
     * 变更后会给该用户发一条系统通知。</p>
     */
    @PutMapping("/{id}")
    @RequireRole(RoleConstant.ADMIN)
    public Result<UserVO> update(@PathVariable Long id,
                                 @Valid @RequestBody UserUpdateRequest request) {
        return Result.ok("用户已更新", sysUserService.update(id, request));
    }

    /**
     * 禁用/启用用户：PUT /api/sys/users/{id}/status?status=1
     *
     * <p>仅管理员可操作；禁用时会提示该用户未提交的日报草稿数量。</p>
     */
    @PutMapping("/{id}/status")
    @RequireRole(RoleConstant.ADMIN)
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return Result.<Void>ok(sysUserService.updateStatus(id, status), null);
    }
}
