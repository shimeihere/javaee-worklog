package com.worklog.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口测试基类：统一处理 context-path(/api)、鉴权头与 JSON 解析。
 *
 * <p>子类负责标注 {@code @SpringBootTest}、{@code @AutoConfigureMockMvc} 等注解。</p>
 */
public abstract class ApiTestSupport {

    protected static final String CTX = "/api";
    protected static final String AUTH_HEADER = "Authorization";
    protected static final String DEFAULT_PASSWORD = "123456";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** 以种子密码登录并返回令牌 */
    protected String login(String username) throws Exception {
        JsonNode body = objectMapper.readTree(
                mockMvc.perform(MockMvcRequestBuilders.post(CTX + "/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"" + username + "\",\"password\":\"" + DEFAULT_PASSWORD + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.path("code").asInt()).as("账号 %s 应能登录", username).isEqualTo(200);
        return body.path("data").path("token").asText();
    }

    protected JsonNode get(String path, String token) throws Exception {
        return get(path, token, Map.of());
    }

    protected JsonNode get(String path, String token, Map<String, String> params) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(CTX + path);
        params.forEach(builder::param);
        return send(builder, token);
    }

    protected JsonNode post(String path, String token, String json) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(CTX + path);
        if (json != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return send(builder, token);
    }

    protected JsonNode put(String path, String token) throws Exception {
        return put(path, token, null);
    }

    protected JsonNode put(String path, String token, String json) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(CTX + path);
        if (json != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return send(builder, token);
    }

    protected JsonNode delete(String path, String token) throws Exception {
        return send(MockMvcRequestBuilders.delete(CTX + path), token);
    }

    /** multipart 上传（附件） */
    protected JsonNode upload(String path, String token, String fieldName, String fileName,
                              byte[] content, Map<String, String> params) throws Exception {
        MockMultipartFile file = new MockMultipartFile(fieldName, fileName,
                MediaType.APPLICATION_OCTET_STREAM_VALUE, content);
        MockMultipartHttpServletRequestBuilder builder =
                MockMvcRequestBuilders.multipart(CTX + path).file(file);
        params.forEach(builder::param);
        return send(builder, token);
    }

    private JsonNode send(MockHttpServletRequestBuilder builder, String token) throws Exception {
        if (token != null) {
            builder.header(AUTH_HEADER, "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
