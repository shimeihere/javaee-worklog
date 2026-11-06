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
 * 操作审计日志 sys_audit_log。
 *
 * <p>只记"管理动作"（谁在什么时候把谁的账号/部门/项目改成了什么），
 * 不记日报周报等业务内容，避免管理员越权看到业务数据。</p>
 */
@Data
@TableName("sys_audit_log")
public class AuditLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名（冗余存储，账号改名/停用后历史仍可读） */
    private String operatorName;

    /** 动作编码，见 AuditAction */
    private String action;

    /** 对象类型：USER / DEPT / PROJECT */
    private String targetType;

    /** 对象ID，批量操作可为空 */
    private Long targetId;

    /** 变更明细（人话） */
    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
