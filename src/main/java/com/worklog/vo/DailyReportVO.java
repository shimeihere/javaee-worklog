package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 日报视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyReportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    /** 填报人姓名 */
    private String userName;

    private Long projectId;

    private String projectName;

    private LocalDate reportDate;

    private String contentDone;

    private String contentPlan;

    private BigDecimal hours;

    /** 0 草稿 / 1 已提交 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Builder.Default
    private List<AttachmentVO> attachments = new ArrayList<>();
}
