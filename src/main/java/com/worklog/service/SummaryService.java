package com.worklog.service;

import com.worklog.common.PageResult;
import com.worklog.dto.SummaryQuery;
import com.worklog.vo.SummaryDailyItemVO;
import com.worklog.vo.SummaryRowVO;
import com.worklog.vo.SummaryWeeklyItemVO;

import java.io.OutputStream;
import java.time.LocalDate;
import java.util.List;

/**
 * 汇总中心服务（M5）：四维联合查询 + Excel 导出 + 个人明细。
 */
public interface SummaryService {

    /**
     * 按维度聚合查询（内存聚合后分页）。
     */
    PageResult<SummaryRowVO> query(SummaryQuery query);

    /**
     * 导出当前维度全部数据到 Excel（字段与列表列一致）。
     */
    void writeExcel(SummaryQuery query, OutputStream outputStream);

    /**
     * 某成员的日报明细（抽屉用）。
     *
     * <p>权限：管理员只给统计不给明细；高管可看任何人；部门负责人仅可看本部门成员。</p>
     */
    List<SummaryDailyItemVO> userDailyDetail(Long userId, LocalDate startDate, LocalDate endDate);

    /**
     * 某成员的周报明细（抽屉的「周报」Tab），权限规则同日报。
     */
    List<SummaryWeeklyItemVO> userWeeklyDetail(Long userId, LocalDate startDate, LocalDate endDate);
}
