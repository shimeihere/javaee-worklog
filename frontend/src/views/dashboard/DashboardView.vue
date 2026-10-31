<template>
  <div class="board">
    <header class="board__head">
      <div>
        <h1>数据看板</h1>
        <p>{{ subtitle }}</p>
      </div>

      <div v-if="canView" class="board__filters">
        <el-radio-group v-model="filters.range" class="seg" @change="load">
          <el-radio-button value="week">本周</el-radio-button>
          <el-radio-button value="lastWeek">上周</el-radio-button>
          <el-radio-button value="month">本月</el-radio-button>
        </el-radio-group>

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
      <h3>看板仅对管理员和部门负责人开放</h3>
      <p>当前账号是「{{ user?.roleName || '员工' }}」，可以继续使用日报填报与个人工作台。</p>
      <el-button text @click="router.push({ name: 'workbench' })">← 返回工作台</el-button>
    </section>

    <template v-else>
      <div v-loading="loading" class="board__grid">
        <!-- 左：Hero + 图表 -->
        <section class="board__main">
          <article class="card hero">
            <div class="hero__ring">
              <EChart :option="ringOption" height="196px" />
              <div class="hero__center">
                <strong>{{ data?.submitRate ?? 0 }}<small>%</small></strong>
                <span>团队总提交率</span>
              </div>
            </div>

            <div class="hero__meta">
              <div class="meta__row">
                <span>统计区间</span>
                <b>{{ rangeLabel }}</b>
              </div>
              <div class="meta__row">
                <span>参与成员</span>
                <b>{{ data?.memberCount ?? 0 }} 人</b>
              </div>
              <div class="meta__row">
                <span>已提交 / 应提交</span>
                <b>{{ data?.submittedCount ?? 0 }} / {{ data?.expectedCount ?? 0 }}</b>
              </div>
              <div class="meta__row">
                <span>有效工作日</span>
                <b>{{ data?.workdays ?? 0 }} 天</b>
              </div>
            </div>
          </article>

          <article class="card">
            <header class="card__head">
              <h3>近 7 日提交趋势</h3>
              <span class="card__hint">按日提交率</span>
            </header>
            <EChart :option="lineOption" height="230px" />
          </article>

          <article class="card">
            <header class="card__head">
              <h3>部门提交率对比</h3>
              <span class="card__hint">{{ isLeader ? '本部门' : '全公司' }}</span>
            </header>
            <EChart :option="barOption" height="230px" />
          </article>

          <article class="card">
            <header class="card__head">
              <h3>近 4 周提交密度</h3>
              <span class="card__hint">颜色越深，当天提交的人越多</span>
            </header>
            <EChart :option="heatOption" height="188px" />
          </article>
        </section>

        <!-- 右：高管看组织/项目排行，负责人看待提醒名单 -->
        <aside class="board__side">
          <!-- 高管：按部门 / 按项目下钻排行，不出现具体员工 -->
          <article v-if="isExecutive" class="card pending">
            <header class="card__head card__head--rank">
              <h3>下钻排行</h3>
              <el-radio-group v-model="rankBy" size="small" class="rank-switch">
                <el-radio-button value="dept">按部门</el-radio-button>
                <el-radio-button value="project">按项目</el-radio-button>
              </el-radio-group>
            </header>

            <ul v-if="rankingRows.length" class="rank-list">
              <li v-for="(item, index) in rankingRows" :key="item.key" class="rank-item">
                <span class="rank-index">{{ index + 1 }}</span>
                <span class="rank-info">
                  <b>{{ item.name }}</b>
                  <small>{{ item.sub }}</small>
                </span>
                <span class="rank-bar"><i :style="{ width: `${item.percent}%` }" /></span>
                <b class="rank-value">{{ item.value }}</b>
              </li>
            </ul>

            <p v-else class="pending__empty">这个区间还没有可排行的数据</p>
          </article>

          <!-- 负责人：本部门待提醒名单（可催报） -->
          <article v-else class="card pending">
            <header class="card__head">
              <h3>待提醒名单</h3>
              <span class="card__hint">{{ pendingUsers.length }} 人</span>
            </header>

            <p v-if="pendingUsers.length === 0" class="pending__empty">
              这个区间里大家都按时提交了，保持住。
            </p>

            <ul v-else class="pending__list">
              <li
                v-for="item in pendingUsers"
                :key="item.userId"
                class="pending__item"
                @click="openDetail(item)"
              >
                <span class="pending__avatar">{{ item.realName.slice(0, 1) }}</span>
                <span class="pending__info">
                  <b>{{ item.realName }}</b>
                  <small>{{ item.deptName || '未分配部门' }}</small>
                </span>
                <span class="pending__gap" :class="{ 'is-heavy': item.missingDays >= 3 }">
                  缺 {{ item.missingDays }} 天
                </span>
              </li>
            </ul>
          </article>
        </aside>
      </div>
    </template>

    <!-- 成员详情 + 催报 -->
    <el-dialog
      v-model="dialogVisible"
      width="420px"
      modal-class="glass-mask"
      class="glass-dialog"
      :show-close="false"
      append-to-body
    >
      <div v-if="selected" class="detail">
        <span class="detail__avatar">{{ selected.realName.slice(0, 1) }}</span>
        <h3>{{ selected.realName }}</h3>
        <p class="detail__meta">{{ selected.deptName || '未分配部门' }} · {{ selected.roleName }}</p>

        <dl class="detail__stats">
          <div>
            <dt>缺报天数</dt>
            <dd>{{ selected.missingDays }} 天</dd>
          </div>
          <div>
            <dt>最近提交</dt>
            <dd>{{ selected.lastSubmitDate || '从未提交' }}</dd>
          </div>
          <div>
            <dt>今日状态</dt>
            <dd :class="selected.todaySubmitted ? 'is-done' : 'is-missing'">
              {{ selected.todaySubmitted ? '已提交' : '未提交' }}
            </dd>
          </div>
        </dl>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="reminding" @click="doRemind">一键催报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import EChart from '@/components/dashboard/EChart.vue'
