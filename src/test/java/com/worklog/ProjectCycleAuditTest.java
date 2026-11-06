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
 * 第三轮反馈：项目周期（进行多少天）+ 操作审计。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("项目周期与操作审计")
class ProjectCycleAuditTest extends ApiTestSupport {

    @Test
    @DisplayName("项目详情带周期字段：已进行天数 / 计划工期 / 剩余天数 / 时间进度自洽")
    void projectCycleFields() throws Exception {
        JsonNode project = get("/projects/1", login("admin")).path("data");

        assertThat(project.path("startDate").asText()).isNotBlank();
        assertThat(project.path("plannedEndDate").asText()).isNotBlank();
        int elapsed = project.path("elapsedDays").asInt();
        int planned = project.path("plannedDays").asInt();
        int remaining = project.path("remainingDays").asInt();
        int progress = project.path("timeProgress").asInt();

        assertThat(elapsed).as("立项当天就是第 1 天").isGreaterThanOrEqualTo(1);
        assertThat(planned).isGreaterThanOrEqualTo(elapsed);
        assertThat(planned - elapsed).as("剩余天数 = 计划工期 - 已进行").isEqualTo(remaining);
        assertThat(progress).isBetween(0, 100);
    }

    @Test
    @DisplayName("看板的项目进度也带周期字段，不只给日报份数")
    void boardProjectHasCycle() throws Exception {
        JsonNode stats = get("/board/overview", login("boss"), Map.of("range", "month"))
                .path("data").path("projectStats");

        assertThat(stats.size()).isGreaterThan(0);
        JsonNode first = stats.get(0);
        assertThat(first.path("elapsedDays").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(first.path("plannedEndDate").asText()).isNotBlank();
        assertThat(first.path("timeProgress").asInt()).isBetween(0, 100);
    }

    @Test
    @DisplayName("编辑用户会写审计：动作、操作人、明细都能查到")
    void auditOnUserUpdate() throws Exception {
        String admin = login("admin");
        put("/sys/users/7", admin,
                "{\"realName\":\"周洋\",\"role\":\"EMPLOYEE\",\"deptId\":2,\"projectIds\":[1]}");

        JsonNode page = get("/sys/audit-logs", admin, Map.of("size", "50", "keyword", "zhouyang")).path("data");
        assertThat(page.path("total").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode first = page.path("records").get(0);
        assertThat(first.path("action").asText()).isEqualTo("UPDATE_USER");
        assertThat(first.path("actionName").asText()).isEqualTo("编辑用户");
        assertThat(first.path("operatorName").asText()).isEqualTo("张启明");
        assertThat(first.path("detail").asText()).contains("部门");
    }

    @Test
    @DisplayName("批量新增写一条汇总审计")
    void auditOnBatchCreate() throws Exception {
        String admin = login("admin");
        post("/sys/users/batch", admin, """
                {"password":"abc123456","deptId":2,"users":[
                  {"username":"audit_a","realName":"审计甲"},
                  {"username":"audit_b","realName":"审计乙"}
                ]}""");

        JsonNode page = get("/sys/audit-logs", admin,
                Map.of("size", "50", "action", "BATCH_CREATE_USER")).path("data");
        assertThat(page.path("total").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(page.path("records").get(0).path("detail").asText())
                .contains("批量新增 2 人").contains("audit_a");
    }

    @Test
    @DisplayName("审计只有管理员能看：负责人与员工都 403")
    void auditPermission() throws Exception {
        assertThat(get("/sys/audit-logs", login("liyue")).path("code").asInt()).isEqualTo(403);
        assertThat(get("/sys/audit-logs", login("wangfan")).path("code").asInt()).isEqualTo(403);
    }

    @Test
    @DisplayName("编辑项目时计划完成日不能早于立项日期")
    void plannedEndValidation() throws Exception {
        String admin = login("admin");
        JsonNode bad = put("/projects/4", admin,
                "{\"projectName\":\"移动端体验优化\",\"status\":0,\"plannedEndDate\":\"2020-01-01\"}");
        assertThat(bad.path("code").asInt()).isEqualTo(400);
        assertThat(bad.path("message").asText()).contains("立项");

        String future = java.time.LocalDate.now().plusDays(30).toString();
        JsonNode ok = put("/projects/4", admin,
                "{\"projectName\":\"移动端体验优化\",\"status\":0,\"plannedEndDate\":\"" + future + "\"}");
        assertThat(ok.path("code").asInt()).isEqualTo(200);
        assertThat(ok.path("data").path("plannedEndDate").asText()).isEqualTo(future);
    }
}
