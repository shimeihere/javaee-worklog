package com.worklog;

import com.fasterxml.jackson.databind.JsonNode;
import com.worklog.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第三阶段验收测试：M2 基础管理（部门树 / 用户管理 / 项目管理 / 数据范围）。
 *
 * <p>全部走真实 HTTP（MockMvc）打到真实 MySQL；类上 {@code @Transactional}
 * 保证禁用、新建项目等写操作在用例结束后回滚，不污染种子数据。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("M2 基础管理")
class BasicAdminTest extends ApiTestSupport {

    // ------------------------------------------------------------------
    // 部门管理
    // ------------------------------------------------------------------

    @Test
    @DisplayName("部门树为两级结构，且每个节点带直属人数")
    void deptTreeIsTwoLevel() throws Exception {
        String admin = login("admin");
        JsonNode data = get("/sys/depts/tree", admin).path("data");

        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).as("只有一个顶级公司节点").isEqualTo(1);

        JsonNode root = data.get(0);
        assertThat(root.path("deptName").asText()).isEqualTo("周览科技");
        assertThat(root.path("parentId").asLong()).isZero();
        assertThat(root.path("userCount").asLong()).as("公司本部直属人数（含高管）").isGreaterThanOrEqualTo(1);
        assertThat(root.path("children").size()).as("三个二级部门").isEqualTo(3);

