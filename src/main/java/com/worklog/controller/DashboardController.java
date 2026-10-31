package com.worklog.controller;

import com.worklog.common.Result;
import com.worklog.service.DashboardService;
import com.worklog.vo.DashboardSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台聚合接口（M6）。
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 工作台首屏指标：GET /api/dashboard/summary
     *
     * <p>一次返回今日填报状态、本周/本月提交率、草稿数、未读数、项目数，
     * 负责人与管理员额外返回团队今日提交情况。</p>
     */
    @GetMapping("/summary")
    public Result<DashboardSummaryVO> summary() {
        return Result.ok(dashboardService.summary());
    }
}
