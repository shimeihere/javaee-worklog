package com.worklog.security;

import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.config.JwtProperties;
import com.worklog.entity.SysUser;
import com.worklog.mapper.SysUserMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * JWT 鉴权拦截器。
 *
 * <p>流程：取 Header 令牌 → 校验签名/有效期 → 回查数据库 → 校验账号状态 →
 * 写入 {@link UserContext} → 校验 {@link RequireRole}。</p>
 *
 * <p>每次请求都回查数据库，是为了让"禁用账号"立即生效（而非等到令牌过期）。
 * 单次主键查询成本很低，MVP 阶段不做缓存。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final SysUserMapper sysUserMapper;

    /** 账号状态：禁用 */
    private static final int STATUS_DISABLED = 1;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 静态资源、CORS 预检等非 Controller 处理
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        Long userId;
        try {
            userId = jwtUtil.parseUserId(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("非法令牌：{}", e.getMessage());
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (user.getStatus() != null && user.getStatus() == STATUS_DISABLED) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        LoginUser loginUser = new LoginUser(
                user.getId(), user.getUsername(), user.getRealName(), user.getRole(), user.getDeptId());
        UserContext.set(loginUser);

        checkRole(handlerMethod, loginUser);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    /** 方法上的注解优先于类上的注解 */
    private void checkRole(HandlerMethod handlerMethod, LoginUser loginUser) {
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole == null) {
            return;
        }
        boolean allowed = Arrays.asList(requireRole.value()).contains(loginUser.role());
        if (!allowed) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /** 支持 "Bearer xxx" 与裸 token 两种写法 */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(jwtProperties.getHeader());
        if (header == null || header.isBlank()) {
            return null;
        }
        String prefix = jwtProperties.getTokenPrefix();
        if (prefix != null && !prefix.isBlank() && header.startsWith(prefix)) {
            return header.substring(prefix.length()).trim();
        }
        return header.trim();
    }
}
