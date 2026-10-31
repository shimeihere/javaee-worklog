package com.worklog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日报表 daily_report（一人一天一份，user_id + report_date 唯一）。
 *
 * <p>状态流转：0 草稿 → 1 已提交，支持撤回（1 → 0）。</p>
 */
@Data
@TableName("daily_report")
public class DailyReport implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 填报人 ID */
    private Long userId;

    /** 关联项目 ID */
    private Long projectId;

    /** 上报日期 */
    private LocalDate reportDate;

    /** 今日完成内容 */
    private String contentDone;

    /** 明日计划 */
    private String contentPlan;

    /** 工时（小时） */
    private BigDecimal hours;

    /** 状态：0 草稿 / 1 已提交 */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
