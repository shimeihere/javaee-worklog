package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.constant.ReportConstant;
import com.worklog.constant.RoleConstant;
import com.worklog.entity.DailyReport;
import com.worklog.entity.Project;
import com.worklog.entity.ProjectMember;
import com.worklog.entity.SysDept;
import com.worklog.entity.SysUser;
import com.worklog.mapper.DailyReportMapper;
import com.worklog.mapper.ProjectMapper;
import com.worklog.mapper.ProjectMemberMapper;
import com.worklog.mapper.SysDeptMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import com.worklog.service.BoardService;
import com.worklog.service.DataScopeService;
import com.worklog.util.ProjectTime;
import com.worklog.vo.BoardDeptVO;
import com.worklog.vo.BoardHeatVO;
import com.worklog.vo.BoardOverviewVO;
import com.worklog.vo.BoardActivityVO;
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

    /** 项目状态：已归档（归档项目不再进入看板的项目进度/排行） */
    private static final int PROJECT_STATUS_ARCHIVED = 1;

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final DailyReportMapper dailyReportMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final DataScopeService dataScopeService;

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
        List<BoardProjectVO> projectStats = buildProjectStats(memberIds, rangeReports, projectId, workdays);
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
                .recentActivities(buildRecentActivities(memberIds))
                .build();
    }

    /**
     * 最新动态：成员最近提交的 10 份日报（右侧「最新动态」Tab）。
     */
    private List<BoardActivityVO> buildRecentActivities(Set<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        List<DailyReport> reports = dailyReportMapper.selectList(new LambdaQueryWrapper<DailyReport>()
                .select(DailyReport::getUserId, DailyReport::getReportDate, DailyReport::getProjectId,
                        DailyReport::getHours, DailyReport::getUpdateTime)
                .in(DailyReport::getUserId, memberIds)
                .eq(DailyReport::getStatus, ReportConstant.STATUS_SUBMITTED)
                .orderByDesc(DailyReport::getReportDate)
                .orderByDesc(DailyReport::getUpdateTime)
                .last("LIMIT 10"));
        if (reports.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = reports.stream().map(DailyReport::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysUser> users = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        Set<Long> projectIds = reports.stream().map(DailyReport::getProjectId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getProjectName, (a, b) -> a));

        Set<Long> deptIds = users.values().stream().map(SysUser::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : sysDeptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));

        return reports.stream().map(report -> {
            SysUser user = users.get(report.getUserId());
            return BoardActivityVO.builder()
                    .userId(report.getUserId())
                    .realName(user == null ? "—" : user.getRealName())
                    .deptName(user == null || user.getDeptId() == null
                            ? null : deptNames.get(user.getDeptId()))
                    .reportDate(report.getReportDate())
                    .projectName(report.getProjectId() == null
                            ? null : projectNames.get(report.getProjectId()))
                    .hours(report.getHours() == null ? null : report.getHours().intValue())
                    .submittedAt(report.getUpdateTime())
                    .build();
        }).toList();
    }

    /**
     * 项目进度 / 项目排行：区间内每个项目的已提交日报数、参与人数与进度。
     *
     * <p>与"只从日报反查项目"不同，这里先按数据范围取出全部项目（部门负责人 = 本部门项目 +
     * 本人负责的项目），所以本期还没人填报的项目也会以 0% 出现在看板上——项目看板要能看见"没人动"的项目。
     * 参与人 = 项目成员表里的人 ∪ 本期在该项目填过日报的人，且只统计当前看板可见成员，保证分母口径一致。</p>
     */
    private List<BoardProjectVO> buildProjectStats(Set<Long> memberIds, List<DailyReport> rangeReports,
                                                   Long projectId, int workdays) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        dataScopeService.applyProjectScope(wrapper);
        wrapper.ne(Project::getStatus, PROJECT_STATUS_ARCHIVED);
        if (projectId != null) {
            wrapper.eq(Project::getId, projectId);
        }
        List<Project> projects = projectMapper.selectList(wrapper);
        if (projects.isEmpty()) {
            return List.of();
        }

        Map<Long, List<DailyReport>> byProject = rangeReports.stream()
                .filter(report -> report.getProjectId() != null)
                .collect(Collectors.groupingBy(DailyReport::getProjectId));

        // 项目成员（只保留当前看板可见成员，避免把别的部门的人算进分母）
        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        Map<Long, Set<Long>> membersByProject = new HashMap<>();
        if (!memberIds.isEmpty()) {
            projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                            .select(ProjectMember::getProjectId, ProjectMember::getUserId)
                            .in(ProjectMember::getProjectId, projectIds))
                    .stream()
                    .filter(member -> memberIds.contains(member.getUserId()))
                    .forEach(member -> membersByProject
                            .computeIfAbsent(member.getProjectId(), key -> new java.util.HashSet<>())
                            .add(member.getUserId()));
        }

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
        LocalDate today = LocalDate.now();
        for (Project project : projects) {
            List<DailyReport> mine = byProject.getOrDefault(project.getId(), List.of());
            LocalDate start = ProjectTime.startDate(project.getCreateTime());
            LocalDate plannedEnd = project.getPlannedEndDate();
            Set<Long> participants = new java.util.HashSet<>(
                    membersByProject.getOrDefault(project.getId(), Set.of()));
            mine.stream().map(DailyReport::getUserId).filter(Objects::nonNull).forEach(participants::add);

            stats.add(BoardProjectVO.builder()
                    .projectId(project.getId())
                    .projectName(project.getProjectName())
                    .leaderName(project.getLeaderId() == null ? null : leaderNames.get(project.getLeaderId()))
                    .deptName(project.getDeptId() == null ? null : deptNames.get(project.getDeptId()))
                    .submitted(mine.size())
                    .participants(participants.size())
                    .rate(rate(mine.size(), Math.max(participants.size(), 1) * Math.max(workdays, 1)))
                    .startDate(start)
                    .plannedEndDate(plannedEnd)
                    .elapsedDays(ProjectTime.elapsedDays(start, today))
                    .plannedDays(ProjectTime.plannedDays(start, plannedEnd))
                    .remainingDays(ProjectTime.remainingDays(plannedEnd, today))
                    .timeProgress(ProjectTime.timeProgress(start, plannedEnd, today))
                    .overdue(ProjectTime.overdue(plannedEnd, today))
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
            case "week", "lastWeek", "month", "last30" -> range;
            default -> throw new BusinessException(ResultCode.BAD_REQUEST,
                    "range 只能是 week / lastWeek / month / last30");
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
            // 近 30 天：跨月也能看到最近的数据，演示/月度复盘都用这个口径
            case "last30" -> new DateRange(today.minusDays(29), today);
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
        // 催报名单是给负责人用的，本人不在其中（自己催自己没有意义）
        Long meId = UserContext.requireUserId();

        Map<Long, List<DailyReport>> byUser = rangeReports.stream()
                .collect(Collectors.groupingBy(DailyReport::getUserId));

        List<BoardPendingVO> pending = new ArrayList<>();
        for (SysUser member : members) {
            if (Objects.equals(member.getId(), meId)) {
                continue;
            }
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
