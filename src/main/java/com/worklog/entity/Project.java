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
 * 项目表 project。
 *
 * <p>数据权限核心字段：leaderId（项目负责人）。项目负责人可见并管理其名下项目；
 * 部门负责人可见本部门内全部项目；管理员具备全局数据权限。</p>
 */
@Data
@TableName("project")
public class Project implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 项目名称 */
    private String projectName;

    /** 项目负责人（sys_user.id） */
    private Long leaderId;

    /** 所属部门 ID */
    private Long deptId;

    /** 项目描述 */
    private String description;

    /** 状态：0 进行中 / 1 已归档 */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
