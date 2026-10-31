<template>
  <aside class="sidebar" :class="{ 'is-collapsed': collapsed }">
    <div class="sidebar__brand">
      <transition name="fade" mode="out-in">
        <span v-if="!collapsed" key="word" class="brand-word">Worklog</span>
        <span v-else key="mark" class="brand-w" aria-label="Worklog">W</span>
      </transition>
    </div>

    <nav class="sidebar__nav">
      <el-tooltip
        v-for="item in navItems"
        :key="item.name"
        :content="item.label"
        :disabled="!collapsed"
        placement="right"
        :offset="16"
        :show-after="120"
      >
        <router-link
          :to="{ name: item.name }"
          class="nav-item"
          :class="{ 'is-active': route.name === item.name }"
        >
          <span class="nav-item__bar" />
          <el-icon class="nav-item__icon">
            <component :is="item.icon" />
          </el-icon>
          <transition name="fade">
            <span v-if="!collapsed" class="nav-item__label">{{ item.label }}</span>
          </transition>
        </router-link>
      </el-tooltip>
    </nav>

    <footer class="sidebar__foot">
      <transition name="fade">
        <span v-if="!collapsed" class="version">v1.0 · MVP</span>
      </transition>
    </footer>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { getUser } from '@/utils/auth'

defineProps({
  collapsed: { type: Boolean, default: false }
})

const route = useRoute()
const user = getUser()

const ALL_NAV_ITEMS = [
  { name: 'workbench', label: '工作台', icon: 'HomeFilled' },
  // 填报：员工与部门负责人
  { name: 'daily-report', label: '日报填报', icon: 'EditPen', roles: ['EMPLOYEE', 'DEPT_LEADER'] },
  { name: 'weekly-report', label: '周报填报', icon: 'Document', roles: ['EMPLOYEE', 'DEPT_LEADER'] },
  // 汇总与看板：高管看全公司、负责人看本部门；管理员不可见
  { name: 'summary', label: '汇总中心', icon: 'Tickets', roles: ['EXECUTIVE', 'DEPT_LEADER'] },
  { name: 'data-board', label: '数据看板', icon: 'DataLine', roles: ['EXECUTIVE', 'DEPT_LEADER'] },
  // 系统设置：仅管理员
  { name: 'system', label: '系统设置', icon: 'Setting', roles: ['ADMIN'] }
]

const navItems = computed(() => {
  const role = user?.role
  return ALL_NAV_ITEMS.filter((item) => !item.roles || item.roles.includes(role))
})
</script>

<style scoped>
.sidebar {
  position: sticky;
  top: 0;
  flex: 0 0 auto;
  width: 232px;
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 18px 14px 16px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.78);
  border-right: 1px solid rgba(63, 74, 90, 0.07);
  backdrop-filter: blur(10px);
  transition:
    width 0.42s var(--wl-spring),
    padding 0.42s var(--wl-spring);
}

.sidebar.is-collapsed {
  width: 76px;
  padding-left: 10px;
  padding-right: 10px;
}

/* ---------------- 品牌 ---------------- */
.sidebar__brand {
  display: flex;
  align-items: center;
  height: 44px;
  margin-bottom: 22px;
  padding: 0 6px;
}

/* 展开态：纯文字 Worklog */
.brand-word {
  font-family: 'Inter', 'HarmonyOS Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-weight: 700;
  font-size: 22px;
  letter-spacing: -0.03em;
  line-height: 1;
  color: #1a1d20;
  white-space: nowrap;
  transition: opacity 0.24s var(--wl-ease);
}

/* 折叠态：纯黑圆角方块 + 白色 W */
.brand-w {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: #1a1d20;
  color: #fff;
  font-family: 'Inter', 'HarmonyOS Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-weight: 700;
  font-size: 15px;
  line-height: 1;
  letter-spacing: 0;
  transition: transform 0.42s var(--wl-spring);
}

.sidebar.is-collapsed .sidebar__brand {
  justify-content: center;
  padding: 0;
}

.sidebar:hover .brand-w {
  transform: scale(1.06);
}

/* ---------------- 导航 ---------------- */
.sidebar__nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 44px;
  padding: 0 12px;
  border-radius: 10px;
  color: rgba(40, 38, 36, 0.66);
  text-decoration: none;
  white-space: nowrap;
  transition:
    background-color 0.3s var(--wl-ease),
    color 0.3s var(--wl-ease),
    padding 0.42s var(--wl-spring);
}

.nav-item:hover {
  background: rgba(63, 74, 90, 0.05);
  color: var(--wl-primary);
}

.nav-item.is-active {
  background: rgba(63, 74, 90, 0.08);
  color: var(--wl-primary);
  font-weight: 600;
}

/* 选中态左侧 2px 主色指示条 */
.nav-item__bar {
  position: absolute;
  left: 0;
  top: 50%;
  width: 2px;
  height: 0;
  border-radius: 2px;
  background: var(--wl-primary);
  transform: translateY(-50%);
  transition: height 0.38s var(--wl-spring);
}

.nav-item.is-active .nav-item__bar {
  height: 20px;
}

.nav-item__icon {
  flex: 0 0 auto;
  font-size: 18px;
}

.nav-item__label {
  font-size: 14px;
  letter-spacing: 0.5px;
}

.sidebar.is-collapsed .nav-item {
  justify-content: center;
  padding: 0;
}

/* ---------------- 页脚 ---------------- */
.sidebar__foot {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 28px;
}

.version {
  color: rgba(40, 38, 36, 0.32);
  font-size: 11px;
  letter-spacing: 1px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.24s var(--wl-ease);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
