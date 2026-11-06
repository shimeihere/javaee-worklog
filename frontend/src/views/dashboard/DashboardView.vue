<template>
  <div class="board">
    <header class="board__head">
      <div>
        <h1>数据看板</h1>
        <p>{{ subtitle }}</p>
      </div>

      <div v-if="canView" class="board__filters">
        <div class="seg">
          <button
            v-for="option in rangeOptions"
            :key="option.value"
            type="button"
            class="seg__btn"
            :class="{ 'is-active': filters.range === option.value }"
            @click="setRange(option.value)"
          >
            {{ option.label }}
          </button>
        </div>

        <el-select
          v-model="filters.projectId"
          class="filter"
          placeholder="全部项目"
          clearable
          @change="load"
        >
          <el-option
            v-for="project in projects"
            :key="project.id"
            :label="project.projectName"
            :value="project.id"
          />
        </el-select>
      </div>
    </header>

    <!-- 无权限 -->
    <section v-if="!canView" class="denied">
      <span class="denied__icon">
        <el-icon><Lock /></el-icon>
      </span>
      <h3>看板仅对高管和部门负责人开放</h3>
      <p v-if="user?.role === 'ADMIN'">
        当前账号是「{{ user.roleName }}」，系统设置与账号管理可继续使用；经营数据不在你的权限范围内。
      </p>
      <p v-else>
        当前账号是「{{ user?.roleName || '员工' }}」，可以继续使用日报填报与个人工作台。
      </p>
      <el-button text @click="router.push({ name: 'workbench' })">← 返回工作台</el-button>
    </section>

    <template v-else>
      <div v-loading="loading" class="board__grid">
        <!-- ================= 左列（2） ================= -->
        <section class="board__main">
          <!-- 环形图 + 四项指标 -->
          <article class="card hero">
            <div class="hero__ring">
              <EChart :option="ringOption" height="150px" />
              <div class="hero__center">
                <strong>{{ data?.submitRate ?? 0 }}</strong><small>%</small>
                <span>团队总提交率</span>
              </div>
            </div>

            <dl class="hero__stats">
              <div>
                <dt>统计区间</dt>
                <dd>{{ rangeLabel }}</dd>
              </div>
              <div>
                <dt>参与成员</dt>
                <dd>{{ data?.memberCount ?? 0 }} 人</dd>
              </div>
              <div>
                <dt>已提交 / 应提交</dt>
                <dd>{{ data?.submittedCount ?? 0 }} / {{ data?.expectedCount ?? 0 }}</dd>
              </div>
              <div>
                <dt>有效工作日</dt>
                <dd>{{ data?.workdays ?? 0 }} 天</dd>
              </div>
            </dl>
          </article>

          <!-- 折线趋势 -->
          <article class="card">
            <header class="card__head">
              <h2>近 7 日提交趋势</h2>
              <span>按日提交率</span>
            </header>
            <EChart :option="lineOption" height="250px" />
          </article>

          <!-- 多部门时才做对比；单部门时项目信息由右侧「项目周期追踪」承担，避免同一份信息画两遍 -->
          <article v-if="multiDept" class="card">
            <header class="card__head">
              <h2>部门提交率对比</h2>
              <span>{{ isLeader ? '本部门' : '全公司' }}</span>
            </header>
            <EChart :option="barOption" height="280px" />
          </article>
        </section>

        <!-- ================= 右列（1） ================= -->
        <aside class="board__side">
          <!-- 部门排行 -->
          <article class="card">
            <header class="card__head">
              <h2>{{ deptRanking.length > 1 ? '部门排行' : '部门提交率' }}</h2>
              <span>{{ deptRanking.length > 1 ? '按提交率' : (isLeader ? '本部门' : '全公司') }}</span>
            </header>

            <ul v-if="deptRanking.length" class="rank">
              <li v-for="(item, index) in deptRanking" :key="item.key" class="rank__item">
                <span class="rank__no">{{ index + 1 }}</span>
                <span class="rank__main">
                  <b>{{ item.name }}</b>
                  <small>{{ item.sub }}</small>
                </span>
                <span class="rank__bar"><i :style="{ width: `${item.percent}%` }" /></span>
                <span class="rank__val">{{ item.value }}</span>
              </li>
            </ul>
            <p v-else class="card__empty">这个区间还没有可排行的数据。</p>
          </article>

          <!-- 项目周期追踪 -->
          <article class="card">
            <header class="card__head">
              <h2>项目周期追踪</h2>
              <span>按剩余天数</span>
            </header>

            <ul v-if="cycleProjects.length" class="cycle">
              <li v-for="project in cycleProjects" :key="project.projectId" class="cycle__item">
                <div class="cycle__row">
                  <b>{{ project.projectName }}</b>
                  <span class="pill" :class="pillTone(project)">{{ remainText(project) }}</span>
                </div>

                <div class="track" :class="{ 'is-open': !project.plannedEndDate }">
                  <i
                    v-if="project.timeProgress != null"
                    :style="{ width: `${project.timeProgress}%` }"
                    :class="trackTone(project)"
                  />
                  <span
                    v-if="project.timeProgress != null"
                    class="track__cursor"
                    :class="trackTone(project)"
                    :style="{ left: `${project.timeProgress}%` }"
                  />
                </div>

                <div class="cycle__foot">
                  <span class="cycle__who">
                    <i>{{ (project.leaderName || '—').slice(0, 1) }}</i>
                    {{ project.leaderName || '未指定' }}
                  </span>
                  <span>
                    {{ shortDate(project.startDate) }} 启动 ·
                    {{ project.plannedEndDate ? `${shortDate(project.plannedEndDate)} 截止` : '未设截止' }}
                  </span>
                </div>
              </li>
            </ul>
            <p v-else class="card__empty">这个范围里还没有项目。</p>
          </article>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock } from '@element-plus/icons-vue'
