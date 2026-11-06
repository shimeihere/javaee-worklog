package com.worklog.controller;

import com.worklog.common.PageResult;
import com.worklog.common.Result;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.ReportQuery;
import com.worklog.dto.WeeklyReportSaveRequest;
import com.worklog.security.RequireRole;
import com.worklog.service.WeeklyReportService;
import com.worklog.vo.WeeklyReportVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 周报接口（M4）。
 *
 * <p>周内任意一天都会归一到该周周一，前端直接传当前选中日期即可。</p>
 */
@RestController
@RequestMapping("/reports/weekly")
@RequiredArgsConstructor
// 读接口同样要挡角色：管理员不参与业务，不能靠 /page、/{id} 顺走全公司周报正文
@RequireRole({RoleConstant.EMPLOYEE, RoleConstant.DEPT_LEADER, RoleConstant.EXECUTIVE})
public class WeeklyReportController {

    private final WeeklyReportService weeklyReportService;

    /**
     * 本人某周周报：GET /api/reports/weekly?startDate=2026-10-29（没有则 data 为 null）
     */
    @GetMapping
    public Result<WeeklyReportVO> getByWeek(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate) {
        return Result.ok(weeklyReportService.getByWeek(startDate));
    }

    /**
     * 存草稿：POST /api/reports/weekly
     *
     * <p>只有员工与部门负责人可以填报。</p>
     */
    @PostMapping
    @RequireRole({RoleConstant.EMPLOYEE, RoleConstant.DEPT_LEADER})
    public Result<WeeklyReportVO> save(@Valid @RequestBody WeeklyReportSaveRequest request) {
        return Result.ok("周报草稿已保存", weeklyReportService.save(request));
    }

    /**
     * 编辑草稿：PUT /api/reports/weekly/{id}
     */
    @PutMapping("/{id}")
    @RequireRole({RoleConstant.EMPLOYEE, RoleConstant.DEPT_LEADER})
    public Result<WeeklyReportVO> update(@PathVariable Long id,
                                         @Valid @RequestBody WeeklyReportSaveRequest request) {
        return Result.ok("周报草稿已更新", weeklyReportService.update(id, request));
    }

    /**
     * 提交：POST /api/reports/weekly/{id}/submit
     */
    @PostMapping("/{id}/submit")
    @RequireRole({RoleConstant.EMPLOYEE, RoleConstant.DEPT_LEADER})
    public Result<WeeklyReportVO> submit(@PathVariable Long id) {
        return Result.ok("周报已提交", weeklyReportService.submit(id));
    }

    /**
     * 撤回：POST /api/reports/weekly/{id}/withdraw
     */
    @PostMapping("/{id}/withdraw")
    @RequireRole({RoleConstant.EMPLOYEE, RoleConstant.DEPT_LEADER})
    public Result<WeeklyReportVO> withdraw(@PathVariable Long id) {
        return Result.ok("已撤回为草稿", weeklyReportService.withdraw(id));
    }

    /**
     * 详情（含附件）：GET /api/reports/weekly/{id}
     */
    @GetMapping("/{id}")
    public Result<WeeklyReportVO> detail(@PathVariable Long id) {
        return Result.ok(weeklyReportService.detail(id));
    }

    /**
     * 分页查询：GET /api/reports/weekly/page?page=1&size=10
     */
    @GetMapping("/page")
    public Result<PageResult<WeeklyReportVO>> page(ReportQuery query) {
        return Result.ok(weeklyReportService.page(query));
    }
}
