package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 汇总表的一行（四个维度共用一套字段，按维度取用其中一部分）。
 *
 * <pre>
 * 人   ：name 姓名  / sub 部门   / extra 角色   / submitted 提交天数 / expected 应提交天数 / hours
 * 项目 ：name 项目名 / sub 负责人 / extra 部门   / submitted 日报数   / memberCount 参与人数 / hours
 * 部门 ：name 部门名 / submitted 提交人次 / expected 应提交人次 / memberCount 人数 / hours
 * 时间 ：name 日期  / sub 星期   / submitted 提交人数 / expected 应提交人数 / hours
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryRowVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** user / project / dept / date */
    private String dimension;

    /** 维度主键（人ID / 项目ID / 部门ID / yyyy-MM-dd） */
    private String id;

    /** 主列 */
    private String name;

    /** 次列 */
    private String sub;

    /** 第三列 */
    private String extra;

    /** 角色编码（人维度用，前端据此给"负责人"加徽章） */
    private String roleCode;

    private Integer submitted;

    private Integer expected;

    /** 提交率 0-100 */
    private Integer rate;

    /** 人数 / 参与人数 */
    private Integer memberCount;

    /** 涉及项目数（人维度） */
    private Integer projectCount;

    /** 总工时 */
    private BigDecimal hours;
}
