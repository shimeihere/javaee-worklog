<template>
  <div class="summary">
    <header class="summary__head">
      <div>
        <h1>汇总中心</h1>
        <p>按人、项目、部门、时间四个维度查看填报情况，数据受权限范围约束</p>
      </div>
    </header>

    <!-- 维度 + 筛选 -->
    <section class="card toolbar">
      <el-radio-group v-model="filters.dimension" class="seg" @change="onDimensionChange">
        <el-radio-button v-for="tab in dimensions" :key="tab.value" :value="tab.value">
          {{ tab.label }}
        </el-radio-button>
      </el-radio-group>

      <div class="filters">
        <el-date-picker
          v-model="filters.dateRange"
          class="filter filter--date"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :clearable="false"
        />

        <el-select
          v-if="!isLeader"
          v-model="filters.deptId"
          class="filter"
          placeholder="全部部门"
          clearable
        >
          <el-option v-for="dept in depts" :key="dept.id" :label="dept.deptName" :value="dept.id" />
        </el-select>

        <el-select v-model="filters.projectId" class="filter" placeholder="全部项目" clearable>
          <el-option
            v-for="project in projects"
            :key="project.id"
            :label="project.projectName"
            :value="project.id"
          />
        </el-select>

        <el-input
          v-model="filters.keyword"
          class="filter filter--kw"
          placeholder="搜索姓名 / 项目 / 部门"
          clearable
          :prefix-icon="Search"
          @keyup.enter="search"
        />

        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </section>

    <!-- 表格 -->
    <section class="card table-card">
      <header class="table-head">
        <div class="table-head__left">
          <h3>{{ currentTab.label }}维度</h3>
          <span class="table-head__hint">共 {{ total }} 条 · {{ rangeHint }}</span>
        </div>

        <el-button class="export-btn" :loading="exporting" :icon="Download" @click="handleExport">
          导出 Excel
        </el-button>
      </header>

      <el-table
        v-loading="loading"
        :data="rows"
        class="table"
        row-key="id"
        :header-cell-style="{ background: '#FAF9F7' }"
      >
        <el-table-column
          v-for="col in columns"
          :key="col.prop"
          :prop="col.prop"
          :label="col.label"
          :min-width="col.minWidth"
          :align="col.align || 'left'"
          :show-overflow-tooltip="!col.linkType"
        >
          <template #default="{ row }">
            <!-- 项目名：点开项目成员名单 -->
            <button
              v-if="col.linkType === 'project'"
              class="name-link"
              type="button"
              @click="openProjectRoster(row)"
            >
              {{ row.name }}
            </button>

            <!-- 部门名：点开部门成员名单 -->
            <button
              v-else-if="col.linkType === 'dept'"
              class="name-link"
              type="button"
              @click="openDeptRoster(row)"
            >
              {{ row.name }}
            </button>

            <!-- 姓名：负责人可点开个人明细；高管只读，负责人旁加徽章 -->
            <span v-else-if="col.linkType === 'user'" class="name-cell">
              <button
                v-if="canViewDetail"
                class="name-link"
                type="button"
                @click="openDetail(row)"
              >
                {{ row.name }}
              </button>
              <span v-else>{{ row.name }}</span>
              <span v-if="row.roleCode === 'DEPT_LEADER'" class="leader-badge">负责人</span>
            </span>

            <span v-else-if="col.submitted" class="mono">
              {{ row.submitted ?? 0 }} / {{ row.expected ?? 0 }}
            </span>
            <span v-else-if="col.percent" class="rate">
              <span class="rate__bar"><i :style="{ width: `${row.rate || 0}%` }" /></span>
              <b>{{ row.rate ?? 0 }}%</b>
            </span>
            <span v-else-if="col.hours">{{ formatHours(row.hours) }}</span>
            <span v-else-if="col.mono" class="mono">{{ row[col.prop] ?? '—' }}</span>
            <span v-else>{{ display(row, col.prop) }}</span>
          </template>
        </el-table-column>

        <template #empty>
          <span class="empty">这个条件下没有数据，换个时间范围试试</span>
        </template>
      </el-table>

      <footer class="table-foot">
        <el-pagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="onSizeChange"
        />
      </footer>
    </section>

    <!-- 成员明细：800px 居中大弹窗（替代原来的抽屉） -->
    <el-dialog
      v-model="drawerVisible"
      width="800px"
      align-center
      append-to-body
      class="detail-dialog"
      :show-close="true"
      title="成员填报明细"
    >
      <div v-loading="drawerLoading" class="drawer">
        <header v-if="drawerUser" class="drawer__head">
          <span class="drawer__avatar">{{ drawerUser.name?.slice(0, 1) }}</span>
          <div class="drawer__meta">
            <b>{{ drawerUser.name }}</b>
            <small>
              {{ drawerUser.sub || '—' }}
              <template v-if="drawerUser.rate != null"> · 提交率 {{ drawerUser.rate }}%</template>
            </small>
          </div>
          <span class="drawer__count">{{ drawerTab === 'daily' ? dailyList.length : weeklyList.length }} 份</span>

          <el-radio-group v-model="drawerTab" class="drawer__tabs" @change="onDrawerTabChange">
            <el-radio-button value="daily">日报</el-radio-button>
            <el-radio-button value="weekly">周报</el-radio-button>
          </el-radio-group>
        </header>

        <div class="drawer__body">
          <!-- 日报 -->
          <el-collapse
            v-if="drawerTab === 'daily' && dailyList.length"
            v-model="activeDetails"
            class="detail-list"
          >
            <el-collapse-item v-for="item in dailyList" :key="item.id" :name="String(item.id)">
              <template #title>
                <div class="detail-title">
                  <span class="detail-date">{{ item.reportDate }}</span>
                  <span class="detail-hours">{{ formatHours(item.hours) }} h</span>
                </div>
              </template>

              <div class="detail-body">
                <p><span>项目</span><b>{{ item.projectName || '—' }}</b></p>
                <p><span>完成</span><b>{{ item.contentDone || '—' }}</b></p>
                <p><span>计划</span><b>{{ item.contentPlan || '—' }}</b></p>
              </div>
            </el-collapse-item>
          </el-collapse>

          <!-- 周报 -->
          <el-collapse
            v-else-if="drawerTab === 'weekly' && weeklyList.length"
            v-model="activeWeeklies"
            class="detail-list"
          >
            <el-collapse-item v-for="item in weeklyList" :key="item.id" :name="String(item.id)">
              <template #title>
                <div class="detail-title">
                  <span class="detail-date">{{ item.startDate }} ~ {{ item.endDate }}</span>
                  <span class="detail-hours">周报</span>
                </div>
              </template>

              <div class="detail-body">
                <p><span>项目</span><b>{{ item.projectName || '—' }}</b></p>
                <p><span>总结</span><b>{{ item.summary || '—' }}</b></p>
                <p><span>计划</span><b>{{ item.plan || '—' }}</b></p>
              </div>
            </el-collapse-item>
          </el-collapse>

          <p v-else-if="!drawerLoading" class="drawer__empty">
            {{ drawerError || (drawerTab === 'daily' ? '这个区间没有已提交的日报' : '这个区间没有已提交的周报') }}
          </p>
        </div>
      </div>

      <template #footer>
        <el-button @click="drawerVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 部门/项目成员名单（穿透，不跳页；点姓名再叠加明细弹窗） -->
    <MemberRosterDialog
      v-model="rosterVisible"
      :title="rosterTitle"
      :members="rosterMembers"
      :loading="rosterLoading"
      :clickable="canViewDetail"
      @select="openDetailFromRoster"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Search } from '@element-plus/icons-vue'
