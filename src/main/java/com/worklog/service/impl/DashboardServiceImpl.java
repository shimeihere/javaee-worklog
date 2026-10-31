package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.worklog.constant.ReportConstant;
import com.worklog.constant.RoleConstant;
import com.worklog.entity.DailyReport;
import com.worklog.entity.Project;
import com.worklog.entity.SysMessage;
import com.worklog.entity.SysUser;
import com.worklog.mapper.DailyReportMapper;
import com.worklog.mapper.ProjectMapper;
import com.worklog.mapper.SysMessageMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import com.worklog.service.DashboardService;
import com.worklog.service.DataScopeService;
import com.worklog.vo.DashboardSummaryVO;
import com.worklog.vo.MonthlyDashboardVO;
import com.worklog.vo.TeamDashboardVO;
import com.worklog.vo.WeeklyDashboardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作台汇总实现。
 *
 * <p>口径说明：</p>
 * <ul>
 *   <li>提交率分母 = 周期内"已过去的工作日（含今天）"，不惩罚未来日期；</li>
 *   <li>周末不计入应提交天数（与日历页规则保持一致）；</li>
 *   <li>团队指标仅 ADMIN（全公司）与 DEPT_LEADER（本部门）返回。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DailyReportMapper dailyReportMapper;
    private final SysMessageMapper sysMessageMapper;
    private final ProjectMapper projectMapper;
    private final SysUserMapper sysUserMapper;
    private final DataScopeService dataScopeService;

    @Override
    public DashboardSummaryVO summary() {
        LoginUser me = UserContext.require();
        LocalDate today = LocalDate.now();

        DailyReport todayReport = dailyReportMapper.selectOne(new LambdaQueryWrapper<DailyReport>()
                .eq(DailyReport::getUserId, me.id())
                .eq(DailyReport::getReportDate, today));

        long pendingDrafts = dailyReportMapper.selectCount(new LambdaQueryWrapper<DailyReport>()
                .eq(DailyReport::getUserId, me.id())
                .eq(DailyReport::getStatus, ReportConstant.STATUS_DRAFT));

        long unread = sysMessageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getUserId, me.id())
                .eq(SysMessage::getIsRead, 0));

        // 项目数同样走数据范围，保证三个角色看到不同口径
        LambdaQueryWrapper<Project> projectWrapper = new LambdaQueryWrapper<>();
        dataScopeService.applyProjectScope(projectWrapper);
        long projectCount = projectMapper.selectCount(projectWrapper);

        DashboardSummaryVO summary = DashboardSummaryVO.builder()
                .today(today)
                .todaySubmitted(todayReport != null
                        && Objects.equals(todayReport.getStatus(), ReportConstant.STATUS_SUBMITTED))
                .todayStatus(todayReport == null ? null : todayReport.getStatus())
                .todayReportId(todayReport == null ? null : todayReport.getId())
                .pendingDrafts(pendingDrafts)
                .unreadMessages(unread)
                .projectCount(projectCount)
                .week(buildWeek(me.id(), today))
                .month(buildMonth(me.id(), today))
                .team(buildTeam(me, today))
                .build();

        // 管理员工作台只关心系统运行情况，不看任何业务指标
        if (RoleConstant.ADMIN.equals(me.role())) {
            summary.setSystemTotalUsers(sysUserMapper.selectCount(null));
            summary.setTodayActiveUsers(countActiveToday(today));
        }
        return summary;
    }

    /** 今日活跃：今天有填报记录（含草稿）的人数 */
    private Long countActiveToday(LocalDate today) {
        return dailyReportMapper.selectList(new LambdaQueryWrapper<DailyReport>()
                        .select(DailyReport::getUserId)
                        .eq(DailyReport::getReportDate, today))
                .stream()
                .map(DailyReport::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    private WeeklyDashboardVO buildWeek(Long userId, LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        LocalDate until = today.isBefore(sunday) ? today : sunday;

        int submitted = (int) countSubmitted(userId, monday, until);
        int workdays = countWorkdays(monday, until);

        return WeeklyDashboardVO.builder()
                .startDate(monday)
                .endDate(sunday)
                .workdays(workdays)
                .submittedDays(submitted)
                .overdueDays(Math.max(workdays - submitted, 0))
                .submitRate(rate(submitted, workdays))
                .build();
    }

    private MonthlyDashboardVO buildMonth(Long userId, LocalDate today) {
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate until = today;

        int submitted = (int) countSubmitted(userId, monthStart, until);
        int workdays = countWorkdays(monthStart, until);

        return MonthlyDashboardVO.builder()
                .workdays(workdays)
                .submittedDays(submitted)
                .overdueDays(Math.max(workdays - submitted, 0))
                .submitRate(rate(submitted, workdays))
                .build();
    }

    private TeamDashboardVO buildTeam(LoginUser me, LocalDate today) {
        // 高管 / 管理员看全公司；部门负责看本部门；员工无团队指标
        boolean global = RoleConstant.isGlobal(me.role());
        if (!global && !RoleConstant.DEPT_LEADER.equals(me.role())) {
            return null;
        }
        if (!global && me.deptId() == null) {
            return null;
        }

        LambdaQueryWrapper<SysUser> memberWrapper = new LambdaQueryWrapper<SysUser>().select(SysUser::getId);
        if (!global) {
            memberWrapper.eq(SysUser::getDeptId, me.deptId());
        }
        Set<Long> memberIds = sysUserMapper.selectList(memberWrapper).stream()
                .map(SysUser::getId)
                .collect(Collectors.toSet());

        if (memberIds.isEmpty()) {
            return TeamDashboardVO.builder().memberCount(0).submittedToday(0).submitRate(0).build();
        }

        long submittedToday = dailyReportMapper.selectCount(new LambdaQueryWrapper<DailyReport>()
                .eq(DailyReport::getReportDate, today)
                .eq(DailyReport::getStatus, ReportConstant.STATUS_SUBMITTED)
                .in(DailyReport::getUserId, memberIds));

        return TeamDashboardVO.builder()
                .memberCount(memberIds.size())
                .submittedToday((int) submittedToday)
                .submitRate(rate((int) submittedToday, memberIds.size()))
                .build();
    }

    private long countSubmitted(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return 0;
        }
        return dailyReportMapper.selectCount(new LambdaQueryWrapper<DailyReport>()
                .eq(DailyReport::getUserId, userId)
                .eq(DailyReport::getStatus, ReportConstant.STATUS_SUBMITTED)
                .between(DailyReport::getReportDate, from, to));
    }

    /** 含首尾的工作日天数 */
    private int countWorkdays(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return 0;
        }
        int days = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                days++;
            }
        }
        return days;
    }

    private int rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round(numerator * 100.0 / denominator);
    }
}
