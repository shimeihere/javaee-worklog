package com.worklog.controller;

import com.worklog.common.PageResult;
import com.worklog.common.Result;
import com.worklog.constant.RoleConstant;
import com.worklog.dto.MessageQuery;
import com.worklog.dto.RemindRequest;
import com.worklog.security.RequireRole;
import com.worklog.service.MessageService;
import com.worklog.vo.MessageVO;
import com.worklog.vo.UnreadCountVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内信接口（M7 提醒中心）。
 */
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * 未读数量：GET /api/messages/unread-count
     */
    @GetMapping("/unread-count")
    public Result<UnreadCountVO> unreadCount() {
        return Result.ok(new UnreadCountVO(messageService.unreadCount()));
    }

    /**
     * 消息列表：GET /api/messages?page=1&size=10&isRead=0&msgType=1
     */
    @GetMapping
    public Result<PageResult<MessageVO>> page(MessageQuery query) {
        return Result.ok(messageService.page(query));
    }

    /**
     * 最近未读（顶栏下拉面板）：GET /api/messages/recent-unread?limit=5
     */
    @GetMapping("/recent-unread")
    public Result<List<MessageVO>> recentUnread(@RequestParam(defaultValue = "5") int limit) {
        return Result.ok(messageService.recentUnread(limit));
    }

    /**
     * 标记单条已读：PUT /api/messages/{id}/read
     */
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return Result.ok("已标记为已读", null);
    }

    /**
     * 全部已读：PUT /api/messages/read-all
     */
    @PutMapping("/read-all")
    public Result<Integer> markAllRead() {
        int count = messageService.markAllRead();
        return Result.ok("已全部标记为已读", count);
    }

    /**
     * 一键催报：POST /api/messages/remind
     *
     * <p>只有部门负责人对本部门成员催报；高管对组织负责、不直接催个人。</p>
     */
    @PostMapping("/remind")
    @RequireRole(RoleConstant.DEPT_LEADER)
    public Result<Void> remind(@Valid @RequestBody RemindRequest request) {
        return Result.<Void>ok(messageService.remind(request.getUserId()), null);
    }
}
