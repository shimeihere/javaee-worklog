package com.worklog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 编辑项目请求（管理员 / 本部门负责人）。
 *
 * <p>只允许改名称、描述与状态；负责人走单独的「改负责人」接口，所属部门不允许改
 * （部门一换，历史日报的数据范围会对不上）。</p>
 */
@Data
public class ProjectUpdateRequest {

    @NotBlank(message = "请填写项目名称")
    @Size(max = 100, message = "项目名称不能超过 100 个字符")
    private String projectName;

    @Size(max = 500, message = "项目描述不能超过 500 个字符")
    private String description;

    /** 0 进行中 / 1 已归档；不传表示不改 */
    private Integer status;

    /**
     * 计划完成日期。
     *
     * <p>这个字段以请求为准：传日期就是改，传 null 就是清空（前端编辑弹窗始终会带上当前值）。</p>
     */
    private LocalDate plannedEndDate;
}
