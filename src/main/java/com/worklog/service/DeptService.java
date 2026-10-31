package com.worklog.service;

import com.worklog.dto.DeptSaveRequest;
import com.worklog.vo.DeptTreeVO;

import java.util.List;

/**
 * 部门服务。
 */
public interface DeptService {

    /**
     * 两级部门树（公司 → 部门），节点带直属人数。
     */
    List<DeptTreeVO> tree();

    /** 新增部门（仅两级） */
    DeptTreeVO create(DeptSaveRequest request);

    /** 编辑部门名称/排序/上级（仍保持两级） */
    DeptTreeVO update(Long id, DeptSaveRequest request);
}
