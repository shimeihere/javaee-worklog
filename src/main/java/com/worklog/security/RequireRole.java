package com.worklog.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色访问控制注解，可标注在 Controller 类或方法上。
 *
 * <pre>{@code
 * @RequireRole({RoleConstant.ADMIN, RoleConstant.DEPT_LEADER})
 * @GetMapping("/sys/users")
 * public Result<?> list() { ... }
 * }</pre>
 *
 * <p>由 {@link JwtAuthInterceptor} 在鉴权后校验，命中失败返回 403。</p>
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色编码，命中任意一个即通过 */
    String[] value();
}
