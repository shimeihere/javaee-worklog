import request from '@/utils/request'

/** 工作台首屏汇总（今日状态 / 本周本月提交率 / 草稿 / 未读 / 项目数 / 团队） */
export function getDashboardSummary() {
  return request.get('/dashboard/summary')
}
