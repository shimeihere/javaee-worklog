<template>
  <div class="report">
    <header class="report__head">
      <div>
        <h1>日报填报</h1>
        <p>记录今天做了什么，让每一周的产出都有迹可循</p>
      </div>

      <el-radio-group :model-value="'daily'" class="seg" @change="switchTab">
        <el-radio-button value="daily">日报</el-radio-button>
        <el-radio-button value="weekly">周报</el-radio-button>
      </el-radio-group>
    </header>

    <!-- ============ 日报 ============ -->
    <div class="report__body">
      <div class="report__aside">
        <ReportCalendar
          v-model="selectedDate"
          :data="calendar"
          :loading="calendarLoading"
          @change-month="onMonthChange"
        />
      </div>

      <section v-loading="reportLoading" class="panel">
        <header class="panel__head">
          <div class="panel__title">
            <h2>{{ dateLabel }}</h2>
            <span class="badge" :class="`badge--${stateKey}`">{{ stateLabel }}</span>
          </div>
          <span v-if="isSubmitted" class="panel__note">已提交的日报为只读，撤回后可重新编辑</span>
        </header>

        <el-form
          ref="formRef"
          :model="form"
          :disabled="isSubmitted"
          label-position="top"
          class="panel__form"
        >
          <el-form-item label="所属项目（选填）" >
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
            <p class="field-hint">不选也能提交，只是不会计入项目进度；做部门事务、杂事时可以留空。</p>
          </el-form-item>

          <el-form-item label="今日完成" required>
            <el-input
              v-model="form.contentDone"
              type="textarea"
              :rows="5"
              maxlength="4000"
              show-word-limit
              placeholder="今天完成了什么？尽量写清楚结果和影响"
            />
          </el-form-item>

          <el-form-item label="明日计划">
            <el-input
              v-model="form.contentPlan"
              type="textarea"
              :rows="3"
              maxlength="4000"
              show-word-limit
              placeholder="明天准备推进什么"
            />
          </el-form-item>

          <el-form-item label="工时（小时）">
            <el-input-number
              v-model="form.hours"
              :min="0"
              :max="24"
              :step="0.5"
              :precision="1"
              controls-position="right"
            />
          </el-form-item>
        </el-form>

        <AttachmentUploader
          :report-id="report?.id || null"
          :attachments="attachments"
          :disabled="isSubmitted"
          @refresh="reloadReport"
        />

        <footer class="panel__foot">
          <template v-if="!isSubmitted">
            <el-button :loading="saving" @click="saveDraft">保存草稿</el-button>
            <el-button type="primary" :loading="saving" @click="submit">提交日报</el-button>
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AttachmentUploader from '@/components/report/AttachmentUploader.vue'
import ReportCalendar from '@/components/report/ReportCalendar.vue'
import {
  getDailyByDate,
  getDailyCalendar,
  listProjects,
  saveDailyDraft,
  submitDaily,
  updateDailyDraft,
  withdrawDaily
} from '@/api/report'

const router = useRouter()

const WEEK_TEXT = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

