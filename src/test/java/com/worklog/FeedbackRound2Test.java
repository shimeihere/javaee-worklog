package com.worklog;

import com.fasterxml.jackson.databind.JsonNode;
import com.worklog.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第二轮甲方反馈：单部门看板换项目进度、账号状态收进编辑、以及核查出的几处问题。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("第二轮反馈")
class FeedbackRound2Test extends ApiTestSupport {

    @Test
    @DisplayName("看板项目口径：没填报的项目也要出现；归档后不再出现")
    void projectProgressIncludesIdleAndExcludesArchived() throws Exception {
        String boss = login("boss");

        JsonNode before = get("/board/overview", boss, Map.of("range", "month"))
                .path("data").path("projectStats");
        assertThat(before.toString()).as("本期没人填报的项目也应出现在项目进度里")
                .contains("移动端体验优化");

        String admin = login("admin");
        JsonNode archived = put("/projects/4", admin,
                "{\"projectName\":\"移动端体验优化\",\"description\":\"\",\"status\":1}");
        assertThat(archived.path("code").asInt()).isEqualTo(200);
        assertThat(archived.path("data").path("status").asInt()).isEqualTo(1);

        JsonNode after = get("/board/overview", boss, Map.of("range", "month"))
                .path("data").path("projectStats");
        assertThat(after.toString()).as("归档项目不再进入项目进度").doesNotContain("移动端体验优化");
    }

    @Test
    @DisplayName("编辑项目：管理员可改名称/描述/状态，部门负责人不能跨部门改")
    void editProjectPermission() throws Exception {
        String admin = login("admin");
        JsonNode updated = put("/projects/4", admin,
                "{\"projectName\":\"移动端体验优化（二期）\",\"description\":\"补充性能基线\",\"status\":0}");
        assertThat(updated.path("code").asInt()).isEqualTo(200);
        assertThat(updated.path("data").path("projectName").asText()).isEqualTo("移动端体验优化（二期）");
        assertThat(updated.path("data").path("description").asText()).isEqualTo("补充性能基线");

        // liyue 是技术中心负责人，项目 3 属于产品设计部 -> 按"不存在"处理
        JsonNode forbidden = put("/projects/3", login("liyue"),
                "{\"projectName\":\"改名\",\"status\":0}");
        assertThat(forbidden.path("code").asInt()).isEqualTo(404);

        JsonNode invalidStatus = put("/projects/4", admin,
                "{\"projectName\":\"移动端体验优化\",\"status\":9}");
        assertThat(invalidStatus.path("code").asInt()).isEqualTo(400);
    }

    @Test
    @DisplayName("待催报名单不包含负责人本人")
    void pendingListExcludesSelf() throws Exception {
        JsonNode pending = get("/board/overview", login("liyue"), Map.of("range", "month"))
                .path("data").path("pendingUsers");

        assertThat(pending.size()).isGreaterThan(0);
        for (JsonNode user : pending) {
            assertThat(user.path("realName").asText()).as("自己催自己没有意义").isNotEqualTo("李悦");
        }
    }

    @Test
    @DisplayName("没被分配项目的员工也能提交日报（项目选填）")
    void submitDailyWithoutProject() throws Exception {
        String sunqi = login("sunqi");
        String today = LocalDate.now().toString();

        JsonNode saved = post("/reports/daily", sunqi,
                "{\"reportDate\":\"" + today + "\",\"contentDone\":\"整理部门季度资料\",\"hours\":4}");
        assertThat(saved.path("code").asInt()).isEqualTo(200);
        assertThat(saved.path("data").path("projectId").isNull()).isTrue();

        JsonNode submitted = post("/reports/daily/" + saved.path("data").path("id").asLong() + "/submit",
                sunqi, null);
        assertThat(submitted.path("code").asInt()).as("没有项目也应能提交").isEqualTo(200);
        assertThat(submitted.path("data").path("status").asInt()).isEqualTo(1);
    }
}
