import request from '@/utils/request'

/** 审计日志分页（仅 ADMIN）：{ page, size, keyword, action } */
export function getAuditLogs(params) {
  return request.get('/sys/audit-logs', { params })
}