import EChart from '@/components/dashboard/EChart.vue'
import { getBoardOverview } from '@/api/board'
import { listProjects } from '@/api/report'
import { useBoardStore } from '@/stores/board'
import { getUser } from '@/utils/auth'
import { d2, tooltipStyle, verticalGradient } from '@/utils/echarts'

const router = useRouter()
const user = getUser()

const canView = computed(() => ['EXECUTIVE', 'DEPT_LEADER'].includes(user?.role))
const isLeader = computed(() => user?.role === 'DEPT_LEADER')

const loading = ref(false)
const data = ref(null)
const projects = ref([])

const boardStore = useBoardStore()
const filters = boardStore.filters

const subtitle = computed(() => {
  if (!canView.value) return '团队提交情况一目了然'
  return isLeader.value ? '本部门成员的工作日提交情况' : '全公司组织与项目的工作日提交情况'
})

const rangeOptions = [
  { value: 'week', label: '本周' },
  { value: 'lastWeek', label: '上周' },
  { value: 'month', label: '本月' },
  { value: 'last30', label: '近 30 天' }
]

function setRange(value) {
  if (filters.range === value) return
  filters.range = value
  load()
}

const rangeLabel = computed(() => {
  const board = data.value
  if (!board) return '—'
  return `${board.startDate} ~ ${board.endDate}`
})

const multiDept = computed(() => (data.value?.deptStats || []).length > 1)
const projectStats = computed(() => data.value?.projectStats || [])

const deptRanking = computed(() =>
  (data.value?.deptStats || []).map((item) => ({
    key: `dept-${item.deptId}`,
    name: item.deptName,
    sub: `${item.memberCount} 人 · ${item.submitted}/${item.expected}`,
    percent: item.rate,
    value: `${item.rate}%`
  }))
)

/** 周期追踪：剩余天数少的排前面，没设计划完成日的放最后 */
const cycleProjects = computed(() =>
  [...projectStats.value].sort((a, b) => {
    const av = a.remainingDays ?? 9999
    const bv = b.remainingDays ?? 9999
    return av - bv
  })
)

function remainText(project) {
  if (!project.plannedEndDate) return '未设完成日'
  if (project.overdue) return `超期 ${Math.abs(project.remainingDays ?? 0)} 天`
  return `剩 ${project.remainingDays ?? 0} 天`
}

