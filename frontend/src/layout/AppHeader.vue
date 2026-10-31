<template>
  <header class="topbar">
    <div class="topbar__left">
      <button
        class="icon-btn"
        type="button"
        :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="emit('toggle')"
      >
        <el-icon>
          <component :is="collapsed ? Expand : Fold" />
        </el-icon>
      </button>

      <el-breadcrumb class="crumbs" separator="/">
        <el-breadcrumb-item v-for="crumb in breadcrumbs" :key="crumb.label" :to="crumb.to">
          {{ crumb.label }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="topbar__right">
      <el-popover
        v-model:visible="messagePanelVisible"
        placement="bottom-end"
        :width="360"
        trigger="click"
        popper-class="msg-popper"
        @show="loadRecent"
      >
        <template #reference>
          <button class="icon-btn" type="button" aria-label="消息中心">
            <el-badge :value="unread" :hidden="unread <= 0" :max="99" class="badge">
              <el-icon><Bell /></el-icon>
            </el-badge>
          </button>
        </template>

        <div class="msg-panel">
          <header class="msg-panel__head">
            <b>消息</b>
            <span>{{ unread }} 条未读</span>
          </header>

          <ul v-if="recent.length" class="msg-panel__list">
            <li
              v-for="item in recent"
              :key="item.id"
              class="msg-item"
              @click="readOne(item)"
            >
              <span
                class="msg-item__dot"
                :class="item.msgType === 1 ? 'is-remind' : 'is-system'"
              />
              <div class="msg-item__body">
                <b>{{ item.title }}</b>
                <p>{{ item.content }}</p>
              </div>
              <time>{{ shortTime(item.createTime) }}</time>
            </li>
          </ul>

          <p v-else class="msg-panel__empty">暂无未读消息</p>

          <footer class="msg-panel__foot">
            <button type="button" @click="goMessages">查看全部 →</button>
          </footer>
        </div>
      </el-popover>

      <el-dropdown trigger="click" placement="bottom-end" @command="handleCommand">
        <button class="user" type="button">
          <span class="user__avatar">{{ initial }}</span>
          <span class="user__text">
            <b>{{ user?.realName || '未登录' }}</b>
            <small>{{ user?.roleName || '' }}</small>
          </span>
          <el-icon class="user__caret"><ArrowDown /></el-icon>
        </button>

        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>{{ user?.deptName || '未分配部门' }}</el-dropdown-item>
            <el-dropdown-item command="profile">个人信息</el-dropdown-item>
            <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Bell, Expand, Fold } from '@element-plus/icons-vue'
import { logoutApi } from '@/api/auth'
import { getRecentUnread, getUnreadCount, markMessageRead } from '@/api/message'
import { clearAuth, getUser } from '@/utils/auth'

defineProps({
  collapsed: { type: Boolean, default: false }
})

const emit = defineEmits(['toggle'])

const route = useRoute()
const router = useRouter()
const user = getUser()
const initial = computed(() => (user?.realName || 'U').slice(0, 1))

/* ---------------- 未读角标 ---------------- */
const unread = ref(0)
let timer = null

async function loadUnread() {
  try {
    const res = await getUnreadCount()
    unread.value = res.data?.count || 0
  } catch {
    /* 拦截器已统一提示，角标保持上一次的值 */
  }
}

onMounted(() => {
  loadUnread()
  timer = setInterval(loadUnread, 60000)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
  }
})

/* ---------------- 面包屑 ---------------- */
const breadcrumbs = computed(() => {
  const title = route.meta?.title
  if (route.name === 'workbench') {
    return [{ label: '工作台' }]
  }
  const list = [{ label: '工作台', to: { name: 'workbench' } }]
  if (title) {
    list.push({ label: title })
  }
  return list
})

/* ---------------- 交互 ---------------- */
const recent = ref([])
const messagePanelVisible = ref(false)

async function loadRecent() {
  try {
    const res = await getRecentUnread(5)
    recent.value = res.data || []
  } catch {
    recent.value = []
  }
}

async function readOne(item) {
  try {
    await markMessageRead(item.id)
    recent.value = recent.value.filter((msg) => msg.id !== item.id)
    unread.value = Math.max(unread.value - 1, 0)
    ElMessage.success('已标记为已读')
  } catch {
    /* 拦截器已提示 */
  }
}

function goMessages() {
  messagePanelVisible.value = false
  router.push({ name: 'messages' })
}

function shortTime(value) {
  if (!value) {
    return ''
  }
  return String(value).slice(5, 16).replace('T', ' ')
}

async function handleCommand(command) {
  if (command === 'logout') {
    try {
      await logoutApi()
    } catch {
      /* 无状态登出，失败也照常清理 */
    }
    clearAuth()
    ElMessage.success('已退出登录')
    router.replace({ name: 'login' })
    return
  }
  if (command === 'profile') {
    router.push({ name: 'profile' })
  }
}
</script>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 64px;
  padding: 0 26px;
  background: rgba(247, 246, 243, 0.82);
  border-bottom: 1px solid rgba(63, 74, 90, 0.07);
  backdrop-filter: blur(12px);
}

.topbar__left,
.topbar__right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.crumbs {
  margin-left: 4px;
}

.crumbs :deep(.el-breadcrumb__inner) {
  color: rgba(40, 38, 36, 0.5);
  font-size: 13px;
  font-weight: 400;
}

.crumbs :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--wl-text);
  font-weight: 600;
}

.crumbs :deep(.el-breadcrumb__separator) {
  color: rgba(63, 74, 90, 0.24);
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: rgba(40, 38, 36, 0.62);
  cursor: pointer;
  transition:
    background-color 0.3s var(--wl-ease),
    color 0.3s var(--wl-ease),
    transform 0.38s var(--wl-spring);
}

.icon-btn:hover {
  background: rgba(63, 74, 90, 0.07);
  color: var(--wl-primary);
  transform: translateY(-1px);
}

.icon-btn:active {
  transform: translateY(0) scale(0.96);
}

/* 未读点用信号紫 */
.badge :deep(.el-badge__content) {
  background: var(--wl-violet);
  border: none;
  font-variant-numeric: tabular-nums;
}

.user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 10px 5px 5px;
  border: 1px solid rgba(63, 74, 90, 0.08);
  border-radius: 999px;
  background: #fff;
  cursor: pointer;
  transition:
    transform 0.4s var(--wl-spring),
    box-shadow 0.4s var(--wl-spring);
}

.user:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 22px -16px rgba(63, 74, 90, 0.9);
}

.user__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(145deg, #3f4a5a, #7a6a93);
  color: #fff;
  font-size: 13px;
}

.user__text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
  text-align: left;
}

.user__text b {
  color: var(--wl-text);
  font-size: 13px;
}

.user__text small {
  color: var(--wl-muted);
  font-size: 11px;
}

.user__caret {
  color: rgba(40, 38, 36, 0.4);
  font-size: 12px;
}

@media (max-width: 720px) {
  .topbar {
    padding: 0 14px;
  }

  .user__text {
    display: none;
  }
}
</style>
