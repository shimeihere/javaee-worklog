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
 * 新增用户（系统设置里的「+ 新增用户」）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("用户新增")
class UserCreateTest extends ApiTestSupport {

    @Test
    @DisplayName("管理员新增用户：密码 BCrypt 落库，可直接登录")
    void createUserAndLogin() throws Exception {
        String admin = login("admin");

        JsonNode created = post("/sys/users", admin,
                "{\"username\":\"newbie\",\"password\":\"abc123456\",\"realName\":\"新来的\","
                        + "\"role\":\"EMPLOYEE\",\"deptId\":2}");
        assertThat(created.path("code").asInt()).isEqualTo(200);
        assertThat(created.path("data").path("username").asText()).isEqualTo("newbie");
        assertThat(created.path("data").path("roleName").asText()).isEqualTo("员工");
        assertThat(created.path("data").path("deptName").asText()).isEqualTo("技术中心");
        assertThat(created.path("data").has("password")).isFalse();

        // 新账号能直接登录（说明密码已按 BCrypt 正确加密）
        JsonNode loginResult = post("/auth/login", null,
                "{\"username\":\"newbie\",\"password\":\"abc123456\"}");
        assertThat(loginResult.path("code").asInt()).isEqualTo(200);
        assertThat(loginResult.path("data").path("user").path("realName").asText()).isEqualTo("新来的");
    }

    @Test
    @DisplayName("账号重复 / 角色非法 / 姓名空 都要被拒")
    void validation() throws Exception {
        String admin = login("admin");

        JsonNode duplicated = post("/sys/users", admin,
                "{\"username\":\"admin\",\"password\":\"abc123456\",\"realName\":\"重名\","
                        + "\"role\":\"EMPLOYEE\",\"deptId\":2}");
        assertThat(duplicated.path("code").asInt()).isEqualTo(400);
        assertThat(duplicated.path("message").asText()).contains("已存在");

        JsonNode badRole = post("/sys/users", admin,
                "{\"username\":\"role_x\",\"password\":\"abc123456\",\"realName\":\"错角色\","
                        + "\"role\":\"BOSS\",\"deptId\":2}");
        assertThat(badRole.path("code").asInt()).isEqualTo(400);

        JsonNode blankName = post("/sys/users", admin,
                "{\"username\":\"noname\",\"password\":\"abc123456\",\"realName\":\"\","
                        + "\"role\":\"EMPLOYEE\",\"deptId\":2}");
        assertThat(blankName.path("code").asInt()).isEqualTo(400);
    }

    @Test
    @DisplayName("部门负责人不能新增用户")
    void leaderForbidden() throws Exception {
        JsonNode result = post("/sys/users", login("liyue"),
                "{\"username\":\"by_leader\",\"password\":\"abc123456\",\"realName\":\"越权\","
                        + "\"role\":\"EMPLOYEE\",\"deptId\":2}");
        assertThat(result.path("code").asInt()).isEqualTo(403);
    }
}
