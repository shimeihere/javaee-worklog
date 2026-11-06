package com.worklog.controller;

import com.worklog.common.PageResult;
import com.worklog.common.Result;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.SummaryQuery;
import com.worklog.security.RequireRole;
import com.worklog.service.SummaryService;
import com.worklog.vo.SummaryDailyItemVO;
import com.worklog.vo.SummaryRowVO;
import com.worklog.vo.SummaryWeeklyItemVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 汇总中心接口（M5）。
 *
 * <p>仅高管与部门负责人可用（管理员只做系统配置，看不到经营数据）；
 * 数据范围：高管全公司、负责人本部门。</p>
 */
@Slf4j
@RestController
@RequestMapping("/summary")
@RequiredArgsConstructor
public class SummaryController {

    private static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final SummaryService summaryService;

    /**
     * 四维查询：GET /api/summary?dimension=user&startDate=&endDate=&deptId=&projectId=&keyword=
     */
    @GetMapping
    @RequireRole({RoleConstant.EXECUTIVE, RoleConstant.DEPT_LEADER})
    public Result<PageResult<SummaryRowVO>> query(SummaryQuery query) {
        return Result.ok(summaryService.query(query));
    }

    /**
     * 导出 Excel：GET /api/summary/export?dimension=user...
     *
     * <p>前端用 axios 以 blob 方式请求（这样才能带上 Authorization 头），
     * 再通过 Blob URL 触发浏览器下载。</p>
     */
    @GetMapping("/export")
    @RequireRole({RoleConstant.EXECUTIVE, RoleConstant.DEPT_LEADER})
    public void export(SummaryQuery query, HttpServletResponse response) throws IOException {
        String fileName = "周览汇总_" + query.getDimension() + "_" + LocalDate.now();
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        response.setContentType(EXCEL_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename*=utf-8''" + encoded + ".xlsx");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        summaryService.writeExcel(query, response.getOutputStream());
        log.info("汇总导出完成：{}", fileName);
    }

    /**
     * 成员日报明细（抽屉）：GET /api/summary/users/{userId}/daily?startDate=&endDate=
     *
     * <p>总裁监督权：高管可下钻查看任何人的明细；负责人仅限本部门；管理员不开放。</p>
     */
    @GetMapping("/users/{userId}/daily")
    @RequireRole({RoleConstant.EXECUTIVE, RoleConstant.DEPT_LEADER})
    public Result<List<SummaryDailyItemVO>> userDaily(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.ok(summaryService.userDailyDetail(userId, startDate, endDate));
    }

    /**
     * 成员周报明细（抽屉的「周报」Tab）：GET /api/summary/users/{userId}/weekly
     */
    @GetMapping("/users/{userId}/weekly")
    @RequireRole({RoleConstant.EXECUTIVE, RoleConstant.DEPT_LEADER})
    public Result<List<SummaryWeeklyItemVO>> userWeekly(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.ok(summaryService.userWeeklyDetail(userId, startDate, endDate));
    }
}
