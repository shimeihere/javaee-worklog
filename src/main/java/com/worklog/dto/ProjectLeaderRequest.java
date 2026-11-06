package com.worklog.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 变更项目负责人请求。
 */
@Data
public class ProjectLeaderRequest {

    @NotNull(message = "请选择新的项目负责人")
    private Long leaderId;
}
