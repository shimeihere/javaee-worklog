package com.worklog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门新增/编辑请求。
 */
@Data
public class DeptSaveRequest {

    /** 上级部门ID，0 或空表示顶级 */
    private Long parentId;

    @NotBlank(message = "请填写部门名称")
    @Size(max = 64, message = "部门名称不能超过 64 个字符")
    private String deptName;

    /** 同级排序号 */
    private Integer sortOrder;
}
