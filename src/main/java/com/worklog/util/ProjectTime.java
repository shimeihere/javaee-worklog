package com.worklog.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 项目周期口径（看板与项目列表共用，保证两处数字一致）。
 *
 * <p>立项日期取 project.create_time 的日期，立项当天算第 1 天；
 * 计划完成日期（planned_end_date）可以为空——空的时候只报"已进行 N 天"，不算百分比，不编造数据。</p>
 */
public final class ProjectTime {

    private ProjectTime() {
    }

    /** 立项日期 */
    public static LocalDate startDate(LocalDateTime createTime) {
        return createTime == null ? LocalDate.now() : createTime.toLocalDate();
    }

    /** 已进行天数（立项当天 = 第 1 天） */
    public static int elapsedDays(LocalDate start, LocalDate today) {
        if (start == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(start, today) + 1;
        return days < 1 ? 1 : (int) days;
    }

    /** 计划工期（含首尾）；没设完成日返回 null */
    public static Integer plannedDays(LocalDate start, LocalDate plannedEnd) {
        if (start == null || plannedEnd == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(start, plannedEnd) + 1;
        return days < 1 ? 1 : (int) days;
    }

    /** 还剩几天；负数表示已经超期；没设完成日返回 null */
    public static Integer remainingDays(LocalDate plannedEnd, LocalDate today) {
        if (plannedEnd == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(today, plannedEnd);
    }

    /** 时间进度 0-100；没设完成日返回 null（前端不画百分比） */
    public static Integer timeProgress(LocalDate start, LocalDate plannedEnd, LocalDate today) {
        Integer total = plannedDays(start, plannedEnd);
        if (total == null || total <= 0) {
            return null;
        }
        int elapsed = elapsedDays(start, today);
        long pct = Math.round(elapsed * 100.0 / total);
        return (int) Math.max(0, Math.min(100, pct));
    }

    /** 是否已过计划完成日 */
    public static boolean overdue(LocalDate plannedEnd, LocalDate today) {
        return plannedEnd != null && today.isAfter(plannedEnd);
    }
}
