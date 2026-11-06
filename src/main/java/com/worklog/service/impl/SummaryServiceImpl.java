package com.worklog.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.worklog.common.PageResult;
import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.constant.ReportConstant;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.SummaryQuery;
import com.worklog.entity.DailyReport;
import com.worklog.entity.Project;
import com.worklog.entity.SysDept;
import com.worklog.entity.SysUser;
import com.worklog.entity.WeeklyReport;
import com.worklog.mapper.DailyReportMapper;
import com.worklog.mapper.ProjectMapper;
import com.worklog.mapper.SysDeptMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.mapper.WeeklyReportMapper;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import com.worklog.service.DataScopeService;
import com.worklog.service.SummaryService;
import com.worklog.vo.SummaryDailyItemVO;
import com.worklog.vo.SummaryRowVO;
import com.worklog.vo.SummaryWeeklyItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 汇总中心实现（M5）。
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>四个维度共用一套"成员 + 已提交日报"底座，只是聚合键不同；</li>
 *   <li>数据范围：员工仅本人、负责人本部门、管理员全部（与看板一致）；</li>
 *   <li>聚合在内存完成后再分页：团队规模在百人内，比写四套 SQL + UNION 更易维护；</li>
 *   <li>导出复用同一份聚合结果与列定义，保证"导出的和看到的一致"。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryServiceImpl implements SummaryService {

    private static final long MAX_PAGE_SIZE = 200;
    private static final long MAX_RANGE_DAYS = 366;

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final DailyReportMapper dailyReportMapper;
    private final WeeklyReportMapper weeklyReportMapper;
    private final ProjectMapper projectMapper;
    private final DataScopeService dataScopeService;

    @Override
    public PageResult<SummaryRowVO> query(SummaryQuery query) {
        List<SummaryRowVO> rows = aggregate(query);

        long size = Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);
        long page = Math.max(query.getPage(), 1);
        int from = (int) ((page - 1) * size);

        List<SummaryRowVO> pageRows = from >= rows.size()
                ? List.of()
                : new ArrayList<>(rows.subList(from, Math.min(from + (int) size, rows.size())));

        return new PageResult<>(pageRows, rows.size(), page, size);
    }

    @Override
    public void writeExcel(SummaryQuery query, OutputStream outputStream) {
        String dimension = normalizeDimension(query.getDimension());
        SummaryQuery exportQuery = new SummaryQuery();
        exportQuery.setDimension(dimension);
        exportQuery.setStartDate(query.getStartDate());
        exportQuery.setEndDate(query.getEndDate());
        exportQuery.setDeptId(query.getDeptId());
        exportQuery.setProjectId(query.getProjectId());
        exportQuery.setKeyword(query.getKeyword());

        List<SummaryRowVO> rows = aggregate(exportQuery);

        List<List<String>> head = headOf(dimension);
        List<List<Object>> data = rows.stream()
                .map(row -> cellsOf(dimension, row))
                .toList();

        EasyExcel.write(outputStream).head(head).sheet("汇总").doWrite(data);
        log.info("汇总导出：dimension={}, rows={}", dimension, rows.size());
    }

    @Override
    public List<SummaryDailyItemVO> userDailyDetail(Long userId, LocalDate startDate, LocalDate endDate) {
        requireDetailTarget(userId);

        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.withDayOfMonth(1);

        List<DailyReport> reports = dailyReportMapper.selectList(new LambdaQueryWrapper<DailyReport>()
                .select(DailyReport::getId, DailyReport::getReportDate, DailyReport::getHours,
                        DailyReport::getProjectId, DailyReport::getContentDone,
                        DailyReport::getContentPlan, DailyReport::getStatus)
                .eq(DailyReport::getUserId, userId)
                .eq(DailyReport::getStatus, ReportConstant.STATUS_SUBMITTED)
                .between(DailyReport::getReportDate, start, end)
                .orderByDesc(DailyReport::getReportDate));

        Map<Long, String> projectNames = projectNames(reports.stream()
                .map(DailyReport::getProjectId).collect(Collectors.toSet()));

        return reports.stream()
                .map(report -> SummaryDailyItemVO.builder()
                        .id(report.getId())
                        .reportDate(report.getReportDate())
                        .hours(report.getHours())
                        .status(report.getStatus())
                        .projectName(report.getProjectId() == null
                                ? null : projectNames.get(report.getProjectId()))
                        .contentDone(report.getContentDone())
                        .contentPlan(report.getContentPlan())
                        .build())
                .toList();
    }

    @Override
    public List<SummaryWeeklyItemVO> userWeeklyDetail(Long userId, LocalDate startDate, LocalDate endDate) {
        requireDetailTarget(userId);

        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.withDayOfMonth(1);

        List<WeeklyReport> reports = weeklyReportMapper.selectList(new LambdaQueryWrapper<WeeklyReport>()
                .select(WeeklyReport::getId, WeeklyReport::getStartDate, WeeklyReport::getEndDate,
                        WeeklyReport::getProjectId, WeeklyReport::getSummary,
                        WeeklyReport::getPlan, WeeklyReport::getStatus)
                .eq(WeeklyReport::getUserId, userId)
                .eq(WeeklyReport::getStatus, ReportConstant.STATUS_SUBMITTED)
                .le(WeeklyReport::getStartDate, end)
                .ge(WeeklyReport::getEndDate, start)
                .orderByDesc(WeeklyReport::getStartDate));

        Map<Long, String> projectNames = projectNames(reports.stream()
                .map(WeeklyReport::getProjectId).collect(Collectors.toSet()));

        return reports.stream()
                .map(report -> SummaryWeeklyItemVO.builder()
                        .id(report.getId())
                        .startDate(report.getStartDate())
                        .endDate(report.getEndDate())
                        .projectName(report.getProjectId() == null
                                ? null : projectNames.get(report.getProjectId()))
                        .summary(report.getSummary())
                        .plan(report.getPlan())
                        .status(report.getStatus())
                        .build())
                .toList();
    }

    /**
     * 明细查看权限：
     * 高管（总裁监督权）可下钻查看任何人；
     * 部门负责人仅限本部门成员；
     * 管理员只做系统配置，不开放个人明细。
     */
    private SysUser requireDetailTarget(Long userId) {
        SysUser target = sysUserMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        LoginUser me = UserContext.require();

        // 总裁监督权：高管可查看任何员工明细
        if (RoleConstant.EXECUTIVE.equals(me.role())) {
            return target;
        }
        if (!RoleConstant.DEPT_LEADER.equals(me.role())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不在你的管理范围内，无法查看个人上报明细");
        }
        if (me.deptId() == null || !Objects.equals(target.getDeptId(), me.deptId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能查看本部门成员的明细");
        }
        return target;
    }

    /** 批量取名，避免逐条查库 */
    private Map<Long, String> projectNames(Set<Long> projectIds) {
        Set<Long> ids = projectIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return projectMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Project::getId, Project::getProjectName, (a, b) -> a));
    }

    // ------------------------------------------------------------------
    // 聚合
    // ------------------------------------------------------------------

    private List<SummaryRowVO> aggregate(SummaryQuery query) {
        LoginUser me = UserContext.require();
        String dimension = normalizeDimension(query.getDimension());

        LocalDate end = query.getEndDate() != null ? query.getEndDate() : LocalDate.now();
        LocalDate start = query.getStartDate() != null ? query.getStartDate() : end.withDayOfMonth(1);
        if (start.isAfter(end)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "时间范围不能超过一年");
        }

        List<SysUser> members = resolveMembers(me, query.getDeptId());
        Set<Long> memberIds = members.stream().map(SysUser::getId).collect(Collectors.toSet());
        int workdays = countWorkdays(start, end);
        List<DailyReport> reports = fetchReports(memberIds, query.getProjectId(), start, end);

        List<SummaryRowVO> rows = switch (dimension) {
            case "project" -> byProject(me, query.getDeptId(), reports);
            case "dept" -> byDept(members, reports, workdays);
            case "date" -> byDate(reports, start, end, members.size());
            default -> byUser(members, reports, workdays);
        };

        return applyKeyword(rows, query.getKeyword());
    }

    private List<SummaryRowVO> byUser(List<SysUser> members, List<DailyReport> reports, int workdays) {
        Map<Long, List<DailyReport>> grouped = reports.stream()
                .filter(report -> report.getUserId() != null)
                .collect(Collectors.groupingBy(DailyReport::getUserId));
        Map<Long, String> deptNames = deptNames(members);

        List<SummaryRowVO> rows = new ArrayList<>();
        for (SysUser member : members) {
            List<DailyReport> mine = grouped.getOrDefault(member.getId(), List.of());
            int submitted = (int) mine.stream()
                    .map(DailyReport::getReportDate)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            int projectCount = (int) mine.stream()
                    .map(DailyReport::getProjectId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();

            rows.add(SummaryRowVO.builder()
                    .dimension("user")
                    .id(String.valueOf(member.getId()))
                    .name(member.getRealName())
                    .sub(member.getDeptId() == null ? null : deptNames.get(member.getDeptId()))
                    .extra(RoleConstant.displayName(member.getRole()))
                    .roleCode(member.getRole())
                    .submitted(submitted)
                    .expected(workdays)
                    .rate(rate(submitted, workdays))
                    .projectCount(projectCount)
                    .hours(sumHours(mine))
                    .build());
        }

        // 提交率低的排前面，方便直接找到问题成员
        rows.sort(Comparator.comparingInt(SummaryRowVO::getRate)
                .thenComparing(SummaryRowVO::getName, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    private List<SummaryRowVO> byProject(LoginUser me, Long deptId, List<DailyReport> reports) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        dataScopeService.applyProjectScope(wrapper);
        // 部门负责人的部门已由数据范围锁定，这里只对管理员应用部门筛选
        if (RoleConstant.ADMIN.equals(me.role()) && deptId != null) {
            wrapper.eq(Project::getDeptId, deptId);
        }
        List<Project> projects = projectMapper.selectList(wrapper);

        Map<Long, List<DailyReport>> grouped = reports.stream()
                .filter(report -> report.getProjectId() != null)
                .collect(Collectors.groupingBy(DailyReport::getProjectId));

        Set<Long> leaderIds = projects.stream().map(Project::getLeaderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> leaderNames = leaderIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(leaderIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        Map<Long, String> deptNames = deptNameByIds(projects.stream()
                .map(Project::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<SummaryRowVO> rows = new ArrayList<>();
        for (Project project : projects) {
            List<DailyReport> mine = grouped.getOrDefault(project.getId(), List.of());
            int participants = (int) mine.stream()
                    .map(DailyReport::getUserId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();

            rows.add(SummaryRowVO.builder()
                    .dimension("project")
                    .id(String.valueOf(project.getId()))
                    .name(project.getProjectName())
                    .sub(project.getLeaderId() == null ? null : leaderNames.get(project.getLeaderId()))
                    .extra(project.getDeptId() == null ? null : deptNames.get(project.getDeptId()))
                    .submitted(mine.size())
                    .memberCount(participants)
                    .hours(sumHours(mine))
                    .build());
        }

        rows.sort(Comparator.comparingInt(SummaryRowVO::getSubmitted).reversed()
                .thenComparing(SummaryRowVO::getName, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    private List<SummaryRowVO> byDept(List<SysUser> members, List<DailyReport> reports, int workdays) {
        Map<Long, Set<Long>> deptMemberIds = new LinkedHashMap<>();
        for (SysUser member : members) {
            if (member.getDeptId() == null) {
                continue;
            }
            deptMemberIds.computeIfAbsent(member.getDeptId(), key -> new HashSet<>()).add(member.getId());
        }

        Map<Long, Integer> submittedByDept = new HashMap<>();
        for (DailyReport report : reports) {
            for (Map.Entry<Long, Set<Long>> entry : deptMemberIds.entrySet()) {
                if (entry.getValue().contains(report.getUserId())) {
                    submittedByDept.merge(entry.getKey(), 1, Integer::sum);
                    break;
                }
            }
        }

        Map<Long, BigDecimal> hoursByDept = new HashMap<>();
        for (DailyReport report : reports) {
            for (Map.Entry<Long, Set<Long>> entry : deptMemberIds.entrySet()) {
                if (entry.getValue().contains(report.getUserId())) {
                    hoursByDept.merge(entry.getKey(),
                            report.getHours() == null ? BigDecimal.ZERO : report.getHours(), BigDecimal::add);
                    break;
                }
            }
        }

        Map<Long, String> deptNames = deptNameByIds(deptMemberIds.keySet());

        List<SummaryRowVO> rows = new ArrayList<>();
        for (Map.Entry<Long, Set<Long>> entry : deptMemberIds.entrySet()) {
            int count = entry.getValue().size();
            int submitted = submittedByDept.getOrDefault(entry.getKey(), 0);
            int expected = count * workdays;

            rows.add(SummaryRowVO.builder()
                    .dimension("dept")
                    .id(String.valueOf(entry.getKey()))
                    .name(deptNames.getOrDefault(entry.getKey(), "未命名部门"))
                    .submitted(submitted)
                    .expected(expected)
                    .memberCount(count)
                    .rate(rate(submitted, expected))
                    .hours(hoursByDept.getOrDefault(entry.getKey(), BigDecimal.ZERO))
                    .build());
        }

        rows.sort(Comparator.comparingInt(SummaryRowVO::getRate)
                .thenComparing(SummaryRowVO::getName, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    private List<SummaryRowVO> byDate(List<DailyReport> reports, LocalDate start, LocalDate end,
                                      int memberCount) {
        Map<LocalDate, List<DailyReport>> grouped = reports.stream()
                .filter(report -> report.getReportDate() != null)
                .collect(Collectors.groupingBy(DailyReport::getReportDate));

        List<SummaryRowVO> rows = new ArrayList<>();
        // 汇总中心默认"最近日期先显示"：日期维度按时间倒序，打开就能看到今天/昨天
        for (LocalDate date = end; !date.isBefore(start); date = date.minusDays(1)) {
            List<DailyReport> dayReports = grouped.getOrDefault(date, List.of());
            int submitted = dayReports.size();

            rows.add(SummaryRowVO.builder()
                    .dimension("date")
                    .id(date.toString())
                    .name(date.toString())
                    .sub(weekdayName(date))
                    .submitted(submitted)
                    .expected(memberCount)
                    .rate(rate(submitted, memberCount))
                    .hours(sumHours(dayReports))
                    .build());
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // 基础查询
    // ------------------------------------------------------------------

    private List<SysUser> resolveMembers(LoginUser me, Long deptId) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId, SysUser::getRealName, SysUser::getRole, SysUser::getDeptId)
                .ne(SysUser::getStatus, 1);

        if (RoleConstant.DEPT_LEADER.equals(me.role())) {
            if (me.deptId() == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "当前账号未分配部门，无法查看汇总");
            }
            wrapper.eq(SysUser::getDeptId, me.deptId());
        } else if (deptId != null) {
            wrapper.eq(SysUser::getDeptId, deptId);
        }
        return sysUserMapper.selectList(wrapper);
    }

    private List<DailyReport> fetchReports(Set<Long> memberIds, Long projectId,
                                           LocalDate start, LocalDate end) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<DailyReport> wrapper = new LambdaQueryWrapper<DailyReport>()
                .select(DailyReport::getUserId, DailyReport::getProjectId,
                        DailyReport::getReportDate, DailyReport::getHours)
                .eq(DailyReport::getStatus, ReportConstant.STATUS_SUBMITTED)
                .in(DailyReport::getUserId, memberIds)
                .between(DailyReport::getReportDate, start, end);
        if (projectId != null) {
            wrapper.eq(DailyReport::getProjectId, projectId);
        }
        return dailyReportMapper.selectList(wrapper);
    }

    private Map<Long, String> deptNames(List<SysUser> members) {
        Set<Long> ids = members.stream().map(SysUser::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return deptNameByIds(ids);
    }

    private Map<Long, String> deptNameByIds(Set<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return Map.of();
        }
        return sysDeptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private String normalizeDimension(String dimension) {
        if (!StringUtils.hasText(dimension)) {
            return "user";
        }
        String value = dimension.trim();
        return switch (value) {
            case "user", "project", "dept", "date" -> value;
            default -> throw new BusinessException(ResultCode.BAD_REQUEST,
                    "dimension 只能是 user / project / dept / date");
        };
    }

    private List<SummaryRowVO> applyKeyword(List<SummaryRowVO> rows, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return rows;
        }
        String key = keyword.trim().toLowerCase();
        return rows.stream()
                .filter(row -> contains(row.getName(), key)
                        || contains(row.getSub(), key)
                        || contains(row.getExtra(), key))
                .toList();
    }

    private boolean contains(String value, String key) {
        return value != null && value.toLowerCase().contains(key);
    }

    private BigDecimal sumHours(List<DailyReport> reports) {
        return reports.stream()
                .map(DailyReport::getHours)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

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

    private String weekdayName(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }

    // ------------------------------------------------------------------
    // 导出列定义（与前端表格列一一对应）
    // ------------------------------------------------------------------

    private List<List<String>> headOf(String dimension) {
        List<String> columns = switch (dimension) {
            case "project" -> List.of("项目", "负责人", "部门", "日报数", "参与人数", "总工时");
            case "dept" -> List.of("部门", "人数", "已提交人次", "应提交人次", "提交率", "总工时");
            case "date" -> List.of("日期", "星期", "已提交人数", "应提交人数", "提交率", "总工时");
            // 与列表列保持一致：角色列已去掉，提交天数合并为"提交情况"
            default -> List.of("姓名", "部门", "提交情况", "提交率", "总工时");
        };
        return columns.stream().map(List::of).toList();
    }

    private List<Object> cellsOf(String dimension, SummaryRowVO row) {
        return switch (dimension) {
            case "project" -> List.of(
                    nullSafe(row.getName()),
                    nullSafe(row.getSub()),
                    nullSafe(row.getExtra()),
                    nullSafe(row.getSubmitted()),
                    nullSafe(row.getMemberCount()),
                    nullSafe(row.getHours()));
            case "dept" -> List.of(
                    nullSafe(row.getName()),
                    nullSafe(row.getMemberCount()),
                    nullSafe(row.getSubmitted()),
                    nullSafe(row.getExpected()),
                    percent(row.getRate()),
                    nullSafe(row.getHours()));
            case "date" -> List.of(
                    nullSafe(row.getName()),
                    nullSafe(row.getSub()),
                    nullSafe(row.getSubmitted()),
                    nullSafe(row.getExpected()),
                    percent(row.getRate()),
                    nullSafe(row.getHours()));
            default -> List.of(
                    nullSafe(row.getName()),
                    nullSafe(row.getSub()),
                    submittedText(row),
                    percent(row.getRate()),
                    nullSafe(row.getHours()));
        };
    }

    /** "4 / 22" 形式的提交情况 */
    private String submittedText(SummaryRowVO row) {
        int submitted = row.getSubmitted() == null ? 0 : row.getSubmitted();
        int expected = row.getExpected() == null ? 0 : row.getExpected();
        return submitted + " / " + expected;
    }

    private Object nullSafe(Object value) {
        return value == null ? "" : value;
    }

    private String percent(Integer rate) {
        return (rate == null ? 0 : rate) + "%";
    }
}
