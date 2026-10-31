package com.worklog;

import com.fasterxml.jackson.databind.JsonNode;
import com.worklog.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 名单穿透：部门/项目成员花名册 + 不越级权限。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("成员花名册")
class MemberRosterTest extends ApiTestSupport {

    @Test
    @DisplayName("高管可查任意部门/项目的成员名单（只给姓名与角色）")
    void executiveCanViewAnyRoster() throws Exception {
        String boss = login("boss");

        JsonNode deptMembers = get("/sys/depts/3/members", boss).path("data");
        assertThat(deptMembers.size()).isEqualTo(3);
        JsonNode first = deptMembers.get(0);
        assertThat(first.path("realName").asText()).isNotBlank();
        assertThat(first.path("roleName").asText()).isNotBlank();
        assertThat(first.has("password")).isFalse();

        JsonNode projectMembers = get("/projects/1/members", boss).path("data");
        assertThat(projectMembers.size()).isGreaterThanOrEqualTo(1);
        assertThat(projectMembers.get(0).path("realName").asText()).isEqualTo("赵琳");
    }

    @Test
    @DisplayName("管理员可在系统设置里查部门/项目成员")
    void adminCanViewRoster() throws Exception {
        String admin = login("admin");

        JsonNode deptMembers = get("/sys/depts/2/members", admin).path("data");
        assertThat(deptMembers.size()).isEqualTo(3);

        JsonNode projectMembers = get("/projects/2/members", admin).path("data");
        assertThat(projectMembers.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("负责人只能查本部门名单，跨部门 403")
    void leaderScopedRoster() throws Exception {
        String liyue = login("liyue");

        assertThat(get("/sys/depts/2/members", liyue).path("data").size()).isEqualTo(3);

        JsonNode crossDept = get("/sys/depts/3/members", liyue);
        assertThat(crossDept.path("code").asInt()).isEqualTo(403);
        assertThat(crossDept.path("message").asText()).contains("本部门");

        // 项目 3 属于产品设计部，技术中心负责人看不到
        JsonNode crossProject = get("/projects/3/members", liyue);
        assertThat(crossProject.path("code").asInt()).isEqualTo(403);
    }

    @Test
    @DisplayName("员工不能看任何名单")
    void employeeForbidden() throws Exception {
        String wangfan = login("wangfan");
        assertThat(get("/sys/depts/2/members", wangfan).path("code").asInt()).isEqualTo(403);
        assertThat(get("/projects/1/members", wangfan).path("code").asInt()).isEqualTo(403);
    }

    @Test
    @DisplayName("管理员工作台只给系统指标：总人数与今日活跃")
    void adminWorkbenchSystemMetrics() throws Exception {
        JsonNode data = get("/dashboard/summary", login("admin")).path("data");

        assertThat(data.path("systemTotalUsers").asLong()).isGreaterThanOrEqualTo(9);
        assertThat(data.path("todayActiveUsers").asLong()).isGreaterThanOrEqualTo(0);

        // 员工侧没有系统指标
        JsonNode employee = get("/dashboard/summary", login("wangfan")).path("data");
        assertThat(employee.path("systemTotalUsers").isNull()).isTrue();
    }
}
