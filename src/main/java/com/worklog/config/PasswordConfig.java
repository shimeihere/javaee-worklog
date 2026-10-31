package com.worklog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密器。
 *
 * <p>仅引入 spring-security-crypto，不启用完整 Spring Security 过滤链，
 * 鉴权由 {@code JwtAuthInterceptor} 完成，避免与自定义 JWT 方案冲突。</p>
 *
 * <p>BCrypt 默认强度 10，每次加密自带随机盐，同一明文两次结果不同，必须用
 * {@code matches(raw, encoded)} 校验。</p>
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
