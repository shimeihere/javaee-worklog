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
 * 项目成员表 project_member。
 *
 * <p>project 表只记录负责人（leader_id），无法表达"谁参与了这个项目"。
 * 本表补齐成员关系：管理员在「用户管理」里把某人加到项目，员工即可在填报时选到该项目，
 * 看板/汇总的"参与人"也以本表为准（历史上填过日报的人仍自动算参与，兼容旧数据）。</p>
 */
@Data
@TableName("project_member")
public class ProjectMember implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 项目ID（project.id） */
    private Long projectId;

    /** 成员用户ID（sys_user.id） */
    private Long userId;

    /** 加入时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
