import request from '@/utils/request'

/** 项目列表（已按数据范围过滤） */
export function listProjects(params = { page: 1, size: 100 }) {
  return request.get('/projects', { params })
}

/** 项目详情 */
export function getProject(id) {
  return request.get(`/projects/${id}`)
}

/** 新建项目（ADMIN / DEPT_LEADER） */
export function createProject(data) {
  return request.post('/projects', data)
}

/** 项目成员花名册（负责人 + 参与人） */
export function getProjectMembers(id) {
  return request.get(`/projects/${id}/members`)
}
