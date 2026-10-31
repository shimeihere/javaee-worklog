package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据看板聚合结果（一次请求返回首屏全部图表数据）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardOverviewVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** week / lastWeek / month */
    private String range;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 区间内已过去的工作日数 */
    private int workdays;

    private int memberCount;

    /** 应提交人次 = 人数 × 工作日 */
    private int expectedCount;

    /** 已提交人次 */
    private int submittedCount;

    /** 团队总提交率 0-100 */
    private int submitRate;

    @Builder.Default
    private List<BoardTrendVO> trend = new ArrayList<>();

    @Builder.Default
    private List<BoardDeptVO> deptStats = new ArrayList<>();

    @Builder.Default
    private List<BoardHeatVO> heatmap = new ArrayList<>();

    /** 项目排行（高管看"按项目"下钻用） */
    @Builder.Default
    private List<BoardProjectVO> projectStats = new ArrayList<>();

    @Builder.Default
    private List<BoardPendingVO> pendingUsers = new ArrayList<>();
}
