package com.worklog;

import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.worklog.entity.DailyReport;
import com.worklog.entity.SysMessage;
import com.worklog.mapper.DailyReportMapper;
import com.worklog.mapper.SysMessageMapper;
import com.worklog.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据看板：四角色权限矩阵 + 聚合口径 + 一键催报。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("数据看板")
class BoardTest extends ApiTestSupport {

    @Autowired
    private DailyReportMapper dailyReportMapper;

    @Autowired
    private SysMessageMapper sysMessageMapper;

    @Test
    @DisplayName("高管本周：全公司成员、部门对比含所有二级部门（含 0 人的）")
    void executiveWeekOverview() throws Exception {
        JsonNode data = get("/board/overview", login("boss"), Map.of("range", "week")).path("data");

        assertThat(data.path("range").asText()).isEqualTo("week");
        // 9 个账号 - 1 个禁用 = 8 个有效成员
        assertThat(data.path("memberCount").asInt()).isEqualTo(8);
        assertThat(data.path("trend").size()).isEqualTo(7);
        assertThat(data.path("heatmap").size()).isEqualTo(28);
        assertThat(data.path("submitRate").asInt()).isBetween(0, 100);
        assertThat(data.path("expectedCount").asInt())
                .isEqualTo(data.path("memberCount").asInt() * data.path("workdays").asInt());

        // 全公司所有二级部门（技术中心 / 产品设计部 / 市场运营部），根部门不计
        assertThat(data.path("deptStats").size()).isEqualTo(3);
        boolean hasMarketDept = false;
        for (JsonNode dept : data.path("deptStats")) {
            if ("市场运营部".equals(dept.path("deptName").asText())) {
                hasMarketDept = true;
            }
        }
        assertThat(hasMarketDept).as("没有在册成员的部门也要出现在对比里").isTrue();

        // 按提交率降序
        int previous = 101;
        for (JsonNode dept : data.path("deptStats")) {
            int rate = dept.path("rate").asInt();
            assertThat(rate).isLessThanOrEqualTo(previous);
            previous = rate;
        }
    }

    @Test
    @DisplayName("部门负责人：范围强制锁死本部门，只有 1 组柱状数据")
    void leaderOverview() throws Exception {
        JsonNode data = get("/board/overview", login("liyue"),
                Map.of("range", "week", "deptId", "3")).path("data");

        assertThat(data.path("memberCount").asInt()).isEqualTo(3);
        assertThat(data.path("deptStats").size()).isEqualTo(1);
        assertThat(data.path("deptStats").get(0).path("deptName").asText()).isEqualTo("技术中心");
    }

    @Test
    @DisplayName("员工与管理员访问看板都返回 403")
    void forbiddenRoles() throws Exception {
        assertThat(get("/board/overview", login("wangfan")).path("code").asInt()).isEqualTo(403);
        assertThat(get("/board/overview", login("admin")).path("code").asInt())
                .as("管理员不得查看经营大盘").isEqualTo(403);
    }

    @Test
    @DisplayName("时间范围切换：上周 / 本月 / 非法值")
    void rangeSwitching() throws Exception {
        String boss = login("boss");

        JsonNode lastWeek = get("/board/overview", boss, Map.of("range", "lastWeek")).path("data");
        assertThat(lastWeek.path("range").asText()).isEqualTo("lastWeek");
        assertThat(lastWeek.path("endDate").asText()).isNotBlank();

        JsonNode month = get("/board/overview", boss, Map.of("range", "month")).path("data");
        assertThat(month.path("range").asText()).isEqualTo("month");
        assertThat(month.path("startDate").asText()).endsWith("-01");

        JsonNode bad = get("/board/overview", boss, Map.of("range", "year"));
        assertThat(bad.path("code").asInt()).isEqualTo(400);
    }

    @Test
    @DisplayName("项目筛选只影响已提交口径，不影响分母")
    void projectFilter() throws Exception {
        String boss = login("boss");
        JsonNode all = get("/board/overview", boss, Map.of("range", "month")).path("data");
        JsonNode filtered = get("/board/overview", boss,
                Map.of("range", "month", "projectId", "1")).path("data");

        assertThat(filtered.path("expectedCount").asInt())
                .as("分母不变").isEqualTo(all.path("expectedCount").asInt());
        assertThat(filtered.path("submittedCount").asInt())
                .isLessThanOrEqualTo(all.path("submittedCount").asInt());
    }

