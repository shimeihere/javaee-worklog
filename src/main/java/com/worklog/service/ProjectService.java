package com.worklog.service;

import com.worklog.common.PageResult;
import com.worklog.dto.ProjectCreateRequest;
import com.worklog.dto.ProjectQuery;
import com.worklog.vo.ProjectVO;

/**
 * 项目服务。
 */
public interface ProjectService {

    /**
     * 创建项目（必须指定负责人），部门负责人只能在本部门下创建。
     */
    ProjectVO create(ProjectCreateRequest request);

    /**
     * 分页查询项目，自动应用数据范围（见 {@link DataScopeService}）。
     */
    PageResult<ProjectVO> page(ProjectQuery query);

    /**
     * 项目详情，越权访问统一按"不存在"处理。
     */
    ProjectVO detail(Long id);
}
