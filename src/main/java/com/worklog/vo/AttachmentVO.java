package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 附件信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 1 日报 / 2 周报 */
    private Integer reportType;

    private Long reportId;

    /** 原始文件名 */
    private String fileName;

    /** 相对访问路径，如 /upload/2026/10/xxx.pdf，前端拼接 API 前缀后即可下载 */
    private String fileUrl;

    /** 字节数 */
    private Long fileSize;

    /** 扩展名 */
    private String fileType;

    private LocalDateTime createTime;
}
