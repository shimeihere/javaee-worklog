package com.worklog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.worklog.entity.SysMessage;
import com.worklog.mapper.SysMessageMapper;
import com.worklog.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第十一阶段验收测试：消息中心（列表 / 已读 / 全部已读 / 最近未读）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("消息中心")
class MessageCenterTest extends ApiTestSupport {

    @Autowired
    private SysMessageMapper sysMessageMapper;

    @Test
    @DisplayName("只看自己的消息，且未读筛选、角标、列表三者口径一致")
    void onlyOwnMessages() throws Exception {
        String zhaolin = login("zhaolin");

        JsonNode all = get("/messages", zhaolin, Map.of("size", "50")).path("data");
        assertThat(all.path("total").asLong()).isGreaterThanOrEqualTo(1);
        all.path("records").forEach(row -> assertThat(row.path("id").asLong()).isPositive());

        JsonNode unread = get("/messages", zhaolin, Map.of("size", "50", "isRead", "0")).path("data");
        assertThat(unread.path("total").asLong()).as("开发期可能有多条催报").isGreaterThanOrEqualTo(1);
        unread.path("records").forEach(row -> {
            assertThat(row.path("isRead").asInt()).isZero();
            assertThat(row.path("msgType").asInt()).isEqualTo(1);
            assertThat(row.path("msgTypeName").asText()).isEqualTo("催报提醒");
        });

        assertThat(get("/messages/unread-count", zhaolin).path("data").path("count").asLong())
                .as("角标应等于未读列表总数")
                .isEqualTo(unread.path("total").asLong());

        // 管理员的消息列表里不应混入别人的消息：数量等于他自己未读数
        String admin = login("admin");
        long adminBadge = get("/messages/unread-count", admin).path("data").path("count").asLong();
        long adminUnread = get("/messages", admin, Map.of("size", "50", "isRead", "0"))
                .path("data").path("total").asLong();
        assertThat(adminUnread).isEqualTo(adminBadge);
    }

    @Test
    @DisplayName("最近未读：最多返回 limit 条，且都是未读")
    void recentUnread() throws Exception {
        insertMessages(1L, 3);
        String admin = login("admin");

        JsonNode data = get("/messages/recent-unread", admin, Map.of("limit", "2")).path("data");
        assertThat(data.size()).isEqualTo(2);
        data.forEach(row -> assertThat(row.path("isRead").asInt()).isZero());

        assertThat(get("/messages/unread-count", admin).path("data").path("count").asLong())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("标记单条已读后未读数下降")
    void markRead() throws Exception {
        insertMessages(1L, 2);
        String admin = login("admin");
        long messageId = get("/messages", admin, Map.of("size", "10")).path("data")
                .path("records").get(0).path("id").asLong();

        JsonNode result = put("/messages/" + messageId + "/read", admin);
        assertThat(result.path("code").asInt()).isEqualTo(200);
        assertThat(get("/messages/unread-count", admin).path("data").path("count").asLong())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("全部已读返回影响条数，之后未读为 0")
    void markAllRead() throws Exception {
        insertMessages(1L, 3);
        String admin = login("admin");

        JsonNode result = put("/messages/read-all", admin);
        assertThat(result.path("code").asInt()).isEqualTo(200);
        assertThat(result.path("data").asInt()).isEqualTo(3);

        assertThat(get("/messages/unread-count", admin).path("data").path("count").asLong()).isZero();
    }

    @Test
    @DisplayName("不能标记别人的消息")
    void cannotMarkOthers() throws Exception {
        long othersMessageId = sysMessageMapper.selectOne(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getUserId, 5L)
                .last("limit 1")).getId();

        JsonNode result = put("/messages/" + othersMessageId + "/read", login("admin"));
        assertThat(result.path("code").asInt()).isEqualTo(404);
    }

    private void insertMessages(Long userId, int count) {
        for (int i = 0; i < count; i++) {
            SysMessage message = new SysMessage();
            message.setUserId(userId);
            message.setTitle("测试通知 " + (i + 1));
            message.setContent("用于测试的站内信内容");
            message.setMsgType(SysMessage.TYPE_SYSTEM);
            message.setIsRead(0);
            sysMessageMapper.insert(message);
        }
    }
}
