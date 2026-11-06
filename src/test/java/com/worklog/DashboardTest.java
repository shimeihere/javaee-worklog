package com.worklog;

import com.fasterxml.jackson.databind.JsonNode;
import com.worklog.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第七阶段验收测试：工作台汇总接口 + 未读角标。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("工作台汇总")
class DashboardTest extends ApiTestSupport {

    @Test
    @DisplayName("管理员：不下发团队业务指标，只给系统指标")
    void adminSummary() throws Exception {
        JsonNode data = get("/dashboard/summary", login("admin")).path("data");

        assertThat(data.path("today").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(data.path("projectCount").asLong()).isEqualTo(4);
        assertThat(data.path("week").path("submitRate").asInt()).isBetween(0, 100);
        assertThat(data.path("week").path("startDate").asText())
                .isEqualTo(LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString());
        assertThat(data.path("team").isNull()).as("管理员不应拿到团队提交率").isTrue();
        assertThat(data.path("systemTotalUsers").asLong()).isGreaterThanOrEqualTo(9);
        assertThat(data.path("month").path("submitRate").asInt()).isBetween(0, 100);
    }

    @Test
    @DisplayName("部门负责人：团队=本部门 3 人，项目=本部门 2 个")
    void leaderSummary() throws Exception {
        JsonNode data = get("/dashboard/summary", login("liyue")).path("data");

        assertThat(data.path("projectCount").asLong()).isEqualTo(2);
        assertThat(data.path("team").path("memberCount").asInt()).isEqualTo(3);
        assertThat(data.path("team").path("submittedToday").asInt()).isBetween(0, 3);
    }

    @Test
    @DisplayName("员工：无团队指标，项目只算参与过的 1 个")
    void employeeSummary() throws Exception {
        JsonNode data = get("/dashboard/summary", login("wangfan")).path("data");

        assertThat(data.path("projectCount").asLong()).isEqualTo(1);
        assertThat(data.path("team").isNull()).as("员工不返回团队指标").isTrue();
        assertThat(data.path("pendingDrafts").asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("未读角标与消息列表口径一致")
    void unreadCount() throws Exception {
        // 不写死条数：开发期会有人真的去点催报，这里验证"角标 = 未读列表总数"这个不变量
        for (String username : new String[]{"zhaolin", "admin"}) {
            String token = login(username);
            long badge = get("/messages/unread-count", token).path("data").path("count").asLong();
            long listTotal = get("/messages", token, Map.of("isRead", "0", "size", "100"))
                    .path("data").path("total").asLong();

            assertThat(badge).as("%s 的角标不应为负", username).isGreaterThanOrEqualTo(0);
            assertThat(listTotal).as("%s 的角标应与未读列表一致", username).isEqualTo(badge);
        }
    }

    @Test
    @DisplayName("未携带令牌访问工作台返回 401")
    void unauthorized() throws Exception {
        assertThat(get("/dashboard/summary", null).path("code").asInt()).isEqualTo(401);
    }

    @Test
    @DisplayName("访问不存在的接口返回 404 而不是 500")
    void unknownPathReturns404() throws Exception {
        JsonNode body = get("/no-such-endpoint", login("admin"));
        assertThat(body.path("code").asInt()).isEqualTo(404);
        assertThat(body.path("message").asText()).contains("接口不存在");
    }
}
