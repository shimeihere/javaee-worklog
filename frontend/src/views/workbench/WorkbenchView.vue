<template>
  <div class="workbench">
    <!-- 问候区 -->
    <section class="hero">
      <div class="hero__main">
        <p class="hero__date">{{ dateText }}</p>
        <h1 class="hero__greeting">
          {{ greeting }}，<span class="hero__name">{{ user?.realName || '' }}</span>
        </h1>
        <p class="hero__sub">{{ heroSub }}</p>
      </div>

      <div v-if="canFill" class="hero__actions">
        <el-button type="primary" size="large" @click="goFill">
          {{ summary?.todaySubmitted ? '查看今日日报' : '去写日报' }}
        </el-button>
      </div>
    </section>

    <!-- 今日未填提示 -->
    <transition name="slide">
      <section v-if="canFill && !loading && summary && !summary.todaySubmitted" class="notice">
        <span class="notice__dot" />
        <div class="notice__text">
          <b>{{ summary.todayStatus === 0 ? '今天的日报还停在草稿' : '今天还没有填报日报' }}</b>
          <span>建议在 18:30 前提交，别让今天的产出只留在记忆里。</span>
        </div>
        <el-button link type="primary" @click="goFill">立即填报 →</el-button>
      </section>
    </transition>

    <!-- KPI -->
    <section class="kpi-grid">
      <template v-if="loading">
        <div v-for="i in 4" :key="i" class="kpi kpi--skeleton">
          <el-skeleton :rows="2" animated />
        </div>
      </template>
      <template v-else>
        <article v-for="kpi in kpis" :key="kpi.label" class="kpi" :class="`kpi--${kpi.tone}`">
          <p class="kpi__label">{{ kpi.label }}</p>
          <p class="kpi__value">
            {{ kpi.value }}<span v-if="kpi.unit" class="kpi__unit">{{ kpi.unit }}</span>
          </p>
          <p class="kpi__hint">{{ kpi.hint }}</p>
        </article>
      </template>
    </section>

    <!-- 快捷入口 -->
    <section class="section">
      <header class="section__head">
        <h2>快捷入口</h2>
        <span class="section__hint">常用动作一步直达</span>
      </header>

      <div class="quick">
        <button
          v-for="item in quickLinks"
          :key="item.label"
          class="quick__item"
          type="button"
          @click="router.push({ name: item.to, query: item.query })"
        >
          <span class="quick__icon">
            <el-icon><component :is="item.icon" /></el-icon>
          </span>
          <span class="quick__text">
            <b>{{ item.label }}</b>
            <small>{{ item.desc }}</small>
          </span>
          <el-icon class="quick__arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { getDashboardSummary } from '@/api/dashboard'
import { getUser } from '@/utils/auth'

const router = useRouter()
const user = getUser()

const loading = ref(true)
const summary = ref(null)

const WEEK_TEXT = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
const now = new Date()

const dateText = `${now.getMonth() + 1} 月 ${now.getDate()} 日 · ${WEEK_TEXT[now.getDay()]}`

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 5) return '夜深了'
  if (hour < 9) return '早上好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const canFill = computed(() => ['EMPLOYEE', 'DEPT_LEADER'].includes(user?.role))
const isAdmin = computed(() => user?.role === 'ADMIN')

const heroSub = computed(() => {
  const s = summary.value
  if (!s) return '正在同步你的工作数据…'

  // 管理员只看系统运行情况，不接触任何业务数据
  if (isAdmin.value) {
    return '系统运行概览 · 只做账号与配置管理'
  }

  // 高管不填报，用组织/项目视角描述
  if (!canFill.value) {
    if (s.team) {
      return `全公司今天已有 ${s.team.submittedToday} 人提交，提交率 ${s.team.submitRate}%`
    }
    return '这里是你的工作概览'
  }

  if (s.todaySubmitted) {
    const remain = Math.max(s.week.workdays - s.week.submittedDays, 0)
    return remain > 0 ? `今日日报已提交 · 本周还有 ${remain} 个工作日待填` : '今日日报已提交 · 本周工作日已全部完成'
  }
  return s.todayStatus === 0 ? '今日日报还是草稿，记得提交' : '今日日报还没开始写'
})

