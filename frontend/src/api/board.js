import request from '@/utils/request'

/**
 * 看板聚合数据
 * @param {{ range?: string, deptId?: number, projectId?: number }} params
 */
export function getBoardOverview(params) {
  return request.get('/board/overview', { params })
}

/** 一键催报 */
export function remindUser(userId) {
  return request.post('/messages/remind', { userId })
}
