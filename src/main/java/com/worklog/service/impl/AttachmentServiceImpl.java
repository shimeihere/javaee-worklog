package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.config.UploadProperties;
import com.worklog.constant.ReportConstant;
import com.worklog.entity.Attachment;
import com.worklog.entity.DailyReport;
import com.worklog.entity.WeeklyReport;
import com.worklog.mapper.AttachmentMapper;
import com.worklog.mapper.DailyReportMapper;
import com.worklog.mapper.WeeklyReportMapper;
import com.worklog.security.UserContext;
import com.worklog.service.AttachmentService;
import com.worklog.vo.AttachmentVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 附件服务实现：本地磁盘存储 + attachment 表登记。
 *
 * <p>目录结构：{@code {worklog.upload.dir}/2026/10/{uuid}.pdf}，
 * 数据库 file_url 存相对路径 {@code /upload/2026/10/{uuid}.pdf}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private final AttachmentMapper attachmentMapper;
    private final DailyReportMapper dailyReportMapper;
    private final WeeklyReportMapper weeklyReportMapper;
    private final UploadProperties uploadProperties;

    @PostConstruct
    public void init() {
        try {
            Path root = uploadRoot();
            Files.createDirectories(root);
            log.info("附件根目录：{}（单文件上限 {} 字节）", root, uploadProperties.getMaxSize());
        } catch (IOException e) {
            throw new IllegalStateException("无法初始化附件目录：" + uploadProperties.getDir(), e);
        }
    }

    private Path uploadRoot() {
        return Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttachmentVO upload(MultipartFile file, Integer reportType, Long reportId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择要上传的文件");
        }
        if (file.getSize() > uploadProperties.getMaxSize()) {
            throw new BusinessException(ResultCode.UPLOAD_TOO_LARGE);
        }

        String originalName = sanitizeFileName(file.getOriginalFilename());
        String ext = extensionOf(originalName);
        if (!uploadProperties.getAllowedExtensions().contains(ext)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "不支持的文件类型：" + (ext.isEmpty() ? "无扩展名" : "." + ext));
        }

        // 只有单据归属人可以加附件（管理员也不例外，避免越权篡改别人的日报）
        requireOwnReport(reportType, reportId);

        LocalDate today = LocalDate.now();
        String folder = today.getYear() + "/" + String.format("%02d", today.getMonthValue());
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            Path targetDir = uploadRoot().resolve(folder);
            Files.createDirectories(targetDir);
            file.transferTo(targetDir.resolve(storedName));
        } catch (IOException e) {
            log.error("附件保存失败：{}", originalName, e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "附件保存失败，请稍后重试");
        }

        Attachment attachment = new Attachment();
        attachment.setReportType(reportType);
        attachment.setReportId(reportId);
        attachment.setFileName(originalName);
        attachment.setFileUrl("/upload/" + folder + "/" + storedName);
        attachment.setFileSize(file.getSize());
        attachment.setFileType(ext);
        attachmentMapper.insert(attachment);

        log.info("附件上传成功：reportType={}, reportId={}, file={}, size={}B",
                reportType, reportId, originalName, file.getSize());
        return toVO(attachment);
    }

    @Override
    public List<AttachmentVO> listByReport(Integer reportType, Long reportId) {
        if (reportType == null || reportId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "缺少 reportType 或 reportId");
        }
        return attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                        .eq(Attachment::getReportType, reportType)
                        .eq(Attachment::getReportId, reportId)
                        .orderByAsc(Attachment::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long attachmentId) {
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "附件不存在");
        }
        requireOwnReport(attachment.getReportType(), attachment.getReportId());

        attachmentMapper.deleteById(attachmentId);

        // 物理文件删除失败不影响业务，只记日志
        try {
            String relative = attachment.getFileUrl().replaceFirst("^/upload/", "");
            Files.deleteIfExists(uploadRoot().resolve(relative));
        } catch (IOException e) {
            log.warn("附件文件删除失败：{}", attachment.getFileUrl(), e);
        }
        log.info("附件已删除：id={}, file={}", attachmentId, attachment.getFileName());
    }

    /** 校验单据存在且属于当前登录人 */
    private void requireOwnReport(Integer reportType, Long reportId) {
        Long currentUserId = UserContext.requireUserId();
        if (Objects.equals(reportType, ReportConstant.REPORT_TYPE_DAILY)) {
            DailyReport report = dailyReportMapper.selectById(reportId);
            if (report == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "日报不存在");
            }
            if (!Objects.equals(report.getUserId(), currentUserId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能为自己的日报添加附件");
            }
        } else if (Objects.equals(reportType, ReportConstant.REPORT_TYPE_WEEKLY)) {
            WeeklyReport report = weeklyReportMapper.selectById(reportId);
            if (report == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "周报不存在");
            }
            if (!Objects.equals(report.getUserId(), currentUserId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能为自己的周报添加附件");
            }
        } else {
            throw new BusinessException(ResultCode.BAD_REQUEST, "reportType 只能是 1（日报）或 2（周报）");
        }
    }

    /** 去掉路径成分，防止 ../ 穿越 */
    private String sanitizeFileName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "unnamed";
        }
        String name = originalFilename.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private AttachmentVO toVO(Attachment attachment) {
        return AttachmentVO.builder()
                .id(attachment.getId())
                .reportType(attachment.getReportType())
                .reportId(attachment.getReportId())
                .fileName(attachment.getFileName())
                .fileUrl(attachment.getFileUrl())
                .fileSize(attachment.getFileSize())
                .fileType(attachment.getFileType())
                .createTime(attachment.getCreateTime())
                .build();
    }
}
