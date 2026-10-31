import request from '@/utils/request'

/**
 * 登录
 * @param {{ username: string, password: string }} data
 * @returns {Promise<{code:number, message:string, data:{token:string, tokenType:string, expiresIn:number, user:object}}>}
 */
export function loginApi(data) {
  return request.post('/auth/login', data)
}

/** 当前登录用户信息 */
export function meApi() {
  return request.get('/auth/me')
}

/** 登出（JWT 无状态，服务端仅预留接口） */
export function logoutApi() {
  return request.post('/auth/logout')
}

/** 修改密码（校验原密码） */
export function changePasswordApi(data) {
  return request.put('/auth/password', data)
}
