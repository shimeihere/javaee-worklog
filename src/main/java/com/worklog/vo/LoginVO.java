package com.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录响应。
 *
 * <p>前端约定：token 存 localStorage，请求头携带 {@code Authorization: Bearer <token>}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 访问令牌 */
    private String token;

    /** 令牌类型，固定 Bearer */
    private String tokenType;

    /** 有效期（秒） */
    private long expiresIn;

    /** 登录用户信息 */
    private UserInfoVO user;
}