const kpis = computed(() => {
  const s = summary.value
  if (!s) return []

  // 管理员工作台：只有系统指标
  if (isAdmin.value) {
    return [
      {
        label: '系统总人数',
        value: s.systemTotalUsers ?? 0,
        unit: '人',
        hint: '全部账号（含禁用）',
        tone: 'primary'
      },
      {
        label: '今日活跃',
        value: s.todayActiveUsers ?? 0,
        unit: '人',
        hint: '今天有填报记录的人数',
        tone: 'violet'
      }
    ]
  }

  const list = []

  if (s.team) {
    list.push({
      label: '团队今日提交',
      value: s.team.submitRate,
      unit: '%',
      hint: `${s.team.submittedToday}/${s.team.memberCount} 人已提交`,
      tone: 'primary'
    })
  }

  // 不填报的角色（高管/管理员）不展示个人提交率
  if (canFill.value) {
    list.push(
      {
        label: '本周提交率',
        value: s.week.submitRate,
        unit: '%',
        hint: `已提交 ${s.week.submittedDays}/${s.week.workdays} 个工作日`,
        tone: 'primary'
      },
      {
        label: '本月提交率',
        value: s.month.submitRate,
        unit: '%',
        hint: `已提交 ${s.month.submittedDays} 天 · 逾期 ${s.month.overdueDays} 天`,
        tone: 'violet'
      },
      {
        label: '待提交草稿',
        value: s.pendingDrafts,
        unit: '份',
        hint: s.pendingDrafts > 0 ? '有草稿没提交，建议尽快补全' : '没有堆积的草稿',
        tone: 'camel'
      }
    )
  }

  list.push(
    {
      label: '未读消息',
      value: s.unreadMessages,
      unit: '条',
      hint: s.unreadMessages > 0 ? '有新的催报或系统通知' : '消息都看完了',
      tone: 'plain'
    },
    {
      label: '我的项目',
      value: s.projectCount,
      unit: '个',
      hint: '按你的数据范围统计',
      tone: 'plain'
    }
  )

  return list
})

