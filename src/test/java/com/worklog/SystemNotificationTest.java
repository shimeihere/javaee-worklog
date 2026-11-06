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
 * 系统通知：管理员新增用户、变更项目负责人时写入 sys_message。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("系统通知")
class SystemNotificationTest extends ApiTestSupport {

    @Test
    @DisplayName("新增用户后，本人会收到一条「账号已开通」系统通知")
    void userCreatedNotification() throws Exception {
        String admin = login("admin");

        JsonNode created = post("/sys/users", admin,
                "{\"username\":\"notify_u1\",\"password\":\"123456\",\"realName\":\"通知测试\","
                        + "\"role\":\"EMPLOYEE\",\"deptId\":2}");
        assertThat(created.path("code").asInt()).isEqualTo(200);

        JsonNode messages = get("/messages", login("notify_u1"),
                Map.of("page", "1", "size", "10")).path("data").path("records");
        assertThat(messages.size()).isGreaterThanOrEqualTo(1);

        JsonNode first = messages.get(0);
        assertThat(first.path("msgType").asInt()).as("系统通知 = 2").isEqualTo(2);
        assertThat(first.path("title").asText()).contains("账号已开通");
        assertThat(first.path("msgTypeName").asText()).isEqualTo("系统通知");
    }

    @Test
    @DisplayName("变更项目负责人：新负责人收到指派通知，旧负责人收到移交通知")
    void projectLeaderChangeNotification() throws Exception {
        String admin = login("admin");

        // 项目 1 原负责人是赵琳(5)，改成陈曦(3)
        JsonNode changed = put("/projects/1/leader", admin, "{\"leaderId\":3}");
        assertThat(changed.path("code").asInt()).isEqualTo(200);
        assertThat(changed.path("data").path("leaderName").asText()).isEqualTo("陈曦");

        JsonNode newLeaderMessages = get("/messages", login("chenxi"),
                Map.of("page", "1", "size", "10")).path("data").path("records");
        assertThat(hasTitle(newLeaderMessages, "你被指派为项目负责人")).isTrue();

        JsonNode oldLeaderMessages = get("/messages", login("zhaolin"),
                Map.of("page", "1", "size", "10")).path("data").path("records");
        assertThat(hasTitle(oldLeaderMessages, "项目负责人已变更")).isTrue();
    }

    @Test
    @DisplayName("员工不能变更项目负责人")
    void employeeCannotChangeLeader() throws Exception {
        JsonNode result = put("/projects/1/leader", login("wangfan"), "{\"leaderId\":3}");
        assertThat(result.path("code").asInt()).isEqualTo(403);
    }

    private boolean hasTitle(JsonNode records, String keyword) {
        for (JsonNode item : records) {
            if (item.path("title").asText().contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
