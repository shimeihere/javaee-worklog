package com.worklog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 第二阶段验收测试：登录、JWT 鉴权、禁用账号拦截、统一返回体与异常处理。
 *
 * <p>依赖 schema.sql 的种子账号（密码统一 123456，zhouxin 为禁用账号）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("登录与鉴权流程")
class AuthFlowTest {

    private static final String CONTEXT_PATH = "/api";
    private static final String PASSWORD = "123456";
    private static final String HEADER = "Authorization";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("正确账号密码可登录并拿到 JWT")
    void loginSuccess() throws Exception {
        JsonNode body = postJson("/auth/login", loginBody("admin", PASSWORD));

        assertThat(body.path("code").asInt()).isEqualTo(200);
        assertThat(body.path("message").asText()).isEqualTo("登录成功");
        // JWT 三段式，header 固定以 eyJ 开头
        assertThat(body.path("data").path("token").asText()).isNotBlank().startsWith("eyJ");
        assertThat(body.path("data").path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.path("data").path("expiresIn").asLong()).isEqualTo(720 * 60L);
        assertThat(body.path("data").path("user").path("username").asText()).isEqualTo("admin");
        assertThat(body.path("data").path("user").path("realName").asText()).isEqualTo("张启明");
        assertThat(body.path("data").path("user").path("roleName").asText()).isEqualTo("系统管理员");
        assertThat(body.path("data").path("user").path("deptName").asText()).isEqualTo("周览科技");
        assertThat(body.path("data").path("user").has("password")).as("登录响应不能带密码").isFalse();
    }

    @Test
    @DisplayName("密码错误返回 4001，且不泄露账号是否存在")
    void loginWithWrongPassword() throws Exception {
        JsonNode wrongPassword = postJson("/auth/login", loginBody("admin", "1234567"));
        assertThat(wrongPassword.path("code").asInt()).isEqualTo(4001);
        assertThat(wrongPassword.path("message").asText()).isEqualTo("账号或密码错误");

        JsonNode ghostAccount = postJson("/auth/login", loginBody("ghost", PASSWORD));
        assertThat(ghostAccount.path("code").asInt()).as("不存在的账号返回同一错误码").isEqualTo(4001);
        assertThat(ghostAccount.path("message").asText()).isEqualTo("账号或密码错误");
    }

    @Test
    @DisplayName("禁用账号登录被拦截（4003）")
    void loginWithDisabledAccount() throws Exception {
        JsonNode body = postJson("/auth/login", loginBody("zhouxin", PASSWORD));

        assertThat(body.path("code").asInt()).isEqualTo(4003);
        assertThat(body.path("message").asText()).contains("禁用");
        assertThat(body.path("data").isNull()).isTrue();
    }

    @Test
    @DisplayName("账号为空触发参数校验（400）")
    void loginWithBlankUsername() throws Exception {
        JsonNode body = postJson("/auth/login", "{\"username\":\"\",\"password\":\"123456\"}");

        assertThat(body.path("code").asInt()).isEqualTo(400);
        assertThat(body.path("message").asText()).isEqualTo("请输入账号");
    }

    @Test
    @DisplayName("请求体不是合法 JSON 时返回统一错误结构（400）")
    void loginWithBrokenJson() throws Exception {
        JsonNode body = postJson("/auth/login", "{\"username\":\"admin\",");

        assertThat(body.path("code").asInt()).isEqualTo(400);
        assertThat(body.path("message").asText()).contains("请求体格式有误");
    }

    @Test
    @DisplayName("未携带令牌访问受保护接口返回 401")
    void meWithoutToken() throws Exception {
        JsonNode body = getJson("/auth/me", null);

        assertThat(body.path("code").asInt()).isEqualTo(401);
        assertThat(body.path("message").asText()).contains("未登录");
    }

    @Test
    @DisplayName("伪造/篡改的令牌返回 401")
    void meWithInvalidToken() throws Exception {
        JsonNode body = getJson("/auth/me", "Bearer abc.def.ghi");

        assertThat(body.path("code").asInt()).isEqualTo(401);
    }

    @Test
    @DisplayName("携带有效令牌可获取当前用户信息，且响应中无密码")
    void meWithToken() throws Exception {
        String token = login("wangfan");
        JsonNode body = getJson("/auth/me", "Bearer " + token);

        assertThat(body.path("code").asInt()).isEqualTo(200);
        assertThat(body.path("data").path("username").asText()).isEqualTo("wangfan");
        assertThat(body.path("data").path("realName").asText()).isEqualTo("王帆");
        assertThat(body.path("data").path("roleName").asText()).isEqualTo("员工");
        assertThat(body.path("data").path("deptName").asText()).isEqualTo("技术中心");
        assertThat(body.path("data").has("password")).as("用户信息不能带密码字段").isFalse();
    }

    @Test
    @DisplayName("@RequireRole 角色校验生效：ADMIN 放行、EMPLOYEE 返回 403")
    void roleCheckWorks() throws Exception {
        JsonNode adminResult = getJson("/probe/admin-only", "Bearer " + login("admin"));
        assertThat(adminResult.path("code").asInt()).isEqualTo(200);
        assertThat(adminResult.path("data").asText()).isEqualTo("admin-ok");

        JsonNode employeeResult = getJson("/probe/admin-only", "Bearer " + login("wangfan"));
        assertThat(employeeResult.path("code").asInt()).isEqualTo(403);
    }

    @Test
    @DisplayName("登出接口可用（无状态方案，由前端丢弃令牌）")
    void logout() throws Exception {
        MvcResult result = mockMvc.perform(post(CONTEXT_PATH + "/auth/logout")
                        .contextPath(CONTEXT_PATH)
                        .header(HEADER, "Bearer " + login("admin")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(read(result).path("code").asInt()).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private String login(String username) throws Exception {
        JsonNode body = postJson("/auth/login", loginBody(username, PASSWORD));
        assertThat(body.path("code").asInt()).as("账号 %s 应能登录", username).isEqualTo(200);
        return body.path("data").path("token").asText();
    }

    private String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private JsonNode postJson(String path, String json) throws Exception {
        MvcResult result = mockMvc.perform(post(CONTEXT_PATH + path)
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        return read(result);
    }

    private JsonNode getJson(String path, String authorization) throws Exception {
        MockHttpServletRequestBuilder builder = get(CONTEXT_PATH + path).contextPath(CONTEXT_PATH);
        if (authorization != null) {
            builder.header(HEADER, authorization);
        }
        MvcResult result = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();
        return read(result);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
