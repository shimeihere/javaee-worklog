import request from '@/utils/request'

/* ---------------- 月历 ---------------- */

/** 月历状态：{ yearMonth: '2026-10' } */
export function getDailyCalendar(yearMonth) {
  return request.get('/reports/daily/calendar', { params: { yearMonth } })
}

/* ---------------- 日报 ---------------- */

/** 某天的日报（没有则 data 为 null） */
export function getDailyByDate(date) {
  return request.get('/reports/daily', { params: { date } })
}

/** 新建 / 覆盖草稿（同一天） */
export function saveDailyDraft(data) {
  return request.post('/reports/daily', data)
}

/** 编辑草稿 */
export function updateDailyDraft(id, data) {
  return request.put(`/reports/daily/${id}`, data)
}

/** 提交 */
export function submitDaily(id) {
  return request.post(`/reports/daily/${id}/submit`)
}

/** 撤回 */
export function withdrawDaily(id) {
  return request.post(`/reports/daily/${id}/withdraw`)
}

/* ---------------- 周报 ---------------- */

/** 某周周报（传周内任意一天，服务端归一到周一；没有则 data 为 null） */
export function getWeeklyByWeek(startDate) {
  return request.get('/reports/weekly', { params: { startDate } })
}

/** 新建 / 覆盖周报草稿（同一周） */
export function saveWeeklyDraft(data) {
  return request.post('/reports/weekly', data)
}

/** 编辑周报草稿 */
export function updateWeeklyDraft(id, data) {
  return request.put(`/reports/weekly/${id}`, data)
}

/** 提交周报 */
export function submitWeekly(id) {
  return request.post(`/reports/weekly/${id}/submit`)
}

/** 撤回周报 */
export function withdrawWeekly(id) {
  return request.post(`/reports/weekly/${id}/withdraw`)
}

/** 周报分页（个人页用来标记哪些周已填） */
export function getWeeklyPage(params) {
  return request.get('/reports/weekly/page', { params })
}

/* ---------------- 项目 ---------------- */

// 项目接口已独立到 api/project.js，这里保留转出，老代码无需改动
export { listProjects } from '@/api/project'

/* ---------------- 附件 ---------------- */

/** 附件列表：reportType 1 日报 / 2 周报 */
export function listAttachments(reportType, reportId) {
  return request.get('/attachments', { params: { reportType, reportId } })
}

/** 上传附件（FormData: file / reportType / reportId） */
export function uploadAttachment(formData) {
  return request.post('/attachments/upload', formData)
}

/** 删除附件 */
export function deleteAttachment(id) {
  return request.delete(`/attachments/${id}`)
}
