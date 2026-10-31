<template>
  <div class="messages">
    <header class="messages__head">
      <div>
        <h1>消息中心</h1>
        <p>催报提醒与系统通知都在这里</p>
      </div>
      <el-button :disabled="unread === 0" :loading="marking" @click="handleMarkAll">
        全部标为已读
      </el-button>
    </header>

    <section class="card">
      <div class="filters">
        <el-radio-group v-model="filters.isRead" class="seg" @change="search">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button :value="0">未读</el-radio-button>
          <el-radio-button :value="1">已读</el-radio-button>
        </el-radio-group>

        <el-select v-model="filters.msgType" class="filter" placeholder="全部类型" clearable @change="search">
          <el-option label="催报提醒" :value="1" />
          <el-option label="系统通知" :value="2" />
        </el-select>

        <span class="filters__hint">共 {{ total }} 条 · {{ unread }} 条未读</span>
      </div>

      <ul v-loading="loading" class="list">
        <li
          v-for="item in list"
          :key="item.id"
          class="msg"
          :class="{ 'is-unread': item.isRead === 0 }"
          @click="handleRead(item)"
        >
          <span class="msg__dot" :class="item.msgType === 1 ? 'is-remind' : 'is-system'" />
          <div class="msg__body">
            <div class="msg__top">
              <b>{{ item.title }}</b>
              <span class="msg__type">{{ item.msgTypeName }}</span>
            </div>
            <p class="msg__content">{{ item.content }}</p>
          </div>
          <div class="msg__right">
            <time>{{ formatTime(item.createTime) }}</time>
            <span v-if="item.isRead === 0" class="msg__action">标为已读</span>
          </div>
        </li>

        <li v-if="!loading && list.length === 0" class="empty">还没有消息</li>
      </ul>

      <footer class="foot">
        <el-pagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="onSizeChange"
        />
      </footer>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMessages, getUnreadCount, markAllMessagesRead, markMessageRead } from '@/api/message'

const list = ref([])
const total = ref(0)
const unread = ref(0)
const loading = ref(false)
const marking = ref(false)

const filters = reactive({
  page: 1,
  size: 10,
  isRead: undefined,
  msgType: undefined
})

async function load() {
  loading.value = true
  try {
    const res = await getMessages({
      page: filters.page,
      size: filters.size,
      isRead: filters.isRead,
      msgType: filters.msgType
    })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
    await loadUnread()
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadUnread() {
  try {
    const res = await getUnreadCount()
    unread.value = res.data?.count || 0
  } catch {
    /* ignore */
  }
}

function search() {
  filters.page = 1
  load()
}

function onSizeChange() {
  filters.page = 1
  load()
}

async function handleRead(item) {
  if (item.isRead === 1) return
  try {
    await markMessageRead(item.id)
    item.isRead = 1
    unread.value = Math.max(unread.value - 1, 0)
    ElMessage.success('已标记为已读')
  } catch {
    /* 拦截器已提示 */
  }
}

async function handleMarkAll() {
  marking.value = true
  try {
    const res = await markAllMessagesRead()
    ElMessage.success(`已将 ${res.data ?? 0} 条消息标为已读`)
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    marking.value = false
  }
}

function formatTime(value) {
  if (!value) return ''
  return String(value).slice(5, 16).replace('T', ' ')
}

onMounted(load)
</script>

<style scoped>
.messages {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 1080px;
  margin: 0 auto;
}

.messages__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 2px 0;
}

.messages__head h1 {
  margin: 0;
  color: var(--wl-text);
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 1px;
}

.messages__head p {
  margin: 8px 0 0;
  color: var(--wl-muted);
  font-size: 12px;
}

.card {
  padding: 16px 18px 10px;
  border-radius: 16px;
  background: var(--wl-card);
  box-shadow:
    0 0 0 1px rgba(63, 74, 90, 0.05),
    0 14px 32px -26px rgba(63, 74, 90, 0.5);
}

.filters {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.seg :deep(.el-radio-button__inner) {
  border-color: rgba(63, 74, 90, 0.12);
  background: #fff;
  color: rgba(40, 38, 36, 0.6);
  font-size: 13px;
  transition: all 0.3s var(--wl-ease);
}

.seg :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--wl-primary);
  border-color: var(--wl-primary);
  color: #fff;
}

.filter {
  width: 140px;
}

.filters__hint {
  margin-left: auto;
  color: rgba(40, 38, 36, 0.36);
  font-size: 11px;
}

/* ---------------- 列表 ---------------- */
.list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
  min-height: 240px;
}

.msg {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition:
    background-color 0.26s var(--wl-ease),
    transform 0.34s var(--wl-spring);
}

.msg + .msg {
  border-top: 1px solid rgba(63, 74, 90, 0.05);
}

.msg:hover {
  background: rgba(63, 74, 90, 0.035);
  transform: translateX(2px);
}

.msg__dot {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  margin-top: 6px;
  border-radius: 50%;
  background: rgba(63, 74, 90, 0.15);
}

.msg.is-unread .msg__dot.is-remind {
  background: var(--wl-camel);
  box-shadow: 0 0 0 4px rgba(185, 154, 122, 0.14);
}

.msg.is-unread .msg__dot.is-system {
  background: var(--wl-violet);
  box-shadow: 0 0 0 4px rgba(122, 106, 147, 0.12);
}

.msg__body {
  flex: 1;
  min-width: 0;
}

.msg__top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.msg__top b {
  color: var(--wl-text);
  font-size: 13px;
  font-weight: 600;
}

.msg.is-unread .msg__top b {
  color: var(--wl-primary);
}

.msg__type {
  padding: 1px 7px;
  border-radius: 5px;
  background: rgba(63, 74, 90, 0.06);
  color: rgba(40, 38, 36, 0.5);
  font-size: 10px;
}

.msg__content {
  margin: 5px 0 0;
  color: rgba(40, 38, 36, 0.5);
  font-size: 12px;
  line-height: 1.7;
}

.msg__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex: 0 0 auto;
}

.msg__right time {
  color: rgba(40, 38, 36, 0.32);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.msg__action {
  color: var(--wl-violet);
  font-size: 11px;
  opacity: 0;
  transition: opacity 0.24s var(--wl-ease);
}

.msg:hover .msg__action {
  opacity: 1;
}

.empty {
  padding: 60px 0;
  color: rgba(40, 38, 36, 0.34);
  font-size: 12px;
  text-align: center;
}

.foot {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 6px;
}
</style>
