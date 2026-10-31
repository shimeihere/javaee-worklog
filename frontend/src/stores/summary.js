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
      dateRange: [monthStartStr(), todayStr()],
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
      this.filters.dateRange = [monthStartStr(), todayStr()]
      this.filters.deptId = null
      this.filters.projectId = null
      this.filters.keyword = ''
    }
  }
})
