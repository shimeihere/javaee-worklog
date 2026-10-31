package com.worklog.service;

import com.worklog.common.PageResult;
import com.worklog.dto.DailyReportSaveRequest;
import com.worklog.dto.ReportQuery;
import com.worklog.vo.CalendarVO;
import com.worklog.vo.DailyReportVO;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 日报服务（M3）。
 */
public interface DailyReportService {

    /** 存草稿：同一天已有草稿则覆盖，已提交则拒绝 */
    DailyReportVO save(DailyReportSaveRequest request);

    /** 编辑草稿 */
    DailyReportVO update(Long id, DailyReportSaveRequest request);

    /** 提交：草稿 -> 已提交 */
    DailyReportVO submit(Long id);

    /** 撤回：已提交 -> 草稿 */
    DailyReportVO withdraw(Long id);

    /** 查询本人某天的日报，没有则返回 null */
    DailyReportVO getByDate(LocalDate date);

    /** 详情（含附件），按数据范围鉴权 */
    DailyReportVO detail(Long id);

    /** 分页（含数据范围过滤） */
    PageResult<DailyReportVO> page(ReportQuery query);

    /** 月历状态 */
    CalendarVO calendar(YearMonth yearMonth);
}
