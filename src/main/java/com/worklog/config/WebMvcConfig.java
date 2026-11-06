package com.worklog.config;

import com.worklog.security.JwtAuthInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Web MVC 配置：/api 前缀、鉴权拦截器、静态资源与 SPA 兜底、跨域。
 *
 * <p>关键点：应用不再设置 context-path，而是把 {@code @RestController} 统一加上
 * {@code /api} 前缀。这样根路径 {@code /} 可以正常返回前端 index.html，
 * 前端路由（如 /workbench）也能被 SPA 兜底接管。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /** 前端构建产物在 classpath 下的位置（npm run build 后复制到 src/main/resources/static） */
    private static final String STATIC_LOCATION = "classpath:/static/";

    private final JwtAuthInterceptor jwtAuthInterceptor;

    /**
     * 所有 REST 接口统一加 /api 前缀，等价于以前的 context-path，但不会影响静态资源。
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api",
                HandlerTypePredicate.forAnnotation(RestController.class)
                        .and(HandlerTypePredicate.forBasePackage("com.worklog")));
    }

    /**
     * 鉴权拦截只作用于 /api/**：静态资源、index.html、前端路由都不该被 JWT 拦下。
     *
     * <p>附件不再走 /upload/** 静态映射：一律通过 /api/attachments/{id}/download
     * 校验登录态与单据数据范围后再回流文件，避免"知道 URL 就能下载"。</p>
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login");
    }

    /** 前端静态资源（含 SPA history 模式兜底）；附件改由 Controller 鉴权后下载 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_LOCATION)
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 单页应用兜底：
     * <ul>
     *   <li>真实存在的静态文件（js/css/图片）正常返回；</li>
     *   <li>没有扩展名、且不在 /api 下的路径，一律返回 index.html，交给 Vue Router；</li>
     *   <li>/api/** 与 /upload/** 不做兜底，未命中仍然返回统一的 JSON 404。</li>
     * </ul>
     */
    static class SpaResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }

            boolean apiPath = isApiPath(resourcePath);
            boolean hasExtension = resourcePath.contains(".");
            if (apiPath || hasExtension) {
                return null;
            }

            Resource index = location.createRelative("index.html");
            return index.exists() && index.isReadable() ? index : null;
        }

        /** /api、/api/**、/upload、/upload/** 一律不做 SPA 兜底，交给接口返回 JSON */
        private boolean isApiPath(String resourcePath) {
            return resourcePath.equals("api") || resourcePath.startsWith("api/")
                    || resourcePath.equals("upload") || resourcePath.startsWith("upload/");
        }
    }
}
