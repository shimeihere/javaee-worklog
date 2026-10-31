<template>
  <div class="report">
    <header class="report__head">
      <div>
        <h1>周报填报</h1>
        <p>以周为单位回看产出，每周一自动归入新的周期</p>
      </div>

      <el-radio-group :model-value="'weekly'" class="seg" @change="switchTab">
        <el-radio-button value="daily">日报</el-radio-button>
        <el-radio-button value="weekly">周报</el-radio-button>
      </el-radio-group>
    </header>

    <div class="report__body">
      <!-- 左侧：周列表 -->
      <aside class="report__aside">
        <section class="weeks">
          <header class="weeks__head">
            <div class="weeks__nav">
              <button class="weeks__nav-btn" type="button" @click="shiftMonth(-1)">
                <el-icon><ArrowLeft /></el-icon>
              </button>
              <span class="weeks__title">{{ monthTitle }}</span>
              <button class="weeks__nav-btn" type="button" @click="shiftMonth(1)">
                <el-icon><ArrowRight /></el-icon>
              </button>
            </div>
            <button class="weeks__today" type="button" @click="goCurrentWeek">本周</button>
          </header>

          <ul v-loading="weeksLoading" class="weeks__list">
            <li
              v-for="week in weeks"
              :key="week.start"
              class="week"
              :class="{
                'is-active': week.start === selectedWeek,
                'is-current': week.start === currentMonday
              }"
              @click="selectWeek(week.start)"
            >
              <div class="week__main">
                <b>{{ week.label }}</b>
                <small>{{ week.start.slice(5) }} ~ {{ week.end.slice(5) }}</small>
              </div>
              <span class="week__tag" :class="`tag--${weekState(week)}`">
                {{ stateLabel(weekState(week)) }}
              </span>
            </li>
          </ul>

          <footer class="weeks__legend">
            <span><i class="dot dot--done" />已提交</span>
            <span><i class="dot dot--draft" />草稿</span>
            <span><i class="dot dot--overdue" />逾期</span>
          </footer>
        </section>
      </aside>

      <!-- 右侧：表单 -->
      <section v-loading="reportLoading" class="panel">
        <header class="panel__head">
          <div class="panel__title">
            <h2>{{ weekLabel }}</h2>
            <span class="badge" :class="`badge--${stateKey}`">{{ stateLabel(stateKey) }}</span>
          </div>
          <span v-if="isSubmitted" class="panel__note">已提交的周报为只读，撤回后可重新编辑</span>
        </header>

        <el-form :model="form" :disabled="isSubmitted" label-position="top" class="panel__form">
          <el-form-item label="所属项目" required>
            <el-select
              v-model="form.projectId"
              class="fill"
              placeholder="选择项目"
              filterable
              :loading="projectsLoading"
            >
              <el-option
                v-for="project in projects"
                :key="project.id"
                :label="project.projectName"
                :value="project.id"
              >
                <span class="opt__name">{{ project.projectName }}</span>
                <span class="opt__meta">{{ project.leaderName }}</span>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="本周总结" required>
            <el-input
              v-model="form.summary"
              type="textarea"
              :rows="6"
              maxlength="4000"
              show-word-limit
              placeholder="本周完成了什么？关键进展、结果和影响"
            />
          </el-form-item>

          <el-form-item label="下周计划">
            <el-input
              v-model="form.plan"
              type="textarea"
              :rows="4"
              maxlength="4000"
              show-word-limit
              placeholder="下周准备推进什么"
            />
          </el-form-item>
        </el-form>

        <AttachmentUploader
          :report-type="2"
          :report-id="report?.id || null"
          :attachments="attachments"
          :disabled="isSubmitted"
          @refresh="reloadReport"
        />

        <footer class="panel__foot">
          <template v-if="!isSubmitted">
            <el-button :loading="saving" @click="saveDraft">保存草稿</el-button>
            <el-button type="primary" :loading="saving" @click="submit">提交周报</el-button>
          </template>
          <el-button v-else class="btn--withdraw" :loading="saving" @click="withdraw">
            撤回重填
          </el-button>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import AttachmentUploader from '@/components/report/AttachmentUploader.vue'
