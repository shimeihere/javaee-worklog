package com.worklog;

import com.worklog.entity.Attachment;
import com.worklog.entity.DailyReport;
import com.worklog.entity.Project;
import com.worklog.entity.SysDept;
import com.worklog.entity.SysMessage;
import com.worklog.entity.SysUser;
import com.worklog.entity.WeeklyReport;
import com.worklog.mapper.AttachmentMapper;
import com.worklog.mapper.DailyReportMapper;
import com.worklog.mapper.ProjectMapper;
import com.worklog.mapper.SysDeptMapper;
import com.worklog.mapper.SysMessageMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.mapper.WeeklyReportMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第一阶段冒烟测试：用真实 Mapper 对真实 MySQL 执行 selectList(null)，
 * 验证 7 个实体与 7 张表的字段映射（含下划线转驼峰、日期/金额类型）。
 *
 * <p>前置条件：已执行 schema.sql，且 application.yml 指向可用的 MySQL。</p>
 */
@SpringBootTest
@DisplayName("实体 <-> 数据表映射冒烟测试")
class WorklogSmokeTest {

    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysDeptMapper sysDeptMapper;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private DailyReportMapper dailyReportMapper;
    @Autowired
    private WeeklyReportMapper weeklyReportMapper;
    @Autowired
    private AttachmentMapper attachmentMapper;
    @Autowired
    private SysMessageMapper sysMessageMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("7 张表均可查询且种子数据存在")
    void allSevenTablesAreQueryable() {
        assertThat(sysUserMapper.selectList(null)).as("sys_user 应有数据").isNotEmpty();
        assertThat(sysDeptMapper.selectList(null)).as("sys_dept 应有数据").isNotEmpty();
        assertThat(projectMapper.selectList(null)).as("project 应有数据").isNotEmpty();
        assertThat(dailyReportMapper.selectList(null)).as("daily_report 应有数据").isNotEmpty();
        assertThat(weeklyReportMapper.selectList(null)).as("weekly_report 应有数据").isNotEmpty();
        assertThat(attachmentMapper.selectList(null)).as("attachment 应有数据").isNotEmpty();
        assertThat(sysMessageMapper.selectList(null)).as("sys_message 应有数据").isNotEmpty();
    }

