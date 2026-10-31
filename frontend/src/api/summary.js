import request from '@/utils/request'

/**
 * 四维汇总查询
 * @param {{ dimension: string, page: number, size: number, startDate?: string, endDate?: string, deptId?: number, projectId?: number, keyword?: string }} params
 */
export function getSummary(params) {
  return request.get('/summary', { params })
}

/** 导出 Excel（blob 方式，才能带上 Authorization 头） */
export function exportSummary(params) {
  return request.get('/summary/export', { params, responseType: 'blob' })
}

/**
 * 某成员的日报明细（抽屉）。
 * 权限：高管可看任何人，部门负责人仅本部门，管理员不可用。
 */
export function getUserDailyDetail(userId, params) {
  return request.get(`/summary/users/${userId}/daily`, { params })
}

/** 某成员的周报明细（抽屉「周报」Tab） */
export function getUserWeeklyDetail(userId, params) {
  return request.get(`/summary/users/${userId}/weekly`, { params })
}