const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(
  now.getDate()
).padStart(2, '0')}`

const quickLinks = computed(() => {
  // 管理员的工作台只留系统设置入口
  if (isAdmin.value) {
    return [{ label: '系统设置', desc: '账号、部门与项目', icon: 'Setting', to: 'system' }]
  }

  const list = [
    {
      label: '写日报',
      desc: '记录今天的进展',
      icon: 'EditPen',
      to: 'daily-report',
      query: { date: todayStr },
      roles: ['EMPLOYEE', 'DEPT_LEADER']
    },
    {
      label: '写周报',
      desc: '汇总本周产出',
      icon: 'Document',
      to: 'weekly-report',
      roles: ['EMPLOYEE', 'DEPT_LEADER']
    },
    {
      label: '汇总中心',
      desc: '四维数据与导出',
      icon: 'Tickets',
      to: 'summary',
      roles: ['EXECUTIVE', 'DEPT_LEADER']
    },
    {
      label: '数据看板',
      desc: '提交率与趋势',
      icon: 'DataLine',
      to: 'data-board',
      roles: ['EXECUTIVE', 'DEPT_LEADER']
    },
    { label: '消息中心', desc: '催报与通知', icon: 'Bell', to: 'messages' }
  ]
  return list.filter((item) => !item.roles || item.roles.includes(user?.role))
})

function goFill() {
  // 带上今天，填报页会直接选中当天
  router.push({ name: 'daily-report', query: { date: todayStr } })
}

async function loadSummary() {
  loading.value = true
  try {
    const res = await getDashboardSummary()
    summary.value = res.data
  } catch {
    /* 拦截器已提示；保留空态 */
  } finally {
    loading.value = false
  }
}

onMounted(loadSummary)
</script>

<style scoped>
.workbench {
  display: flex;
  flex-direction: column;
  gap: 22px;
  max-width: 1240px;
  margin: 0 auto;
}

/* ---------------- 问候 ---------------- */
.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 6px 2px 0;
}

.hero__date {
  margin: 0 0 8px;
  color: var(--wl-muted);
  font-size: 12px;
  letter-spacing: 1.2px;
}

.hero__greeting {
  margin: 0;
  color: var(--wl-text);
  font-size: 26px;
  font-weight: 600;
  letter-spacing: 1px;
}

.hero__name {
  color: var(--wl-primary);
}

.hero__sub {
  margin: 10px 0 0;
  color: var(--wl-muted);
  font-size: 13px;
}

.hero__actions :deep(.el-button) {
  height: 42px;
  padding: 0 22px;
  letter-spacing: 2px;
}

/* ---------------- 未填提示 ---------------- */
.notice {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  border-radius: 12px;
  border: 1px solid rgba(185, 154, 122, 0.32);
  background: linear-gradient(90deg, rgba(185, 154, 122, 0.12), rgba(185, 154, 122, 0.04));
}

.notice__dot {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--wl-camel);
  box-shadow: 0 0 0 5px rgba(185, 154, 122, 0.16);
}

.notice__text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.notice__text b {
  color: #8a6d4f;
  font-size: 13px;
  font-weight: 600;
}

.notice__text span {
  color: rgba(40, 38, 36, 0.5);
  font-size: 12px;
}

/* ---------------- KPI ---------------- */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 14px;
}

.kpi {
  position: relative;
  padding: 18px 18px 16px;
  border-radius: 16px;
  background: var(--wl-card);
  box-shadow:
    0 0 0 1px rgba(63, 74, 90, 0.05),
    0 12px 28px -22px rgba(63, 74, 90, 0.45);
  transition:
    transform 0.42s var(--wl-spring),
    box-shadow 0.42s var(--wl-spring);
}

.kpi:hover {
  transform: translateY(-3px);
  box-shadow:
    0 0 0 1px rgba(63, 74, 90, 0.07),
    0 20px 38px -24px rgba(63, 74, 90, 0.5);
}

.kpi::before {
  content: '';
  position: absolute;
  left: 18px;
  top: 0;
  width: 22px;
  height: 2px;
  border-radius: 2px;
  background: rgba(63, 74, 90, 0.2);
}

.kpi--primary::before {
  background: var(--wl-primary);
}

.kpi--violet::before {
  background: var(--wl-violet);
}

.kpi--camel::before {
  background: var(--wl-camel);
}

.kpi--skeleton {
  min-height: 104px;
}

.kpi__label {
  margin: 6px 0 0;
  color: var(--wl-muted);
  font-size: 12px;
  letter-spacing: 0.6px;
}

.kpi__value {
  margin: 8px 0 0;
  color: var(--wl-text);
  font-size: 28px;
  font-weight: 600;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.kpi__unit {
  margin-left: 3px;
  color: var(--wl-muted);
  font-size: 13px;
  font-weight: 400;
}

.kpi__hint {
  margin: 8px 0 0;
  color: rgba(40, 38, 36, 0.42);
  font-size: 12px;
}

/* ---------------- 快捷入口 ---------------- */
.section__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
  padding: 0 2px;
}

.section__head h2 {
  margin: 0;
  color: var(--wl-text);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
}

.section__hint {
  color: rgba(40, 38, 36, 0.38);
  font-size: 12px;
}

.quick {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.quick__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid rgba(63, 74, 90, 0.06);
  border-radius: 14px;
  background: var(--wl-card);
  cursor: pointer;
  text-align: left;
  transition:
    transform 0.42s var(--wl-spring),
    border-color 0.3s var(--wl-ease),
    box-shadow 0.42s var(--wl-spring);
}

.quick__item:hover {
  transform: translateY(-3px);
  border-color: rgba(122, 106, 147, 0.32);
  box-shadow: 0 18px 32px -26px rgba(63, 74, 90, 0.7);
}

.quick__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 11px;
  background: rgba(63, 74, 90, 0.06);
  color: var(--wl-primary);
  font-size: 17px;
}

.quick__text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.quick__text b {
  color: var(--wl-text);
  font-size: 13px;
  font-weight: 600;
}

.quick__text small {
  color: rgba(40, 38, 36, 0.42);
  font-size: 12px;
}

.quick__arrow {
  color: rgba(63, 74, 90, 0.24);
  font-size: 13px;
  transition: transform 0.3s var(--wl-ease);
}

.quick__item:hover .quick__arrow {
  transform: translateX(3px);
  color: var(--wl-violet);
}

/* ---------------- 动效 ---------------- */
.slide-enter-active,
.slide-leave-active {
  transition:
    opacity 0.34s var(--wl-spring),
    transform 0.34s var(--wl-spring);
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (max-width: 720px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero__greeting {
    font-size: 22px;
  }
}
</style>
