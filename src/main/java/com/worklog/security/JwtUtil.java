package com.worklog.security;

import com.worklog.config.JwtProperties;
import com.worklog.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 生成与解析（jjwt 0.12.x API）。
 *
 * <p>令牌为无状态、不可撤销方案：登出仅前端丢弃令牌；需要强制下线时依赖
 * 每次请求回查 sys_user.status，禁用账号立即失效。</p>
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties jwtProperties;

    /** HS256 要求密钥至少 256 bit（32 字节） */
    private SecretKey signingKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成访问令牌，subject 存用户ID。
     */
    public String generateToken(SysUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(getExpiresInSeconds());
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLE, user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并校验签名与有效期。
     *
     * @throws io.jsonwebtoken.ExpiredJwtException 令牌过期
     * @throws io.jsonwebtoken.JwtException        签名不合法 / 格式错误
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 解析用户ID */
    public Long parseUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    /** 令牌有效期（秒），返回给前端用于倒计时/续期判断 */
    public long getExpiresInSeconds() {
        return jwtProperties.getExpireMinutes() * 60L;
    }
}