function formatDate(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

const today = formatDate(new Date())

// 支持从工作台「写日报」带日期进来（?date=yyyy-MM-dd），默认选中今天
const route = useRoute()
const queryDate =
  typeof route.query.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(route.query.date)
    ? route.query.date
    : today

const selectedDate = ref(queryDate)
const viewMonth = ref(queryDate.slice(0, 7))

const calendar = ref(null)
const calendarLoading = ref(false)

const report = ref(null)
const reportLoading = ref(false)

const projects = ref([])
const projectsLoading = ref(false)

const saving = ref(false)
const formRef = ref(null)

const form = reactive({
  projectId: null,
  contentDone: '',
  contentPlan: '',
  hours: null
})

/* ---------------- 派生状态 ---------------- */

const isSubmitted = computed(() => report.value?.status === 1)

const stateKey = computed(() => {
  if (isSubmitted.value) return 'done'
  return report.value ? 'draft' : 'empty'
})

const stateLabel = computed(() => {
  if (isSubmitted.value) return '已提交'
  return report.value ? '草稿' : '未填'
})

const attachments = computed(() => report.value?.attachments || [])

const dateLabel = computed(() => {
  const date = new Date(`${selectedDate.value}T00:00:00`)
  const base = `${date.getMonth() + 1} 月 ${date.getDate()} 日 · ${WEEK_TEXT[date.getDay()]}`
  return selectedDate.value === today ? `${base} · 今天` : base
})

/* ---------------- 数据加载 ---------------- */

async function loadCalendar() {
  calendarLoading.value = true
  try {
    const res = await getDailyCalendar(viewMonth.value)
    calendar.value = res.data
  } catch {
    /* 拦截器已提示 */
  } finally {
    calendarLoading.value = false
  }
}

async function loadProjects() {
  projectsLoading.value = true
  try {
    const res = await listProjects({ page: 1, size: 100 })
    projects.value = res.data?.records || []
  } catch {
    /* 拦截器已提示 */
  } finally {
    projectsLoading.value = false
  }
}

function syncForm() {
  form.projectId = report.value?.projectId ?? null
  form.contentDone = report.value?.contentDone ?? ''
  form.contentPlan = report.value?.contentPlan ?? ''
  form.hours = report.value?.hours ?? null
}

async function reloadReport() {
  reportLoading.value = true
  try {
    const res = await getDailyByDate(selectedDate.value)
    report.value = res.data || null
    syncForm()
  } catch {
    /* 拦截器已提示 */
  } finally {
    reportLoading.value = false
  }
}

async function refreshAll() {
  await Promise.all([reloadReport(), loadCalendar()])
}

/* ---------------- 交互 ---------------- */

async function onMonthChange(yearMonth) {
  viewMonth.value = yearMonth
  if (selectedDate.value.slice(0, 7) !== yearMonth) {
    const currentMonth = today.slice(0, 7)
    selectedDate.value = yearMonth === currentMonth ? today : `${yearMonth}-01`
  }
  await loadCalendar()
}

function buildPayload() {
  return {
    reportDate: selectedDate.value,
    projectId: form.projectId,
    contentDone: form.contentDone,
    contentPlan: form.contentPlan,
    hours: form.hours
  }
}

async function saveDraft() {
  if (saving.value || isSubmitted.value) {
    return
  }
  saving.value = true
  try {
    if (report.value?.id) {
      await updateDailyDraft(report.value.id, buildPayload())
    } else {
      await saveDailyDraft(buildPayload())
    }
    ElMessage.success('草稿已保存')
    await refreshAll()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function submit() {
  if (saving.value || isSubmitted.value) {
    return
  }
  if (!form.contentDone?.trim()) {
    ElMessage.warning('提交前请填写今日完成内容')
    return
  }

  saving.value = true
  try {
    let id = report.value?.id
    if (id) {
      // 提交前先把当前编辑内容落库，避免丢失修改
      await updateDailyDraft(id, buildPayload())
    } else {
      const created = await saveDailyDraft(buildPayload())
      id = created.data.id
    }
    await submitDaily(id)
    ElMessage.success('日报已提交')
    await refreshAll()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function withdraw() {
  if (!report.value?.id || saving.value) {
    return
  }
  saving.value = true
  try {
    await withdrawDaily(report.value.id)
    ElMessage.success('已撤回为草稿，可以重新编辑')
    await refreshAll()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

/* ---------------- 生命周期 ---------------- */

watch(selectedDate, () => {
  reloadReport()
})

function switchTab(value) {
  if (value === 'weekly') {
    router.push({ name: 'weekly-report' })
  }
}

onMounted(async () => {
  await Promise.all([loadProjects(), loadCalendar()])
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

/* 分段控件式的 Tab */
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
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.report__aside {
  position: sticky;
  top: 82px;
}

/* ---------------- 表单面板 ---------------- */
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
  letter-spacing: 0.5px;
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
  transition: box-shadow 0.36s var(--wl-spring);
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

/* ---------------- 周报占位 ---------------- */
.soon {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 64px 24px;
  border-radius: 16px;
  background: var(--wl-card);
  box-shadow:
    0 0 0 1px rgba(63, 74, 90, 0.05),
    0 14px 32px -26px rgba(63, 74, 90, 0.4);
  text-align: center;
}

.soon__icon {
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

.soon h3 {
  margin: 6px 0 0;
  color: var(--wl-primary);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
}

.soon p {
  margin: 0;
  max-width: 380px;
  color: rgba(40, 38, 36, 0.45);
  font-size: 12px;
  line-height: 1.9;
}

@media (max-width: 1080px) {
  .report__body {
    grid-template-columns: minmax(0, 1fr);
  }

  .report__aside {
    position: static;
  }
}

.field-hint {
  width: 100%;
  margin: 6px 0 0;
  color: rgba(40, 38, 36, 0.4);
  font-size: 11px;
  line-height: 1.6;
}
</style>