import MemberRosterDialog from '@/components/common/MemberRosterDialog.vue'
import { getDeptMembers, getDeptTree } from '@/api/dept'
import { getProjectMembers } from '@/api/project'
import { listProjects } from '@/api/report'
import { exportSummary, getSummary, getUserDailyDetail, getUserWeeklyDetail } from '@/api/summary'
import { useSummaryStore } from '@/stores/summary'
import { getUser } from '@/utils/auth'

const user = getUser()
const isLeader = computed(() => user?.role === 'DEPT_LEADER')
// 总裁监督权：高管可下钻看任何人明细；负责人只看本部门；管理员不开放
const canViewDetail = computed(() => ['EXECUTIVE', 'DEPT_LEADER'].includes(user?.role))

const dimensions = [
  { value: 'project', label: '项目' },
  { value: 'dept', label: '部门' },
  { value: 'user', label: '人' },
  { value: 'date', label: '时间' }
]

function todayStr() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function monthStartStr() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`
}

// 筛选条件放 Pinia：切到看板/工作台再回来，条件还在
const summaryStore = useSummaryStore()
const filters = summaryStore.filters

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const exporting = ref(false)
const depts = ref([])
const projects = ref([])

/* ---------------- 明细抽屉 ---------------- */
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const drawerUser = ref(null)
const drawerError = ref('')
const drawerTab = ref('daily')
const dailyList = ref([])
const weeklyList = ref([])
const activeDetails = ref([])
const activeWeeklies = ref([])

function rangeParams() {
  const [startDate, endDate] = filters.dateRange || []
  return { startDate, endDate }
}

/**
 * 打开成员明细大窗。
 *
 * 注意：不能按"当前维度"提前 return —— 项目/部门维度是从成员名单里点进来的，
 * 那样写会把点击事件直接吞掉（验收时表现为"点人名没反应"）。
 */
async function openDetail(row) {
  if (!row?.id) {
    return
  }
  drawerUser.value = row
  drawerTab.value = 'daily'
  dailyList.value = []
  weeklyList.value = []
  drawerError.value = ''
  activeDetails.value = []
  activeWeeklies.value = []
  drawerVisible.value = true

  await loadDaily()
}

/** 从名单弹窗里点姓名：先收起名单，再弹明细大窗，不跳页 */
function openDetailFromRoster(member) {
  if (!member) return
  rosterVisible.value = false
  openDetail({
    id: member.id,
    name: member.realName,
    sub: member.deptName,
    rate: null
  })
}

async function loadDaily() {
  if (!drawerUser.value) return
  drawerLoading.value = true
  drawerError.value = ''
  try {
    const res = await getUserDailyDetail(drawerUser.value.id, rangeParams())
    dailyList.value = res.data || []
  } catch (error) {
    drawerError.value = error?.message || '明细加载失败'
  } finally {
    drawerLoading.value = false
  }
}

async function loadWeekly() {
  if (!drawerUser.value) return
  drawerLoading.value = true
  drawerError.value = ''
  try {
    const res = await getUserWeeklyDetail(drawerUser.value.id, rangeParams())
    weeklyList.value = res.data || []
  } catch (error) {
    drawerError.value = error?.message || '周报加载失败'
  } finally {
    drawerLoading.value = false
  }
}

function onDrawerTabChange(value) {
  drawerError.value = ''
  if (value === 'weekly' && weeklyList.value.length === 0) {
    loadWeekly()
  }
}

/* ---------------- 部门/项目成员名单 ---------------- */
const rosterVisible = ref(false)
const rosterTitle = ref('成员名单')
const rosterMembers = ref([])
const rosterLoading = ref(false)

async function openDeptRoster(row) {
  rosterTitle.value = `${row.name} · 成员名单`
  rosterMembers.value = []
  rosterVisible.value = true
  rosterLoading.value = true
  try {
    const res = await getDeptMembers(row.id)
    rosterMembers.value = res.data || []
  } catch {
    rosterMembers.value = []
  } finally {
    rosterLoading.value = false
  }
}

async function openProjectRoster(row) {
  rosterTitle.value = `${row.name} · 成员名单`
  rosterMembers.value = []
  rosterVisible.value = true
  rosterLoading.value = true
  try {
    const res = await getProjectMembers(row.id)
    rosterMembers.value = res.data || []
  } catch {
    rosterMembers.value = []
  } finally {
    rosterLoading.value = false
  }
}

const currentTab = computed(
  () => dimensions.find((item) => item.value === filters.dimension) || dimensions[0]
)

const rangeHint = computed(() => {
  const [start, end] = filters.dateRange || []
  return start && end ? `${start} ~ ${end}` : '本月'
})

/** 列定义：与后端导出列保持一致；linkType 控制是否可点开名单/明细 */
const COLUMN_MAP = {
  project: [
    { prop: 'name', label: '项目', minWidth: 220, linkType: 'project' },
    { prop: 'sub', label: '负责人', minWidth: 120 },
    { prop: 'extra', label: '部门', minWidth: 140 },
    { prop: 'submitted', label: '日报数', minWidth: 110, align: 'right', mono: true },
    { prop: 'memberCount', label: '参与人数', minWidth: 110, align: 'right', mono: true },
    { prop: 'hours', label: '总工时', minWidth: 100, align: 'right', hours: true }
  ],
  dept: [
    { prop: 'name', label: '部门', minWidth: 180, linkType: 'dept' },
    { prop: 'memberCount', label: '人数', minWidth: 90, align: 'right', mono: true },
    { prop: 'submitted', label: '已提交人次', minWidth: 130, align: 'right', mono: true },
    { prop: 'expected', label: '应提交人次', minWidth: 130, align: 'right', mono: true },
    { prop: 'rate', label: '提交率', minWidth: 150, percent: true },
    { prop: 'hours', label: '总工时', minWidth: 100, align: 'right', hours: true }
  ],
  user: [
    { prop: 'name', label: '姓名', minWidth: 150, linkType: 'user' },
    { prop: 'sub', label: '部门', minWidth: 150 },
    { prop: 'submitted', label: '提交情况', minWidth: 120, align: 'right', submitted: true },
    { prop: 'rate', label: '提交率', minWidth: 150, percent: true },
    { prop: 'hours', label: '总工时', minWidth: 100, align: 'right', hours: true }
  ],
  date: [
    { prop: 'name', label: '日期', minWidth: 140, mono: true },
    { prop: 'sub', label: '星期', minWidth: 100 },
    { prop: 'submitted', label: '已提交人数', minWidth: 130, align: 'right', mono: true },
    { prop: 'expected', label: '应提交人数', minWidth: 130, align: 'right', mono: true },
    { prop: 'rate', label: '提交率', minWidth: 150, percent: true },
    { prop: 'hours', label: '总工时', minWidth: 100, align: 'right', hours: true }
  ]
}

const columns = computed(() => COLUMN_MAP[filters.dimension] || COLUMN_MAP.user)

/* ---------------- 数据 ---------------- */

function buildParams() {
  const [startDate, endDate] = filters.dateRange || []
  return {
    dimension: filters.dimension,
    page: filters.page,
    size: filters.size,
    startDate,
    endDate,
    deptId: isLeader.value ? undefined : filters.deptId || undefined,
    projectId: filters.projectId || undefined,
    keyword: filters.keyword || undefined
  }
}

async function load() {
  loading.value = true
  try {
    const res = await getSummary(buildParams())
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  filters.page = 1
  load()
}

function onDimensionChange() {
  filters.page = 1
  load()
}

function onSizeChange() {
  filters.page = 1
  load()
}

function reset() {
  filters.dimension = 'user'
  filters.page = 1
  filters.size = 10
  filters.dateRange = [monthStartStr(), todayStr()]
  filters.deptId = null
  filters.projectId = null
  filters.keyword = ''
  load()
}

function display(row, prop) {
  const value = row?.[prop]
  return value === null || value === undefined || value === '' ? '—' : value
}

function formatHours(hours) {
  const value = Number(hours ?? 0)
  return Number.isNaN(value) ? '0.0' : value.toFixed(1)
}

async function handleExport() {
  exporting.value = true
  try {
    const blob = await exportSummary(buildParams())

    // 后端异常时会返回 JSON（仍带 200 状态），这里拦下来给出提示
    if (blob?.type && blob.type.includes('application/json')) {
      const text = await blob.text()
      let message = '导出失败'
      try {
        message = JSON.parse(text)?.message || message
      } catch {
        /* ignore */
      }
      ElMessage.error(message)
      return
    }

    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `周览汇总_${currentTab.value.label}_${todayStr()}.xlsx`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)

    ElMessage.success('已开始下载')
  } catch {
    /* 拦截器已提示 */
  } finally {
    exporting.value = false
  }
}

async function loadOptions() {
  try {
    const res = await listProjects({ page: 1, size: 100 })
    projects.value = res.data?.records || []
  } catch {
    /* ignore */
  }
  if (isLeader.value) {
    return
  }
  try {
    const res = await getDeptTree()
    depts.value = (res.data || []).flatMap((root) => root.children || [])
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
.summary {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 1360px;
  margin: 0 auto;
}

.summary__head {
  padding: 4px 2px 0;
}

.summary__head h1 {
  margin: 0;
  color: var(--wl-text);
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 1px;
}

.summary__head p {
  margin: 8px 0 0;
  color: var(--wl-muted);
  font-size: 12px;
}

.card {
  border-radius: 16px;
  background: var(--wl-card);
  box-shadow:
    0 0 0 1px rgba(63, 74, 90, 0.05),
    0 14px 32px -26px rgba(63, 74, 90, 0.5);
}

/* ---------------- 工具栏 ---------------- */
.toolbar {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 18px;
}

.seg {
  align-self: flex-start;
}

.seg :deep(.el-radio-button__inner) {
  border-color: rgba(63, 74, 90, 0.12);
  background: #fff;
  color: rgba(40, 38, 36, 0.6);
  font-size: 13px;
  padding: 8px 20px;
  transition: all 0.3s var(--wl-ease);
}

.seg :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--wl-primary);
  border-color: var(--wl-primary);
  color: #fff;
  box-shadow: 0 10px 22px -16px rgba(63, 74, 90, 0.95);
}

.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter {
  width: 150px;
}

.filter--date {
  width: 250px;
}

.filter--kw {
  width: 220px;
}

/* ---------------- 表格 ---------------- */
.table-card {
  padding: 16px 18px 10px;
}

.table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.table-head__left {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.table-head__left h3 {
  margin: 0;
  color: var(--wl-text);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.8px;
}

.table-head__hint {
  color: rgba(40, 38, 36, 0.36);
  font-size: 11px;
}

.export-btn {
  border-color: rgba(63, 74, 90, 0.16);
  color: var(--wl-primary);
}

.export-btn:hover {
  border-color: rgba(63, 74, 90, 0.36);
  background: rgba(63, 74, 90, 0.04);
  color: var(--wl-primary);
}

/* 极简表格：无粗边框、浅灰表头、行悬浮变色 */
.table {
  --el-table-border-color: transparent;
  --el-table-header-bg-color: #faf9f7;
  --el-table-row-hover-bg-color: rgba(63, 74, 90, 0.035);
  --el-table-text-color: #282624;
  --el-table-header-text-color: rgba(40, 38, 36, 0.52);
  background: transparent;
}

.table :deep(th.el-table__cell) {
  border-bottom: 1px solid rgba(63, 74, 90, 0.07);
  font-weight: 600;
  font-size: 12px;
  letter-spacing: 0.4px;
}

.table :deep(td.el-table__cell) {
  border-bottom: 1px solid rgba(63, 74, 90, 0.04);
  font-size: 13px;
  padding: 11px 0;
}

.table :deep(.el-table__row:hover > td.el-table__cell) {
  background: rgba(63, 74, 90, 0.035) !important;
}

.table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.rate {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.rate__bar {
  position: relative;
  display: inline-block;
  width: 64px;
  height: 4px;
  border-radius: 2px;
  background: rgba(63, 74, 90, 0.08);
  overflow: hidden;
}

.rate__bar i {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  border-radius: 2px;
  background: linear-gradient(90deg, #3f4a5a, #7a6a93);
  transition: width 0.5s var(--wl-spring);
}

.rate b {
  min-width: 38px;
  color: var(--wl-text);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.mono {
  font-variant-numeric: tabular-nums;
}

.empty {
  color: rgba(40, 38, 36, 0.36);
  font-size: 12px;
}

.table-foot {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 6px;
}

.table-foot :deep(.el-pagination) {
  --el-pagination-button-bg-color: transparent;
}

/* 姓名链接 */
.name-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.leader-badge {
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(122, 106, 147, 0.12);
  color: var(--wl-violet);
  font-size: 10px;
  line-height: 1.6;
  white-space: nowrap;
}

.name-link {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--wl-primary);
  font-size: 13px;
  font-weight: 500;
  text-decoration: underline;
  text-decoration-color: rgba(63, 74, 90, 0.25);
  text-underline-offset: 3px;
  cursor: pointer;
  transition:
    color 0.24s var(--wl-ease),
    text-decoration-color 0.24s var(--wl-ease);
}

.name-link:hover {
  color: var(--wl-violet);
  text-decoration-color: rgba(122, 106, 147, 0.6);
}

/* ---------------- 明细大弹窗（800px） ---------------- */
.drawer {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 240px;
}

.drawer__body {
  max-height: 56vh;
  overflow-y: auto;
  padding-right: 4px;
}

.drawer__body::-webkit-scrollbar {
  width: 4px;
}

.drawer__body::-webkit-scrollbar-thumb {
  border-radius: 2px;
  background: rgba(63, 74, 90, 0.14);
}

.drawer__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(63, 74, 90, 0.06);
}

.drawer__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: linear-gradient(145deg, #3f4a5a, #7a6a93);
  color: #fff;
  font-size: 15px;
}

.drawer__meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.drawer__meta b {
  color: var(--wl-text);
  font-size: 14px;
  font-weight: 600;
}

.drawer__meta small {
  color: rgba(40, 38, 36, 0.42);
  font-size: 11px;
}

.drawer__count {
  padding: 3px 8px;
  border-radius: 6px;
  background: rgba(122, 106, 147, 0.1);
  color: var(--wl-violet);
  font-size: 11px;
}

.drawer__tabs {
  margin-left: auto;
  align-self: center;
}

.drawer__tabs :deep(.el-radio-button__inner) {
  border-color: rgba(63, 74, 90, 0.12);
  background: #fff;
  color: rgba(40, 38, 36, 0.6);
  font-size: 12px;
  padding: 6px 18px;
}

.drawer__tabs :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--wl-primary);
  border-color: var(--wl-primary);
  color: #fff;
}

.detail-list {
  border-top: none;
  border-bottom: none;
}

.detail-list :deep(.el-collapse-item__header) {
  border-bottom: 1px solid rgba(63, 74, 90, 0.05);
  font-size: 13px;
}

.detail-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 8px;
}

.detail-date {
  color: var(--wl-text);
  font-variant-numeric: tabular-nums;
}

.detail-hours {
  color: rgba(40, 38, 36, 0.42);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.detail-body p {
  display: flex;
  gap: 10px;
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 1.75;
}

.detail-body p span {
  flex: 0 0 32px;
  color: rgba(40, 38, 36, 0.34);
}

.detail-body p b {
  flex: 1;
  color: rgba(40, 38, 36, 0.72);
  font-weight: 400;
  white-space: pre-wrap;
}

.drawer__empty {
  padding: 44px 0;
  margin: 0;
  color: rgba(40, 38, 36, 0.34);
  font-size: 12px;
  text-align: center;
}
</style>
