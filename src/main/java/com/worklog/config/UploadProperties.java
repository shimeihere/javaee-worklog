package com.worklog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 附件上传配置（对应 application.yml 中 worklog.upload.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "worklog.upload")
public class UploadProperties {

    /** 附件落盘根目录 */
    private String dir = "./uploads";

    /** 单文件上限（字节），默认 10MB */
    private long maxSize = 10 * 1024 * 1024L;

    /** 允许的扩展名白名单（小写，不含点） */
    private List<String> allowedExtensions = new ArrayList<>(List.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "png", "jpg", "jpeg", "gif", "txt", "md", "zip"));
}
