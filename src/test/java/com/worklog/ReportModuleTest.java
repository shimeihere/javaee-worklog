package com.worklog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.worklog.config.UploadProperties;
import com.worklog.entity.DailyReport;
import com.worklog.mapper.DailyReportMapper;
import com.worklog.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第四阶段验收测试：M3/M4 填报模块（日报、周报、日历、附件、填报数据范围）。
 *
 * <p>全部走真实 HTTP + 真实 MySQL + 真实磁盘；类上 {@code @Transactional}
 * 让写操作在用例结束后回滚，不污染种子数据。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("M3/M4 填报模块")
class ReportModuleTest extends ApiTestSupport {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private DailyReportMapper dailyReportMapper;

    @Autowired
    private UploadProperties uploadProperties;

    // ------------------------------------------------------------------
    // 日报生命周期
    // ------------------------------------------------------------------

    @Test
    @DisplayName("日报：存草稿 → 提交 → 撤回，日历状态同步变化")
    void dailyLifecycle() throws Exception {
        String token = login("wangfan");

        JsonNode draft = post("/reports/daily", token, dailyBody(TODAY, "完成填报模块联调与自测", 6.5));
        assertThat(draft.path("code").asInt()).isEqualTo(200);
        assertThat(draft.path("data").path("status").asInt()).isZero();
        assertThat(draft.path("data").path("contentDone").asText())
                .as("中文内容应原样存取").isEqualTo("完成填报模块联调与自测");
        assertThat(draft.path("data").path("projectName").asText()).contains("周览");
        long id = draft.path("data").path("id").asLong();

        JsonNode calendar = calendarOf(token);
        assertThat(dayOf(calendar, TODAY).path("status").asInt())
                .as("今天有草稿但未提交 -> 未填(0)").isZero();

        JsonNode submitted = post("/reports/daily/" + id + "/submit", token, null);
        assertThat(submitted.path("code").asInt()).isEqualTo(200);
        assertThat(submitted.path("data").path("status").asInt()).isEqualTo(1);
        assertThat(dayOf(calendarOf(token), TODAY).path("status").asInt()).isEqualTo(1);

        JsonNode withdrawn = post("/reports/daily/" + id + "/withdraw", token, null);
        assertThat(withdrawn.path("code").asInt()).isEqualTo(200);
        assertThat(withdrawn.path("data").path("status").asInt()).isZero();
        assertThat(dayOf(calendarOf(token), TODAY).path("status").asInt()).isZero();
    }

    @Test
    @DisplayName("日历规则：已逾期 2 / 周末不算逾期 / 统计字段自洽")
    void calendarRules() throws Exception {
        // liyue 没有任何日报，适合验证"应填未填"
        String token = login("liyue");
        YearMonth month = YearMonth.from(TODAY);
        JsonNode calendar = calendarOf(token);

        assertThat(calendar.path("yearMonth").asText()).isEqualTo(month.toString());
        assertThat(calendar.path("totalDays").asInt()).isEqualTo(month.lengthOfMonth());
        assertThat(calendar.path("days").size()).isEqualTo(month.lengthOfMonth());

        // 本月第一个已过去的工作日 -> 逾期
        for (LocalDate date = month.atDay(1); date.isBefore(TODAY); date = date.plusDays(1)) {
            if (isWeekend(date)) {
                continue;
            }
            JsonNode day = dayOf(calendar, date);
            assertThat(day.path("status").asInt()).as("%s 应判为逾期", date).isEqualTo(2);
            assertThat(day.path("workday").asBoolean()).isTrue();
            break;
        }

        // 周末即使过去也不算逾期
        for (LocalDate date = month.atDay(1); date.isBefore(TODAY); date = date.plusDays(1)) {
            if (!isWeekend(date)) {
                continue;
            }
            JsonNode day = dayOf(calendar, date);
            assertThat(day.path("status").asInt()).as("%s 是周末，不应算逾期", date).isZero();
            assertThat(day.path("workday").asBoolean()).isFalse();
            break;
        }

        assertThat(calendar.path("submittedDays").asInt() + calendar.path("overdueDays").asInt())
                .isLessThanOrEqualTo(calendar.path("workdays").asInt());
        assertThat(calendar.path("submitRate").asInt()).isZero();
    }

