package com.worklog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 附件表 attachment（多态关联：report_type + report_id）。
 */
@Data
@TableName("attachment")
public class Attachment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联类型常量：日报 */
    public static final int REPORT_TYPE_DAILY = 1;

    /** 关联类型常量：周报 */
    public static final int REPORT_TYPE_WEEKLY = 2;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联类型：1 日报 / 2 周报 */
    private Integer reportType;

    /** 关联记录 ID */
    private Long reportId;

    /** 原始文件名 */
    private String fileName;

    /** 文件存储路径 / 访问地址 */
    private String fileUrl;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件扩展名，如 pdf / docx / png */
    private String fileType;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
