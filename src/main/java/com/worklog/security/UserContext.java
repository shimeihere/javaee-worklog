package com.worklog.security;

import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;

/**
 * 当前请求的登录用户上下文（ThreadLocal）。
 *
 * <p>由 {@link JwtAuthInterceptor} 在 preHandle 写入、afterCompletion 清理，
 * 业务层通过 {@link #require()} 获取当前用户，无需在方法签名里层层传递。</p>
 *
 * <p>注意：异步线程（@Async、定时任务）中拿不到该值，需要自行透传。</p>
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser loginUser) {
        HOLDER.set(loginUser);
    }

    /** 可能为 null（如登录接口本身） */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 必须已登录，否则抛 401 */
    public static LoginUser require() {
        LoginUser loginUser = HOLDER.get();
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    /** 当前登录用户ID */
    public static Long requireUserId() {
        return require().id();
    }

    /** 当前登录用户角色编码 */
    public static String requireRole() {
        return require().role();
    }

    /** 请求结束必须调用，防止线程池复用导致用户串号 */
    public static void clear() {
        HOLDER.remove();
    }
}
