package com.worklog.service;

import com.worklog.vo.BoardOverviewVO;

/**
 * 数据看板服务（M6）。
 */
public interface BoardService {

    /**
     * 看板聚合数据。
     *
     * @param range     week / lastWeek / month
     * @param deptId    部门筛选（部门负责人自动锁定本部门）
     * @param projectId 项目筛选（只影响"已提交"的统计口径）
     */
    BoardOverviewVO overview(String range, Long deptId, Long projectId);
}
