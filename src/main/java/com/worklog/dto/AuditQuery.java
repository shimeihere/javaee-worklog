package com.worklog.dto;

import lombok.Data;

/**
 * 审计日志分页查询条件。
 */
@Data
public class AuditQuery {

    private long page = 1;

    private long size = 20;

    /** 关键字：操作人 / 明细 */
    private String keyword;

    /** 动作编码，如 UPDATE_USER */
    private String action;
}
