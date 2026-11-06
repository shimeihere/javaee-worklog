package com.worklog.service;

import com.worklog.common.PageResult;
import com.worklog.dto.AuditQuery;
import com.worklog.vo.AuditLogVO;

/**
 * 操作审计。
 */
public interface AuditService {

    /**
     * 记一条管理动作。写日志失败不影响主流程。
     */
    void record(String action, String targetType, Long targetId, String detail);

    /**
     * 分页查询（仅管理员）。
     */
    PageResult<AuditLogVO> page(AuditQuery query);
}
