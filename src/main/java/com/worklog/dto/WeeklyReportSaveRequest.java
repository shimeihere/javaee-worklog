package com.worklog.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 周报保存请求（存草稿与编辑草稿共用）。
 */
@Data
public class WeeklyReportSaveRequest {

    /** 本周内任意一天；服务端会自动归一到周一，新增时必填 */
    private LocalDate startDate;

    /** 关联项目ID */
    private Long projectId;

    /** 本周总结 */
    @Size(max = 4000, message = "本周总结不能超过 4000 字")
    private String summary;

    /** 下周计划 */
    @Size(max = 4000, message = "下周计划不能超过 4000 字")
    private String plan;
}
