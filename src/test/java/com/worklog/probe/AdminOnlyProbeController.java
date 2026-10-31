package com.worklog.probe;

import com.worklog.common.Result;
import com.worklog.constant.RoleConstant;
import com.worklog.security.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅测试可见的角色校验探针接口（位于 src/test，不会打进生产包）。
 *
 * <p>用于验证 {@link RequireRole} + {@code JwtAuthInterceptor} 的角色拦截链路。</p>
 */
@RestController
public class AdminOnlyProbeController {

    @RequireRole(RoleConstant.ADMIN)
    @GetMapping("/probe/admin-only")
    public Result<String> adminOnly() {
        return Result.ok("admin-ok");
    }
}
