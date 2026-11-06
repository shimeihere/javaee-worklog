import request from '@/utils/request'

/** 用户分页（ADMIN / DEPT_LEADER） */
export function getUsers(params) {
  return request.get('/sys/users', { params })
}

/** 新增用户（仅 ADMIN） */
export function createUser(data) {
  return request.post('/sys/users', data)
}

/** 批量新增用户（仅 ADMIN）：逐行返回成功/失败原因 */
export function batchCreateUsers(data) {
  return request.post('/sys/users/batch', data)
}

/** 编辑用户（仅 ADMIN）：姓名、角色、部门、参与项目 */
export function updateUser(id, data) {
  return request.put(`/sys/users/${id}`, data)
}

/** 启用/禁用用户（仅 ADMIN） */
export function updateUserStatus(id, status) {
  return request.put(`/sys/users/${id}/status`, null, { params: { status } })
}
