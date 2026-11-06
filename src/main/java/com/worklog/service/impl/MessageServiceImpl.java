package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.worklog.common.PageResult;
import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.MessageQuery;
import com.worklog.entity.SysMessage;
import com.worklog.entity.SysUser;
import com.worklog.mapper.SysMessageMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import com.worklog.service.MessageService;
import com.worklog.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 站内信服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private static final long MAX_PAGE_SIZE = 100;

    private final SysMessageMapper sysMessageMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public long unreadCount() {
        return sysMessageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getUserId, UserContext.requireUserId())
                .eq(SysMessage::getIsRead, 0));
    }

    @Override
    public PageResult<MessageVO> page(MessageQuery query) {
        Long userId = UserContext.requireUserId();
        long page = Math.max(query.getPage(), 1);
        long size = Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<SysMessage> wrapper = new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getUserId, userId);
        if (query.getIsRead() != null) {
            wrapper.eq(SysMessage::getIsRead, query.getIsRead());
        }
        if (query.getMsgType() != null) {
            wrapper.eq(SysMessage::getMsgType, query.getMsgType());
        }
        wrapper.orderByDesc(SysMessage::getCreateTime).orderByDesc(SysMessage::getId);

        Page<SysMessage> result = sysMessageMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result, result.getRecords().stream().map(this::toVO).toList());
    }

    @Override
    public List<MessageVO> recentUnread(int limit) {
        int size = Math.min(Math.max(limit, 1), 20);
        Long userId = UserContext.requireUserId();

        Page<SysMessage> page = sysMessageMapper.selectPage(new Page<>(1, size),
                new LambdaQueryWrapper<SysMessage>()
                        .eq(SysMessage::getUserId, userId)
                        .eq(SysMessage::getIsRead, 0)
                        .orderByDesc(SysMessage::getCreateTime)
                        .orderByDesc(SysMessage::getId));

        return page.getRecords().stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id) {
        Long userId = UserContext.requireUserId();
        int updated = sysMessageMapper.update(null, new LambdaUpdateWrapper<SysMessage>()
                .eq(SysMessage::getId, id)
                .eq(SysMessage::getUserId, userId)
                .set(SysMessage::getIsRead, 1));
        if (updated == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "消息不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead() {
        Long userId = UserContext.requireUserId();
        return sysMessageMapper.update(null, new LambdaUpdateWrapper<SysMessage>()
                .eq(SysMessage::getUserId, userId)
                .eq(SysMessage::getIsRead, 0)
                .set(SysMessage::getIsRead, 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String remind(Long userId) {
        LoginUser operator = UserContext.require();
        // 层层管理：只有部门负责人能催本部门成员；高管对组织/项目负责，不直接催个人
        if (!RoleConstant.DEPT_LEADER.equals(operator.role())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有部门负责人可以催报本部门成员");
        }
        if (Objects.equals(operator.id(), userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不需要催自己");
        }

        SysUser target = sysUserMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!Objects.equals(target.getDeptId(), operator.deptId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能催报本部门成员");
        }

        // 同一天不重复催同一个人，避免刷屏
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long sentToday = sysMessageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getUserId, userId)
                .eq(SysMessage::getMsgType, SysMessage.TYPE_REMIND)
                .ge(SysMessage::getCreateTime, startOfDay));
        if (sentToday > 0) {
            return "今天已经催过 TA 了，不用重复提醒";
        }

        SysMessage message = new SysMessage();
        message.setUserId(userId);
        message.setTitle("日报催报提醒");
        message.setContent("你在 " + LocalDate.now() + " 的日报还未提交，请尽快完成填报。");
        message.setMsgType(SysMessage.TYPE_REMIND);
        message.setIsRead(0);
        sysMessageMapper.insert(message);

        log.info("催报发送：target={}({}), operator={}", target.getUsername(), target.getRealName(),
                operator.username());
        return "已发送催报提醒给 " + target.getRealName();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void notifySystem(Long userId, String title, String content) {
        if (userId == null) {
            return;
        }
        SysMessage message = new SysMessage();
        message.setUserId(userId);
        message.setTitle(title);
        message.setContent(content);
        message.setMsgType(SysMessage.TYPE_SYSTEM);
        message.setIsRead(0);
        sysMessageMapper.insert(message);
        log.info("系统通知：userId={}, title={}", userId, title);
    }

    private MessageVO toVO(SysMessage message) {
        return MessageVO.builder()
                .id(message.getId())
                .title(message.getTitle())
                .content(message.getContent())
                .msgType(message.getMsgType())
                .msgTypeName(Objects.equals(message.getMsgType(), SysMessage.TYPE_REMIND)
                        ? "催报提醒" : "系统通知")
                .isRead(message.getIsRead())
                .createTime(message.getCreateTime())
                .build();
    }
}