import { getBoardOverview, remindUser } from '@/api/board'
import { listProjects } from '@/api/report'
import { useBoardStore } from '@/stores/board'
import { getUser } from '@/utils/auth'
import { d2, diagonalGradient, tooltipStyle, verticalGradient } from '@/utils/echarts'

const router = useRouter()
const user = getUser()

const canView = computed(() => ['EXECUTIVE', 'DEPT_LEADER'].includes(user?.role))
const isLeader = computed(() => user?.role === 'DEPT_LEADER')
const isExecutive = computed(() => user?.role === 'EXECUTIVE')

const loading = ref(false)
const data = ref(null)
const projects = ref([])

// 筛选条件放在 Pinia，切页面回来还是上次选的范围
const boardStore = useBoardStore()
const filters = boardStore.filters

const selected = ref(null)
const dialogVisible = ref(false)
const reminding = ref(false)

const pendingUsers = computed(() => data.value?.pendingUsers || [])

/* 高管：按部门 / 按项目下钻排行（不出现具体员工） */
const rankBy = ref('dept')
const rankingRows = computed(() => {
  const d = data.value
  if (!d) return []

  if (rankBy.value === 'dept') {
    return (d.deptStats || []).map((item) => ({
      key: `dept-${item.deptId}`,
      name: item.deptName,
      sub: `${item.memberCount} 人 · ${item.submitted}/${item.expected}`,
      percent: item.rate,
      value: `${item.rate}%`
    }))
  }

  const projects = d.projectStats || []
  const max = Math.max(1, ...projects.map((item) => item.submitted))
  return projects.map((item) => ({
    key: `project-${item.projectId}`,
    name: item.projectName,
    sub: `${item.leaderName || '—'} · ${item.participants} 人参与`,
    percent: Math.round((item.submitted * 100) / max),
    value: `${item.submitted} 份`
  }))
})

const rangeLabel = computed(() => {
  const d = data.value
  if (!d) return '—'
  return `${d.startDate} ~ ${d.endDate}`
})

const subtitle = computed(() => {
  if (!canView.value) return '团队提交情况一目了然'
  if (isLeader.value) return '本部门成员的工作日提交情况'
  return '全公司组织与项目的工作日提交情况'
})

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
        radius: ['76%', '92%'],
        silent: true,
        label: { show: false },
        data: [{ value: 100, itemStyle: { color: d2.track } }]
      },
      {
        type: 'pie',
        radius: ['76%', '92%'],
        startAngle: 90,
        silent: true,
        label: { show: false },
        labelLine: { show: false },
        itemStyle: { borderRadius: 24, borderColor: '#fff', borderWidth: 2 },
        data: [
          { value: rate, itemStyle: { color: diagonalGradient(d2.primary, d2.violet) } },
          { value: Math.max(100 - rate, 0), itemStyle: { color: 'transparent' } }
        ]
      }
    ]
  }
})

const lineOption = computed(() => {
  const trend = data.value?.trend || []
  return {
    backgroundColor: 'transparent',
    grid: { left: 4, right: 14, top: 18, bottom: 4, containLabel: true },
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
      max: 100,
      splitLine: { lineStyle: { color: d2.grid } },
      axisLabel: { color: d2.muted, fontSize: 11, formatter: '{value}%' }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: trend.map((item) => item.rate),
        lineStyle: { width: 2.4, color: d2.primary },
        itemStyle: { color: d2.primary, borderColor: '#fff', borderWidth: 2 },
        areaStyle: { color: verticalGradient('rgba(63,74,90,.26)', 'rgba(63,74,90,0)') }
      }
    ]
  }
})