function pillTone(project) {
  if (project.overdue) return 'is-late'
  const remain = project.remainingDays ?? 9999
  if (remain <= 7) return 'is-late'
  if (remain <= 30) return 'is-soon'
  return ''
}

function trackTone(project) {
  if (project.overdue) return 'is-late'
  const remain = project.remainingDays ?? 9999
  if (remain <= 7) return 'is-late'
  if (remain <= 30) return 'is-soon'
  return ''
}

function shortDate(value) {
  return value ? String(value).slice(5) : '—'
}

/* ---------------- 图表 ---------------- */

const ringOption = computed(() => {
  const rate = data.value?.submitRate ?? 0
  return {
    backgroundColor: 'transparent',
    animationDuration: 900,
    animationEasing: 'cubicOut',
    series: [
      {
        type: 'pie',
        radius: ['80%', '94%'],
        silent: true,
        label: { show: false },
        data: [{ value: 100, itemStyle: { color: '#f0ede8' } }]
      },
      {
        type: 'pie',
        radius: ['80%', '94%'],
        startAngle: 90,
        silent: true,
        label: { show: false },
        labelLine: { show: false },
        itemStyle: { borderRadius: 30 },
        data: [
          { value: rate, itemStyle: { color: d2.primary } },
          { value: Math.max(100 - rate, 0), itemStyle: { color: 'transparent' } }
        ]
      }
    ]
  }
})

const lineOption = computed(() => {
  const trend = data.value?.trend || []
  const max = Math.max(10, ...trend.map((item) => item.rate))
  return {
    backgroundColor: 'transparent',
    animationDuration: 800,
    grid: { left: 6, right: 16, top: 20, bottom: 6, containLabel: true },
    tooltip: {
      ...tooltipStyle,
      trigger: 'axis',
      formatter: (params) => {
        const item = params[0]
        const day = trend[item.dataIndex]
        return `${day.date}<br/>提交率 ${day.rate}%（${day.submitted}/${day.expected || 0} 人）`
      }
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.map((item) => item.date.slice(5)),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: d2.axis } },
      axisLabel: { color: d2.muted, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max,
      splitLine: { lineStyle: { color: d2.grid, type: 'dashed' } },
      axisLabel: { color: d2.muted, fontSize: 11, formatter: '{value}%' }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        data: trend.map((item) => item.rate),
        lineStyle: { width: 2, color: d2.primary },
        itemStyle: { color: d2.primary, borderColor: '#fff', borderWidth: 2 },
        areaStyle: { color: verticalGradient('rgba(63,74,90,.16)', 'rgba(63,74,90,0)') }
      }
    ]
  }
})

const barOption = computed(() => {
  const stats = data.value?.deptStats || []
  return {
    backgroundColor: 'transparent',
    animationDuration: 800,
    grid: { left: 6, right: 16, top: 26, bottom: 6, containLabel: true },
    tooltip: {
      ...tooltipStyle,
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(63,74,90,.03)' } },
      formatter: (params) => {
        const dept = stats[params[0].dataIndex]
        return `${dept.deptName}<br/>提交率 ${dept.rate}%（${dept.submitted}/${dept.expected}）`
      }
    },
    xAxis: {
      type: 'category',
      data: stats.map((item) => item.deptName),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: d2.axis } },
      axisLabel: { color: d2.muted, fontSize: 11, interval: 0, width: 84, overflow: 'truncate' }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: Math.max(25, ...stats.map((item) => item.rate)),
      splitLine: { lineStyle: { color: d2.grid, type: 'dashed' } },
      axisLabel: { color: d2.muted, fontSize: 11, formatter: '{value}%' }
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 24,
        showBackground: true,
        backgroundStyle: { color: 'rgba(63,74,90,.03)' },
        data: stats.map((item) => item.rate),
        label: {
          show: true,
          position: 'top',
          color: d2.muted,
          fontSize: 11,
          formatter: ({ value }) => `${value}%`
        },
        itemStyle: { borderRadius: [4, 4, 0, 0], color: d2.primary }
      }
    ]
  }
})

/* ---------------- 数据 ---------------- */