import {
  getWeeklyByWeek,
  getWeeklyPage,
  listProjects,
  saveWeeklyDraft,
  submitWeekly,
  updateWeeklyDraft,
  withdrawWeekly
} from '@/api/report'
import { getUser } from '@/utils/auth'

const router = useRouter()
const me = getUser()

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatDate(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function mondayOf(date) {
  const copy = new Date(date)
  const offset = (copy.getDay() + 6) % 7
  copy.setDate(copy.getDate() - offset)
  copy.setHours(0, 0, 0, 0)
  return copy
}

const today = new Date()
const todayStr = formatDate(today)
const currentMonday = formatDate(mondayOf(today))

const selectedWeek = ref(currentMonday)
const viewMonth = ref(`${today.getFullYear()}-${pad(today.getMonth() + 1)}`)

const weeks = ref([])
const weeksLoading = ref(false)
const reportMap = ref({})

const report = ref(null)
const reportLoading = ref(false)

const projects = ref([])
const projectsLoading = ref(false)

const saving = ref(false)

const form = reactive({
  projectId: null,
  summary: '',
  plan: ''
})

/* ---------------- 派生 ---------------- */

const isSubmitted = computed(() => report.value?.status === 1)

const stateKey = computed(() => {
  if (isSubmitted.value) return 'done'
  return report.value ? 'draft' : 'empty'
})

const attachments = computed(() => report.value?.attachments || [])

const monthTitle = computed(() => {
  const [y, m] = viewMonth.value.split('-')
  return `${y} 年 ${Number(m)} 月`
})

const weekLabel = computed(() => {
  const week = weeks.value.find((item) => item.start === selectedWeek.value)
  if (!week) return ''
  return `${week.label}${week.start === currentMonday ? ' · 本周' : ''}`
})

function stateLabel(state) {
  return { done: '已提交', draft: '草稿', overdue: '逾期', empty: '未填' }[state] || '未填'
}

function weekState(week) {
  const item = reportMap.value[week.start]
  if (item?.status === 1) return 'done'
  if (item) return 'draft'
  return week.end < todayStr ? 'overdue' : 'empty'
}

/* ---------------- 月份与周 ---------------- */

function buildWeeks(yearMonth) {
  const [y, m] = yearMonth.split('-').map(Number)
  const firstDay = new Date(y, m - 1, 1)
  const lastDay = new Date(y, m, 0)

  const list = []
  let cursor = mondayOf(firstDay)
  while (cursor <= lastDay) {
    const start = new Date(cursor)
    const end = new Date(cursor)
    end.setDate(end.getDate() + 6)
    list.push({
      start: formatDate(start),
      end: formatDate(end),
      label: `${start.getMonth() + 1}/${start.getDate()} - ${end.getMonth() + 1}/${end.getDate()}`
    })
    cursor.setDate(cursor.getDate() + 7)
  }
  return list
}

function shiftMonth(delta) {
  const [y, m] = viewMonth.value.split('-').map(Number)
  const next = new Date(y, m - 1 + delta, 1)
  viewMonth.value = `${next.getFullYear()}-${pad(next.getMonth() + 1)}`
  weeks.value = buildWeeks(viewMonth.value)
  loadWeeks()
}

function goCurrentWeek() {
  viewMonth.value = `${today.getFullYear()}-${pad(today.getMonth() + 1)}`
  weeks.value = buildWeeks(viewMonth.value)
  selectedWeek.value = currentMonday
  loadWeeks()
  reloadReport()
}

function selectWeek(start) {
  if (selectedWeek.value === start) return
  selectedWeek.value = start
  reloadReport()
}

/* ---------------- 数据 ---------------- */

async function loadWeeks() {
  weeksLoading.value = true
  try {
    const [y, m] = viewMonth.value.split('-').map(Number)
    const monthEnd = new Date(y, m, 0)
    const from = mondayOf(new Date(y, m - 1, 1))
    from.setDate(from.getDate() - 7)

    const res = await getWeeklyPage({
      userId: me?.id,
      size: 100,
      startDate: formatDate(from),
      endDate: formatDate(monthEnd)
    })

    const map = {}
    ;(res.data?.records || []).forEach((item) => {
      map[item.startDate] = item
    })
    reportMap.value = map
  } catch {
    reportMap.value = {}
  } finally {
    weeksLoading.value = false
  }
}

async function loadProjects() {
  projectsLoading.value = true
  try {
    const res = await listProjects({ page: 1, size: 100 })
    projects.value = res.data?.records || []
  } catch {
    /* ignore */
  } finally {
    projectsLoading.value = false
  }
}

function syncForm() {
  form.projectId = report.value?.projectId ?? null
  form.summary = report.value?.summary ?? ''
  form.plan = report.value?.plan ?? ''
}

async function reloadReport() {
  reportLoading.value = true
  try {
    const res = await getWeeklyByWeek(selectedWeek.value)
    report.value = res.data || null
    syncForm()
  } catch {
    report.value = null
    syncForm()
  } finally {
    reportLoading.value = false
  }
}

async function refreshAll() {
  await Promise.all([reloadReport(), loadWeeks()])
}

/* ---------------- 操作 ---------------- */

function switchTab(value) {
  if (value === 'daily') {
    router.push({ name: 'daily-report' })
  }
}

function buildPayload() {
  return {
    startDate: selectedWeek.value,
    projectId: form.projectId,
    summary: form.summary,
    plan: form.plan
  }
}

async function saveDraft() {
  if (saving.value || isSubmitted.value) return
  saving.value = true
  try {
    if (report.value?.id) {
      await updateWeeklyDraft(report.value.id, buildPayload())
    } else {
      await saveWeeklyDraft(buildPayload())
    }
    ElMessage.success('周报草稿已保存')
    await refreshAll()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function submit() {
  if (saving.value || isSubmitted.value) return
  if (!form.projectId) {
    ElMessage.warning('提交前请选择所属项目')
    return
  }
  if (!form.summary?.trim()) {
    ElMessage.warning('提交前请填写本周总结')
    return
  }

  saving.value = true
  try {
    let id = report.value?.id
    if (id) {
      await updateWeeklyDraft(id, buildPayload())
    } else {
      const created = await saveWeeklyDraft(buildPayload())
      id = created.data.id
    }
    await submitWeekly(id)
    ElMessage.success('周报已提交')
    await refreshAll()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function withdraw() {
  if (!report.value?.id || saving.value) return
  saving.value = true
  try {
    await withdrawWeekly(report.value.id)
    ElMessage.success('已撤回为草稿，可以重新编辑')
    await refreshAll()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  weeks.value = buildWeeks(viewMonth.value)
  await Promise.all([loadProjects(), loadWeeks()])
  await reloadReport()
})
</script>

<style scoped>
.report {
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-width: 1240px;
  margin: 0 auto;
}

.report__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 2px 0;
}

.report__head h1 {
  margin: 0;
  color: var(--wl-text);
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 1px;
}

.report__head p {
  margin: 8px 0 0;
  color: var(--wl-muted);
  font-size: 12px;
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

.report__body {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.report__aside {
  position: sticky;
  top: 82px;
}

/* ---------------- 周列表 ---------------- */
.weeks {
  padding: 16px 16px 12px;
  border-radius: 16px;
  background: var(--wl-card);
  box-shadow:
    0 0 0 1px rgba(63, 74, 90, 0.05),
    0 14px 32px -26px rgba(63, 74, 90, 0.5);
}

.weeks__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.weeks__nav {
  display: flex;
  align-items: center;
  gap: 6px;
}

.weeks__nav-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: rgba(40, 38, 36, 0.5);
  cursor: pointer;
  transition: all 0.28s var(--wl-ease);
}

.weeks__nav-btn:hover {
  background: rgba(63, 74, 90, 0.07);
  color: var(--wl-primary);
}

.weeks__title {
  min-width: 92px;
  text-align: center;
  color: var(--wl-text);
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.weeks__today {
  padding: 4px 10px;
  border: 1px solid rgba(63, 74, 90, 0.12);
  border-radius: 6px;
  background: #fff;
  color: var(--wl-violet);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s var(--wl-ease);
}

.weeks__today:hover {
  border-color: rgba(122, 106, 147, 0.4);
}

.weeks__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.week {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  transition:
    background-color 0.26s var(--wl-ease),
    border-color 0.26s var(--wl-ease),
    transform 0.34s var(--wl-spring);
}

.week:hover {
  background: rgba(63, 74, 90, 0.04);
  transform: translateX(2px);
}

.week.is-active {
  border-color: rgba(63, 74, 90, 0.35);
  background: rgba(63, 74, 90, 0.06);
}

.week.is-current .week__main b::after {
  content: '本周';
  margin-left: 6px;
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(122, 106, 147, 0.14);
  color: var(--wl-violet);
  font-size: 10px;
  font-weight: 500;
}

.week__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.week__main b {
  color: var(--wl-text);
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.week__main small {
  color: rgba(40, 38, 36, 0.34);
  font-size: 11px;
}

.week__tag {
  flex: 0 0 auto;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
}

.tag--done {
  background: rgba(63, 74, 90, 0.1);
  color: var(--wl-primary);
}

.tag--draft {
  background: rgba(122, 106, 147, 0.12);
  color: var(--wl-violet);
}

.tag--overdue {
  background: rgba(185, 154, 122, 0.16);
  color: #8a6d4f;
}

.tag--empty {
  background: rgba(40, 38, 36, 0.05);
  color: rgba(40, 38, 36, 0.42);
}

.weeks__legend {
  display: flex;
  gap: 12px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid rgba(63, 74, 90, 0.06);
}

.weeks__legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: rgba(40, 38, 36, 0.42);
  font-size: 11px;
}

.dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.dot--done {
  background: var(--wl-primary);
}

.dot--draft {
  background: var(--wl-violet);
}

.dot--overdue {
  background: var(--wl-camel);
}

/* ---------------- 表单 ---------------- */
.panel {
  padding: 20px 22px 18px;
  border-radius: 16px;
  background: var(--wl-card);
  box-shadow:
    0 0 0 1px rgba(63, 74, 90, 0.05),
    0 14px 32px -26px rgba(63, 74, 90, 0.5);
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(63, 74, 90, 0.06);
}

.panel__title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel__title h2 {
  margin: 0;
  color: var(--wl-text);
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.panel__note {
  color: rgba(40, 38, 36, 0.38);
  font-size: 11px;
}

.badge {
  padding: 3px 9px;
  border-radius: 6px;
  font-size: 11px;
}

.badge--done {
  background: rgba(63, 74, 90, 0.1);
  color: var(--wl-primary);
}

.badge--draft {
  background: rgba(122, 106, 147, 0.12);
  color: var(--wl-violet);
}

.badge--empty {
  background: rgba(40, 38, 36, 0.06);
  color: rgba(40, 38, 36, 0.5);
}

.panel__form :deep(.el-textarea__inner) {
  border-radius: 10px;
  line-height: 1.8;
}

.panel__form :deep(.el-form-item__label) {
  color: rgba(40, 38, 36, 0.62);
  font-size: 12px;
  letter-spacing: 0.5px;
}

.fill {
  width: 100%;
}

.opt__name {
  color: var(--wl-text);
}

.opt__meta {
  margin-left: 8px;
  color: rgba(40, 38, 36, 0.36);
  font-size: 12px;
}

.panel__foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid rgba(63, 74, 90, 0.06);
}

.panel__foot :deep(.el-button) {
  height: 38px;
  padding: 0 20px;
  letter-spacing: 1px;
}

.btn--withdraw {
  border-color: rgba(185, 154, 122, 0.45);
  background: rgba(185, 154, 122, 0.1);
  color: #8a6d4f;
}

.btn--withdraw:hover {
  border-color: rgba(185, 154, 122, 0.7);
  background: rgba(185, 154, 122, 0.16);
  color: #7a5f45;
}

@media (max-width: 1080px) {
  .report__body {
    grid-template-columns: minmax(0, 1fr);
  }

  .report__aside {
    position: static;
  }
}
</style>
