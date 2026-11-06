package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.worklog.common.PageResult;
import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.constant.ReportConstant;
import com.worklog.dto.ReportQuery;
import com.worklog.dto.WeeklyReportSaveRequest;
import com.worklog.entity.Project;
import com.worklog.entity.SysUser;
import com.worklog.entity.WeeklyReport;
import com.worklog.mapper.ProjectMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.mapper.WeeklyReportMapper;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import com.worklog.service.AttachmentService;
import com.worklog.service.DataScopeService;
import com.worklog.service.WeeklyReportService;
import com.worklog.vo.WeeklyReportVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 周报服务实现。
 *
 * <p>周以周一为起点、周日为终点：传入周内任意一天，服务端自动归一到周一，
 * 保证 (user_id, start_date) 唯一约束不会被"同周多份"击穿。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportServiceImpl implements WeeklyReportService {

    private static final long MAX_PAGE_SIZE = 100;

    private final WeeklyReportMapper weeklyReportMapper;
    private final ProjectMapper projectMapper;
    private final SysUserMapper sysUserMapper;
    private final AttachmentService attachmentService;
    private final DataScopeService dataScopeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WeeklyReportVO save(WeeklyReportSaveRequest request) {
        LoginUser me = UserContext.require();
        if (request.getStartDate() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择周报所属周");
        }
        LocalDate monday = toMonday(request.getStartDate());
        if (monday.isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能填写未来周的周报");
        }
        validateProject(request.getProjectId());

        WeeklyReport existing = findByUserAndWeek(me.id(), monday);
        if (existing == null) {
            WeeklyReport report = new WeeklyReport();
            report.setUserId(me.id());
            report.setStartDate(monday);
            report.setEndDate(monday.plusDays(6));
            report.setProjectId(request.getProjectId());
            report.setSummary(trimToNull(request.getSummary()));
            report.setPlan(trimToNull(request.getPlan()));
            report.setStatus(ReportConstant.STATUS_DRAFT);
            weeklyReportMapper.insert(report);
            log.info("新建周报草稿：id={}, userId={}, week={}", report.getId(), me.id(), monday);
            return toVO(report);
        }

        if (!Objects.equals(existing.getStatus(), ReportConstant.STATUS_DRAFT)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该周周报已提交，如需修改请先撤回");
        }
        return applyUpdate(existing.getId(), request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WeeklyReportVO update(Long id, WeeklyReportSaveRequest request) {
        WeeklyReport report = requireOwned(id);
        if (!Objects.equals(report.getStatus(), ReportConstant.STATUS_DRAFT)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有草稿状态的周报可以编辑，请先撤回");
        }
        validateProject(request.getProjectId());
        return applyUpdate(id, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WeeklyReportVO submit(Long id) {
        WeeklyReport report = requireOwned(id);
        if (Objects.equals(report.getStatus(), ReportConstant.STATUS_SUBMITTED)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该周报已提交，请勿重复提交");
        }
        if (!StringUtils.hasText(report.getSummary())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "提交前请先填写本周总结");
        }
        changeStatus(id, ReportConstant.STATUS_SUBMITTED);
        log.info("周报提交：id={}, userId={}, week={}", id, report.getUserId(), report.getStartDate());
        return toVO(weeklyReportMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WeeklyReportVO withdraw(Long id) {
        WeeklyReport report = requireOwned(id);
        if (!Objects.equals(report.getStatus(), ReportConstant.STATUS_SUBMITTED)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有已提交的周报可以撤回");
        }
        changeStatus(id, ReportConstant.STATUS_DRAFT);
        log.info("周报撤回：id={}, userId={}, week={}", id, report.getUserId(), report.getStartDate());
        return toVO(weeklyReportMapper.selectById(id));
    }

    @Override
    public WeeklyReportVO getByWeek(LocalDate anyDayInWeek) {
        if (anyDayInWeek == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请传入日期");
        }
        WeeklyReport report = findByUserAndWeek(UserContext.requireUserId(), toMonday(anyDayInWeek));
        return report == null ? null : toVO(report);
    }

    @Override
    public WeeklyReportVO detail(Long id) {
        WeeklyReport report = weeklyReportMapper.selectById(id);
        if (report == null || !dataScopeService.canViewReportOf(report.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "周报不存在或无权访问");
        }
        return toVO(report);
    }

    @Override
    public PageResult<WeeklyReportVO> page(ReportQuery query) {
        long page = Math.max(query.getPage(), 1);
        long size = Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<WeeklyReport> wrapper = new LambdaQueryWrapper<>();
        if (query.getUserId() != null) {
            wrapper.eq(WeeklyReport::getUserId, query.getUserId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(WeeklyReport::getStatus, query.getStatus());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(WeeklyReport::getStartDate, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(WeeklyReport::getEndDate, query.getEndDate());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(WeeklyReport::getSummary, query.getKeyword().trim());
        }
        dataScopeService.applyWeeklyReportScope(wrapper);
        wrapper.orderByDesc(WeeklyReport::getStartDate).orderByDesc(WeeklyReport::getId);

        Page<WeeklyReport> result = weeklyReportMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result, toVOList(result.getRecords()));
    }

    // ------------------------------------------------------------------

    /** 任意日期 -> 该周周一 */
    private LocalDate toMonday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private WeeklyReport findByUserAndWeek(Long userId, LocalDate monday) {
        return weeklyReportMapper.selectOne(new LambdaQueryWrapper<WeeklyReport>()
                .eq(WeeklyReport::getUserId, userId)
                .eq(WeeklyReport::getStartDate, monday));
    }

    private WeeklyReport requireOwned(Long id) {
        WeeklyReport report = weeklyReportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "周报不存在");
        }
        if (!Objects.equals(report.getUserId(), UserContext.requireUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作自己的周报");
        }
        return report;
    }

    private WeeklyReportVO applyUpdate(Long id, WeeklyReportSaveRequest request) {
        weeklyReportMapper.update(null, new LambdaUpdateWrapper<WeeklyReport>()
                .eq(WeeklyReport::getId, id)
                .set(WeeklyReport::getProjectId, request.getProjectId())
                .set(WeeklyReport::getSummary, trimToNull(request.getSummary()))
                .set(WeeklyReport::getPlan, trimToNull(request.getPlan()))
                .set(WeeklyReport::getUpdateTime, LocalDateTime.now()));
        return toVO(weeklyReportMapper.selectById(id));
    }

    private void changeStatus(Long id, int status) {
        weeklyReportMapper.update(null, new LambdaUpdateWrapper<WeeklyReport>()
                .eq(WeeklyReport::getId, id)
                .set(WeeklyReport::getStatus, status)
                .set(WeeklyReport::getUpdateTime, LocalDateTime.now()));
    }

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
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private WeeklyReportVO toVO(WeeklyReport report) {
        WeeklyReportVO vo = toVOList(List.of(report)).get(0);
        vo.setAttachments(attachmentService.listByReport(ReportConstant.REPORT_TYPE_WEEKLY, report.getId()));
        return vo;
    }

    private List<WeeklyReportVO> toVOList(List<WeeklyReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = reports.stream().map(WeeklyReport::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> projectIds = reports.stream().map(WeeklyReport::getProjectId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> userNames = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getProjectName, (a, b) -> a));

        return reports.stream()
                .map(report -> WeeklyReportVO.builder()
                        .id(report.getId())
                        .userId(report.getUserId())
                        .userName(userNames.get(report.getUserId()))
                        .projectId(report.getProjectId())
                        .projectName(report.getProjectId() == null ? null : projectNames.get(report.getProjectId()))
                        .startDate(report.getStartDate())
                        .endDate(report.getEndDate())
                        .summary(report.getSummary())
                        .plan(report.getPlan())
                        .status(report.getStatus())
                        .createTime(report.getCreateTime())
                        .updateTime(report.getUpdateTime())
                        .build())
                .toList();
    }
}
