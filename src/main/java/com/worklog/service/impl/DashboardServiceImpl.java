package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.worklog.constant.ReportConstant;
import com.worklog.constant.RoleConstant;
import com.worklog.entity.DailyReport;
import com.worklog.entity.Project;
import com.worklog.entity.SysDept;
import com.worklog.entity.SysMessage;
import com.worklog.entity.SysUser;
import com.worklog.mapper.DailyReportMapper;
import com.worklog.mapper.ProjectMapper;
import com.worklog.mapper.SysDeptMapper;
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
 *   <li>团队指标仅 EXECUTIVE（全公司）与 DEPT_LEADER（本部门）返回；管理员只拿系统指标。</li>
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
    private final SysDeptMapper sysDeptMapper;
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
            // 部门数不含根节点（"周览科技"本身不算一个部门），项目数不按数据范围切
            summary.setDeptCount(sysDeptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                    .gt(SysDept::getParentId, 0)));
            summary.setCompanyProjectCount(projectMapper.selectCount(null));
        }

        // 部门负责人：本周"应提交未提交"的人次，就是工作台上的待提醒名单
        if (RoleConstant.DEPT_LEADER.equals(me.role()) && me.deptId() != null) {
            Set<Long> memberIds = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .select(SysUser::getId)
                            .eq(SysUser::getDeptId, me.deptId()))
                    .stream().map(SysUser::getId)
                    .filter(id -> !id.equals(me.id()))
                    .collect(Collectors.toSet());
            summary.setRemindCount(missingPersonDays(memberIds, today));
        }

        // 高管：全公司本周未提交人次，以及与昨日相比的变化
        if (RoleConstant.EXECUTIVE.equals(me.role())) {
            long current = missingCompanyPersonDays(today);
            long yesterday = missingCompanyPersonDays(today.minusDays(1));
            summary.setOverdueCount(current);
            summary.setOverdueDelta(current - yesterday);
        }
        return summary;
    }

    /** 指定成员在"本周一 ~ 今天"应提交而未提交的人次 */
    private long missingPersonDays(Set<Long> memberIds, LocalDate today) {
        if (memberIds.isEmpty()) {
            return 0;
        }
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long expected = (long) memberIds.size() * countWorkdays(monday, today);
        long submitted = dailyReportMapper.selectCount(new LambdaQueryWrapper<DailyReport>()
                .eq(DailyReport::getStatus, ReportConstant.STATUS_SUBMITTED)
                .between(DailyReport::getReportDate, monday, today)
                .in(DailyReport::getUserId, memberIds));
        return Math.max(expected - submitted, 0);
    }

    /** 全公司在"本周一 ~ 指定日期"应提交而未提交的人次 */
    private long missingCompanyPersonDays(LocalDate until) {
        long users = sysUserMapper.selectCount(null);
        if (users <= 0) {
            return 0;
        }
        LocalDate monday = until.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long expected = users * countWorkdays(monday, until);
        long submitted = dailyReportMapper.selectCount(new LambdaQueryWrapper<DailyReport>()
                .eq(DailyReport::getStatus, ReportConstant.STATUS_SUBMITTED)
                .between(DailyReport::getReportDate, monday, until));
        return Math.max(expected - submitted, 0);
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
        // 管理员只做系统运维，不下发任何业务口径（防泄密）
        if (RoleConstant.ADMIN.equals(me.role())) {
            return null;
        }
        // 高管看全公司；部门负责看本部门；员工无团队指标
        boolean global = RoleConstant.EXECUTIVE.equals(me.role());
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
