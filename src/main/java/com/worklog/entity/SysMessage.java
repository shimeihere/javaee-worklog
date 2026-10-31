package com.worklog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内信表 sys_message。
 *
 * <p>消息类型：1 催报提醒 / 2 系统通知；isRead：0 未读 / 1 已读。</p>
 */
@Data
@TableName("sys_message")
public class SysMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息类型常量：催报提醒 */
    public static final int TYPE_REMIND = 1;

    /** 消息类型常量：系统通知 */
    public static final int TYPE_SYSTEM = 2;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 接收人 ID */
    private Long userId;

    /** 消息标题 */
    private String title;

    /** 消息内容 */
    private String content;

    /** 消息类型：1 催报提醒 / 2 系统通知 */
    private Integer msgType;

    /** 是否已读：0 未读 / 1 已读 */
    private Integer isRead;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
