import request from '@/utils/request'

/** 用户分页（ADMIN / DEPT_LEADER） */
export function getUsers(params) {
  return request.get('/sys/users', { params })
}

/** 新增用户（仅 ADMIN） */
export function createUser(data) {
  return request.post('/sys/users', data)
}

/** 启用/禁用用户（仅 ADMIN） */
export function updateUserStatus(id, status) {
  return request.put(`/sys/users/${id}/status`, null, { params: { status } })
}
