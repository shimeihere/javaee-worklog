

# Worklog - 企业日报周报与项目看板系统

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg) ![Vue.js](https://img.shields.io/badge/Vue-3.x-42b883.svg) ![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg) ![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)

基于前后端分离架构的企业团队效能管理系统，实现从底层员工填报、主管审查到高管数据大盘的完整业务闭环。

## 📖 项目简介

本系统为 JavaEE 期末大作业。系统名为 **周览 (Worklog)**，后端基于 Spring Boot 3 提供 RESTful API，前端采用 Vue 3 + Element Plus 构建单页面应用（SPA）。系统内置完整的 RBAC 权限控制体系与数据隔离（DataScope）机制，并结合 ECharts 提供多维度的数据可视化看板。

## 🛠️ 技术栈

### 后端
*   **核心框架**：Java 17 / Spring Boot 3
*   **持久层**：MyBatis-Plus / MySQL 8.0
*   **安全与鉴权**：Spring Security Crypto / JJWT (JSON Web Token)
*   **工具库**：Lombok / EasyExcel (数据导出) / Apache Maven

### 前端
*   **核心框架**：Vue 3 (Composition API) / Vite
*   **UI 组件库**：Element Plus
*   **状态管理**：Pinia
*   **数据可视化**：ECharts 5
*   **网络请求**：Axios

## ✨ 核心特性

### 1. 基于 RBAC 的四级数据权限隔离
系统遵循企业最小特权原则，在 Service 层通过 `DataScope` 拦截器实现严格的越级访问控制：
*   **高管 (EXECUTIVE)**：全局视角。查看全公司大盘数据与部门对比，可下钻查阅明细，但不直接参与催报业务。
*   **系统管理员 (ADMIN)**：负责账号、部门、项目等基础数据维护。无业务数据查阅权限，防范内部数据泄露。
*   **部门负责人 (DEPT_LEADER)**：仅限本部门数据。可查看下属日报/周报明细，并拥有发送催报提醒的权限。
*   **员工 (EMPLOYEE)**：仅可进行个人的日报/周报填报，以及查看个人历史记录。

### 2. 填报与审批业务闭环
*   **日历视图**：左侧提供本月日历状态点阵，右侧提供动态表单，支持草稿、提交、撤回等状态流转。
*   **防呆机制**：日报与周报的「所属项目」字段设为选填，解决未分配项目员工的填报阻塞问题。
*   **催报防重**：待催报名单自动剔除已提交人员与负责人本人，避免重复骚扰。

### 3. 多维度数据看板与聚合中心
*   **汇总中心**：支持按人、项目、部门、时间四个维度进行数据聚合查询，并接入 EasyExcel 支持一键导出报表。
*   **可视化看板**：基于 ECharts 绘制，包含核心指标环形图、提交趋势折线图与部门对比柱状图。
*   **项目周期追踪**：采用自定义的横向进度条，依据计划完成日与当前日期动态计算展示「第 X / Y 天 · 剩 N 天」，超期项目自动标红。

### 4. 前端 UI 与交互规范
*   **设计系统**：全局使用 `#F7F6F3` 暖调底色，卡片采用 `16px` 圆角与极细内阴影。
*   **微交互**：统一使用 `cubic-bezier(0.34, 1.56, 0.64, 1)` 贝塞尔曲线实现面板与按钮的弹簧回弹效果。
*   **字体排印**：数字强制使用 `JetBrains Mono` 等宽字体，保证数据对齐；字体文件已做本地化自托管，无外部网络依赖。

## 🧩 数据库模型

系统包含 9 张核心数据表：

| 表名 | 描述 |
| --- | --- |
| `sys_dept` | 部门表（两级组织架构） |
| `sys_user` | 用户表（包含角色、部门归属等） |
| `project` | 项目表（关联负责人、计划完成日） |
| `project_member` | 项目成员关系映射表 |
| `daily_report` | 日报表 |
| `weekly_report` | 周报表 |
| `attachment` | 附件表（通过类型与ID与报表多态关联） |
| `sys_message` | 站内信表（系统通知与催报） |
| `sys_audit_log` | 审计日志表（记录关键管理操作） |

## 🚀 快速开始

### 1. 环境准备
请确保本地已安装以下环境：
*   JDK 17 或更高版本
*   MySQL 8.0 或更高版本
*   Node.js (推荐 v18+)

### 2. 数据库初始化
在 MySQL 中创建名为 `worklog` 的数据库，并执行根目录下的 `schema.sql` 脚本：
```bash
mysql -uroot -p<your_password> < schema.sql
```
*(注：脚本会自动创建 9 张表并插入演示数据，日期基于 `CURDATE()` 动态生成，确保任何时候导入都有本周数据。)*

### 3. 配置文件修改
将 `src/main/resources/application.example.yml` 复制并重命名为 `application.yml`，修改对应的数据库连接信息：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/worklog?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password # 修改为你的密码
```

### 4. 启动项目
*   **后端启动**：在 IDEA 中运行 `WorklogApplication.java` 主类（默认端口 8080）。
*   **前端启动**：进入 `frontend` 目录，执行 `npm install` 安装依赖，然后执行 `npm run dev` 启动开发服务器。
*(注意：本地开发时，前端请求会通过 Vite 代理转发至 8080 端口)*

### 5. 默认账号 (密码均为 123456)
*   `admin`：系统管理员
*   `boss`：高管
*   `liyue`：部门负责人
*   `wangfan`：员工

## 📁 目录结构

```text
├── frontend/           # Vue 3 前端工程
├── src/                # Spring Boot 后端源码
│   ├── main/           # 核心业务代码与配置
│   └── test/           # 单元测试代码
├── .gitignore          # Git 忽略文件配置
├── pom.xml             # Maven 依赖管理
├── schema.sql          # 数据库初始化脚本
└── README.md           # 项目说明文档
```

