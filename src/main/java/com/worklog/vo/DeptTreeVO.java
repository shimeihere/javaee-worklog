package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门树节点（两级：公司 → 部门）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeptTreeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long parentId;

    private String deptName;

    private Integer sortOrder;

    /** 该部门直属人数（不含子部门） */
    private Long userCount;

    /** 子部门，叶子节点为空数组而非 null，方便前端 el-tree 直接渲染 */
    @Builder.Default
    private List<DeptTreeVO> children = new ArrayList<>();
}
