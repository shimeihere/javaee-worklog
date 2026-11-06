<template>
  <div class="workbench">
    <!-- 问候区 -->
    <section class="wb-head">
      <div class="wb-head__main">
        <p class="wb-date">{{ dateText }}</p>
        <h1 class="wb-greet">{{ greeting }}，{{ user?.realName || '' }}</h1>
        <p class="wb-sub">{{ heroSub }}</p>
      </div>

      <!-- 管理员：右上角四个行内指标（不占卡片） -->
      <div v-if="isAdmin" class="wb-stats">
        <div v-for="stat in adminStats" :key="stat.label" class="wb-stat">
          <span>{{ stat.label }}</span>
          <b>{{ stat.value }}</b>
        </div>
      </div>

      <el-button v-else-if="canFill" class="wb-cta" @click="goFill">
        {{ summary?.todaySubmitted ? '查看今日日报' : '去写日报' }}
      </el-button>
    </section>

    <!-- KPI：强制 4 列 -->
    <section v-if="!isAdmin" class="kpi-grid">
      <template v-if="loading">
        <article v-for="i in 4" :key="i" class="kpi kpi--skeleton">
          <el-skeleton :rows="2" animated />
        </article>
      </template>
      <template v-else>
        <article
          v-for="kpi in kpis"
          :key="kpi.label"
          class="kpi"
          :class="[kpi.tone ? `is-${kpi.tone}` : '', { 'is-clickable': !!kpi.action }]"
          @click="kpi.action && kpi.action()"
        >
          <p class="kpi__label">{{ kpi.label }}</p>
          <p class="kpi__value">
            {{ kpi.value }}<span class="kpi__unit">{{ kpi.unit }}</span>
          </p>
          <p class="kpi__hint">{{ kpi.hint }}</p>
        </article>
      </template>
    </section>

    <div class="wb-main" :class="layoutClass">
      <!-- ================= 左列 ================= -->
      <div class="wb-col">
        <!-- 员工 / 负责人：本周节奏（圆点，无色块） -->
        <article v-if="canFill && week.length" class="card">
          <header class="card__head">
            <h2>本周节奏</h2>
            <span>{{ weekSummary }}</span>
          </header>

          <div class="rhythm">
            <button
              v-for="day in week"
              :key="day.date"
              type="button"
              class="rhythm__day"
              :class="[`is-${day.state}`, { 'is-today': day.isToday }]"
              :title="`${day.date} · ${stateText(day.state)}`"
              @click="openDay(day)"
            >
              <span class="rhythm__label">{{ day.label }}</span>
              <i class="rhythm__dot" />
            </button>
          </div>
        </article>

        <!-- 员工 / 负责人：快捷入口 -->
        <article v-if="canFill" class="card">
          <header class="card__head">
            <h2>快捷入口</h2>
            <span>常用动作一步直达</span>
          </header>

          <div class="quick" :style="{ '--quick-cols': quickLinks.length }">
            <button
              v-for="item in quickLinks"
              :key="item.label"
              class="quick__btn"
              type="button"
              @click="go(item)"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </button>
          </div>
        </article>

        <!-- 高管：在管项目 -->
        <article v-if="isExec" class="card">
          <header class="card__head card__head--line">
            <h2>在管项目</h2>
            <span>按立项天数看周期</span>
          </header>

          <p v-if="!projects.length" class="card__empty">公司里还没有进行中的项目</p>

          <ul v-else class="proj">
            <li v-for="project in projects" :key="project.id" class="proj__item">
              <div class="proj__row">
                <b>{{ project.projectName }}</b>
                <span class="pill" :class="{ 'is-late': project.overdue }">
                  {{ remainText(project) }}
                </span>
              </div>
              <div class="proj__row proj__row--sub">
                <span>立项 {{ project.elapsedDays }} 天</span>
                <span>
                  {{
                    project.plannedEndDate
                      ? `计划完成 ${project.plannedEndDate}`
                      : '未设计划完成日'
                  }}
                </span>
              </div>
            </li>
          </ul>
        </article>

        <!-- 管理员：控制台 -->
        <article v-if="isAdmin" class="card">
          <header class="card__head card__head--line">
            <h2>控制台</h2>
            <span>管理操作</span>
          </header>

          <ul class="console">
            <li
              v-for="row in consoleRows"
              :key="row.label"
              class="console__item"
              @click="router.push({ name: 'system', query: { tab: row.tab } })"
            >
              <span class="console__icon">
                <el-icon><component :is="row.icon" /></el-icon>
              </span>
              <span class="console__text">
                <b>{{ row.label }}</b>
                <small>{{ row.desc }}</small>
              </span>
              <el-icon class="console__arrow"><ArrowRight /></el-icon>
            </li>
          </ul>
        </article>
      </div>

      <!-- ================= 右列 ================= -->
      <div class="wb-col">
        <!-- 员工 / 负责人：最近提交（时间线） -->
        <article v-if="canFill" class="card">
          <header class="card__head">
            <h2>{{ isLeader ? '最近提交' : '我的最近提交' }}</h2>
            <span>最近 4 份日报</span>
          </header>

          <p v-if="!recent.items.length" class="card__empty">
            {{ recentLoading ? '正在加载…' : recent.empty }}
          </p>

          <ol v-else class="tl">
            <li v-for="(item, index) in recent.items" :key="index" class="tl__item">
              <span class="tl__dot" />
              <span class="tl__body">
                <b>{{ item.main }}</b>
                <small>{{ item.meta }}</small>
              </span>
            </li>
          </ol>
        </article>

        <!-- 高管：快捷入口（竖排） -->
        <article v-if="isExec" class="card card--fill">
          <header class="card__head">
            <h2>快捷入口</h2>
            <span>常用动作</span>
          </header>

          <ul class="quick-list">
            <li
              v-for="item in quickLinks"
              :key="item.label"
              class="quick-list__item"
              @click="go(item)"
            >
              <span class="quick-list__icon">
                <el-icon><component :is="item.icon" /></el-icon>
              </span>
              <span class="quick-list__text">
                <b>{{ item.label }}</b>
                <small>{{ item.desc }}</small>
              </span>
              <el-icon class="quick-list__arrow"><ArrowRight /></el-icon>
            </li>
          </ul>
        </article>

        <!-- 管理员：审计日志 -->
        <article v-if="isAdmin" class="card">
          <header class="card__head card__head--line">
            <h2>审计日志</h2>
            <span>最近操作记录</span>
          </header>

          <p v-if="!logs.length" class="card__empty">
            {{ recentLoading ? '正在加载…' : '还没有管理操作记录' }}
          </p>

          <ul v-else class="log">
            <li v-for="item in logs" :key="item.id" class="log__item">
              <span class="log__bar" :class="`is-${toneOf(item.action)}`" />
              <span class="log__text">
                <b>{{ item.operatorName }}</b>
                <em>{{ shortText(item.detail, 30) }}</em>
              </span>
              <span class="log__meta">
                <time>{{ timeText(item.createTime) }}</time>
                <span class="tag">{{ item.actionName }}</span>
              </span>
            </li>
          </ul>
        </article>
      </div>
    </div>

    <!-- 待提醒名单：负责人点 KPI 卡片弹出来，逐个催报 -->
    <el-dialog
      v-model="remindVisible"
      title="待提醒名单"
      width="440px"
      :show-close="true"
      class="remind-dialog"
    >
      <p v-if="remindLoading" class="remind__empty">正在加载…</p>
      <p v-else-if="!pendingUsers.length" class="remind__empty">
        本周全员已交齐，没有需要提醒的成员。
      </p>
      <ul v-else class="remind">
        <li v-for="item in pendingUsers" :key="item.userId" class="remind__item">
          <span class="remind__avatar">{{ (item.realName || '').slice(0, 1) }}</span>
          <span class="remind__text">
            <b>{{ item.realName }}</b>
            <small>{{ item.deptName || '未分配部门' }} · 缺 {{ item.missingDays }} 天</small>
          </span>
          <el-button
            size="small"
            :loading="remindingId === item.userId"
            @click="doRemind(item)"
          >
            催报
          </el-button>
        </li>
      </ul>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getAuditLogs } from '@/api/audit'
