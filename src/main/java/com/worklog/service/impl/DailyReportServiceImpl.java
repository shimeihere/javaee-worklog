package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.worklog.common.PageResult;
import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.constant.ReportConstant;
import com.worklog.dto.DailyReportSaveRequest;
import com.worklog.dto.ReportQuery;
import com.worklog.entity.DailyReport;
import com.worklog.entity.Project;
import com.worklog.entity.SysUser;
import com.worklog.mapper.DailyReportMapper;
import com.worklog.mapper.ProjectMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import com.worklog.service.AttachmentService;
import com.worklog.service.DailyReportService;
import com.worklog.service.DataScopeService;
import com.worklog.vo.CalendarDayVO;
import com.worklog.vo.CalendarVO;
import com.worklog.vo.DailyReportVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 日报服务实现。
 *
 * <p>状态流转：草稿(0) ⇄ 已提交(1)。提交只校验完成内容；关联项目选填
 * （有些人做的是部门事务、没有具体项目，不能因此交不了日报）；撤回不做时间限制，
 * 便于员工修正历史记录（是否允许撤回历史由产品决定，当前放开）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportServiceImpl implements DailyReportService {

    private static final long MAX_PAGE_SIZE = 100;

    private final DailyReportMapper dailyReportMapper;
    private final ProjectMapper projectMapper;
    private final SysUserMapper sysUserMapper;
    private final AttachmentService attachmentService;
    private final DataScopeService dataScopeService;

    // ------------------------------------------------------------------
    // 写操作
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DailyReportVO save(DailyReportSaveRequest request) {
        LoginUser me = UserContext.require();
        LocalDate date = request.getReportDate();
        if (date == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择日报日期");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能填写未来日期的日报");
        }
        validateProject(request.getProjectId());

        DailyReport existing = findByUserAndDate(me.id(), date);
        if (existing == null) {
            DailyReport report = new DailyReport();
            report.setUserId(me.id());
            report.setReportDate(date);
            report.setProjectId(request.getProjectId());
            report.setContentDone(trimToNull(request.getContentDone()));
            report.setContentPlan(trimToNull(request.getContentPlan()));
            report.setHours(request.getHours() == null ? BigDecimal.ZERO : request.getHours());
            report.setStatus(ReportConstant.STATUS_DRAFT);
            dailyReportMapper.insert(report);
            log.info("新建日报草稿：id={}, userId={}, date={}", report.getId(), me.id(), date);
            return toVO(report);
        }

        if (!Objects.equals(existing.getStatus(), ReportConstant.STATUS_DRAFT)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该日期日报已提交，如需修改请先撤回");
        }
        return applyUpdate(existing.getId(), request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DailyReportVO update(Long id, DailyReportSaveRequest request) {
        DailyReport report = requireOwned(id);
        if (!Objects.equals(report.getStatus(), ReportConstant.STATUS_DRAFT)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有草稿状态的日报可以编辑，请先撤回");
        }
        validateProject(request.getProjectId());
        return applyUpdate(id, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DailyReportVO submit(Long id) {
        DailyReport report = requireOwned(id);
        if (Objects.equals(report.getStatus(), ReportConstant.STATUS_SUBMITTED)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该日报已提交，请勿重复提交");
        }
        if (!StringUtils.hasText(report.getContentDone())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "提交前请先填写今日完成内容");
        }
        changeStatus(id, ReportConstant.STATUS_SUBMITTED);
        log.info("日报提交：id={}, userId={}, date={}", id, report.getUserId(), report.getReportDate());
        return toVO(dailyReportMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DailyReportVO withdraw(Long id) {
        DailyReport report = requireOwned(id);
        if (!Objects.equals(report.getStatus(), ReportConstant.STATUS_SUBMITTED)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有已提交的日报可以撤回");
        }
        changeStatus(id, ReportConstant.STATUS_DRAFT);
        log.info("日报撤回：id={}, userId={}, date={}", id, report.getUserId(), report.getReportDate());
        return toVO(dailyReportMapper.selectById(id));
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Override
    public DailyReportVO getByDate(LocalDate date) {
        if (date == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请传入日期");
        }
        DailyReport report = findByUserAndDate(UserContext.requireUserId(), date);
        return report == null ? null : toVO(report);
    }

    @Override
    public DailyReportVO detail(Long id) {
        DailyReport report = dailyReportMapper.selectById(id);
        if (report == null || !dataScopeService.canViewReportOf(report.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "日报不存在或无权访问");
        }
        return toVO(report);
    }

    @Override
    public PageResult<DailyReportVO> page(ReportQuery query) {
        long page = Math.max(query.getPage(), 1);
        long size = Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<DailyReport> wrapper = new LambdaQueryWrapper<>();
        if (query.getUserId() != null) {
            wrapper.eq(DailyReport::getUserId, query.getUserId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(DailyReport::getStatus, query.getStatus());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(DailyReport::getReportDate, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(DailyReport::getReportDate, query.getEndDate());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(DailyReport::getContentDone, query.getKeyword().trim());
        }
        dataScopeService.applyDailyReportScope(wrapper);
        wrapper.orderByDesc(DailyReport::getReportDate).orderByDesc(DailyReport::getId);

        Page<DailyReport> result = dailyReportMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result, toVOList(result.getRecords()));
    }

    @Override
    public CalendarVO calendar(YearMonth yearMonth) {
        Long userId = UserContext.requireUserId();
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        Map<LocalDate, DailyReport> reportByDate = dailyReportMapper.selectList(
                        new LambdaQueryWrapper<DailyReport>()
                                .eq(DailyReport::getUserId, userId)
                                .between(DailyReport::getReportDate, monthStart, monthEnd))
                .stream()
                .collect(Collectors.toMap(DailyReport::getReportDate, r -> r, (a, b) -> a));

        LocalDate today = LocalDate.now();
        List<CalendarDayVO> days = new ArrayList<>();
        int workdays = 0;
        int submitted = 0;
        int overdue = 0;
        int notFilled = 0;

        for (LocalDate date = monthStart; !date.isAfter(monthEnd); date = date.plusDays(1)) {
            boolean workday = date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY;
            if (workday) {
                workdays++;
            }

            DailyReport report = reportByDate.get(date);
            int status;
            if (report != null && Objects.equals(report.getStatus(), ReportConstant.STATUS_SUBMITTED)) {
                status = ReportConstant.CALENDAR_SUBMITTED;
            } else if (workday && date.isBefore(today)) {
                // 工作日且已过期（含只存了草稿没提交的情况）
                status = ReportConstant.CALENDAR_OVERDUE;
            } else {
                status = ReportConstant.CALENDAR_NOT_FILLED;
            }

            switch (status) {
                case ReportConstant.CALENDAR_SUBMITTED -> submitted++;
                case ReportConstant.CALENDAR_OVERDUE -> overdue++;
                default -> {
                    if (workday) {
                        notFilled++;
                    }
                }
            }

            days.add(CalendarDayVO.builder()
                    .date(date)
                    .status(status)
                    .workday(workday)
                    .reportId(report == null ? null : report.getId())
                    .hours(report == null ? null : report.getHours())
                    .build());
        }

        int submitRate = workdays == 0 ? 0 : (int) Math.round(submitted * 100.0 / workdays);
        return CalendarVO.builder()
                .yearMonth(yearMonth.toString())
                .totalDays(days.size())
                .workdays(workdays)
                .submittedDays(submitted)
                .overdueDays(overdue)
                .notFilledDays(notFilled)
                .submitRate(submitRate)
                .days(days)
                .build();
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private DailyReport findByUserAndDate(Long userId, LocalDate date) {
        return dailyReportMapper.selectOne(new LambdaQueryWrapper<DailyReport>()
                .eq(DailyReport::getUserId, userId)
                .eq(DailyReport::getReportDate, date));
    }

    private DailyReport requireOwned(Long id) {
        DailyReport report = dailyReportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "日报不存在");
        }
        if (!Objects.equals(report.getUserId(), UserContext.requireUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作自己的日报");
        }
        return report;
    }

    /** 用 UpdateWrapper 显式 set，保证清空字段（置 null）也能落库 */
    private DailyReportVO applyUpdate(Long id, DailyReportSaveRequest request) {
        dailyReportMapper.update(null, new LambdaUpdateWrapper<DailyReport>()
                .eq(DailyReport::getId, id)
                .set(DailyReport::getProjectId, request.getProjectId())
                .set(DailyReport::getContentDone, trimToNull(request.getContentDone()))
                .set(DailyReport::getContentPlan, trimToNull(request.getContentPlan()))
                .set(DailyReport::getHours, request.getHours())
                .set(DailyReport::getUpdateTime, LocalDateTime.now()));
        return toVO(dailyReportMapper.selectById(id));
    }

    private void changeStatus(Long id, int status) {
        dailyReportMapper.update(null, new LambdaUpdateWrapper<DailyReport>()
                .eq(DailyReport::getId, id)
                .set(DailyReport::getStatus, status)
                .set(DailyReport::getUpdateTime, LocalDateTime.now()));
    }

    /** 项目必须是当前用户可见的，防止员工把日报挂到别人部门的项目上 */
    private void validateProject(Long projectId) {
        if (projectId == null) {
            return;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "所选项目不存在");
        }
        if (!dataScopeService.canViewProject(project)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权选择该项目");
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private DailyReportVO toVO(DailyReport report) {
        DailyReportVO vo = toVOList(List.of(report)).get(0);
        vo.setAttachments(attachmentService.listByReport(ReportConstant.REPORT_TYPE_DAILY, report.getId()));
        return vo;
    }

    private List<DailyReportVO> toVOList(List<DailyReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = reports.stream().map(DailyReport::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> projectIds = reports.stream().map(DailyReport::getProjectId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> userNames = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getProjectName, (a, b) -> a));

        return reports.stream()
                .map(report -> DailyReportVO.builder()
                        .id(report.getId())
                        .userId(report.getUserId())
                        .userName(userNames.get(report.getUserId()))
                        .projectId(report.getProjectId())
                        .projectName(report.getProjectId() == null ? null : projectNames.get(report.getProjectId()))
                        .reportDate(report.getReportDate())
                        .contentDone(report.getContentDone())
                        .contentPlan(report.getContentPlan())
                        .hours(report.getHours())
                        .status(report.getStatus())
                        .createTime(report.getCreateTime())
                        .updateTime(report.getUpdateTime())
                        .build())
                .toList();
    }
}
