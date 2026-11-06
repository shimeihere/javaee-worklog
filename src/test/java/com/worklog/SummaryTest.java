package com.worklog;

import com.fasterxml.jackson.databind.JsonNode;
import com.worklog.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 汇总中心：四维查询 + Excel 导出 + 明细抽屉（含四角色权限）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("汇总中心")
class SummaryTest extends ApiTestSupport {

    @Test
    @DisplayName("按人：高管看到全部成员，字段齐全且按提交率升序")
    void userDimension() throws Exception {
        JsonNode data = get("/summary", login("boss"),
                Map.of("dimension", "user", "size", "50")).path("data");

        assertThat(data.path("total").asLong()).isGreaterThanOrEqualTo(8);
        JsonNode first = data.path("records").get(0);
        assertThat(first.path("dimension").asText()).isEqualTo("user");
        assertThat(first.path("name").asText()).isNotBlank();
        assertThat(first.path("expected").asInt()).isPositive();
        assertThat(first.path("rate").asInt()).isBetween(0, 100);

        int previous = -1;
        for (JsonNode row : data.path("records")) {
            assertThat(row.path("rate").asInt()).isGreaterThanOrEqualTo(previous);
            previous = row.path("rate").asInt();
        }
    }

    @Test
    @DisplayName("按项目：带负责人、部门、参与人数")
    void projectDimension() throws Exception {
        JsonNode data = get("/summary", login("boss"),
                Map.of("dimension", "project", "size", "50")).path("data");

        assertThat(data.path("total").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode first = data.path("records").get(0);
        assertThat(first.path("dimension").asText()).isEqualTo("project");
        assertThat(first.path("sub").asText()).isNotBlank();
        assertThat(first.path("memberCount").asInt()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("按部门：人数×工作日 作为分母")
    void deptDimension() throws Exception {
        // 固定 10-26 ~ 10-29（周一到周四，4 个工作日），避免依赖默认区间
        JsonNode data = get("/summary", login("boss"),
                Map.of("dimension", "dept", "size", "50",
                        "startDate", "2026-10-26", "endDate", "2026-10-29")).path("data");

        assertThat(data.path("total").asLong()).isGreaterThanOrEqualTo(2);
        JsonNode first = data.path("records").get(0);
        assertThat(first.path("dimension").asText()).isEqualTo("dept");
        assertThat(first.path("expected").asInt())
                .isEqualTo(first.path("memberCount").asInt() * 4);
    }

    @Test
    @DisplayName("按时间：返回区间内每一天，且最近日期先显示（含没有提交的周末）")
    void dateDimension() throws Exception {
        JsonNode data = get("/summary", login("boss"),
                Map.of("dimension", "date", "size", "60",
                        "startDate", "2026-10-19", "endDate", "2026-10-29")).path("data");

        assertThat(data.path("total").asLong()).isEqualTo(11);
        JsonNode first = data.path("records").get(0);
        assertThat(first.path("name").asText()).as("最近日期排第一").isEqualTo("2026-10-29");
        assertThat(first.path("sub").asText()).startsWith("星期");

        // 整列按日期倒序，不会出现"翻到最后一页才是今天"
        String previous = null;
        for (JsonNode row : data.path("records")) {
            String date = row.path("name").asText();
            if (previous != null) {
                assertThat(date.compareTo(previous)).isLessThan(0);
            }
            previous = date;
        }
    }

    @Test
    @DisplayName("数据范围：负责人只看本部门；员工与管理员都不能访问汇总")
    void scopeAndPermission() throws Exception {
        JsonNode leader = get("/summary", login("liyue"),
                Map.of("dimension", "user", "size", "50")).path("data");
        assertThat(leader.path("total").asLong()).isEqualTo(3);

        assertThat(get("/summary", login("wangfan")).path("code").asInt()).isEqualTo(403);
        assertThat(get("/summary", login("admin")).path("code").asInt())
                .as("管理员不得查看经营汇总").isEqualTo(403);
    }

    @Test
    @DisplayName("参数校验：非法维度、开始晚于结束都要 400")
    void validation() throws Exception {
        JsonNode badDimension = get("/summary", login("boss"),
                Map.of("dimension", "week"));
        assertThat(badDimension.path("code").asInt()).isEqualTo(400);

        JsonNode badRange = get("/summary", login("boss"),
                Map.of("dimension", "user", "startDate", "2026-10-29", "endDate", "2026-10-01"));
        assertThat(badRange.path("code").asInt()).isEqualTo(400);
    }

    @Test
    @DisplayName("关键字过滤命中人名")
    void keywordFilter() throws Exception {
        JsonNode data = get("/summary", login("boss"),
                Map.of("dimension", "user", "keyword", "李")).path("data");
        assertThat(data.path("total").asLong()).isEqualTo(1);
        assertThat(data.path("records").get(0).path("name").asText()).isEqualTo("李悦");
    }

    @Test
    @DisplayName("导出 Excel：返回 xlsx 二进制，大小合理")
    void exportExcel() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(CTX + "/summary/export")
                        .param("dimension", "user")
                        .header(AUTH_HEADER, "Bearer " + login("boss")))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(result.getResponse().getContentType())
                .contains("spreadsheetml.sheet");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("attachment").contains(".xlsx");
        assertThat(bytes.length).as("xlsx 至少几 KB").isGreaterThan(2000);
        // xlsx 是 zip 包，前两个字节固定 PK
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');
    }

    // ------------------------------------------------------------------
    // 明细抽屉
    // ------------------------------------------------------------------

    @Test
    @DisplayName("明细：负责人只能看本部门；高管可看任何人；管理员一律 403")
    void userDetailPermission() throws Exception {
        String liyue = login("liyue");

        JsonNode ownDept = get("/summary/users/4/daily", liyue,
                Map.of("startDate", LocalDate.now().minusDays(30).toString(), "endDate", LocalDate.now().toString()));
        assertThat(ownDept.path("code").asInt()).isEqualTo(200);
        assertThat(ownDept.path("data").size()).isGreaterThan(0);
        JsonNode first = ownDept.path("data").get(0);
        assertThat(first.path("reportDate").asText()).isNotBlank();
        assertThat(first.path("hours").isNull()).isFalse();

        JsonNode crossDept = get("/summary/users/6/daily", liyue);
        assertThat(crossDept.path("code").asInt()).isEqualTo(403);
        assertThat(crossDept.path("message").asText()).contains("本部门");

        // 总裁监督权：高管可下钻查看任何员工明细（含跨部门）
        JsonNode execView = get("/summary/users/6/daily", login("boss"),
                Map.of("startDate", LocalDate.now().minusDays(30).toString(), "endDate", LocalDate.now().toString()));
        assertThat(execView.path("code").asInt()).as("高管可看任何人明细").isEqualTo(200);
        assertThat(execView.path("data").size()).isGreaterThan(0);

        // 管理员连汇总都进不去
        JsonNode admin = get("/summary/users/4/daily", login("admin"));
        assertThat(admin.path("code").asInt()).as("管理员不开放明细").isEqualTo(403);
    }

    @Test
    @DisplayName("周报明细：负责人看本部门、高管看任何人，跨部门负责人 403")
    void userWeeklyDetail() throws Exception {
        JsonNode weekly = get("/summary/users/4/weekly", login("liyue"),
                Map.of("startDate", LocalDate.now().minusDays(30).toString(), "endDate", LocalDate.now().toString()));
        assertThat(weekly.path("code").asInt()).isEqualTo(200);
        assertThat(weekly.path("data").size()).isGreaterThan(0);

        JsonNode first = weekly.path("data").get(0);
        assertThat(first.path("startDate").asText()).isNotBlank();
        assertThat(first.path("endDate").asText()).isNotBlank();

        // 负责人跨部门查周报被拦
        assertThat(get("/summary/users/6/weekly", login("liyue")).path("code").asInt())
                .isEqualTo(403);
        // 高管可看任何人的周报
        assertThat(get("/summary/users/4/weekly", login("boss")).path("code").asInt())
                .isEqualTo(200);
    }
}