    @Test
    @DisplayName("提交校验：内容为空拒绝提交，未来日期拒绝保存")
    void submitAndDateValidation() throws Exception {
        String token = login("wangfan");

        JsonNode blankDraft = post("/reports/daily", token,
                "{\"reportDate\":\"" + TODAY + "\",\"projectId\":1,\"contentDone\":\"  \",\"hours\":2}");
        long id = blankDraft.path("data").path("id").asLong();
        JsonNode rejected = post("/reports/daily/" + id + "/submit", token, null);
        assertThat(rejected.path("code").asInt()).isEqualTo(400);
        assertThat(rejected.path("message").asText()).contains("完成内容");

        JsonNode future = post("/reports/daily", token, dailyBody(TODAY.plusDays(1), "明天", 1));
        assertThat(future.path("code").asInt()).isEqualTo(400);
        assertThat(future.path("message").asText()).contains("未来");

        JsonNode badHours = post("/reports/daily", token,
                "{\"reportDate\":\"" + TODAY + "\",\"projectId\":1,\"contentDone\":\"超时\",\"hours\":30}");
        assertThat(badHours.path("code").asInt()).isEqualTo(400);
        assertThat(badHours.path("message").asText()).contains("24");
    }

    @Test
    @DisplayName("编辑规则：同一天重复保存是覆盖而非报错；已提交不可编辑")
    void editRules() throws Exception {
        String token = login("wangfan");

        JsonNode first = post("/reports/daily", token, dailyBody(TODAY, "第一版内容", 3));
        JsonNode second = post("/reports/daily", token, dailyBody(TODAY, "第二版内容", 4));
        assertThat(second.path("data").path("id").asLong())
                .as("同一天应命中唯一键并覆盖").isEqualTo(first.path("data").path("id").asLong());
        assertThat(second.path("data").path("contentDone").asText()).isEqualTo("第二版内容");
        assertThat(second.path("data").path("hours").asDouble()).isEqualTo(4.0);

        long id = second.path("data").path("id").asLong();
        post("/reports/daily/" + id + "/submit", token, null);

        JsonNode editSubmitted = put("/reports/daily/" + id, token, dailyBody(TODAY, "改不动", 1));
        assertThat(editSubmitted.path("code").asInt()).isEqualTo(400);
        assertThat(editSubmitted.path("message").asText()).contains("撤回");

        JsonNode duplicatedSubmit = post("/reports/daily/" + id + "/submit", token, null);
        assertThat(duplicatedSubmit.path("code").asInt()).isEqualTo(400);
    }

    // ------------------------------------------------------------------
    // 数据范围
    // ------------------------------------------------------------------

    @Test
    @DisplayName("填报数据范围：员工仅本人 / 负责人本部门 / 管理员不可见")
    void reportScopeByRole() throws Exception {
        // 管理员只做系统配置，业务单据一律 403（防泄密）
        assertThat(get("/reports/daily/page", login("admin"), Map.of("size", "100")).path("code").asInt())
                .as("管理员不得读取日报列表").isEqualTo(403);
        // 断言不写死条数：开发期会有人在页面上手动造数据，这里只验证"归属"与"下限"

        JsonNode leaderView = get("/reports/daily/page", login("liyue"), Map.of("size", "100")).path("data");
        assertThat(leaderView.path("total").asLong()).as("技术中心成员不少于 7 条").isGreaterThanOrEqualTo(7);
        leaderView.path("records").forEach(row ->
                assertThat(row.path("userId").asLong()).as("只能出现技术中心成员").isIn(2L, 4L, 5L));

        JsonNode employeeView = get("/reports/daily/page", login("wangfan"), Map.of("size", "100")).path("data");
        assertThat(employeeView.path("total").asLong()).as("王帆自己不少于 4 条").isGreaterThanOrEqualTo(4);
        employeeView.path("records").forEach(row ->
                assertThat(row.path("userId").asLong()).as("员工只能看到自己").isEqualTo(4L));

        JsonNode designView = get("/reports/daily/page", login("chenxi"), Map.of("size", "100")).path("data");
        assertThat(designView.path("total").asLong()).as("产品设计部不少于 3 条").isGreaterThanOrEqualTo(3);
        designView.path("records").forEach(row ->
                assertThat(row.path("userId").asLong()).isIn(3L, 6L, 7L));

        // 周报同样受范围控制：管理员 403，负责人本部门
        assertThat(get("/reports/weekly/page", login("admin"), Map.of("size", "100")).path("code").asInt())
                .as("管理员不得读取周报列表").isEqualTo(403);
        assertThat(get("/reports/weekly/page", login("liyue"), Map.of("size", "100"))
                .path("data").path("total").asLong()).isGreaterThanOrEqualTo(2);
        JsonNode weeklyEmployee = get("/reports/weekly/page", login("wangfan"), Map.of("size", "100"))
                .path("data");
        assertThat(weeklyEmployee.path("total").asLong()).isGreaterThanOrEqualTo(1);
        weeklyEmployee.path("records").forEach(row ->
                assertThat(row.path("userId").asLong()).isEqualTo(4L));
    }

