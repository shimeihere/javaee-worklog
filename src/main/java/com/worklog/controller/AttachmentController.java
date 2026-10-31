package com.worklog.controller;

import com.worklog.common.Result;
import com.worklog.service.AttachmentService;
import com.worklog.vo.AttachmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 附件接口（M3/M4）。
 *
 * <p>限制 10MB（由 spring.servlet.multipart 与服务层双重校验），
 * 落盘目录由 worklog.upload.dir 指定，并通过 /api/upload/** 静态映射访问。</p>
 */
@RestController
@RequestMapping("/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    /**
     * 上传附件：POST /api/attachments/upload（multipart/form-data）
     *
     * @param file       文件
     * @param reportType 1 日报 / 2 周报
     * @param reportId   单据ID
     */
    @PostMapping("/upload")
    public Result<AttachmentVO> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam Integer reportType,
                                       @RequestParam Long reportId) {
        return Result.ok("附件上传成功", attachmentService.upload(file, reportType, reportId));
    }

    /**
     * 附件列表：GET /api/attachments?reportType=1&reportId=12
     */
    @GetMapping
    public Result<List<AttachmentVO>> list(@RequestParam Integer reportType,
                                           @RequestParam Long reportId) {
        return Result.ok(attachmentService.listByReport(reportType, reportId));
    }

    /**
     * 删除附件：DELETE /api/attachments/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        attachmentService.delete(id);
        return Result.ok("附件已删除", null);
    }
}