        JsonNode tech = root.path("children").get(0);
        assertThat(tech.path("deptName").asText()).isEqualTo("技术中心");
        assertThat(tech.path("userCount").asLong()).isEqualTo(3);
        assertThat(tech.path("children").size()).as("只有两级，叶子节点无子部门").isZero();
    }

    // ------------------------------------------------------------------
    // 用户管理
    // ------------------------------------------------------------------

    @Test
    @DisplayName("用户分页：支持部门、角色、关键字筛选，且不返回密码")
    void userPageFiltersWork() throws Exception {
        String admin = login("admin");

        JsonNode all = get("/sys/users", admin, Map.of("page", "1", "size", "20")).path("data");
        assertThat(all.path("total").asLong()).isGreaterThanOrEqualTo(9);
        assertThat(all.path("records").size()).isGreaterThanOrEqualTo(9);
        assertThat(all.path("records").get(0).has("password")).as("列表不能带密码").isFalse();
        assertThat(all.path("records").get(0).path("deptName").asText()).isNotBlank();

        JsonNode employees = get("/sys/users", admin, Map.of("role", "EMPLOYEE", "size", "50")).path("data");
        assertThat(employees.path("total").asLong()).isEqualTo(5);

        JsonNode techDept = get("/sys/users", admin, Map.of("deptId", "2", "size", "50")).path("data");
        assertThat(techDept.path("total").asLong()).isEqualTo(3);

        JsonNode byKeyword = get("/sys/users", admin, Map.of("keyword", "李")).path("data");
        assertThat(byKeyword.path("total").asLong()).isEqualTo(1);
        assertThat(byKeyword.path("records").get(0).path("realName").asText()).isEqualTo("李悦");
    }

    @Test
    @DisplayName("数据范围：部门负责人只看本部门成员，员工无权访问用户列表")
    void userScopeByRole() throws Exception {
        JsonNode leaderView = get("/sys/users", login("liyue"), Map.of("size", "50")).path("data");

        assertThat(leaderView.path("total").asLong()).as("技术中心共 3 人").isEqualTo(3);
        leaderView.path("records").forEach(row ->
                assertThat(row.path("deptId").asLong()).isEqualTo(2));

        JsonNode employeeView = get("/sys/users", login("wangfan"));
        assertThat(employeeView.path("code").asInt()).as("员工访问用户管理应 403").isEqualTo(403);
    }

    @Test
    @DisplayName("禁用用户：提示未提交日报草稿，且该账号立即无法登录")
    void disableUserWarnsAboutDrafts() throws Exception {
        String admin = login("admin");

        // wangfan(id=4) 有一份今日草稿
        JsonNode disabled = put("/sys/users/4/status?status=1", admin);
        assertThat(disabled.path("code").asInt()).isEqualTo(200);
        assertThat(disabled.path("message").asText()).contains("未提交").contains("1 份");

        JsonNode row = get("/sys/users", admin, Map.of("keyword", "wangfan")).path("data").path("records").get(0);
        assertThat(row.path("status").asInt()).isEqualTo(1);

        JsonNode blockedLogin = post("/auth/login", null,
                "{\"username\":\"wangfan\",\"password\":\"123456\"}");
        assertThat(blockedLogin.path("code").asInt()).as("禁用后应登录失败").isEqualTo(4003);

        JsonNode enabled = put("/sys/users/4/status?status=0", admin);
        assertThat(enabled.path("code").asInt()).isEqualTo(200);
        assertThat(enabled.path("message").asText()).isEqualTo("账号已启用");
    }

    @Test
    @DisplayName("禁用接口的边界：不能禁用自己，部门负责人无权限")
    void disableUserGuards() throws Exception {
        String admin = login("admin");

        JsonNode self = put("/sys/users/1/status?status=1", admin);
        assertThat(self.path("code").asInt()).isEqualTo(400);
        assertThat(self.path("message").asText()).contains("自己");

        JsonNode byLeader = put("/sys/users/4/status?status=1", login("liyue"));
        assertThat(byLeader.path("code").asInt()).as("仅 ADMIN 可用").isEqualTo(403);

        JsonNode badStatus = put("/sys/users/4/status?status=9", admin);
        assertThat(badStatus.path("code").asInt()).isEqualTo(400);
    }

    // ------------------------------------------------------------------
    // 项目管理 + 数据范围
    // ------------------------------------------------------------------

    @Test
    @DisplayName("项目列表按角色过滤：ADMIN 全部、DEPT_LEADER 本部门、EMPLOYEE 负责或参与")
    void projectScopeByRole() throws Exception {
        JsonNode adminView = get("/projects", login("admin"), Map.of("size", "50")).path("data");
        assertThat(adminView.path("total").asLong()).isEqualTo(4);

        // project 4（移动端）已划到产品设计部，技术中心只剩 1、2 两个项目
        JsonNode leaderView = get("/projects", login("liyue"), Map.of("size", "50")).path("data");
        assertThat(leaderView.path("total").asLong()).as("技术中心下 2 个项目").isEqualTo(2);
        leaderView.path("records").forEach(row ->
                assertThat(row.path("deptId").asLong()).isEqualTo(2));

        JsonNode employeeView = get("/projects", login("wangfan"), Map.of("size", "50")).path("data");
        assertThat(employeeView.path("total").asLong())
                .as("王帆只在项目1填过日报，因此只可见 1 个").isEqualTo(1);
        assertThat(employeeView.path("records").get(0).path("projectName").asText()).contains("周览");

        // 陈曦负责移动端（项目4）与品牌视觉（项目3），都在产品设计部
        JsonNode designLeader = get("/projects", login("chenxi"), Map.of("size", "50")).path("data");
        assertThat(designLeader.path("total").asLong()).isEqualTo(2);
        designLeader.path("records").forEach(row ->
                assertThat(row.path("deptId").asLong()).isEqualTo(3));
    }

    @Test
    @DisplayName("项目详情越权按 404 处理，避免探测其他部门项目")
    void projectDetailScope() throws Exception {
        String wangfan = login("wangfan");

        JsonNode visible = get("/projects/1", wangfan);
        assertThat(visible.path("code").asInt()).isEqualTo(200);
        assertThat(visible.path("data").path("leaderName").asText()).isEqualTo("赵琳");

        JsonNode hidden = get("/projects/2", wangfan);
        assertThat(hidden.path("code").asInt()).isEqualTo(404);

        assertThat(get("/projects/9999", login("admin")).path("code").asInt()).isEqualTo(404);
    }

    @Test
    @DisplayName("部门负责人可在本部门创建项目并立即看到")
    void createProjectAsDeptLeader() throws Exception {
        String liyue = login("liyue");

        JsonNode created = post("/projects", liyue,
                "{\"projectName\":\"部门看板二期\",\"leaderId\":2,\"deptId\":2,\"description\":\"看板与催报联动\"}");
        assertThat(created.path("code").asInt()).isEqualTo(200);
        assertThat(created.path("data").path("id").asLong()).isPositive();
        assertThat(created.path("data").path("leaderName").asText()).isEqualTo("李悦");
        assertThat(created.path("data").path("deptName").asText()).isEqualTo("技术中心");
        assertThat(created.path("data").path("status").asInt()).isZero();

        JsonNode list = get("/projects", liyue, Map.of("size", "50")).path("data");
        assertThat(list.path("total").asLong()).as("技术中心原有 2 个 + 新建 1 个").isEqualTo(3);
    }

    @Test
    @DisplayName("创建项目的越权与校验：跨部门 403、员工 403、负责人禁用 400、名称必填")
    void createProjectGuards() throws Exception {
        String liyue = login("liyue");

        JsonNode crossDept = post("/projects", liyue,
                "{\"projectName\":\"越权项目\",\"leaderId\":3,\"deptId\":3}");
        assertThat(crossDept.path("code").asInt()).isEqualTo(403);
        assertThat(crossDept.path("message").asText()).contains("本部门");

        JsonNode byEmployee = post("/projects", login("wangfan"),
                "{\"projectName\":\"员工建项目\",\"leaderId\":4,\"deptId\":2}");
        assertThat(byEmployee.path("code").asInt()).isEqualTo(403);

        // leaderId=8 是已禁用账号
        JsonNode disabledLeader = post("/projects", login("admin"),
                "{\"projectName\":\"禁用负责人项目\",\"leaderId\":8,\"deptId\":4}");
        assertThat(disabledLeader.path("code").asInt()).isEqualTo(400);
        assertThat(disabledLeader.path("message").asText()).contains("禁用");

        JsonNode blankName = post("/projects", login("admin"),
                "{\"projectName\":\"  \",\"leaderId\":2,\"deptId\":2}");
        assertThat(blankName.path("code").asInt()).isEqualTo(400);
        assertThat(blankName.path("message").asText()).contains("项目名称");

        JsonNode noLeader = post("/projects", login("admin"),
                "{\"projectName\":\"没有负责人\",\"deptId\":2}");
        assertThat(noLeader.path("code").asInt()).isEqualTo(400);
        assertThat(noLeader.path("message").asText()).contains("负责人");
    }
}
