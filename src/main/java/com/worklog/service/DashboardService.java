package com.worklog.service;

import com.worklog.vo.DashboardSummaryVO;

/**
 * 工作台汇总服务。
 */
public interface DashboardService {

    /**
     * 当前登录人的工作台首屏指标（一次请求返回全部）。
     */
    DashboardSummaryVO summary();
}
