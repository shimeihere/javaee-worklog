import request from '@/utils/request'

/** 两级部门树 */
export function getDeptTree() {
  return request.get('/sys/depts/tree')
}

/** 新增部门（仅 ADMIN） */
export function createDept(data) {
  return request.post('/sys/depts', data)
}

/** 编辑部门（仅 ADMIN） */
export function updateDept(id, data) {
  return request.put(`/sys/depts/${id}`, data)
}

/** 部门成员花名册（姓名 + 角色） */
export function getDeptMembers(id) {
  return request.get(`/sys/depts/${id}/members`)
}
