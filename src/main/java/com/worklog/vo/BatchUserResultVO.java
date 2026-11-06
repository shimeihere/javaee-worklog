package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量新增用户结果。
 *
 * <p>逐行返回失败原因，管理员可直接照着改那几行重新提交。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchUserResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 提交总行数 */
    private int total;

    /** 成功条数 */
    private int createdCount;

    /** 失败条数 */
    private int failedCount;

    /** 成功创建的账号 */
    @Builder.Default
    private List<String> createdUsernames = new ArrayList<>();

    /** 失败明细，格式：第 N 行「账号」：原因 */
    @Builder.Default
    private List<String> failures = new ArrayList<>();
}
