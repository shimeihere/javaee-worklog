import { defineStore } from 'pinia'

/**
 * 数据看板的筛选条件缓存。
 *
 * 放在 Pinia 里，切到别的页面再回来时不用重新选时间范围/部门/项目。
 */
export const useBoardStore = defineStore('board', {
  state: () => ({
    filters: {
      range: 'week',
      deptId: null,
      projectId: null
    }
  }),
  actions: {
    reset() {
      this.filters.range = 'week'
      this.filters.deptId = null
      this.filters.projectId = null
    }
  }
})