async function load() {
  if (!canView.value) {
    return
  }
  loading.value = true
  try {
    const res = await getBoardOverview({
      range: filters.range,
      projectId: filters.projectId || undefined
    })
    data.value = res.data
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const res = await listProjects({ page: 1, size: 100 })
    projects.value = res.data?.records || []
  } catch {
    /* ignore */
  }
}

onMounted(async () => {
  await loadOptions()
  await load()
})
</script>

<style scoped>
.board {
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-width: 1360px;
  margin: 0 auto;
}

.board__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 4px 2px 0;
}

.board__head h1 {
  margin: 0;
  color: var(--wl-text);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.board__head p {
  margin: 10px 0 0;
  color: rgba(40, 38, 36, 0.45);
  font-size: 13.5px;
}

.board__filters {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 分段控件：一个描边容器内并排，选中项主色实心（不用 el-radio-button，避免它的 -1px 边距错位） */
.seg {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px;
  border: 1px solid var(--wl-border);
  border-radius: 12px;
  background: #fff;
}

.seg__btn {
  height: 32px;
  padding: 0 16px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: rgba(40, 38, 36, 0.6);
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
  transition:
    background-color 0.3s var(--wl-ease),
    color 0.3s var(--wl-ease),
    transform 0.3s var(--wl-spring);
}

.seg__btn:hover {
  background: rgba(63, 74, 90, 0.04);
  color: var(--wl-text);
}

.seg__btn:active {
  transform: scale(0.96);
}

.seg__btn.is-active,
.seg__btn.is-active:hover {
  background: var(--wl-primary);
  color: #fff;
}

.filter {
  width: 180px;
}

.filter :deep(.el-select__wrapper) {
  min-height: 40px;
  border-radius: 12px;
  box-shadow: 0 0 0 1px var(--wl-border) inset;
}

/* ---------------- 栅格：左 2 : 右 1 ---------------- */
.board__grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 16px;
  /* 左右两列底边必须对齐 */
  align-items: stretch;
}

.board__main,
.board__side {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

/* 列内最后一张卡吃掉剩余高度 */
.board__main > .card:last-child,
.board__side > .card:last-child {
  flex: 1 1 auto;
}

/* ---------------- 卡片 ---------------- */
.card {
  padding: 20px 22px 22px;
  border: 1px solid var(--wl-border);
  border-radius: var(--wl-radius-card);
  background: var(--wl-card);
  box-shadow: var(--wl-shadow-card);
  animation: board-rise 0.42s var(--wl-spring) both;
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

@keyframes board-rise {
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
  margin-bottom: 8px;
  padding-bottom: 12px;
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
  margin: 20px 0 6px;
  color: rgba(40, 38, 36, 0.35);
  font-size: 13px;
  text-align: center;
}

/* ---------------- Hero ---------------- */
.hero {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 26px;
  align-items: center;
}

.hero__ring {
  position: relative;
  width: 190px;
  height: 150px;
}

.hero__center {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  align-content: center;
  gap: 2px;
}

.hero__center strong {
  color: var(--wl-text);
  font-family: var(--wl-mono);
  font-size: 40px;
  font-weight: 700;
  line-height: 1;
}

.hero__center small {
  margin-bottom: 4px;
  color: rgba(40, 38, 36, 0.45);
  font-size: 14px;
}

.hero__center span {
  width: 100%;
  color: rgba(40, 38, 36, 0.45);
  font-size: 12.5px;
  text-align: center;
}

.hero__stats {
  margin: 0;
  padding-left: 26px;
  border-left: 1px solid rgba(231, 228, 222, 0.9);
}

.hero__stats > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px dashed var(--wl-border);
}

.hero__stats > div:first-child {
  padding-top: 0;
}

.hero__stats > div:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.hero__stats dt {
  color: rgba(40, 38, 36, 0.45);
  font-size: 13.5px;
}

.hero__stats dd {
  margin: 0;
  color: var(--wl-text);
  font-family: var(--wl-mono);
  font-size: 14px;
  font-weight: 600;
}

/* ---------------- 部门排行 ---------------- */
.rank {
  margin: 0;
  padding: 6px 0 0;
  list-style: none;
  counter-reset: rank;
}

.rank__item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) 64px auto;
  align-items: center;
  gap: 12px;
  padding: 14px 2px;
}

