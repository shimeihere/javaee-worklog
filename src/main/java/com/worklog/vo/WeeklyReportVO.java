package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 周报视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyReportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private String userName;

    private Long projectId;

    private String projectName;

    /** 本周一 */
    private LocalDate startDate;

    /** 本周日 */
    private LocalDate endDate;

    private String summary;

    private String plan;

    /** 0 草稿 / 1 已提交 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Builder.Default
    private List<AttachmentVO> attachments = new ArrayList<>();
}
