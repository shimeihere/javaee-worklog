import { defineStore } from 'pinia'

function pad(value) {
  return String(value).padStart(2, '0')
}

function todayStr() {
  const now = new Date()
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

function monthStartStr() {
  const now = new Date()
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-01`
}

/** 近 30 天：默认范围不要卡在"本月 1 号"，否则月初打开一片空数据 */
function daysAgoStr(days) {
  const d = new Date()
  d.setDate(d.getDate() - days)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/**
 * 汇总中心的筛选条件缓存（维度、分页、日期、部门、项目、关键字）。
 *
 * 切页面回来继续保持上次的查询条件，避免反复重选。
 */
export const useSummaryStore = defineStore('summary', {
  state: () => ({
    filters: {
      dimension: 'project',
      page: 1,
      size: 10,
      dateRange: [daysAgoStr(29), todayStr()],
      deptId: null,
      projectId: null,
      keyword: ''
    }
  }),
  actions: {
    reset() {
      this.filters.dimension = 'project'
      this.filters.page = 1
      this.filters.size = 10
      this.filters.dateRange = [daysAgoStr(29), todayStr()]
      this.filters.deptId = null
      this.filters.projectId = null
      this.filters.keyword = ''
    }
  }
})
