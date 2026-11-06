-- =====================================================================
--  已有数据库升级脚本（不清数据）
--  适用：之前已经导入过旧版 schema.sql 的库，只想补「项目成员」能力。
--  全新安装直接用 schema.sql 即可，不需要执行本文件。
-- =====================================================================

USE `worklog`;

CREATE TABLE IF NOT EXISTS `project_member`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id`  BIGINT   NOT NULL COMMENT '项目ID（project.id）',
    `user_id`     BIGINT   NOT NULL COMMENT '成员用户ID（sys_user.id）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_user` (`project_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='项目成员表';

-- 1) 项目负责人自动算成员
INSERT IGNORE INTO `project_member` (`project_id`, `user_id`)
SELECT `id`, `leader_id` FROM `project` WHERE `leader_id` IS NOT NULL;

-- 2) 历史日报/周报里出现过的人，自动补成成员（保持"参与过"语义不变）
INSERT IGNORE INTO `project_member` (`project_id`, `user_id`)
SELECT `project_id`, `user_id` FROM `daily_report` WHERE `project_id` IS NOT NULL;

INSERT IGNORE INTO `project_member` (`project_id`, `user_id`)
SELECT `project_id`, `user_id` FROM `weekly_report` WHERE `project_id` IS NOT NULL;

-- =====================================================================
--  2026-11 第二轮升级：项目周期（计划完成日）+ 操作审计
--  同样不清数据，可重复执行。
-- =====================================================================

-- 1) 项目表补"计划完成日期"（可空；空时只显示已进行天数）
--    MySQL 不支持 ADD COLUMN IF NOT EXISTS，这里用 information_schema 判断，保证可重复执行
SET @has_col := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'project'
                   AND column_name = 'planned_end_date');
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE `project` ADD COLUMN `planned_end_date` DATE DEFAULT NULL COMMENT ''计划完成日期（可空，用来看项目周期进度）'' AFTER `description`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 给还没设计划完成日的项目补一个演示值（按今天 + 60 天）；
--    上线时如果不想动老项目，把这条 UPDATE 注释掉即可
UPDATE `project` SET `planned_end_date` = DATE_ADD(CURDATE(), INTERVAL 60 DAY)
WHERE `planned_end_date` IS NULL;

-- 3) 操作审计表
CREATE TABLE IF NOT EXISTS `sys_audit_log`
(
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `operator_id`   BIGINT               DEFAULT NULL,
    `operator_name` VARCHAR(50)          DEFAULT NULL,
    `action`        VARCHAR(40) NOT NULL,
    `target_type`   VARCHAR(20)          DEFAULT NULL,
    `target_id`     BIGINT               DEFAULT NULL,
    `detail`        VARCHAR(500)         DEFAULT NULL,
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_action` (`action`),
    KEY `idx_operator` (`operator_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='操作审计日志';
