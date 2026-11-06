package com.worklog.service;

import com.worklog.vo.AttachmentVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 附件服务（M3/M4）。
 */
public interface AttachmentService {

    /**
     * 上传附件并写入 attachment 表。仅允许为"自己的"日报/周报追加附件。
     */
    AttachmentVO upload(MultipartFile file, Integer reportType, Long reportId);

    /**
     * 查询某条填报记录的附件列表（只有能看该单据的人才看得到）。
     */
    List<AttachmentVO> listByReport(Integer reportType, Long reportId);

    /**
     * 下载附件（校验登录态 + 单据数据范围后回写文件流）。
     */
    void download(Long attachmentId, HttpServletResponse response) throws IOException;

    /**
     * 删除附件（同时删除磁盘文件）。
     */
    void delete(Long attachmentId);
}
