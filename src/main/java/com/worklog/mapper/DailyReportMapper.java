package com.worklog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.worklog.entity.DailyReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日报 Mapper。
 */
@Mapper
public interface DailyReportMapper extends BaseMapper<DailyReport> {
}
