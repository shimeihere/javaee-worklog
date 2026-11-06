package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.worklog.common.PageResult;
import com.worklog.constant.AuditAction;
import com.worklog.dto.AuditQuery;
import com.worklog.entity.AuditLog;
import com.worklog.mapper.AuditLogMapper;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import com.worklog.service.AuditService;
import com.worklog.vo.AuditLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 审计日志实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final long MAX_PAGE_SIZE = 100;

    private final AuditLogMapper auditLogMapper;

    @Override
    public void record(String action, String targetType, Long targetId, String detail) {
        try {
            LoginUser me = UserContext.get();
            AuditLog entry = new AuditLog();
            entry.setOperatorId(me == null ? null : me.id());
            entry.setOperatorName(me == null ? "系统"
                    : (StringUtils.hasText(me.realName()) ? me.realName() : me.username()));
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setDetail(detail == null ? null
                    : (detail.length() > 500 ? detail.substring(0, 500) : detail));
            auditLogMapper.insert(entry);
        } catch (Exception e) {
            // 审计失败不能影响业务主流程
            log.warn("写审计日志失败：action={}, target={}#{}", action, targetType, targetId, e);
        }
    }

    @Override
    public PageResult<AuditLogVO> page(AuditQuery query) {
        long page = Math.max(query.getPage(), 1);
        long size = Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getAction())) {
            wrapper.eq(AuditLog::getAction, query.getAction().trim());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(AuditLog::getOperatorName, keyword)
                    .or()
                    .like(AuditLog::getDetail, keyword));
        }
        wrapper.orderByDesc(AuditLog::getId);

        Page<AuditLog> result = auditLogMapper.selectPage(new Page<>(page, size), wrapper);
        List<AuditLogVO> records = result.getRecords().stream()
                .map(entry -> AuditLogVO.builder()
                        .id(entry.getId())
                        .operatorName(entry.getOperatorName())
                        .action(entry.getAction())
                        .actionName(AuditAction.label(entry.getAction()))
                        .targetType(entry.getTargetType())
                        .targetId(entry.getTargetId())
                        .detail(entry.getDetail())
                        .createTime(entry.getCreateTime())
                        .build())
                .toList();
        return PageResult.of(result, records);
    }
}
