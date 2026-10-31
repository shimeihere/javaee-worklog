package com.worklog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 周报表 weekly_report（一人一周一份，user_id + start_date 唯一）。
 */
@Data
@TableName("weekly_report")
public class WeeklyReport implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 填报人 ID */
    private Long userId;

    /** 关联项目 ID */
    private Long projectId;

    /** 本周起始日期（周一） */
    private LocalDate startDate;

    /** 本周结束日期（周日） */
    private LocalDate endDate;

    /** 本周总结 */
    private String summary;

    /** 下周计划 */
    private String plan;

    /** 状态：0 草稿 / 1 已提交 */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
