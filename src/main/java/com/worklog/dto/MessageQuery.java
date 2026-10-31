package com.worklog.dto;

import lombok.Data;

/**
 * 站内信分页查询条件。
 */
@Data
public class MessageQuery {

    private long page = 1;

    private long size = 10;

    /** 0 未读 / 1 已读，为空不过滤 */
    private Integer isRead;

    /** 1 催报 / 2 系统通知，为空不过滤 */
    private Integer msgType;
}
