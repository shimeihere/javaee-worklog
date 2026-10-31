package com.worklog.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 催报请求。
 */
@Data
public class RemindRequest {

    @NotNull(message = "请选择要催报的成员")
    private Long userId;
}