import { getBoardOverview } from '@/api/board'
import { getDashboardSummary } from '@/api/dashboard'
import { remindUser } from '@/api/message'
import { getDailyCalendar, getDailyPage } from '@/api/report'
import { listProjects } from '@/api/project'
import { getUser } from '@/utils/auth'

const router = useRouter()
const user = getUser()

const loading = ref(true)
const summary = ref(null)

const week = ref([])
const projects = ref([])
const logs = ref([])
const recentLoading = ref(true)
const recent = ref({ items: [], empty: '还没有写过日报' })

const remindVisible = ref(false)
const remindLoading = ref(false)
const remindingId = ref(null)
const pendingUsers = ref([])

const isAdmin = computed(() => user?.role === 'ADMIN')
const isExec = computed(() => user?.role === 'EXECUTIVE')
const isLeader = computed(() => user?.role === 'DEPT_LEADER')
const canFill = computed(() => ['EMPLOYEE', 'DEPT_LEADER'].includes(user?.role))

const layoutClass = computed(() => {
  if (isAdmin.value) return 'wb-main--admin'
  if (isExec.value) return 'wb-main--exec'
  return 'wb-main--fill'
})

function shortText(text, limit) {
  if (!text) return '—'
  const value = String(text).replace(/\s+/g, ' ').trim()
  return value.length > limit ? `${value.slice(0, limit)}…` : value
}