.rank__no {
  color: rgba(40, 38, 36, 0.25);
  font-family: var(--wl-mono);
  font-size: 18px;
  font-weight: 600;
}

.rank__main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.rank__main b {
  color: var(--wl-text);
  font-size: 14px;
  font-weight: 600;
}

.rank__main small {
  color: rgba(40, 38, 36, 0.4);
  font-size: 12px;
}

.rank__bar {
  position: relative;
  height: 4px;
  border-radius: 2px;
  background: #f0ede8;
  overflow: hidden;
}

.rank__bar i {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: 2px;
  background: var(--wl-primary);
  transition: width 0.6s var(--wl-spring);
}

.rank__val {
  color: var(--wl-text);
  font-family: var(--wl-mono);
  font-size: 13px;
  font-weight: 600;
  text-align: right;
  white-space: nowrap;
}

/* ---------------- 项目周期追踪 ---------------- */
.cycle {
  margin: 0;
  padding: 6px 0 0;
  list-style: none;
}

.cycle__item {
  padding: 14px 2px 16px;
  border-bottom: 1px dashed var(--wl-border);
}

.cycle__item:last-child {
  border-bottom: none;
  padding-bottom: 2px;
}

.cycle__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.cycle__row b {
  color: var(--wl-text);
  font-size: 14.5px;
  font-weight: 600;
}

.pill {
  flex: none;
  padding: 3px 10px;
  border-radius: 8px;
  background: #f4f2ee;
  color: rgba(40, 38, 36, 0.55);
  font-size: 12.5px;
  white-space: nowrap;
}

.pill.is-soon {
  background: rgba(185, 154, 122, 0.16);
  color: #8a6d4f;
}

.pill.is-late {
  background: rgba(164, 72, 60, 0.12);
  color: #a4483c;
}

/* 进度条：6px 轨道 + 12px 白色游标 */
.track {
  position: relative;
  height: 6px;
  margin: 14px 0 12px;
  border-radius: 3px;
  background: #f0ede8;
}

.track i {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: 3px;
  background: var(--wl-primary);
  transition: width 0.7s var(--wl-spring);
}

.track i.is-late {
  background: #a4483c;
}

.track i.is-soon {
  background: var(--wl-camel);
}

.track.is-open {
  background: repeating-linear-gradient(
    90deg,
    #f0ede8 0 8px,
    transparent 8px 14px
  );
}

.track__cursor {
  position: absolute;
  top: 50%;
  width: 12px;
  height: 12px;
  border: 3px solid var(--wl-primary);
  border-radius: 50%;
  background: #fff;
  transform: translate(-50%, -50%);
  transition: left 0.7s var(--wl-spring);
}

.track__cursor.is-late {
  border-color: #a4483c;
}

.track__cursor.is-soon {
  border-color: var(--wl-camel);
}

.cycle__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: rgba(40, 38, 36, 0.4);
  font-size: 12.5px;
}

.cycle__who {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.cycle__who i {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #f4f2ee;
  color: var(--wl-primary);
  font-size: 11px;
  font-style: normal;
}

/* ---------------- 无权限 ---------------- */
.denied {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 70px 24px;
  border: 1px solid var(--wl-border);
  border-radius: var(--wl-radius-card);
  background: var(--wl-card);
  text-align: center;
}

.denied__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: #f4f2ee;
  color: var(--wl-primary);
  font-size: 22px;
}

.denied h3 {
  margin: 6px 0 0;
  color: var(--wl-text);
  font-size: 16px;
  font-weight: 600;
}

.denied p {
  margin: 0;
  max-width: 520px;
  color: rgba(40, 38, 36, 0.45);
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 1200px) {
  .board__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .hero {
    grid-template-columns: minmax(0, 1fr);
  }

  .hero__stats {
    padding-left: 0;
    border-left: none;
    border-top: 1px solid rgba(231, 228, 222, 0.9);
    padding-top: 8px;
  }
}
</style>
