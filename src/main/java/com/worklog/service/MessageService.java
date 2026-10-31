package com.worklog.service;

import com.worklog.common.PageResult;
import com.worklog.dto.MessageQuery;
import com.worklog.vo.MessageVO;

import java.util.List;

/**
 * 站内信服务（M7）。
 */
public interface MessageService {

    /** 当前登录人未读消息数 */
    long unreadCount();

    /** 分页列表（只看自己的消息） */
    PageResult<MessageVO> page(MessageQuery query);

    /** 最近 N 条未读（顶栏下拉面板） */
    List<MessageVO> recentUnread(int limit);

    /** 标记单条已读 */
    void markRead(Long id);

    /** 全部标记已读，返回影响条数 */
    int markAllRead();

    /**
     * 催报：给指定成员发一条催报站内信。
     *
     * @return 面向操作人的提示语（当天重复催报会返回"已催过"）
     */
    String remind(Long userId);
}
