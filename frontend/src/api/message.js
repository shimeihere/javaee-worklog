import request from '@/utils/request'

/** 未读站内信数量（顶栏角标） */
export function getUnreadCount() {
  return request.get('/messages/unread-count')
}

/** 消息分页列表 */
export function getMessages(params) {
  return request.get('/messages', { params })
}

/** 最近未读（顶栏下拉面板） */
export function getRecentUnread(limit = 5) {
  return request.get('/messages/recent-unread', { params: { limit } })
}

/** 标记单条已读 */
export function markMessageRead(id) {
  return request.put(`/messages/${id}/read`)
}

/** 全部已读 */
export function markAllMessagesRead() {
  return request.put('/messages/read-all')
}

/** 一键催报（看板/汇总用） */
export function remindUser(userId) {
  return request.post('/messages/remind', { userId })
}
