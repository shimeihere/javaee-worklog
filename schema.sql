-- =====================================================================
--  周览 Worklog · 企业日报周报与项目看板系统
--  数据库初始化脚本 schema.sql  (MySQL 8.0+)
--  说明：
--    1. 本脚本可重复执行（先删表后建表），包含 7 张核心表与初始化测试数据；
--    2. 统一使用 utf8mb4 / InnoDB，所有业务表带 create_time、update_time；
--    3. 初始账号密码统一为 123456（数据库内存放 BCrypt 密文）。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `worklog`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `worklog`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `sys_user`;
DROP TABLE IF EXISTS `sys_dept`;
DROP TABLE IF EXISTS `project`;
DROP TABLE IF EXISTS `daily_report`;
DROP TABLE IF EXISTS `weekly_report`;
DROP TABLE IF EXISTS `attachment`;
DROP TABLE IF EXISTS `sys_message`;

-- ---------------------------------------------------------------------
-- 1. 部门表 sys_dept（两级部门树：parent_id = 0 表示顶级部门）
-- ---------------------------------------------------------------------
CREATE TABLE `sys_dept`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '上级部门ID，0 表示顶级部门',
    `dept_name`   VARCHAR(64) NOT NULL COMMENT '部门名称',
    `sort_order`  INT         NOT NULL DEFAULT 0 COMMENT '同级排序号，升序',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='部门表';