    @Test
    @DisplayName("高管看板不下发员工名单：待提醒为空，只给部门与项目排行")
    void executiveSeesNoPendingList() throws Exception {
        JsonNode data = get("/board/overview", login("boss"), Map.of("range", "month")).path("data");

        assertThat(data.path("pendingUsers").size())
                .as("高管不直接对个人催报").isZero();
        assertThat(data.path("projectStats").size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("负责人待提醒名单：按缺报天数降序，且带出姓名")
    void pendingList() throws Exception {
        JsonNode pending = get("/board/overview", login("liyue"), Map.of("range", "month"))
                .path("data").path("pendingUsers");

        assertThat(pending.size()).isGreaterThan(0);
        int previous = Integer.MAX_VALUE;
        for (JsonNode user : pending) {
            int missing = user.path("missingDays").asInt();
            assertThat(missing).isPositive().isLessThanOrEqualTo(previous);
            previous = missing;
            assertThat(user.path("realName").asText()).isNotBlank();
        }
    }

    @Test
    @DisplayName("待提醒名单排除今天已提交的人")
    void pendingExcludesTodaySubmitters() throws Exception {
        DailyReport report = new DailyReport();
        report.setUserId(4L);
        report.setProjectId(1L);
        report.setReportDate(LocalDate.now());
        report.setContentDone("今天已经交了");
        report.setHours(BigDecimal.valueOf(8));
        report.setStatus(1);
        dailyReportMapper.insert(report);

        JsonNode pending = get("/board/overview", login("liyue"), Map.of("range", "month"))
                .path("data").path("pendingUsers");

        boolean containsWangfan = false;
        for (JsonNode user : pending) {
            if (user.path("userId").asLong() == 4L) {
                containsWangfan = true;
            }
        }
        assertThat(containsWangfan).as("今天已提交的人不应出现在催报名单").isFalse();
    }

    // ------------------------------------------------------------------
    // 一键催报
    // ------------------------------------------------------------------

    @Test
    @DisplayName("负责人催报本部门成员，同一天重复催报会被拦下")
    void remindOncePerDay() throws Exception {
        // 先清掉今天已有的催报（开发期可能有人在页面上点过），保证从"未催过"开始
        sysMessageMapper.delete(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getUserId, 4L)
                .eq(SysMessage::getMsgType, SysMessage.TYPE_REMIND)
                .ge(SysMessage::getCreateTime, LocalDate.now().atStartOfDay()));

        String liyue = login("liyue");

        JsonNode first = post("/messages/remind", liyue, "{\"userId\":4}");
        assertThat(first.path("code").asInt()).isEqualTo(200);
        assertThat(first.path("message").asText()).contains("已发送催报");

        JsonNode second = post("/messages/remind", liyue, "{\"userId\":4}");
        assertThat(second.path("code").asInt()).isEqualTo(200);
        assertThat(second.path("message").asText()).contains("已经催过");
    }

    @Test
    @DisplayName("催报权限：只有负责人能催，且只能催本部门；高管/管理员/员工都 403")
    void remindGuards() throws Exception {
        JsonNode crossDept = post("/messages/remind", login("liyue"), "{\"userId\":3}");
        assertThat(crossDept.path("code").asInt()).isEqualTo(403);
        assertThat(crossDept.path("message").asText()).contains("本部门");

        assertThat(post("/messages/remind", login("wangfan"), "{\"userId\":5}").path("code").asInt())
                .isEqualTo(403);
        assertThat(post("/messages/remind", login("admin"), "{\"userId\":5}").path("code").asInt())
                .as("管理员不参与业务催报").isEqualTo(403);
        assertThat(post("/messages/remind", login("boss"), "{\"userId\":5}").path("code").asInt())
                .as("高管只对组织负责，不直接催个人").isEqualTo(403);

        // 负责人不能催自己
        JsonNode self = post("/messages/remind", login("liyue"), "{\"userId\":2}");
        assertThat(self.path("code").asInt()).isEqualTo(400);
        assertThat(self.path("message").asText()).contains("催自己");
    }
}