    @Test
    @DisplayName("越权规则：只能改自己的；员工看不了别人的详情，负责人可以")
    void crossUserRules() throws Exception {
        DailyReport others = dailyReportMapper.selectOne(new LambdaQueryWrapper<DailyReport>()
                .eq(DailyReport::getUserId, 5L)
                .last("limit 1"));
        assertThat(others).isNotNull();
        long othersId = others.getId();

        JsonNode editOthers = put("/reports/daily/" + othersId, login("wangfan"),
                dailyBody(TODAY, "越权修改", 1));
        assertThat(editOthers.path("code").asInt()).isEqualTo(403);
        assertThat(editOthers.path("message").asText()).contains("自己的日报");

        JsonNode employeeDetail = get("/reports/daily/" + othersId, login("wangfan"));
        assertThat(employeeDetail.path("code").asInt()).as("员工越权按 404").isEqualTo(404);

        JsonNode leaderDetail = get("/reports/daily/" + othersId, login("liyue"));
        assertThat(leaderDetail.path("code").asInt()).as("负责人可看本部门").isEqualTo(200);
        assertThat(leaderDetail.path("data").path("userName").asText()).isEqualTo("赵琳");
    }

    // ------------------------------------------------------------------
    // 附件
    // ------------------------------------------------------------------

    @Test
    @DisplayName("附件：上传落盘 + 写库 + 详情可见 + 删除清理文件")
    void attachmentLifecycle() throws Exception {
        String token = login("wangfan");
        long reportId = post("/reports/daily", token, dailyBody(TODAY, "带附件的日报", 6))
                .path("data").path("id").asLong();

        byte[] content = "附件内容".getBytes(StandardCharsets.UTF_8);
        JsonNode uploaded = upload("/attachments/upload", token, "file", "联调截图.png", content,
                Map.of("reportType", "1", "reportId", String.valueOf(reportId)));

        assertThat(uploaded.path("code").asInt()).isEqualTo(200);
        assertThat(uploaded.path("data").path("fileName").asText()).isEqualTo("联调截图.png");
        assertThat(uploaded.path("data").path("fileType").asText()).isEqualTo("png");
        assertThat(uploaded.path("data").path("fileSize").asLong()).isEqualTo(content.length);
        String fileUrl = uploaded.path("data").path("fileUrl").asText();
        assertThat(fileUrl).startsWith("/upload/");

        Path diskFile = Paths.get(uploadProperties.getDir())
                .resolve(fileUrl.replaceFirst("^/upload/", ""));
        assertThat(Files.exists(diskFile)).as("文件应真实落盘：%s", diskFile).isTrue();
        assertThat(Files.readAllBytes(diskFile)).isEqualTo(content);

        JsonNode detail = get("/reports/daily/" + reportId, token);
        assertThat(detail.path("data").path("attachments").size()).isEqualTo(1);
        assertThat(detail.path("data").path("attachments").get(0).path("fileName").asText())
                .isEqualTo("联调截图.png");

        JsonNode list = get("/attachments", token,
                Map.of("reportType", "1", "reportId", String.valueOf(reportId)));
        assertThat(list.path("data").size()).isEqualTo(1);

        long attachmentId = uploaded.path("data").path("id").asLong();
        assertThat(delete("/attachments/" + attachmentId, token).path("code").asInt()).isEqualTo(200);
        assertThat(Files.exists(diskFile)).as("删除后磁盘文件应被清理").isFalse();
        assertThat(get("/attachments", token,
                Map.of("reportType", "1", "reportId", String.valueOf(reportId))).path("data").size()).isZero();
    }

