package com.worklog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置（对应 application.yml 中 worklog.jwt.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "worklog.jwt")
public class JwtProperties {

    /** 令牌请求头名称 */
    private String header = "Authorization";

    /** 令牌前缀，注意带末尾空格 */
    private String tokenPrefix = "Bearer ";

    /** 签名密钥，生产环境务必通过环境变量注入，长度不小于 32 字节 */
    private String secret;

    /** 有效期（分钟） */
    private long expireMinutes = 720L;
}
