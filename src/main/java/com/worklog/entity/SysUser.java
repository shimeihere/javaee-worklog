package com.worklog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户表 sys_user。
 *
 * <p>角色：EMPLOYEE 员工 / DEPT_LEADER 部门负责人 / ADMIN 管理员；状态：0 正常 / 1 禁用。</p>
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String username;

    /** 登录密码（BCrypt 密文，永不返回给前端） */
    @JsonIgnore
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 角色：EMPLOYEE / DEPT_LEADER / ADMIN */
    private String role;

    /** 所属部门 ID */
    private Long deptId;

    /** 状态：0 正常 / 1 禁用 */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
