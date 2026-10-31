import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearAuth, getToken } from '@/utils/auth'

/**
 * 统一 Axios 实例
 *
 * 后端约定：HTTP 状态码恒为 200，业务结果放在 body.code 里。
 * 因此拦截器只需要判断 code，前端各处不用重复写 if。
 */
const service = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

/** 防止并发的多个 401 触发多次跳转 */
let redirecting = false

async function redirectToLogin(message) {
  if (redirecting) {
    return
  }
  redirecting = true
  clearAuth()

  // 动态引入，避免 router -> view -> api -> request -> router 的循环依赖
  const { default: router } = await import('@/router')
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    ElMessage.warning(message || '登录状态已失效，请重新登录')
    await router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }

  setTimeout(() => {
    redirecting = false
  }, 800)
}

/* ---------------- 请求拦截：自动附带 Token ---------------- */
service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // FormData（附件上传）必须让浏览器自己带 multipart boundary，
    // 否则实例默认的 application/json 会把 FormData 序列化成 JSON。
    if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
      delete config.headers['Content-Type']
      delete config.headers['content-type']
    }

    return config
  },
  (error) => Promise.reject(error)
)

/* ---------------- 响应拦截：统一处理 code != 200 ---------------- */
service.interceptors.response.use(
  (response) => {
    const body = response.data

    // 非标准响应（如后续导出 Excel 的 blob）直接放行
    if (!body || typeof body !== 'object' || body.code === undefined) {
      return body
    }

    if (body.code === 200) {
      return body
    }

    // 401 未登录 / 4002 令牌过期：清空登录态并跳登录页
    if (body.code === 401 || body.code === 4002) {
      redirectToLogin(body.message)
    } else if (body.code === 4003) {
      // 账号被禁用：属于登录失败的一种，提示后停留
      ElMessage.error(body.message || '账号已被禁用')
    } else {
      ElMessage.error(body.message || '请求失败，请稍后重试')
    }

    const error = new Error(body.message || '请求失败')
    error.code = body.code
    error.body = body
    return Promise.reject(error)
  },
  (error) => {
    const status = error?.response?.status
    const backendMessage = error?.response?.data?.message

    if (status === 401) {
      redirectToLogin(backendMessage)
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else if (!error.response) {
      ElMessage.error('无法连接后端服务，请确认后端已在 8080 端口启动')
    } else {
      ElMessage.error(backendMessage || `请求失败（${status}）`)
    }

    return Promise.reject(error)
  }
)

export default service
