# 企业日报周报与项目看板系统 (Worklog)

本项目为 JavaEE 期末大作业。系统名为 **周览 (Worklog)**，旨在解决企业内部日报/周报填报繁琐、数据汇总困难、项目进度不透明等痛点。系统采用前后端分离架构，实现了从“员工填报”到“主管审查”，再到“高管大盘”的完整业务闭环。

## 🛠️ 核心技术栈

*   **后端**：Java 17 / Spring Boot 3 / MyBatis-Plus / JWT (鉴权) / EasyExcel (数据导出)
*   **前端**：Vue 3 / Vite / Element Plus / Pinia / ECharts / Axios
*   **数据库**：MySQL 8.0

## 👥 四层角色与权限控制 (RBAC)

系统严格遵循企业管理学中的“最小特权原则”与“防越级审查”，实现了精准的数据范围隔离（DataScope）：

1.  **高管 (EXECUTIVE)**：全局视角。可查看全公司数据大盘与部门对比，拥有最高业务审查权限（下钻看明细），但不直接参与催报。
2.  **系统管理员 (ADMIN)**：系统基建维护者。负责账号、部门、项目管理，**无业务数据查看权限**（防内部泄密）。
3.  **部门负责人 (DEPT_LEADER)**：中层管理。只能查看本部门员工的大盘数据、日报明细，并拥有催报权限。
4.  **员工 (EMPLOYEE)**：执行层。只能进行自己的日报/周报填报与查看。

## 🚀 部署与运行指南

### 1. 环境准备
*   确保已安装 **JDK 17+**、**MySQL 8.0+**、**Node.js**。
*   在你的 MySQL 中创建一个名为 `worklog_db` 的数据库。

### 2. 数据库初始化
*   找到项目根目录下的 `schema.sql` 文件。
*   将其导入到你的 MySQL 数据库（`worklog_db`）中运行，以初始化表结构及演示测试数据。

### 3. 配置数据库连接
打开 `src/main/resources/application.yml`（或 `application.properties`），将数据库账号密码修改为你本地的配置：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/worklog_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root        # 改为你的账号
    password: your_password # 改为你的密码