    @Test
    @DisplayName("sys_user 字段映射正确（role/real_name/create_time/password）")
    void sysUserMappingIsCorrect() {
        List<SysUser> users = sysUserMapper.selectList(null);
        SysUser admin = users.stream()
                .filter(u -> "admin".equals(u.getUsername()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("找不到 admin 账号"));

        assertThat(admin.getId()).isEqualTo(1L);
        assertThat(admin.getRealName()).as("real_name -> realName").isEqualTo("张启明");
        assertThat(admin.getRole()).as("role -> role").isEqualTo("ADMIN");
        assertThat(admin.getDeptId()).as("dept_id -> deptId").isEqualTo(1L);
        assertThat(admin.getStatus()).as("status -> status").isEqualTo(0);
        assertThat(admin.getCreateTime()).as("create_time -> createTime").isNotNull();
        assertThat(admin.getUpdateTime()).as("update_time -> updateTime").isNotNull();
        assertThat(admin.getPassword()).as("密码应为 BCrypt 密文").startsWith("$2a$10$");

        // 种子密码 123456 能被 BCrypt 校验通过，证明初始化数据可用
        assertThat(passwordEncoder.matches("123456", admin.getPassword()))
                .as("admin 密码 123456 应校验通过")
                .isTrue();
        assertThat(passwordEncoder.matches("wrong-password", admin.getPassword()))
                .as("错误密码不应校验通过")
                .isFalse();
    }

    @Test
    @DisplayName("sys_dept 字段映射正确（两级部门树）")
    void sysDeptMappingIsCorrect() {
        List<SysDept> depts = sysDeptMapper.selectList(null);
        SysDept root = depts.stream().filter(d -> d.getId() == 1L).findFirst().orElseThrow();

        assertThat(root.getParentId()).as("parent_id -> parentId").isZero();
        assertThat(root.getDeptName()).as("dept_name -> deptName").isEqualTo("周览科技");
        assertThat(root.getSortOrder()).as("sort_order -> sortOrder").isNotNull();

        assertThat(depts).anyMatch(d -> "技术中心".equals(d.getDeptName()) && d.getParentId() == 1L);
    }

    @Test
    @DisplayName("project 字段映射正确（leader_id / description）")
    void projectMappingIsCorrect() {
        List<Project> projects = projectMapper.selectList(null);
        assertThat(projects).anyMatch(p -> "周览 Worklog 平台建设".equals(p.getProjectName()));

        Project worklog = projects.stream()
                .filter(p -> p.getProjectName().contains("周览"))
                .findFirst()
                .orElseThrow();
        assertThat(worklog.getLeaderId()).as("leader_id -> leaderId").isEqualTo(5L);
        assertThat(worklog.getDeptId()).as("dept_id -> deptId").isEqualTo(2L);
        assertThat(worklog.getDescription()).as("description -> description").isNotBlank();
        assertThat(worklog.getStatus()).as("status -> status").isNotNull();
    }

    @Test
    @DisplayName("daily_report 字段映射正确（LocalDate / BigDecimal / TEXT）")
    void dailyReportMappingIsCorrect() {
        List<DailyReport> reports = dailyReportMapper.selectList(null);

        DailyReport sample = reports.stream()
                .filter(r -> r.getHours() != null && r.getHours().compareTo(BigDecimal.valueOf(7.5)) == 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("找不到工时 7.5 的日报"));

        assertThat(sample.getReportDate()).as("report_date -> reportDate").isNotNull();
        assertThat(sample.getContentDone()).as("content_done -> contentDone").isNotBlank();
        assertThat(sample.getContentPlan()).as("content_plan -> contentPlan").isNotBlank();
        assertThat(sample.getUserId()).as("user_id -> userId").isNotNull();
        assertThat(sample.getProjectId()).as("project_id -> projectId").isNotNull();
        assertThat(reports).as("应同时存在草稿与已提交").anyMatch(r -> r.getStatus() == 1)
                .anyMatch(r -> r.getStatus() == 0);
    }

    @Test
    @DisplayName("weekly_report 字段映射正确（起止日期）")
    void weeklyReportMappingIsCorrect() {
        List<WeeklyReport> reports = weeklyReportMapper.selectList(null);
        WeeklyReport sample = reports.get(0);

        assertThat(sample.getStartDate()).as("start_date -> startDate").isNotNull();
        assertThat(sample.getEndDate()).as("end_date -> endDate").isNotNull();
        assertThat(sample.getEndDate()).isAfter(sample.getStartDate());
        assertThat(sample.getSummary()).as("summary -> summary").isNotBlank();
        assertThat(sample.getPlan()).as("plan -> plan").isNotBlank();
    }

    @Test
    @DisplayName("attachment 字段映射正确（多态关联字段）")
    void attachmentMappingIsCorrect() {
        List<Attachment> attachments = attachmentMapper.selectList(null);

        assertThat(attachments).anyMatch(a -> a.getReportType() == Attachment.REPORT_TYPE_DAILY);
        assertThat(attachments).anyMatch(a -> a.getReportType() == Attachment.REPORT_TYPE_WEEKLY);

        Attachment sample = attachments.get(0);
        assertThat(sample.getReportId()).as("report_id -> reportId").isNotNull();
        assertThat(sample.getFileName()).as("file_name -> fileName").isNotBlank();
        assertThat(sample.getFileUrl()).as("file_url -> fileUrl").isNotBlank();
        assertThat(sample.getFileSize()).as("file_size -> fileSize").isPositive();
        assertThat(sample.getCreateTime()).as("create_time -> createTime").isNotNull();
    }

    @Test
    @DisplayName("sys_message 字段映射正确（is_read / msg_type）")
    void sysMessageMappingIsCorrect() {
        List<SysMessage> messages = sysMessageMapper.selectList(null);

        assertThat(messages).anyMatch(m -> m.getIsRead() == 0);
        assertThat(messages).anyMatch(m -> m.getIsRead() == 1);
        assertThat(messages).anyMatch(m -> m.getMsgType() == SysMessage.TYPE_REMIND);

        SysMessage sample = messages.get(0);
        assertThat(sample.getUserId()).as("user_id -> userId").isNotNull();
        assertThat(sample.getTitle()).as("title -> title").isNotBlank();
        assertThat(sample.getContent()).as("content -> content").isNotBlank();
        assertThat(sample.getCreateTime()).as("create_time -> createTime").isNotNull();
    }
}
