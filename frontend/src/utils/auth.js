const TOKEN_KEY = 'worklog_token'
const USER_KEY = 'worklog_user'

/** 读取令牌 */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

/** 写入令牌 */
export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  }
}

/** 读取登录用户信息（LoginVO.user） */
export function getUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

/** 写入登录用户信息 */
export function setUser(user) {
  if (user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  }
}

/** 清空登录态（登出 / 令牌失效时调用） */
export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/** 是否已登录（仅判断本地是否有令牌，真实有效性由后端校验） */
export function isLoggedIn() {
  return Boolean(getToken())
}
