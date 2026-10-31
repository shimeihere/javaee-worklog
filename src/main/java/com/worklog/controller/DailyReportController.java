package com.worklog.controller;

import com.worklog.common.PageResult;
import com.worklog.common.Result;
import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.DailyReportSaveRequest;
import com.worklog.dto.ReportQuery;
import com.worklog.security.RequireRole;
import com.worklog.service.DailyReportService;
import com.worklog.vo.CalendarVO;
import com.worklog.vo.DailyReportVO;
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
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * 日报接口（M3）。
 *
 * <p>写操作（保存/编辑/提交/撤回）只允许操作本人日报；
 * 读操作（详情/分页）按数据范围过滤。</p>
 */
@RestController
@RequestMapping("/reports/daily")
@RequiredArgsConstructor
public class DailyReportController {

    private final DailyReportService dailyReportService;

    /**
     * 月历状态：GET /api/reports/daily/calendar?yearMonth=2026-10
     */
    @GetMapping("/calendar")
    public Result<CalendarVO> calendar(@RequestParam String yearMonth) {
        try {
            return Result.ok(dailyReportService.calendar(YearMonth.parse(yearMonth)));
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "月份格式应为 yyyy-MM，例如 2026-10");
        }
    }

    /**
     * 本人某天的日报：GET /api/reports/daily?date=2026-10-29（没有则 data 为 null）
     */
    @GetMapping
    public Result<DailyReportVO> getByDate(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return Result.ok(dailyReportService.getByDate(date));
    }

    /**
     * 存草稿：POST /api/reports/daily（同一天重复调用即覆盖草稿）
     *
     * <p>只有员工与部门负责人可以填报；高管/管理员不参与填报。</p>
     */
    @PostMapping
    @RequireRole({RoleConstant.EMPLOYEE, RoleConstant.DEPT_LEADER})
    public Result<DailyReportVO> save(@Valid @RequestBody DailyReportSaveRequest request) {
        return Result.ok("草稿已保存", dailyReportService.save(request));
    }

    /**
     * 编辑草稿：PUT /api/reports/daily/{id}
     */
    @PutMapping("/{id}")
    @RequireRole({RoleConstant.EMPLOYEE, RoleConstant.DEPT_LEADER})
    public Result<DailyReportVO> update(@PathVariable Long id,
                                        @Valid @RequestBody DailyReportSaveRequest request) {
        return Result.ok("草稿已更新", dailyReportService.update(id, request));
    }

    /**
     * 提交：POST /api/reports/daily/{id}/submit
     */
    @PostMapping("/{id}/submit")
    @RequireRole({RoleConstant.EMPLOYEE, RoleConstant.DEPT_LEADER})
    public Result<DailyReportVO> submit(@PathVariable Long id) {
        return Result.ok("日报已提交", dailyReportService.submit(id));
    }

    /**
     * 撤回：POST /api/reports/daily/{id}/withdraw
     */
    @PostMapping("/{id}/withdraw")
    @RequireRole({RoleConstant.EMPLOYEE, RoleConstant.DEPT_LEADER})
    public Result<DailyReportVO> withdraw(@PathVariable Long id) {
        return Result.ok("已撤回为草稿", dailyReportService.withdraw(id));
    }

    /**
     * 详情（含附件）：GET /api/reports/daily/{id}
     */
    @GetMapping("/{id}")
    public Result<DailyReportVO> detail(@PathVariable Long id) {
        return Result.ok(dailyReportService.detail(id));
    }

    /**
     * 分页查询：GET /api/reports/daily/page?page=1&size=10&startDate=2026-10-01&endDate=2026-10-31
     */
    @GetMapping("/page")
    public Result<PageResult<DailyReportVO>> page(ReportQuery query) {
        return Result.ok(dailyReportService.page(query));
    }
}
