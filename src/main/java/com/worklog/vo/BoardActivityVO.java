package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 看板右侧「最新动态」：最近提交的日报。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardActivityVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;

    private String realName;

    private String deptName;

    private LocalDate reportDate;

    private String projectName;

    private Integer hours;

    private LocalDateTime submittedAt;
}
