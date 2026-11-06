package com.worklog.service;

import com.worklog.common.PageResult;
import com.worklog.dto.ProjectCreateRequest;
import com.worklog.dto.ProjectQuery;
import com.worklog.dto.ProjectUpdateRequest;
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

    /**
     * 变更项目负责人：旧负责人收到移交通知，新负责人收到指派通知。
     */
    ProjectVO changeLeader(Long id, Long leaderId);

    /**
     * 编辑项目：名称、描述、状态（进行中 / 已归档）。
     *
     * <p>归档时给负责人发一条系统通知；部门负责人只能改本部门（或本人负责）的项目。</p>
     */
    ProjectVO update(Long id, ProjectUpdateRequest request);
}
