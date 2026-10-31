import { createRouter, createWebHistory } from 'vue-router'
import { getToken, getUser } from '@/utils/auth'

/** 角色常量，与后端 RoleConstant 保持一致 */
export const ROLES = {
  EXECUTIVE: 'EXECUTIVE',
  ADMIN: 'ADMIN',
  DEPT_LEADER: 'DEPT_LEADER',
  EMPLOYEE: 'EMPLOYEE'
}

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guestOnly: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/AppLayout.vue'),
    redirect: '/workbench',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'workbench',
        name: 'workbench',
        component: () => import('@/views/workbench/WorkbenchView.vue'),
        meta: { title: '工作台' }
      },
      {
        path: 'reports/daily',
        name: 'daily-report',
        component: () => import('@/views/report/DailyReportView.vue'),
        // 只有员工与部门负责人可以填报
        meta: { title: '日报填报', roles: [ROLES.EMPLOYEE, ROLES.DEPT_LEADER] }
      },
      {
        path: 'reports/weekly',
        name: 'weekly-report',
        component: () => import('@/views/report/WeeklyReportView.vue'),
        meta: { title: '周报填报', roles: [ROLES.EMPLOYEE, ROLES.DEPT_LEADER] }
      },
      {
        path: 'reports/summary',
        name: 'summary',
        component: () => import('@/views/summary/SummaryView.vue'),
        // 高管看全公司、负责人看本部门；管理员不可见（防泄密）
        meta: { title: '汇总中心', roles: [ROLES.EXECUTIVE, ROLES.DEPT_LEADER] }
      },
      {
        path: 'dashboard',
        name: 'data-board',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '数据看板', roles: [ROLES.EXECUTIVE, ROLES.DEPT_LEADER] }
      },
      {
        path: 'messages',
        name: 'messages',
        component: () => import('@/views/message/MessageCenterView.vue'),
        meta: { title: '消息中心' }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/profile/ProfileView.vue'),
        meta: { title: '个人中心' }
      },
      {
        path: 'system',
        name: 'system',
        component: () => import('@/views/system/SystemSettingsView.vue'),
        meta: { title: '系统设置', roles: [ROLES.ADMIN] }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/workbench'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

/**
 * 路由守卫
 * 1) 需要登录但没令牌 -> 登录页
 * 2) 页面声明了 roles 但当前角色不在其中 -> 回工作台（后端也会 403，双保险）
 */
router.beforeEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} · 周览 Worklog` : '周览 Worklog'

  const token = getToken()
  const requiresAuth = to.matched.some((record) => record.meta?.requiresAuth)

  if (requiresAuth && !token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta?.guestOnly && token) {
    return { name: 'workbench' }
  }

  const allowed = to.meta?.roles
  if (allowed) {
    const role = getUser()?.role
    if (!allowed.includes(role)) {
      return { name: 'workbench' }
    }
  }

  return true
})

export default router
