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
 * 四角色权限矩阵 + 个人中心改密。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("角色矩阵与个人中心")
class RoleMatrixTest extends ApiTestSupport {

    @Test
    @DisplayName("高管账号 boss 可登录，角色为 EXECUTIVE")
    void executiveLogin() throws Exception {
        JsonNode login = post("/auth/login", null,
                "{\"username\":\"boss\",\"password\":\"123456\"}");
        assertThat(login.path("code").asInt()).isEqualTo(200);
        assertThat(login.path("data").path("user").path("role").asText()).isEqualTo("EXECUTIVE");
        assertThat(login.path("data").path("user").path("roleName").asText()).isEqualTo("高管");
    }

    @Test
    @DisplayName("高管与管理员都不能填报日报/周报")
    void nonFillingRolesCannotSubmit() throws Exception {
        String daily = "{\"reportDate\":\"2026-10-28\",\"projectId\":1,"
                + "\"contentDone\":\"不该被写入\",\"hours\":1}";
        String weekly = "{\"startDate\":\"2026-10-26\",\"projectId\":1,"
                + "\"summary\":\"不该被写入\",\"plan\":\"x\"}";

        for (String username : new String[]{"boss", "admin"}) {
            String token = login(username);

            JsonNode dailyResult = post("/reports/daily", token, daily);
            assertThat(dailyResult.path("code").asInt())
                    .as("%s 不应能写日报", username).isEqualTo(403);

            JsonNode weeklyResult = post("/reports/weekly", token, weekly);
            assertThat(weeklyResult.path("code").asInt())
                    .as("%s 不应能写周报", username).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("员工与负责人仍然可以正常填报")
    void fillingRolesStillWork() throws Exception {
        JsonNode result = post("/reports/daily", login("wangfan"),
                "{\"reportDate\":\"2026-10-28\",\"projectId\":1,"
                        + "\"contentDone\":\"员工正常填报\",\"hours\":2}");
        assertThat(result.path("code").asInt()).isEqualTo(200);
        assertThat(result.path("data").path("status").asInt()).isZero();
    }

    @Test
    @DisplayName("修改密码：原密码错 400；改成功后新密码可登录")
    void changePassword() throws Exception {
        String token = login("boss");

        JsonNode wrongOld = put("/auth/password", token,
                "{\"oldPassword\":\"wrong123\",\"newPassword\":\"newpass123\"}");
        assertThat(wrongOld.path("code").asInt()).isEqualTo(400);
        assertThat(wrongOld.path("message").asText()).contains("原密码");

        JsonNode ok = put("/auth/password", token,
                "{\"oldPassword\":\"123456\",\"newPassword\":\"newpass123\"}");
        assertThat(ok.path("code").asInt()).isEqualTo(200);

        JsonNode withNew = post("/auth/login", null,
                "{\"username\":\"boss\",\"password\":\"newpass123\"}");
        assertThat(withNew.path("code").asInt()).isEqualTo(200);

        JsonNode withOld = post("/auth/login", null,
                "{\"username\":\"boss\",\"password\":\"123456\"}");
        assertThat(withOld.path("code").asInt()).isEqualTo(4001);
    }

    @Test
    @DisplayName("个人中心能拿到自己的账号/姓名/部门/角色")
    void meReturnsProfileFields() throws Exception {
        JsonNode data = get("/auth/me", login("boss")).path("data");
        assertThat(data.path("username").asText()).isEqualTo("boss");
        assertThat(data.path("realName").asText()).isNotBlank();
        assertThat(data.path("deptName").asText()).isNotBlank();
        assertThat(data.path("roleName").asText()).isEqualTo("高管");
        assertThat(data.has("password")).isFalse();
    }
}
