package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 看板右侧"按项目"排行的一行。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardProjectVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long projectId;

    private String projectName;

    private String leaderName;

    private String deptName;

    /** 区间内该项目收到的已提交日报数 */
    private int submitted;

    /** 参与人数（在该项目填报过的人） */
    private int participants;

    private int rate;
}
