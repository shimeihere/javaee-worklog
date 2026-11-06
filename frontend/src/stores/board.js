import { defineStore } from 'pinia'

/**
 * 数据看板的筛选条件缓存。
 *
 * 放在 Pinia 里，切到别的页面再回来时不用重新选时间范围/部门/项目。
 */
export const useBoardStore = defineStore('board', {
  state: () => ({
    filters: {
      // 默认近 30 天：按周/按本月看时，月初或周一打开很容易全是 0，演示第一眼像坏掉了
      range: 'last30',
      deptId: null,
      projectId: null
    }
  }),
  actions: {
    reset() {
      this.filters.range = 'last30'
      this.filters.deptId = null
      this.filters.projectId = null
    }
  }
})
