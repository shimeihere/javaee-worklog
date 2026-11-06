package com.worklog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 创建项目请求体。
 */
@Data
public class ProjectCreateRequest {

    @NotBlank(message = "请填写项目名称")
    @Size(max = 100, message = "项目名称不能超过 100 个字符")
    private String projectName;

    /** 项目负责人（必须是存在且未被禁用的用户） */
    @NotNull(message = "请指定项目负责人")
    private Long leaderId;

    /** 所属部门；部门负责人创建时强制为本人部门 */
    @NotNull(message = "请选择所属部门")
    private Long deptId;

    @Size(max = 500, message = "项目描述不能超过 500 个字符")
    private String description;

    /** 计划完成日期（选填）；不填就只统计"已进行天数" */
    private LocalDate plannedEndDate;
}
