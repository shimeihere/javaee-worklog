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
 * 第十一阶段验收测试：部门新增 / 编辑（仅两级）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("部门管理")
class DeptManageTest extends ApiTestSupport {

    @Test
    @DisplayName("管理员可在顶级公司下新增二级部门")
    void createSecondLevelDept() throws Exception {
        String admin = login("admin");

        JsonNode created = post("/sys/depts", admin,
                "{\"parentId\":1,\"deptName\":\"质量保障部\",\"sortOrder\":9}");
        assertThat(created.path("code").asInt()).isEqualTo(200);
        assertThat(created.path("data").path("deptName").asText()).isEqualTo("质量保障部");
        assertThat(created.path("data").path("parentId").asLong()).isEqualTo(1L);

        JsonNode tree = get("/sys/depts/tree", admin).path("data");
        JsonNode root = tree.get(0);
        assertThat(root.path("children").size()).isEqualTo(4);
    }

    @Test
    @DisplayName("不允许挂在二级部门下（三级结构被拒绝）")
    void rejectThirdLevel() throws Exception {
        String admin = login("admin");

        JsonNode result = post("/sys/depts", admin,
                "{\"parentId\":2,\"deptName\":\"前端组\"}");
        assertThat(result.path("code").asInt()).isEqualTo(400);
        assertThat(result.path("message").asText()).contains("两级");
    }

    @Test
    @DisplayName("部门名为空触发参数校验")
    void blankName() throws Exception {
        JsonNode result = post("/sys/depts", login("admin"), "{\"parentId\":1,\"deptName\":\"  \"}");
        assertThat(result.path("code").asInt()).isEqualTo(400);
        assertThat(result.path("message").asText()).contains("部门名称");
    }

    @Test
    @DisplayName("编辑部门名称后树里能读到新名字")
    void updateDept() throws Exception {
        String admin = login("admin");

        JsonNode updated = put("/sys/depts/4", admin, "{\"deptName\":\"市场与运营部\",\"sortOrder\":3}");
        assertThat(updated.path("code").asInt()).isEqualTo(200);

        JsonNode tree = get("/sys/depts/tree", admin).path("data");
        boolean found = false;
        for (JsonNode child : tree.get(0).path("children")) {
            if ("市场与运营部".equals(child.path("deptName").asText())) {
                found = true;
            }
        }
        assertThat(found).as("树中应出现改名后的部门").isTrue();
    }

    @Test
    @DisplayName("部门负责人和员工都不能维护部门")
    void permission() throws Exception {
        assertThat(post("/sys/depts", login("liyue"),
                "{\"parentId\":1,\"deptName\":\"越权部门\"}").path("code").asInt()).isEqualTo(403);
        assertThat(put("/sys/depts/4", login("wangfan"),
                "{\"deptName\":\"越权改名\"}").path("code").asInt()).isEqualTo(403);
    }
}
