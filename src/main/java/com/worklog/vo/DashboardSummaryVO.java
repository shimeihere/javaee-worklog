package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 工作台汇总数据（GET /api/dashboard/summary）。
 *
 * <p>一次请求返回工作台首屏所需的全部指标，避免前端串行调用多个接口。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 服务器当天日期，前端据此判断"今天" */
    private LocalDate today;

    /** 今天是否已提交日报 */
    private Boolean todaySubmitted;

    /** 今天的日报状态：null 未填 / 0 草稿 / 1 已提交 */
    private Integer todayStatus;

    /** 今天的日报ID，未填时为 null */
    private Long todayReportId;

    /** 我的草稿数量 */
    private long pendingDrafts;

    /** 未读站内信数量 */
    private long unreadMessages;

    /** 我可参与的项目数量（已按数据范围过滤） */
    private long projectCount;

    private WeeklyDashboardVO week;

    private MonthlyDashboardVO month;

    /** 仅 CEO/高管/部门负责人有值，员工为 null */
    private TeamDashboardVO team;

    /** 系统总人数（仅管理员工作台展示） */
    private Long systemTotalUsers;

    /** 今日活跃人数：今天有填报记录的人（仅管理员工作台展示） */
    private Long todayActiveUsers;

    /** 部门数量（不含根节点，仅管理员工作台展示） */
    private Long deptCount;

    /** 全公司项目总数（仅管理员工作台展示） */
    private Long companyProjectCount;

    /** 本周应提交未提交人次（仅部门负责人：待提醒名单） */
    private Long remindCount;

    /** 本周全公司应提交未提交人次（仅高管：全公司逾期） */
    private Long overdueCount;

    /** 逾期人次较昨日变化：负数=减少，正数=增加，0=持平 */
    private Long overdueDelta;
}
