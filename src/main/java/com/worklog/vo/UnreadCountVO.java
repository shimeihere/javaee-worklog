package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 未读站内信数量（顶栏角标用）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long count;
}