-- ---------------------------------------------------------------------
-- 2. 系统用户表 sys_user
--    role: EMPLOYEE 员工 / DEPT_LEADER 部门负责人 / ADMIN 管理员
--    status: 0 正常 / 1 禁用（禁用账号登录时直接拦截）
-- ---------------------------------------------------------------------
CREATE TABLE `sys_user`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(50)  NOT NULL COMMENT '登录账号',
    `password`    VARCHAR(100) NOT NULL COMMENT '登录密码（BCrypt 密文）',
    `real_name`   VARCHAR(50)  NOT NULL COMMENT '真实姓名',
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'EMPLOYEE' COMMENT '角色：EMPLOYEE / DEPT_LEADER / ADMIN',
    `dept_id`     BIGINT                DEFAULT NULL COMMENT '所属部门ID',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 正常 / 1 禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_dept_id` (`dept_id`),
    KEY `idx_role` (`role`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='系统用户表';

-- ---------------------------------------------------------------------
-- 3. 项目表 project
--    数据权限核心字段：leader_id（项目负责人）
-- ---------------------------------------------------------------------
CREATE TABLE `project`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_name` VARCHAR(100) NOT NULL COMMENT '项目名称',
    `leader_id`    BIGINT       NOT NULL COMMENT '项目负责人（sys_user.id）',
    `dept_id`      BIGINT       NOT NULL COMMENT '所属部门ID',
    `description`  VARCHAR(500)          DEFAULT NULL COMMENT '项目描述',
    `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 进行中 / 1 已归档',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_leader_id` (`leader_id`),
    KEY `idx_dept_id` (`dept_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='项目表';

-- ---------------------------------------------------------------------
-- 4. 日报表 daily_report（一人一天一份，故 user_id + report_date 唯一）
-- ---------------------------------------------------------------------
CREATE TABLE `daily_report`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`      BIGINT       NOT NULL COMMENT '填报人ID',
    `project_id`   BIGINT                DEFAULT NULL COMMENT '关联项目ID',
    `report_date`  DATE         NOT NULL COMMENT '上报日期',
    `content_done` TEXT COMMENT '今日完成内容',
    `content_plan` TEXT COMMENT '明日计划',
    `hours`        DECIMAL(4, 1)         DEFAULT 0.0 COMMENT '工时（小时）',
    `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 草稿 / 1 已提交',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `report_date`),
    KEY `idx_project_id` (`project_id`),
    KEY `idx_report_date` (`report_date`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='日报表';

-- ---------------------------------------------------------------------
-- 5. 周报表 weekly_report（一人一周一份，故 user_id + start_date 唯一）
-- ---------------------------------------------------------------------
CREATE TABLE `weekly_report`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT   NOT NULL COMMENT '填报人ID',
    `project_id`  BIGINT            DEFAULT NULL COMMENT '关联项目ID',
    `start_date`  DATE     NOT NULL COMMENT '本周起始日期（周一）',
    `end_date`    DATE     NOT NULL COMMENT '本周结束日期（周日）',
    `summary`     TEXT COMMENT '本周总结',
    `plan`        TEXT COMMENT '下周计划',
    `status`      TINYINT  NOT NULL DEFAULT 0 COMMENT '状态：0 草稿 / 1 已提交',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_start` (`user_id`, `start_date`),
    KEY `idx_project_id` (`project_id`),
    KEY `idx_week_range` (`start_date`, `end_date`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='周报表';

-- ---------------------------------------------------------------------
-- 6. 附件表 attachment（多态关联：report_type + report_id）
-- ---------------------------------------------------------------------
CREATE TABLE `attachment`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `report_type` TINYINT      NOT NULL COMMENT '关联类型：1 日报 / 2 周报',
    `report_id`   BIGINT       NOT NULL COMMENT '关联记录ID（daily_report.id 或 weekly_report.id）',
    `file_name`   VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_url`    VARCHAR(500) NOT NULL COMMENT '文件存储路径/访问地址',
    `file_size`   BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节），上限 10MB',
    `file_type`   VARCHAR(50)           DEFAULT NULL COMMENT '文件扩展名，如 pdf / docx / png',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_report` (`report_type`, `report_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='附件表';

-- ---------------------------------------------------------------------
-- 7. 站内信表 sys_message
-- ---------------------------------------------------------------------
CREATE TABLE `sys_message`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT      NOT NULL COMMENT '接收人ID',
    `title`       VARCHAR(100) NOT NULL COMMENT '消息标题',
    `content`     VARCHAR(500)         DEFAULT NULL COMMENT '消息内容',
    `msg_type`    TINYINT     NOT NULL DEFAULT 1 COMMENT '消息类型：1 催报提醒 / 2 系统通知',
    `is_read`     TINYINT     NOT NULL DEFAULT 0 COMMENT '是否已读：0 未读 / 1 已读',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_read` (`user_id`, `is_read`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='站内信表';

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
--  初始化测试数据
-- =====================================================================

-- 部门：总部 + 3 个二级部门（两级部门树）
INSERT INTO `sys_dept` (`id`, `parent_id`, `dept_name`, `sort_order`)
VALUES (1, 0, '周览科技', 1),
       (2, 1, '技术中心', 1),
       (3, 1, '产品设计部', 2),
       (4, 1, '市场运营部', 3);

-- 用户：密码统一 123456（BCrypt）；zhouxin 为禁用账号，用于验证登录拦截
-- 角色：EXECUTIVE 高管 / ADMIN 系统管理员 / DEPT_LEADER 部门负责人 / EMPLOYEE 员工
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `role`, `dept_id`, `status`)
VALUES (1, 'admin', '$2a$10$gGL8go0CloXGDBuPQqwAuOKFTKuNEiB5//7A/oLYJmWZymFnFsqXm', '张启明', 'ADMIN', 1, 0),
       (2, 'liyue', '$2a$10$gGL8go0CloXGDBuPQqwAuOKFTKuNEiB5//7A/oLYJmWZymFnFsqXm', '李悦', 'DEPT_LEADER', 2, 0),
       (3, 'chenxi', '$2a$10$gGL8go0CloXGDBuPQqwAuOKFTKuNEiB5//7A/oLYJmWZymFnFsqXm', '陈曦', 'DEPT_LEADER', 3, 0),
       (4, 'wangfan', '$2a$10$gGL8go0CloXGDBuPQqwAuOKFTKuNEiB5//7A/oLYJmWZymFnFsqXm', '王帆', 'EMPLOYEE', 2, 0),
       (5, 'zhaolin', '$2a$10$gGL8go0CloXGDBuPQqwAuOKFTKuNEiB5//7A/oLYJmWZymFnFsqXm', '赵琳', 'EMPLOYEE', 2, 0),
       (6, 'sunqi', '$2a$10$gGL8go0CloXGDBuPQqwAuOKFTKuNEiB5//7A/oLYJmWZymFnFsqXm', '孙琪', 'EMPLOYEE', 3, 0),
       (7, 'zhouyang', '$2a$10$gGL8go0CloXGDBuPQqwAuOKFTKuNEiB5//7A/oLYJmWZymFnFsqXm', '周洋', 'EMPLOYEE', 3, 0),
       (8, 'zhouxin', '$2a$10$gGL8go0CloXGDBuPQqwAuOKFTKuNEiB5//7A/oLYJmWZymFnFsqXm', '周欣', 'EMPLOYEE', 4, 1),
       (9, 'boss', '$2a$10$gGL8go0CloXGDBuPQqwAuOKFTKuNEiB5//7A/oLYJmWZymFnFsqXm', '周明远', 'EXECUTIVE', 1, 0);

-- 项目负责人：赵琳负责 Worklog、李悦负责数据中台、陈曦负责移动端与品牌视觉
INSERT INTO `project` (`id`, `project_name`, `leader_id`, `dept_id`, `description`, `status`)
VALUES (1, '周览 Worklog 平台建设', 5, 2, '企业日报周报与项目看板系统，MVP 阶段', 0),
       (2, '客户数据中台重构', 2, 2, '统一客户主数据，替换旧版 ETL 链路', 0),
       (3, '品牌视觉升级 2.0', 3, 3, '品牌色彩、字体与官网视觉体系升级', 0),
       (4, '移动端体验优化', 3, 3, '移动端首屏性能与交互一致性优化', 0);

-- 日报：近 5 个工作日数据，便于看板/汇总中心演示
INSERT INTO `daily_report` (`user_id`, `project_id`, `report_date`, `content_done`, `content_plan`, `hours`, `status`)
VALUES (4, 1, DATE_SUB(CURDATE(), INTERVAL 4 DAY), '完成日报表单左侧日历组件联调；补充提交状态枚举。', '接入草稿自动保存，联调附件上传限制。', 8.0, 1),
       (4, 1, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '完成草稿保存接口与前端表单校验。', '联调附件上传 10MB 限制与预览。', 7.5, 1),
       (4, 1, DATE_SUB(CURDATE(), INTERVAL 2 DAY), '附件上传联调完成，修复文件名中文乱码。', '开始周报模块表单与提交逻辑。', 8.0, 1),
       (5, 2, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '梳理旧版 ETL 任务清单与依赖关系。', '输出中台重构技术方案初稿。', 8.0, 1),
       (5, 2, DATE_SUB(CURDATE(), INTERVAL 2 DAY), '完成客户主数据模型设计，评审通过。', '编写数据迁移脚本。', 6.5, 1),
       (5, 2, DATE_SUB(CURDATE(), INTERVAL 1 DAY), '数据迁移脚本 60% 完成，补充脏数据清洗规则。', '完成迁移脚本并做一次全量演练。', 7.0, 1),
       (6, 3, DATE_SUB(CURDATE(), INTERVAL 2 DAY), '完成品牌辅助色延展，输出 3 套配色方案。', '与产品确认主视觉方向。', 6.0, 1),
       (6, 3, DATE_SUB(CURDATE(), INTERVAL 1 DAY), '确定配色方案 B，输出图标规范初稿。', '补充插画风格规范。', 7.0, 1),
       (7, 3, DATE_SUB(CURDATE(), INTERVAL 1 DAY), '完成官网首页视觉改版稿 2 版。', '根据反馈调整排版节奏。', 5.5, 0),
       (4, 1, CURDATE(), '开始数据看板 Hero 环图开发。', '完成环图与 KPI 卡片联动。', 3.0, 0);

-- 周报：本周 & 上周各一条
INSERT INTO `weekly_report` (`user_id`, `project_id`, `start_date`, `end_date`, `summary`, `plan`, `status`)
VALUES (4, 1, DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY),
        DATE_ADD(DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY), INTERVAL 6 DAY),
        '本周完成日报模块表单、草稿保存与附件上传联调，修复 5 个缺陷。',
        '下周推进周报模块与汇总中心四维筛选。', 1),
       (5, 2, DATE_SUB(CURDATE(), INTERVAL (WEEKDAY(CURDATE()) + 7) DAY),
        DATE_ADD(DATE_SUB(CURDATE(), INTERVAL (WEEKDAY(CURDATE()) + 7) DAY), INTERVAL 6 DAY),
        '完成客户主数据模型设计与评审，迁移脚本完成 60%。',
        '完成全量迁移演练并输出回滚方案。', 1),
       (6, 3, DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY),
        DATE_ADD(DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY), INTERVAL 6 DAY),
        '品牌视觉升级完成辅助色延展与图标规范初稿。',
        '输出官网首页高保真稿并组织评审。', 1);

-- 附件：挂在已提交的日报/周报上
INSERT INTO `attachment` (`report_type`, `report_id`, `file_name`, `file_url`, `file_size`, `file_type`)
VALUES (1, 1, '日报表单联调截图.png', '/upload/2026/01/daily-form-debug.png', 486233, 'png'),
       (1, 3, '附件上传联调记录.pdf', '/upload/2026/01/upload-test-report.pdf', 1048576, 'pdf'),
       (2, 1, '周报-王帆-第37周.docx', '/upload/2026/01/weekly-wangfan-w37.docx', 214233, 'docx');

-- 站内信：催报提醒 + 系统通知，覆盖未读/已读
INSERT INTO `sys_message` (`user_id`, `title`, `content`, `msg_type`, `is_read`)
VALUES (5, '今日日报待提交', '你今天的日报还未提交，请于 18:30 前完成填报。', 1, 0),
       (7, '今日日报待提交', '你今天的日报还未提交，请于 18:30 前完成填报。', 1, 0),
       (4, '周报已汇总', '你的第 37 周周报已被部门负责人查看。', 2, 1),
       (6, '系统升级通知', '系统将于本周日 22:00 - 23:00 进行维护升级。', 2, 0);