/* ---------------- 问候区 ---------------- */

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

const heroSub = computed(() => {
  const s = summary.value
  if (!s) return '正在同步你的工作数据…'

  if (isAdmin.value) {
    return '系统运行状态良好。请在这里完成日常管理配置。'
  }

  if (!canFill.value) {
    if (s.team) {
      return `全公司今天已有 ${s.team.submittedToday} 人提交，提交率 ${s.team.submitRate}%`
    }
    return '这里是你的工作概览'
  }

  if (s.todaySubmitted) {
    const remain = Math.max(s.week.workdays - s.week.submittedDays, 0)
    return remain > 0
      ? `今日日报已提交 · 本周还有 ${remain} 个工作日待填`
      : '今日日报已提交 · 本周工作日已全部完成'
  }
  return s.todayStatus === 0 ? '今日日报还是草稿，记得提交' : '今日日报还没开始写'
})

/* ---------------- KPI（每个角色固定 4 张） ---------------- */

const kpis = computed(() => {
  const s = summary.value
  if (!s) return []

  if (isExec.value) {
    const delta = s.overdueDelta ?? 0
    return [
      {
        label: '团队今日提交',
        value: s.team?.submitRate ?? 0,
        unit: '%',
        hint: `${s.team?.submittedToday ?? 0} / ${s.team?.memberCount ?? 0} 人已提交`
      },
      {
        label: '未读消息',
        value: s.unreadMessages ?? 0,
        unit: '条',
        hint: s.unreadMessages > 0 ? '有新的催报或系统通知' : '消息都看完了'
      },
      {
        label: '公司项目',
        value: s.projectCount ?? 0,
        unit: '个',
        hint: '全公司项目数'
      },
      {
        label: '全公司逾期',
        value: s.overdueCount ?? 0,
        unit: '份',
        hint:
          delta < 0 ? `较昨日减少 ${-delta} 份` : delta > 0 ? `较昨日增加 ${delta} 份` : '与昨日持平',
        tone: (s.overdueCount ?? 0) > 0 ? 'danger' : ''
      }
    ]
  }

  if (isLeader.value) {
    const remind = s.remindCount ?? 0
    return [
      {
        label: '团队今日提交',
        value: s.team?.submitRate ?? 0,
        unit: '%',
        hint: `${s.team?.submittedToday ?? 0} / ${s.team?.memberCount ?? 0} 人已提交`
      },
      {
        label: '本周提交率',
        value: s.week.submitRate,
        unit: '%',
        hint: `已提交 ${s.week.submittedDays}/${s.week.workdays} 个工作日`
      },
      {
        label: '待提醒名单',
        value: remind,
        unit: '份',
        hint: remind > 0 ? '点击查看可以催报的成员' : '本周全员已交',
        tone: remind > 0 ? 'warm' : '',
        action: openRemind
      },
      {
        label: '未读消息',
        value: s.unreadMessages ?? 0,
        unit: '条',
        hint: s.unreadMessages > 0 ? '有新的催报或系统通知' : '消息都看完了'
      }
    ]
  }

  const drafts = s.pendingDrafts ?? 0
  return [
    {
      label: '本周提交率',
      value: s.week.submitRate,
      unit: '%',
      hint: `已提交 ${s.week.submittedDays}/${s.week.workdays} 个工作日`
    },
    {
      label: '待提交草稿',
      value: drafts,
      unit: '份',
      hint: drafts > 0 ? '有草稿未提交，建议尽快补全' : '没有堆积的草稿',
      tone: drafts > 0 ? 'warm' : ''
    },
    {
      label: '未读消息',
      value: s.unreadMessages ?? 0,
      unit: '条',
      hint: s.unreadMessages > 0 ? '有新的催报或系统通知' : '消息都看完了'
    },
    {
      label: '我的项目',
      value: s.projectCount ?? 0,
      unit: '个',
      hint: '正在参与的项目数'
    }
  ]
})

