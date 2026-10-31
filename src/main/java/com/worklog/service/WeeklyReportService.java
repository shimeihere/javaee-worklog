package com.worklog.service;

import com.worklog.common.PageResult;
import com.worklog.dto.ReportQuery;
import com.worklog.dto.WeeklyReportSaveRequest;
import com.worklog.vo.WeeklyReportVO;

import java.time.LocalDate;

/**
 * 周报服务（M4）。
 */
public interface WeeklyReportService {

    /** 存草稿：同一周已有草稿则覆盖，已提交则拒绝 */
    WeeklyReportVO save(WeeklyReportSaveRequest request);

    /** 编辑草稿 */
    WeeklyReportVO update(Long id, WeeklyReportSaveRequest request);

    /** 提交：草稿 -> 已提交 */
    WeeklyReportVO submit(Long id);

    /** 撤回：已提交 -> 草稿 */
    WeeklyReportVO withdraw(Long id);

    /** 按周内任意一天查询本人周报，没有则返回 null */
    WeeklyReportVO getByWeek(LocalDate anyDayInWeek);

    /** 详情（含附件），按数据范围鉴权 */
    WeeklyReportVO detail(Long id);

    /** 分页（含数据范围过滤） */
    PageResult<WeeklyReportVO> page(ReportQuery query);
}
