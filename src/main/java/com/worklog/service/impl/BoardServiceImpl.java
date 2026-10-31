package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.constant.ReportConstant;
import com.worklog.constant.RoleConstant;
import com.worklog.entity.DailyReport;
import com.worklog.entity.Project;
import com.worklog.entity.SysDept;
import com.worklog.entity.SysUser;
import com.worklog.mapper.DailyReportMapper;
import com.worklog.mapper.ProjectMapper;
import com.worklog.mapper.SysDeptMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import com.worklog.service.BoardService;
import com.worklog.vo.BoardDeptVO;
import com.worklog.vo.BoardHeatVO;
import com.worklog.vo.BoardOverviewVO;
import com.worklog.vo.BoardPendingVO;
import com.worklog.vo.BoardProjectVO;
import com.worklog.vo.BoardTrendVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 看板聚合实现。
 *
 * <p>口径：</p>
 * <ul>
 *   <li>提交率 = 已提交人次 /（成员数 × 区间内已过去工作日数）；</li>
 *   <li>禁用的账号不计入成员，也不计入应提交；</li>
 *   <li>趋势固定取近 7 天、热力图固定取近 28 天，不受时间范围筛选影响；</li>
 *   <li>项目筛选只改"已提交"的统计口径（即该项目下的日报），分母仍是全部成员应提交。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private static final int TREND_DAYS = 7;
    private static final int HEATMAP_DAYS = 28;

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final DailyReportMapper dailyReportMapper;
    private final ProjectMapper projectMapper;

    @Override
    public BoardOverviewVO overview(String range, Long deptId, Long projectId) {
        LoginUser me = UserContext.require();
        String normalized = normalizeRange(range);
        LocalDate today = LocalDate.now();
        DateRange dateRange = resolveRange(normalized, today);

        List<SysUser> members = resolveMembers(me, deptId);
        Set<Long> memberIds = members.stream().map(SysUser::getId).collect(Collectors.toSet());
        int memberCount = members.size();
        int workdays = countWorkdays(dateRange.start, minDate(dateRange.end, today));
        int expectedCount = memberCount * workdays;

        List<DailyReport> rangeReports = fetchReports(memberIds, projectId, dateRange.start, dateRange.end);
        int submittedCount = rangeReports.size();
        int submitRate = rate(submittedCount, expectedCount);

        List<BoardDeptVO> deptStats = buildDeptStats(me, members, rangeReports, workdays);
        List<BoardProjectVO> projectStats = buildProjectStats(rangeReports, workdays);
        // 高管不直接对个人催报，只对组织与项目负责，所以不下发待催报名单
        List<BoardPendingVO> pendingUsers = RoleConstant.EXECUTIVE.equals(me.role())
                ? List.of()
                : buildPending(members, rangeReports, workdays, today);

        // 趋势与热力图：固定窗口，保证图表始终有画面
        LocalDate heatStart = today.minusDays(HEATMAP_DAYS - 1);
        List<DailyReport> recentReports = fetchReports(memberIds, projectId, heatStart, today);
        Map<LocalDate, Integer> perDay = countByDate(recentReports);

        List<BoardTrendVO> trend = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            trend.add(buildDay(date, perDay.getOrDefault(date, 0), memberCount));
        }

        List<BoardHeatVO> heatmap = new ArrayList<>(HEATMAP_DAYS);
        for (int i = 0; i < HEATMAP_DAYS; i++) {
            LocalDate date = heatStart.plusDays(i);
            boolean workday = isWorkday(date);
            int submitted = perDay.getOrDefault(date, 0);
            heatmap.add(BoardHeatVO.builder()
                    .date(date)
                    .submitted(submitted)
                    .rate(workday ? rate(submitted, memberCount) : 0)
                    .workday(workday)
                    .build());
        }

        return BoardOverviewVO.builder()
                .range(normalized)
                .startDate(dateRange.start)
                .endDate(dateRange.end)
                .workdays(workdays)
                .memberCount(memberCount)
                .expectedCount(expectedCount)
                .submittedCount(submittedCount)
                .submitRate(submitRate)
                .trend(trend)
                .deptStats(deptStats)
                .heatmap(heatmap)
                .projectStats(projectStats)
                .pendingUsers(pendingUsers)
                .build();
    }

    /**
     * 项目排行：区间内每个项目收到的日报数与参与人数。
     */
    private List<BoardProjectVO> buildProjectStats(List<DailyReport> rangeReports, int workdays) {
        Map<Long, List<DailyReport>> byProject = rangeReports.stream()
                .filter(report -> report.getProjectId() != null)
                .collect(Collectors.groupingBy(DailyReport::getProjectId));
        if (byProject.isEmpty()) {
            return List.of();
        }

        List<Project> projects = projectMapper.selectBatchIds(byProject.keySet());

        Set<Long> leaderIds = projects.stream().map(Project::getLeaderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> leaderNames = leaderIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(leaderIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        Set<Long> deptIds = projects.stream().map(Project::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : sysDeptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));

        List<BoardProjectVO> stats = new ArrayList<>();
        for (Project project : projects) {
            List<DailyReport> mine = byProject.getOrDefault(project.getId(), List.of());
            int participants = (int) mine.stream().map(DailyReport::getUserId)
                    .filter(Objects::nonNull).distinct().count();

            stats.add(BoardProjectVO.builder()
                    .projectId(project.getId())
                    .projectName(project.getProjectName())
                    .leaderName(project.getLeaderId() == null ? null : leaderNames.get(project.getLeaderId()))
                    .deptName(project.getDeptId() == null ? null : deptNames.get(project.getDeptId()))
                    .submitted(mine.size())
                    .participants(participants)
                    .rate(rate(mine.size(), participants * Math.max(workdays, 1)))
                    .build());
        }

        stats.sort(Comparator.comparingInt(BoardProjectVO::getSubmitted).reversed()
                .thenComparing(BoardProjectVO::getProjectName,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return stats;
    }

    // ------------------------------------------------------------------

    private String normalizeRange(String range) {
        if (range == null || range.isBlank()) {
            return "week";
        }
        return switch (range) {
            case "week", "lastWeek", "month" -> range;
            default -> throw new BusinessException(ResultCode.BAD_REQUEST, "range 只能是 week / lastWeek / month");
        };
    }

    private DateRange resolveRange(String range, LocalDate today) {
        return switch (range) {
            case "lastWeek" -> {
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
                yield new DateRange(monday, monday.plusDays(6));
            }
            case "month" -> new DateRange(today.withDayOfMonth(1),
                    today.withDayOfMonth(today.lengthOfMonth()));
            default -> {
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new DateRange(monday, monday.plusDays(6));
            }
        };
    }

    /** 成员范围：负责人锁本部门，管理员可按部门筛选 */
    /**
     * 看板成员范围：
     * 高管 = 全公司（忽略 deptId 参数，保证部门对比能覆盖所有部门）；
     * 负责人 = 强制本部门（传别的部门也不生效）。
     */
    private List<SysUser> resolveMembers(LoginUser me, Long deptId) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId, SysUser::getRealName, SysUser::getRole, SysUser::getDeptId)
                .ne(SysUser::getStatus, 1);

        if (RoleConstant.DEPT_LEADER.equals(me.role())) {
            if (me.deptId() == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "当前账号未分配部门，无法查看看板");
            }
            wrapper.eq(SysUser::getDeptId, me.deptId());
        } else if (RoleConstant.EXECUTIVE.equals(me.role())) {
            // 全公司大盘：不按部门过滤
            log.debug("高管看板：全公司成员范围");
        } else if (deptId != null) {
            wrapper.eq(SysUser::getDeptId, deptId);
        }
        return sysUserMapper.selectList(wrapper);
    }

    private List<DailyReport> fetchReports(Set<Long> memberIds, Long projectId,
                                           LocalDate start, LocalDate end) {
        if (memberIds.isEmpty() || start.isAfter(end)) {
            return List.of();
        }
        LambdaQueryWrapper<DailyReport> wrapper = new LambdaQueryWrapper<DailyReport>()
                // projectId 也要查出来，项目排行按它分组
                .select(DailyReport::getUserId, DailyReport::getReportDate, DailyReport::getProjectId)
                .eq(DailyReport::getStatus, ReportConstant.STATUS_SUBMITTED)
                .in(DailyReport::getUserId, memberIds)
                .between(DailyReport::getReportDate, start, end);
        if (projectId != null) {
            wrapper.eq(DailyReport::getProjectId, projectId);
        }
        return dailyReportMapper.selectList(wrapper);
    }

    private List<BoardDeptVO> buildDeptStats(LoginUser me, List<SysUser> members,
                                             List<DailyReport> rangeReports, int workdays) {
        // 部门 -> 成员ID
        Map<Long, Set<Long>> deptMemberIds = new LinkedHashMap<>();
        for (SysUser member : members) {
            if (member.getDeptId() == null) {
                continue;
            }
            deptMemberIds.computeIfAbsent(member.getDeptId(), k -> new java.util.HashSet<>())
                    .add(member.getId());
        }

        Map<Long, Integer> submittedByDept = new HashMap<>();
        for (DailyReport report : rangeReports) {
            for (Map.Entry<Long, Set<Long>> entry : deptMemberIds.entrySet()) {
                if (entry.getValue().contains(report.getUserId())) {
                    submittedByDept.merge(entry.getKey(), 1, Integer::sum);
                    break;
                }
            }
        }

        // 展示口径：高管看全公司所有二级部门（含 0 人的），负责人只看自己部门
        List<SysDept> targetDepts = resolveStatDepts(me);
        if (targetDepts.isEmpty()) {
            return List.of();
        }

        List<BoardDeptVO> stats = new ArrayList<>();
        for (SysDept dept : targetDepts) {
            Set<Long> memberIds = deptMemberIds.getOrDefault(dept.getId(), Set.of());
            int count = memberIds.size();
            int submitted = submittedByDept.getOrDefault(dept.getId(), 0);
            int expected = count * workdays;

            stats.add(BoardDeptVO.builder()
                    .deptId(dept.getId())
                    .deptName(dept.getDeptName())
                    .memberCount(count)
                    .submitted(submitted)
                    .expected(expected)
                    .rate(rate(submitted, expected))
                    .build());
        }
        stats.sort(Comparator.comparingInt(BoardDeptVO::getRate).reversed());
        return stats;
    }

    /** 部门对比图要展示哪些部门 */
    private List<SysDept> resolveStatDepts(LoginUser me) {
        if (RoleConstant.EXECUTIVE.equals(me.role())) {
            // 全公司所有二级部门（parent_id != 0），含当前没有成员的部门
            return sysDeptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                    .ne(SysDept::getParentId, 0L)
                    .orderByAsc(SysDept::getSortOrder)
                    .orderByAsc(SysDept::getId));
        }
        if (me.deptId() == null) {
            return List.of();
        }
        SysDept own = sysDeptMapper.selectById(me.deptId());
        return own == null ? List.of() : List.of(own);
    }

    private List<BoardPendingVO> buildPending(List<SysUser> members, List<DailyReport> rangeReports,
                                              int workdays, LocalDate today) {
        if (members.isEmpty() || workdays <= 0) {
            return List.of();
        }
        Map<Long, String> deptNames = deptNames(members);

        Map<Long, List<DailyReport>> byUser = rangeReports.stream()
                .collect(Collectors.groupingBy(DailyReport::getUserId));

        List<BoardPendingVO> pending = new ArrayList<>();
        for (SysUser member : members) {
            List<DailyReport> mine = byUser.getOrDefault(member.getId(), List.of());
            long submittedDays = mine.stream()
                    .map(DailyReport::getReportDate)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            int missing = (int) (workdays - submittedDays);
            if (missing <= 0) {
                continue;
            }
            // 今天已经提交的人不进催报名单：只统计"今天 status = 0（未提交）"的成员
            boolean todaySubmitted = mine.stream().anyMatch(r -> today.equals(r.getReportDate()));
            if (todaySubmitted) {
                continue;
            }
            LocalDate lastSubmit = mine.stream()
                    .map(DailyReport::getReportDate)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(null);

            pending.add(BoardPendingVO.builder()
                    .userId(member.getId())
                    .realName(member.getRealName())
                    .deptName(member.getDeptId() == null ? null : deptNames.get(member.getDeptId()))
                    .roleName(RoleConstant.displayName(member.getRole()))
                    .missingDays(missing)
                    .lastSubmitDate(lastSubmit)
                    .todaySubmitted(false)
                    .build());
        }

        pending.sort(Comparator.comparingInt(BoardPendingVO::getMissingDays).reversed()
                .thenComparing(BoardPendingVO::getRealName, Comparator.nullsLast(Comparator.naturalOrder())));
        return pending.size() > 50 ? pending.subList(0, 50) : pending;
    }

    private BoardTrendVO buildDay(LocalDate date, int submitted, int memberCount) {
        boolean workday = isWorkday(date);
        int expected = workday ? memberCount : 0;
        return BoardTrendVO.builder()
                .date(date)
                .submitted(submitted)
                .expected(expected)
                .rate(expected == 0 ? 0 : rate(submitted, expected))
                .workday(workday)
                .build();
    }

    private Map<Long, String> deptNames(List<SysUser> members) {
        Set<Long> deptIds = members.stream()
                .map(SysUser::getDeptId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (deptIds.isEmpty()) {
            return Map.of();
        }
        return sysDeptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));
    }

    /** 部门实体（需要 parentId 区分根部门） */
    private Map<Long, SysDept> deptEntities(Set<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return Map.of();
        }
        return sysDeptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, dept -> dept, (a, b) -> a));
    }

    private Map<LocalDate, Integer> countByDate(List<DailyReport> reports) {
        Map<LocalDate, Integer> map = new HashMap<>();
        for (DailyReport report : reports) {
            if (report.getReportDate() != null) {
                map.merge(report.getReportDate(), 1, Integer::sum);
            }
        }
        return map;
    }

    private int countWorkdays(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            return 0;
        }
        int days = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (isWorkday(date)) {
                days++;
            }
        }
        return days;
    }

    private boolean isWorkday(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    private LocalDate minDate(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private int rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round(numerator * 100.0 / denominator);
    }

    /** 内部用的日期区间 */
    private record DateRange(LocalDate start, LocalDate end) {
    }
}