const adminStats = computed(() => {
  const s = summary.value
  return [
    { label: '总人数', value: s?.systemTotalUsers ?? 0 },
    { label: '今日活跃', value: s?.todayActiveUsers ?? 0 },
    { label: '部门', value: s?.deptCount ?? 0 },
    { label: '项目', value: s?.companyProjectCount ?? 0 }
  ]
})

/* ---------------- 快捷入口 ---------------- */

const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(
  now.getDate()
).padStart(2, '0')}`

const quickLinks = computed(() => {
  if (isExec.value) {
    return [
      { label: '汇总中心', desc: '四维数据与导出', icon: 'Tickets', to: 'summary' },
      { label: '数据看板', desc: '提交率与趋势', icon: 'DataLine', to: 'data-board' },
      { label: '消息中心', desc: '催报与通知', icon: 'Bell', to: 'messages' }
    ]
  }

  if (isLeader.value) {
    return [
      { label: '写日报', desc: '记录今天的进展', icon: 'EditPen', to: 'daily-report', query: { date: todayStr } },
      { label: '写周报', desc: '汇总本周产出', icon: 'Document', to: 'weekly-report' },
      { label: '数据看板', desc: '提交率与趋势', icon: 'DataLine', to: 'data-board' },
      { label: '消息中心', desc: '催报与通知', icon: 'Bell', to: 'messages' }
    ]
  }

  return [
    { label: '写日报', desc: '记录今天的进展', icon: 'EditPen', to: 'daily-report', query: { date: todayStr } },
    { label: '写周报', desc: '汇总本周产出', icon: 'Document', to: 'weekly-report' },
    { label: '消息中心', desc: '催报与通知', icon: 'Bell', to: 'messages' }
  ]
})

const consoleRows = [
  { label: '用户管理', desc: '账号新增、禁用与重置', icon: 'User', tab: 'user' },
  { label: '项目管理', desc: '创建项目并分配负责人', icon: 'Folder', tab: 'project' },
  { label: '部门管理', desc: '维护企业组织架构树', icon: 'OfficeBuilding', tab: 'dept' }
]

async function openRemind() {
  remindVisible.value = true
  remindLoading.value = true
  try {
    const res = await getBoardOverview({ range: 'week' })
    pendingUsers.value = res.data?.pendingUsers || []
  } catch {
    pendingUsers.value = []
  } finally {
    remindLoading.value = false
  }
}

async function doRemind(item) {
  remindingId.value = item.userId
  try {
    const res = await remindUser(item.userId)
    ElMessage.success(res.message || '已发送催报')
    pendingUsers.value = pendingUsers.value.filter((row) => row.userId !== item.userId)
  } catch {
    /* 拦截器已提示 */
  } finally {
    remindingId.value = null
  }
}

function go(item) {
  router.push({ name: item.to, query: item.query })
}

function goFill() {
  if (summary.value?.todaySubmitted) {
    router.push({ name: 'daily-report', query: { date: todayStr } })
    return
  }
  router.push({ name: 'daily-report', query: { date: todayStr } })
}

/* ---------------- 本周节奏 ---------------- */

const pad2 = (n) => String(n).padStart(2, '0')
const monthKey = (date) => `${date.getFullYear()}-${pad2(date.getMonth() + 1)}`
const dateKey = (date) => `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`

const weekSummary = computed(() => {
  const passed = week.value.filter(
    (day) => day.label !== '六' && day.label !== '日' && day.state !== 'future'
  )
  const done = passed.filter((day) => day.state === 'done').length
  return `${done}/${passed.length} 个工作日已提交`
})

function stateText(state) {
  return (
    {
      done: '已提交',
      missing: '未提交',
      overdue: '逾期',
      today: '今天',
      rest: '休息日',
      future: '未到'
    }[state] || ''
  )
}

function openDay(day) {
  if (!canFill.value) return
  router.push({ name: 'daily-report', query: { date: day.date } })
}

async function loadWeek() {
  if (!canFill.value) return
  try {
    const base = new Date()
    base.setHours(0, 0, 0, 0)
    const monday = new Date(base)
    monday.setDate(base.getDate() - ((base.getDay() + 6) % 7))
    const sunday = new Date(monday)
    sunday.setDate(monday.getDate() + 6)

    const months = [...new Set([monthKey(monday), monthKey(sunday)])]
    const results = await Promise.all(months.map((month) => getDailyCalendar(month)))
    const days = results.flatMap((res) => res.data?.days || [])
    const byDate = new Map(days.map((day) => [day.date, day]))

    const labels = ['一', '二', '三', '四', '五', '六', '日']
    week.value = labels.map((label, index) => {
      const date = new Date(monday)
      date.setDate(monday.getDate() + index)
      const key = dateKey(date)
      const item = byDate.get(key)
      const isToday = key === todayStr
      let state = 'future'
      if (item?.status === 1) {
        state = 'done'
      } else if (item?.workday === false) {
        state = 'rest'
      } else if (date < base) {
        state = item?.status === 2 ? 'overdue' : 'missing'
      } else if (isToday) {
        state = 'today'
      }
      return { label, day: date.getDate(), date: key, state, isToday }
    })
  } catch {
    week.value = []
  }
}

/* ---------------- 最近提交 / 在管项目 / 审计日志 ---------------- */

function timeText(value) {
  return String(value || '').replace('T', ' ').slice(5, 16)
}

function toneOf(action) {
  if (!action) return 'plain'
  if (action.includes('PROJECT')) return 'violet'
  if (action.includes('DEPT')) return 'camel'
  if (action.includes('USER_STATUS')) return 'camel'
  return 'primary'
}

function remainText(project) {
  if (!project?.plannedEndDate) return '未设完成日'
  if (project.overdue) return `超期 ${Math.abs(project.remainingDays ?? 0)} 天`
  return `剩 ${project.remainingDays ?? 0} 天`
}

async function loadRecent() {
  recentLoading.value = true
  try {
    if (canFill.value) {
      const res = await getDailyPage({ page: 1, size: 4 })
      const records = res.data?.records || []
      recent.value = {
        empty: '还没有写过日报',
        items: records.map((item) => ({
          main: shortText(item.contentDone, 40),
          meta: `${String(item.reportDate || '').slice(5)} · ${
            item.status === 1 ? '已提交' : '草稿'
          }${item.hours ? ` · ${item.hours}h` : ''}${item.projectName ? ` · ${item.projectName}` : ''}`
        }))
      }
    } else if (isAdmin.value) {
      const res = await getAuditLogs({ page: 1, size: 4 })
      logs.value = res.data?.records || []
    } else if (isExec.value) {
      const res = await listProjects({ page: 1, size: 5 })
      projects.value = res.data?.records || []
    }
  } catch {
    recent.value = { ...recent.value, items: [] }
    logs.value = []
    projects.value = []
  } finally {
    recentLoading.value = false
  }
}

async function loadSummary() {
  loading.value = true
  try {
    const res = await getDashboardSummary()
    summary.value = res.data
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadSummary()
  loadRecent()
  loadWeek()
})
</script>

<style scoped>
/* ================= 基座 ================= */
.workbench {
  display: flex;
  flex-direction: column;
  gap: 22px;
  max-width: 1240px;
  margin: 0 auto;
}

/* ---------------- 问候 ---------------- */
.wb-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 4px 2px 0;
}

.wb-date {
  margin: 0 0 14px;
  color: rgba(40, 38, 36, 0.42);
  font-size: 13px;
  letter-spacing: 0.4px;
}

.wb-greet {
  margin: 0;
  color: var(--wl-text);
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 0.5px;
  line-height: 1.2;
}

.wb-sub {
  margin: 12px 0 0;
  color: rgba(40, 38, 36, 0.5);
  font-size: 14px;
}

.wb-cta {
  height: 44px;
  padding: 0 22px;
  border: none;
  border-radius: 10px;
  background: var(--wl-primary);
  color: #fff;
  font-size: 14px;
  letter-spacing: 0.5px;
  transition: var(--wl-motion);
}

.wb-cta:hover,
.wb-cta:focus {
  background: #4a566a;
  color: #fff;
  transform: translateY(-1px);
}

/* 管理员：行内指标 */
.wb-stats {
  display: flex;
  align-items: flex-start;
  gap: 44px;
  padding-top: 6px;
}

.wb-stat {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.wb-stat span {
  color: rgba(40, 38, 36, 0.45);
  font-size: 13px;
}

.wb-stat b {
  color: var(--wl-text);
  font-family: var(--wl-mono);
  font-size: 34px;
  font-weight: 600;
  line-height: 1;
}

/* ---------------- KPI：4 列 ---------------- */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.kpi {
  padding: 20px 22px 18px;
  border: 1px solid var(--wl-border);
  border-radius: var(--wl-radius-card);
  background: var(--wl-card);
  box-shadow: var(--wl-shadow-card);
  transition: var(--wl-motion);
}

.kpi:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 30px rgba(40, 38, 36, 0.06);
}

.kpi:active {
  transform: translateY(-1px) scale(0.99);
}

.kpi.is-clickable {
  cursor: pointer;
}

:deep(.remind-dialog) {
  border-radius: 16px;
  padding: 8px 6px 18px;
}

:deep(.remind-dialog .el-dialog__title) {
  font-size: 16px;
  font-weight: 600;
}

:deep(.remind-dialog .el-dialog__header) {
  padding: 12px 16px 10px;
}

:deep(.remind-dialog .el-dialog__body) {
  padding: 4px 16px 0;
}

.remind {
  margin: 0;
  padding: 0;
  list-style: none;
}

.remind__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 2px;
  border-bottom: 1px solid rgba(231, 228, 222, 0.9);
}

.remind__item:last-child {
  border-bottom: none;
}

.remind__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #f4f2ee;
  color: var(--wl-primary);
  font-size: 13px;
}

.remind__text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.remind__text b {
  color: var(--wl-text);
  font-size: 14px;
  font-weight: 600;
}

.remind__text small {
  color: rgba(40, 38, 36, 0.42);
  font-size: 12.5px;
}

.remind__empty {
  margin: 12px 0;
  color: rgba(40, 38, 36, 0.4);
  font-size: 13px;
}

.kpi--skeleton {
  min-height: 112px;
}

.kpi__label {
  margin: 0;
  color: rgba(40, 38, 36, 0.5);
  font-size: 14px;
}

.kpi__value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin: 10px 0 0;
  color: var(--wl-text);
  font-family: var(--wl-mono);
  font-size: 34px;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: 0.5px;
}

.kpi__unit {
  color: rgba(40, 38, 36, 0.45);
  font-family: var(--wl-font);
  font-size: 14px;
  font-weight: 400;
  letter-spacing: 0;
}

.kpi__hint {
  margin: 10px 0 0;
  color: rgba(40, 38, 36, 0.38);
  font-size: 12.5px;
}

.kpi.is-warm .kpi__value {
  color: var(--wl-camel-deep);
}

.kpi.is-danger .kpi__value {
  color: #a4483c;
}

/* ---------------- 主栅格 ---------------- */
.wb-main {
  display: grid;
  gap: 16px;
  /* 两列拉齐：左列两张卡、右列一张卡，底部必须落在同一条线上 */
  align-items: stretch;
}

.wb-main--fill {
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
}

.wb-main--exec {
  grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
}

.wb-main--admin {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.9fr);
}

.wb-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

/* 列内最后一张卡吃掉剩余高度，两列底边才会严格对齐 */
.wb-col > .card:last-child {
  flex: 1 1 auto;
}

/* ---------------- 卡片 ---------------- */
.card {
  padding: 20px 22px 22px;
  border: 1px solid var(--wl-border);
  border-radius: var(--wl-radius-card);
  background: var(--wl-card);
  box-shadow: var(--wl-shadow-card);
  animation: wb-rise 0.42s var(--wl-spring) both;
  transition:
    transform 0.42s var(--wl-spring),
    box-shadow 0.42s var(--wl-spring),
    border-color 0.3s var(--wl-ease);
}

.card:hover {
  transform: translateY(-2px);
  border-color: #ded9d1;
  box-shadow: 0 12px 30px rgba(40, 38, 36, 0.06);
}

.card--fill {
  height: 100%;
}

@keyframes wb-rise {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
  padding-bottom: 14px;
}

/* 列表类卡片（在管项目 / 控制台 / 审计日志）标题下面才画分隔线 */
.card__head--line {
  border-bottom: 1px solid rgba(231, 228, 222, 0.9);
}

.card__head h2 {
  margin: 0;
  color: var(--wl-text);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.4px;
}

.card__head span {
  color: rgba(40, 38, 36, 0.38);
  font-size: 13px;
}

.card__empty {
  margin: 18px 0 6px;
  color: rgba(40, 38, 36, 0.35);
  font-size: 13px;
  text-align: center;
}

/* ---------------- 本周节奏：12px 圆点，无底色块 ---------------- */
.rhythm {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  padding: 18px 0 6px;
}

.rhythm__day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 26px;
  padding: 6px 0;
  border: none;
  background: none;
  cursor: pointer;
  transition: var(--wl-motion);
}

.rhythm__label {
  color: rgba(40, 38, 36, 0.42);
  font-size: 14px;
  letter-spacing: 0.5px;
}

.rhythm__dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #efece7;
  transition: var(--wl-motion);
}

.rhythm__day.is-done .rhythm__dot {
  background: var(--wl-primary);
}

.rhythm__day.is-missing .rhythm__dot {
  background: #ddd8d0;
}

.rhythm__day.is-overdue .rhythm__dot {
  background: var(--wl-camel);
}

.rhythm__day.is-today .rhythm__label {
  color: var(--wl-text);
  font-weight: 700;
}

.rhythm__day.is-today .rhythm__dot {
  background: var(--wl-primary);
  box-shadow:
    0 0 0 3px var(--wl-card),
    0 0 0 4px rgba(63, 74, 90, 0.18);
}

.rhythm__day.is-today.is-missing .rhythm__dot,
.rhythm__day.is-today.is-future .rhythm__dot {
  background: #ddd8d0;
}

.rhythm__day:hover {
  transform: translateY(-2px);
}

.rhythm__day:hover .rhythm__dot {
  transform: scale(1.18);
}

.rhythm__day:active {
  transform: scale(0.96);
}

/* ---------------- 快捷入口 ---------------- */
.quick {
  display: grid;
  grid-template-columns: repeat(var(--quick-cols, 4), minmax(0, 1fr));
  gap: 12px;
  padding-top: 18px;
}

.quick__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 54px;
  padding: 0 12px;
  border: 1px solid var(--wl-border);
  border-radius: 12px;
  background: #fcfbf9;
  color: var(--wl-text);
  font-size: 14px;
  cursor: pointer;
  transition: var(--wl-motion);
}

.quick__btn:hover {
  border-color: rgba(63, 74, 90, 0.28);
  background: #fff;
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(40, 38, 36, 0.06);
}

.quick__btn:active {
  transform: scale(0.98);
}

.quick__btn .el-icon {
  color: var(--wl-primary);
  font-size: 16px;
}

/* ---------------- 最近提交：时间线 ---------------- */
.tl {
  position: relative;
  margin: 0;
  padding: 18px 0 0;
  list-style: none;
}

.tl::before {
  content: '';
  position: absolute;
  left: 4.5px;
  top: 26px;
  bottom: 14px;
  width: 1px;
  background: rgba(63, 74, 90, 0.12);
}

.tl__item {
  position: relative;
  padding: 0 0 22px 26px;
  border-radius: 8px;
  transition:
    transform 0.42s var(--wl-spring),
    background-color 0.3s var(--wl-ease);
}

.tl__item:hover {
  background: rgba(63, 74, 90, 0.02);
  transform: translateX(3px);
}

.tl__item:hover .tl__dot {
  transform: scale(1.25);
}

.tl__item:active {
  transform: translateX(1px) scale(0.995);
}

.tl__item:last-child {
  padding-bottom: 2px;
}

.tl__dot {
  position: absolute;
  left: 0;
  top: 4px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--wl-primary);
  box-shadow: 0 0 0 2.5px var(--wl-card);
  transition: transform 0.42s var(--wl-spring);
}

.tl__body {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.tl__body b {
  color: var(--wl-text);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.tl__body small {
  color: rgba(40, 38, 36, 0.4);
  font-size: 12px;
}

/* ---------------- 高管：在管项目 ---------------- */
.proj {
  margin: 0;
  padding: 0;
  list-style: none;
}

.proj__item {
  padding: 16px 2px;
  border-bottom: 1px dashed var(--wl-border);
}

.proj__item:last-child {
  border-bottom: none;
  padding-bottom: 2px;
}

.proj__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.proj__row b {
  color: var(--wl-text);
  font-size: 15px;
  font-weight: 600;
}

.proj__row--sub {
  margin-top: 8px;
  color: rgba(40, 38, 36, 0.4);
  font-size: 13px;
}

.pill {
  flex: none;
  padding: 3px 10px;
  border-radius: 8px;
  background: rgba(185, 154, 122, 0.16);
  color: #8a6d4f;
  font-size: 12.5px;
  white-space: nowrap;
}

.pill.is-late {
  background: rgba(164, 72, 60, 0.12);
  color: #a4483c;
}

/* ---------------- 高管：竖排快捷入口 ---------------- */
.quick-list {
  margin: 0;
  padding: 8px 0 0;
  list-style: none;
}

.quick-list__item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 4px;
  border-bottom: 1px solid rgba(231, 228, 222, 0.9);
  cursor: pointer;
  transition: var(--wl-motion);
}

.quick-list__item:last-child {
  border-bottom: none;
}

.quick-list__item:hover {
  transform: translateX(2px);
}

.quick-list__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: #f4f2ee;
  color: var(--wl-primary);
  font-size: 18px;
  transition: var(--wl-motion);
}

.quick-list__item:hover .quick-list__icon {
  background: rgba(63, 74, 90, 0.1);
  transform: scale(1.05);
}

.quick-list__text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.quick-list__text b {
  color: var(--wl-text);
  font-size: 15px;
  font-weight: 600;
}

.quick-list__text small {
  color: rgba(40, 38, 36, 0.42);
  font-size: 13px;
}

.quick-list__arrow {
  color: rgba(40, 38, 36, 0.25);
  font-size: 14px;
  transition: var(--wl-motion);
}

.quick-list__item:hover .quick-list__arrow {
  color: var(--wl-primary);
  transform: translateX(3px);
}

/* ---------------- 管理员：控制台 ---------------- */
.console {
  margin: 0;
  padding: 0;
  list-style: none;
}

.console__item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 4px;
  border-bottom: 1px solid rgba(231, 228, 222, 0.9);
  cursor: pointer;
  transition: var(--wl-motion);
}

.console__item:last-child {
  border-bottom: none;
}

.console__item:hover {
  transform: translateX(2px);
}

.console__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #f4f2ee;
  color: var(--wl-primary);
  font-size: 19px;
  transition: var(--wl-motion);
}

.console__item:hover .console__icon {
  background: rgba(63, 74, 90, 0.1);
  transform: scale(1.05);
}

.console__text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.console__text b {
  color: var(--wl-text);
  font-size: 15px;
  font-weight: 600;
}

.console__text small {
  color: rgba(40, 38, 36, 0.42);
  font-size: 13px;
}

.console__arrow {
  color: rgba(40, 38, 36, 0.22);
  font-size: 14px;
}

/* ---------------- 管理员：审计日志 ---------------- */
.log {
  margin: 0;
  padding: 0;
  list-style: none;
}

.log__item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 18px 2px;
  border-bottom: 1px solid rgba(231, 228, 222, 0.9);
  transition: var(--wl-motion);
}

.log__item:last-child {
  border-bottom: none;
}

.log__item:hover {
  background: rgba(63, 74, 90, 0.02);
}

.log__bar {
  flex: none;
  width: 3px;
  align-self: stretch;
  min-height: 34px;
  border-radius: 2px;
  background: var(--wl-primary);
}

.log__bar.is-violet {
  background: var(--wl-violet);
}

.log__bar.is-camel {
  background: var(--wl-camel);
}

.log__bar.is-plain {
  background: rgba(40, 38, 36, 0.18);
}

.log__text {
  flex: 1;
  min-width: 0;
  color: rgba(40, 38, 36, 0.72);
  font-size: 14px;
  line-height: 1.6;
}

.log__text b {
  margin-right: 6px;
  color: var(--wl-text);
  font-weight: 600;
}

.log__text em {
  color: var(--wl-text);
  font-style: normal;
  font-weight: 500;
}

.log__meta {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.log__meta time {
  color: rgba(40, 38, 36, 0.4);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.tag {
  padding: 2px 8px;
  border-radius: 6px;
  background: #f4f2ee;
  color: rgba(40, 38, 36, 0.55);
  font-size: 12px;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1100px) {
  .wb-main--fill,
  .wb-main--exec,
  .wb-main--admin {
    grid-template-columns: minmax(0, 1fr);
  }

  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .wb-greet {
    font-size: 28px;
  }
}

@media (max-width: 720px) {
  .wb-head {
    flex-direction: column;
  }

  .wb-stats {
    gap: 24px;
  }

  .kpi-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .quick {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}


</style>
