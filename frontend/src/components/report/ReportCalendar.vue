<template>
  <section class="cal">
    <header class="cal__head">
      <div class="cal__nav">
        <button class="cal__nav-btn" type="button" aria-label="上个月" @click="shiftMonth(-1)">
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <span class="cal__title">{{ title }}</span>
        <button class="cal__nav-btn" type="button" aria-label="下个月" @click="shiftMonth(1)">
          <el-icon><ArrowRight /></el-icon>
        </button>
      </div>

      <button class="cal__today" type="button" @click="goToday">今天</button>
    </header>

    <div class="cal__week">
      <span v-for="label in weekLabels" :key="label">{{ label }}</span>
    </div>

    <div v-loading="loading" class="cal__grid">
      <span v-for="n in offset" :key="`blank-${n}`" class="cal__blank" />

      <button
        v-for="day in days"
        :key="day.date"
        type="button"
        class="cell"
        :class="{
          'is-selected': day.date === modelValue,
          'is-today': day.date === today,
          'is-future': day.future,
          'is-weekend': !day.workday
        }"
        :disabled="day.future"
        @click="emit('update:modelValue', day.date)"
      >
        <span class="cell__num">{{ day.day }}</span>
        <span class="cell__dots">
          <i v-if="day.status === 1" class="dot dot--done" />
          <i v-else-if="day.status === 2" class="dot dot--overdue" />
          <i v-else-if="day.reportId" class="dot dot--draft" />
          <i v-else class="dot dot--none" />
        </span>
      </button>
    </div>

    <footer class="cal__legend">
      <span class="legend__item"><i class="dot dot--done" />已提交</span>
      <span class="legend__item"><i class="dot dot--draft" />草稿</span>
      <span class="legend__item"><i class="dot dot--overdue" />逾期</span>
      <span class="legend__stat">{{ submitted }}/{{ workdays }} 天</span>
    </footer>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'

const props = defineProps({
  /** 选中日期 yyyy-MM-dd */
  modelValue: { type: String, default: '' },
  /** 后端返回的 CalendarVO */
  data: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'change-month'])

const weekLabels = ['一', '二', '三', '四', '五', '六', '日']

function formatDate(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

const today = formatDate(new Date())

const currentYearMonth = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
})

const yearMonth = computed(() => props.data?.yearMonth || currentYearMonth.value)

const title = computed(() => {
  const [y, m] = yearMonth.value.split('-')
  return `${y} 年 ${Number(m)} 月`
})

/** 当月 1 号是星期几（周一为 0），用于前面的空白格 */
const offset = computed(() => {
  const [y, m] = yearMonth.value.split('-').map(Number)
  return (new Date(y, m - 1, 1).getDay() + 6) % 7
})

const days = computed(() => {
  const list = props.data?.days || []
  return list.map((item) => ({
    date: item.date,
    day: Number(String(item.date).slice(-2)),
    status: item.status,
    reportId: item.reportId || null,
    workday: item.workday !== false,
    future: item.date > today
  }))
})

const submitted = computed(() => props.data?.submittedDays ?? 0)
const workdays = computed(() => props.data?.workdays ?? 0)

function shiftMonth(delta) {
  const [y, m] = yearMonth.value.split('-').map(Number)
  const next = new Date(y, m - 1 + delta, 1)
  emit(
    'change-month',
    `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}`
  )
}

function goToday() {
  emit('change-month', currentYearMonth.value)
  emit('update:modelValue', today)
}
</script>

<style scoped>
.cal {
  padding: 18px 18px 14px;
  border-radius: 16px;
  background: var(--wl-card);
  box-shadow:
    0 0 0 1px rgba(63, 74, 90, 0.05),
    0 14px 32px -26px rgba(63, 74, 90, 0.5);
}

.cal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.cal__nav {
  display: flex;
  align-items: center;
  gap: 6px;
}

.cal__nav-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: rgba(40, 38, 36, 0.5);
  cursor: pointer;
  transition:
    background-color 0.28s var(--wl-ease),
    color 0.28s var(--wl-ease),
    transform 0.36s var(--wl-spring);
}

.cal__nav-btn:hover {
  background: rgba(63, 74, 90, 0.07);
  color: var(--wl-primary);
  transform: translateY(-1px);
}

.cal__title {
  min-width: 96px;
  text-align: center;
  color: var(--wl-text);
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.cal__today {
  padding: 4px 10px;
  border: 1px solid rgba(63, 74, 90, 0.12);
  border-radius: 6px;
  background: #fff;
  color: var(--wl-violet);
  font-size: 12px;
  cursor: pointer;
  transition:
    border-color 0.28s var(--wl-ease),
    transform 0.36s var(--wl-spring);
}

.cal__today:hover {
  border-color: rgba(122, 106, 147, 0.4);
  transform: translateY(-1px);
}

.cal__week {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-bottom: 6px;
}

.cal__week span {
  color: rgba(40, 38, 36, 0.34);
  font-size: 11px;
  text-align: center;
  letter-spacing: 0.5px;
}

.cal__grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
  min-height: 240px;
}

.cal__blank {
  height: 42px;
}

.cell {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  height: 42px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  transition:
    background-color 0.28s var(--wl-ease),
    border-color 0.28s var(--wl-ease),
    transform 0.36s var(--wl-spring),
    box-shadow 0.36s var(--wl-spring);
}

.cell:hover:not(:disabled) {
  background: rgba(63, 74, 90, 0.05);
  transform: translateY(-1px);
}

.cell__num {
  color: var(--wl-text);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.cell.is-weekend .cell__num {
  color: rgba(40, 38, 36, 0.4);
}

.cell.is-today .cell__num {
  color: var(--wl-primary);
  font-weight: 700;
}

.cell.is-future {
  cursor: not-allowed;
}

.cell.is-future .cell__num {
  color: rgba(40, 38, 36, 0.22);
}

.cell.is-selected {
  border-color: rgba(63, 74, 90, 0.45);
  background: #fff;
  box-shadow: 0 10px 20px -16px rgba(63, 74, 90, 0.9);
}

.cell__dots {
  display: flex;
  align-items: center;
  gap: 2px;
  height: 5px;
}

.dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: transparent;
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

.dot--none {
  background: rgba(63, 74, 90, 0.12);
}

.cal__legend {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(63, 74, 90, 0.06);
}

.legend__item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: rgba(40, 38, 36, 0.45);
  font-size: 11px;
}

.legend__stat {
  margin-left: auto;
  color: rgba(40, 38, 36, 0.38);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}
</style>
