package com.worklog.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 填报记录分页查询条件（汇总与管理端使用）。
 *
 * <p>数据范围由 Service 层统一追加：员工仅自己、负责人本部门、管理员全部。</p>
 */
@Data
public class ReportQuery {

    private long page = 1;

    private long size = 10;

    /** 指定填报人 */
    private Long userId;

    /** 状态：0 草稿 / 1 已提交 */
    private Integer status;

    /** 起始日期（含） */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 结束日期（含） */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 关键字：按完成内容/总结模糊匹配 */
    private String keyword;
}
