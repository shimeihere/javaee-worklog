package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 热力图单日格子（近 4 周）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardHeatVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private LocalDate date;

    private int submitted;

    private int rate;

    private boolean workday;
}
