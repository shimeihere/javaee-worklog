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
 * 用户管理增强：编辑用户（调部门 / 加项目）与批量新增。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("用户管理：编辑与批量新增")
class UserManageTest extends ApiTestSupport {

    @Test
    @DisplayName("编辑用户：调部门 + 加项目，项目名单与员工可选项目同步生效")
    void editUserMovesDeptAndJoinsProject() throws Exception {
        String admin = login("admin");

        JsonNode updated = put("/sys/users/7", admin,
                "{\"realName\":\"周洋\",\"role\":\"EMPLOYEE\",\"deptId\":2,\"projectIds\":[1,2]}");
        assertThat(updated.path("code").asInt()).isEqualTo(200);
        assertThat(updated.path("data").path("deptId").asLong()).isEqualTo(2);
        assertThat(updated.path("data").path("deptName").asText()).isEqualTo("技术中心");
        assertThat(updated.path("data").path("projectIds").size()).isEqualTo(2);
        assertThat(updated.path("data").path("projectNames").size()).isEqualTo(2);

        // 项目成员名单里应当出现他（管理员分配的成员，不依赖是否填过日报）
        JsonNode members = get("/projects/1/members", admin).path("data");
        assertThat(members.toString()).contains("周洋");

        // 员工本人填报时能选到这个项目（数据范围 = 分配的项目 ∪ 填过的项目）
        JsonNode myProjects = get("/projects", login("zhouyang"), Map.of("size", "50")).path("data");
        assertThat(myProjects.path("records").toString()).contains("周览 Worklog 平台建设");
    }

    @Test
    @DisplayName("编辑用户：传空数组表示退出全部项目，null 表示项目不动")
    void editUserProjectListIsOptional() throws Exception {
        String admin = login("admin");

        JsonNode untouched = put("/sys/users/4", admin,
                "{\"realName\":\"王帆\",\"role\":\"EMPLOYEE\",\"deptId\":2}");
        assertThat(untouched.path("code").asInt()).isEqualTo(200);
        assertThat(untouched.path("data").path("projectIds").size()).as("不传 projectIds 时项目保持原样").isEqualTo(1);

        JsonNode cleared = put("/sys/users/4", admin,
                "{\"realName\":\"王帆\",\"role\":\"EMPLOYEE\",\"deptId\":2,\"projectIds\":[]}");
        assertThat(cleared.path("code").asInt()).isEqualTo(200);
        assertThat(cleared.path("data").path("projectIds").size()).isZero();
    }

    @Test
    @DisplayName("批量新增：成功入库并能用统一密码登录，重复账号只报错那一行")
    void batchCreateUsers() throws Exception {
        String admin = login("admin");

        JsonNode result = post("/sys/users/batch", admin, """
                {"password":"abc123456","role":"EMPLOYEE","deptId":2,"users":[
                  {"username":"batch_a","realName":"批量甲"},
                  {"username":"batch_b","realName":"批量乙","role":"DEPT_LEADER"},
                  {"username":"admin","realName":"重复账号"}
                ]}""");

        assertThat(result.path("code").asInt()).isEqualTo(200);
        JsonNode data = result.path("data");
        assertThat(data.path("createdCount").asInt()).isEqualTo(2);
        assertThat(data.path("failedCount").asInt()).isEqualTo(1);
        assertThat(data.path("failures").get(0).asText()).contains("admin").contains("第 3 行");

        JsonNode loginResult = post("/auth/login", null,
                "{\"username\":\"batch_b\",\"password\":\"abc123456\"}");
        assertThat(loginResult.path("code").asInt()).isEqualTo(200);
        assertThat(loginResult.path("data").path("user").path("role").asText()).isEqualTo("DEPT_LEADER");
    }

    @Test
    @DisplayName("批量新增：缺少部门 / 密码过短整批 400")
    void batchValidation() throws Exception {
        String admin = login("admin");

        JsonNode noDept = post("/sys/users/batch", admin, """
                {"password":"abc123456","role":"EMPLOYEE","users":[
                  {"username":"no_dept","realName":"无部门"}
                ]}""");
        assertThat(noDept.path("data").path("createdCount").asInt()).isZero();
        assertThat(noDept.path("data").path("failedCount").asInt()).isEqualTo(1);
        assertThat(noDept.path("data").path("failures").get(0).asText()).contains("部门");

        JsonNode shortPwd = post("/sys/users/batch", admin, """
                {"password":"123","users":[{"username":"short_pwd","realName":"短密码"}]}""");
        assertThat(shortPwd.path("code").asInt()).isEqualTo(400);
    }

    @Test
    @DisplayName("只有管理员能编辑用户与批量新增")
    void onlyAdminCanManage() throws Exception {
        String leader = login("liyue");

        JsonNode edit = put("/sys/users/6", leader,
                "{\"realName\":\"孙琪\",\"role\":\"EMPLOYEE\",\"deptId\":2}");
        assertThat(edit.path("code").asInt()).isEqualTo(403);

        JsonNode batch = post("/sys/users/batch", leader,
                "{\"password\":\"abc123456\",\"deptId\":2,\"users\":[{\"username\":\"x\",\"realName\":\"x\"}]}");
        assertThat(batch.path("code").asInt()).isEqualTo(403);
    }
}