const barOption = computed(() => {
  const stats = data.value?.deptStats || []
  return {
    backgroundColor: 'transparent',
    grid: { left: 4, right: 14, top: 18, bottom: 4, containLabel: true },
    tooltip: {
      ...tooltipStyle,
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(63,74,90,.04)' } },
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
      max: 100,
      splitLine: { lineStyle: { color: d2.grid } },
      axisLabel: { color: d2.muted, fontSize: 11, formatter: '{value}%' }
    },
    series: [
      {
        type: 'bar',
        barWidth: '44%',
        data: stats.map((item) => item.rate),
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: verticalGradient(d2.primary, d2.violet)
        }
      }
    ]
  }
})

const heatOption = computed(() => {
  const cells = data.value?.heatmap || []
  if (cells.length === 0) {
    return {}
  }
  return {
    backgroundColor: 'transparent',
    tooltip: {
      ...tooltipStyle,
      formatter: (params) => `${params.value[0]}<br/>提交率 ${params.value[1]}%`
    },
    visualMap: {
      show: false,
      min: 0,
      max: 100,
      inRange: { color: ['rgba(63,74,90,.07)', '#9aa3b0', d2.primary] }
    },
    calendar: {
      range: [cells[0].date, cells[cells.length - 1].date],
      left: 36,
      right: 10,
      top: 32,
      bottom: 4,
      cellSize: ['auto', 15],
      orient: 'horizontal',
      splitLine: { show: false },
      itemStyle: { color: 'transparent', borderWidth: 3, borderColor: '#fff' },
      yearLabel: { show: false },
      monthLabel: { color: d2.muted, fontSize: 10 },
      dayLabel: {
        firstDay: 1,
        nameMap: ['一', '二', '三', '四', '五', '六', '日'],
        color: d2.muted,
        fontSize: 10
      }
    },
    series: [
      {
        type: 'heatmap',
        coordinateSystem: 'calendar',
        data: cells.map((cell) => [cell.date, cell.rate]),
        itemStyle: { borderRadius: 3 }
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

function openDetail(item) {
  selected.value = item
  dialogVisible.value = true
}

async function doRemind() {
  if (!selected.value) {
    return
  }
  reminding.value = true
  try {
    const res = await remindUser(selected.value.userId)
    ElMessage.success(res.message)
    dialogVisible.value = false
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    reminding.value = false
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
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 1px;
}

.board__head p {
  margin: 8px 0 0;
  color: var(--wl-muted);
  font-size: 12px;
}

.board__filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter {
  width: 150px;
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
  box-shadow: 0 10px 22px -16px rgba(63, 74, 90, 0.95);
}

.board__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(300px, 0.85fr);
  gap: 16px;
  align-items: start;
  min-height: 320px;
}

.board__main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card {
  padding: 18px 20px 16px;
  border-radius: 16px;
  background: var(--wl-card);
  box-shadow:
    0 0 0 1px rgba(63, 74, 90, 0.05),
    0 14px 32px -26px rgba(63, 74, 90, 0.5);
}

.card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.card__head h3 {
  margin: 0;
  color: var(--wl-text);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.8px;
}

.card__hint {
  color: rgba(40, 38, 36, 0.36);
  font-size: 11px;
}

/* ---------------- Hero ---------------- */
.hero {
  display: flex;
  align-items: center;
  gap: 26px;
}

.hero__ring {
  position: relative;
  flex: 0 0 210px;
  width: 210px;
}

.hero__center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  pointer-events: none;
}

.hero__center strong {
  color: var(--wl-text);
  font-size: 30px;
  font-weight: 600;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.hero__center small {
  margin-left: 2px;
  color: var(--wl-muted);
  font-size: 14px;
  font-weight: 400;
}

.hero__center span {
  color: rgba(40, 38, 36, 0.4);
  font-size: 11px;
  letter-spacing: 0.6px;
}

.hero__meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meta__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 2px;
  border-bottom: 1px dashed rgba(63, 74, 90, 0.07);
}

.meta__row:last-child {
  border-bottom: none;
}

.meta__row span {
  color: rgba(40, 38, 36, 0.45);
  font-size: 12px;
}

.meta__row b {
  color: var(--wl-text);
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* ---------------- 待提醒 ---------------- */
.pending {
  position: sticky;
  top: 82px;
}

.card__head--rank {
  flex-wrap: wrap;
  row-gap: 8px;
}

.rank-switch :deep(.el-radio-button__inner) {
  padding: 4px 10px;
  border-color: rgba(63, 74, 90, 0.12);
  background: #fff;
  color: rgba(40, 38, 36, 0.55);
  font-size: 11px;
}

.rank-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--wl-primary);
  border-color: var(--wl-primary);
  color: #fff;
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 4px 0 0;
  padding: 0;
  list-style: none;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 6px;
  border-radius: 10px;
  transition: background-color 0.24s var(--wl-ease);
}

.rank-item:hover {
  background: rgba(63, 74, 90, 0.035);
}

.rank-index {
  flex: 0 0 18px;
  color: rgba(40, 38, 36, 0.3);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  text-align: center;
}

.rank-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.rank-info b {
  overflow: hidden;
  color: var(--wl-text);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-info small {
  color: rgba(40, 38, 36, 0.38);
  font-size: 10px;
}

.rank-bar {
  position: relative;
  flex: 0 0 56px;
  height: 4px;
  border-radius: 2px;
  background: rgba(63, 74, 90, 0.08);
  overflow: hidden;
}

.rank-bar i {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  border-radius: 2px;
  background: linear-gradient(90deg, #3f4a5a, #7a6a93);
  transition: width 0.5s var(--wl-spring);
}

.rank-value {
  flex: 0 0 auto;
  color: var(--wl-primary);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.pending__empty {
  margin: 24px 0 12px;
  color: rgba(40, 38, 36, 0.38);
  font-size: 12px;
  text-align: center;
}

.pending__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
  max-height: 520px;
  overflow-y: auto;
}

.pending__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 8px;
  border-radius: 10px;
  cursor: pointer;
  transition:
    background-color 0.26s var(--wl-ease),
    transform 0.34s var(--wl-spring);
}

.pending__item:hover {
  background: rgba(63, 74, 90, 0.04);
  transform: translateX(2px);
}

.pending__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(63, 74, 90, 0.08);
  color: var(--wl-primary);
  font-size: 12px;
  font-weight: 600;
}

.pending__info {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.pending__info b {
  color: var(--wl-text);
  font-size: 13px;
  font-weight: 500;
}

.pending__info small {
  color: rgba(40, 38, 36, 0.36);
  font-size: 11px;
}

.pending__gap {
  flex: 0 0 auto;
  padding: 3px 8px;
  border-radius: 6px;
  background: rgba(185, 154, 122, 0.14);
  color: #8a6d4f;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.pending__gap.is-heavy {
  background: rgba(185, 154, 122, 0.24);
  color: #7a5f45;
  font-weight: 600;
}

/* ---------------- 无权限 ---------------- */
.denied {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 64px 24px;
  border-radius: 16px;
  background: var(--wl-card);
  box-shadow: 0 0 0 1px rgba(63, 74, 90, 0.05);
  text-align: center;
}

.denied__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 13px;
  background: rgba(122, 106, 147, 0.1);
  color: var(--wl-violet);
  font-size: 19px;
}

.denied h3 {
  margin: 6px 0 0;
  color: var(--wl-primary);
  font-size: 15px;
  font-weight: 600;
}

.denied p {
  margin: 0;
  color: rgba(40, 38, 36, 0.45);
  font-size: 12px;
}

/* ---------------- 弹窗内容 ---------------- */
.detail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding-top: 6px;
}

.detail__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  margin-bottom: 6px;
  border-radius: 50%;
  background: linear-gradient(145deg, #3f4a5a, #7a6a93);
  color: #fff;
  font-size: 20px;
  font-weight: 600;
  box-shadow: 0 14px 28px -18px rgba(63, 74, 90, 0.9);
}

.detail h3 {
  margin: 0;
  color: var(--wl-text);
  font-size: 17px;
  font-weight: 600;
}

.detail__meta {
  margin: 4px 0 0;
  color: rgba(40, 38, 36, 0.42);
  font-size: 12px;
}

.detail__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  width: 100%;
  margin: 18px 0 0;
}

.detail__stats div {
  padding: 12px 8px;
  border-radius: 10px;
  background: rgba(63, 74, 90, 0.04);
  text-align: center;
}

.detail__stats dt {
  color: rgba(40, 38, 36, 0.4);
  font-size: 11px;
}

.detail__stats dd {
  margin: 6px 0 0;
  color: var(--wl-text);
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.detail__stats dd.is-done {
  color: var(--wl-primary);
}

.detail__stats dd.is-missing {
  color: #8a6d4f;
}

@media (max-width: 1180px) {
  .board__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .pending {
    position: static;
  }

  .hero {
    flex-wrap: wrap;
  }
}
</style>
