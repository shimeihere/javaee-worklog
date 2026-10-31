package com.worklog.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务状态码。
 *
 * <p>1xxx 预留系统级、2xxx 预留业务模块级；4xxx 段为业务错误细分码。</p>
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    /** 成功 */
    SUCCESS(200, "操作成功"),

    /** 参数错误 */
    BAD_REQUEST(400, "请求参数有误"),
    /** 未登录 / 令牌无效 */
    UNAUTHORIZED(401, "未登录或登录状态已失效"),
    /** 无权限 */
    FORBIDDEN(403, "没有访问权限"),
    /** 资源不存在 */
    NOT_FOUND(404, "请求的资源不存在"),
    /** 方法不支持 */
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    /** 账号或密码错误 */
    LOGIN_FAILED(4001, "账号或密码错误"),
    /** 令牌过期 */
    TOKEN_EXPIRED(4002, "登录已过期，请重新登录"),
    /** 账号被禁用 */
    ACCOUNT_DISABLED(4003, "账号已被禁用，请联系管理员"),
    /** 附件过大 */
    UPLOAD_TOO_LARGE(4004, "单个附件不能超过 10MB"),

    /** 系统异常 */
    SERVER_ERROR(500, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;
}
