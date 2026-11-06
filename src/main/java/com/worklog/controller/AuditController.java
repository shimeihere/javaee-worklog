package com.worklog.controller;

import com.worklog.common.PageResult;
import com.worklog.common.Result;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.AuditQuery;
import com.worklog.security.RequireRole;
import com.worklog.service.AuditService;
import com.worklog.vo.AuditLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作审计接口（仅 ADMIN）。
 */
@RestController
@RequestMapping("/sys/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /**
     * 审计日志分页：GET /api/sys/audit-logs?page=1&size=20&keyword=王&action=UPDATE_USER
     */
    @GetMapping
    @RequireRole(RoleConstant.ADMIN)
    public Result<PageResult<AuditLogVO>> page(AuditQuery query) {
        return Result.ok(auditService.page(query));
    }
}