    @Test
    @DisplayName("附件校验：超 10MB、非法扩展名、往别人日报上传都要被拦")
    void attachmentGuards() throws Exception {
        String token = login("wangfan");
        long reportId = post("/reports/daily", token, dailyBody(TODAY, "附件校验用", 2))
                .path("data").path("id").asLong();

        byte[] tooLarge = new byte[10 * 1024 * 1024 + 1];
        JsonNode oversize = upload("/attachments/upload", token, "file", "big.pdf", tooLarge,
                Map.of("reportType", "1", "reportId", String.valueOf(reportId)));
        assertThat(oversize.path("code").asInt()).isEqualTo(4004);
        assertThat(oversize.path("message").asText()).contains("10MB");

        JsonNode badType = upload("/attachments/upload", token, "file", "virus.exe", new byte[]{1, 2, 3},
                Map.of("reportType", "1", "reportId", String.valueOf(reportId)));
        assertThat(badType.path("code").asInt()).isEqualTo(400);

        DailyReport others = dailyReportMapper.selectOne(new LambdaQueryWrapper<DailyReport>()
                .eq(DailyReport::getUserId, 5L).last("limit 1"));
        JsonNode crossUpload = upload("/attachments/upload", token, "file", "偷传.png", new byte[]{1},
                Map.of("reportType", "1", "reportId", String.valueOf(others.getId())));
        assertThat(crossUpload.path("code").asInt()).isEqualTo(403);
    }

    // ------------------------------------------------------------------
    // 周报
    // ------------------------------------------------------------------

    @Test
    @DisplayName("周报：任意日期归一到周一，草稿→提交→撤回，同周覆盖")
    void weeklyLifecycle() throws Exception {
        String token = login("zhaolin");
        LocalDate monday = TODAY.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        String body = "{\"startDate\":\"" + TODAY + "\",\"projectId\":2,"
                + "\"summary\":\"本周完成主数据迁移脚本\",\"plan\":\"下周做全量演练\"}";

        JsonNode draft = post("/reports/weekly", token, body);
        assertThat(draft.path("code").asInt()).isEqualTo(200);
        assertThat(draft.path("data").path("startDate").asText()).as("应归一到周一").isEqualTo(monday.toString());
        assertThat(draft.path("data").path("endDate").asText()).isEqualTo(monday.plusDays(6).toString());
        assertThat(draft.path("data").path("status").asInt()).isZero();
        long id = draft.path("data").path("id").asLong();

        JsonNode again = post("/reports/weekly", token, body);
        assertThat(again.path("data").path("id").asLong()).as("同周重复保存应覆盖").isEqualTo(id);

        JsonNode submitted = post("/reports/weekly/" + id + "/submit", token, null);
        assertThat(submitted.path("data").path("status").asInt()).isEqualTo(1);

        JsonNode withdrawn = post("/reports/weekly/" + id + "/withdraw", token, null);
        assertThat(withdrawn.path("data").path("status").asInt()).isZero();

        JsonNode byWeek = get("/reports/weekly", token, Map.of("startDate", TODAY.toString()));
        assertThat(byWeek.path("data").path("id").asLong()).isEqualTo(id);
    }

    @Test
    @DisplayName("周报：未来周不允许填写")
    void weeklyFutureWeekRejected() throws Exception {
        LocalDate nextMonday = TODAY.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1);
        JsonNode body = post("/reports/weekly", login("zhaolin"),
                "{\"startDate\":\"" + nextMonday + "\",\"projectId\":2,\"summary\":\"提前写\",\"plan\":\"x\"}");
        assertThat(body.path("code").asInt()).isEqualTo(400);
        assertThat(body.path("message").asText()).contains("未来");
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private String dailyBody(LocalDate date, String content, double hours) {
        return "{\"reportDate\":\"" + date + "\",\"projectId\":1,"
                + "\"contentDone\":\"" + content + "\",\"contentPlan\":\"明日继续联调\","
                + "\"hours\":" + hours + "}";
    }

    private JsonNode calendarOf(String token) throws Exception {
        return get("/reports/daily/calendar", token,
                Map.of("yearMonth", YearMonth.from(TODAY).toString())).path("data");
    }

    private JsonNode dayOf(JsonNode calendar, LocalDate date) {
        for (JsonNode day : calendar.path("days")) {
            if (date.toString().equals(day.path("date").asText())) {
                return day;
            }
        }
        throw new AssertionError("日历中找不到日期 " + date);
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
