package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志列表行。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String operatorName;

    private String action;

    private String actionName;

    private String targetType;

    private Long targetId;

    private String detail;

    private LocalDateTime createTime;
}
