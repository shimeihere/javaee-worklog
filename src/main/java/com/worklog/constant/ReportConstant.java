package com.worklog.constant;

/**
 * 填报模块常量。
 */
public final class ReportConstant {

    /** 日报/周报状态：草稿 */
    public static final int STATUS_DRAFT = 0;

    /** 日报/周报状态：已提交 */
    public static final int STATUS_SUBMITTED = 1;

    /** 附件关联类型：日报 */
    public static final int REPORT_TYPE_DAILY = 1;

    /** 附件关联类型：周报 */
    public static final int REPORT_TYPE_WEEKLY = 2;

    /** 日历状态：未填 */
    public static final int CALENDAR_NOT_FILLED = 0;

    /** 日历状态：已提交 */
    public static final int CALENDAR_SUBMITTED = 1;

    /** 日历状态：已逾期（应提交而未提交） */
    public static final int CALENDAR_OVERDUE = 2;

    private ReportConstant() {
    }
}
