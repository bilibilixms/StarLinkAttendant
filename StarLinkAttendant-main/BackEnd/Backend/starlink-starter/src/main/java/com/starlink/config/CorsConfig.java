package com.starlink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS 跨域配置。
 * <p>
 * 使用 {@link CorsConfigurationSource} 而非 {@link org.springframework.web.filter.CorsFilter}，
 * 以便 Spring Security 的 {@code .cors()} 能正确拾取配置。
 * <p>
 * 开发环境允许前端开发服务器（{@code http://localhost:5173}）跨域访问后端 API。
 *
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许的源（开发环境允许所有 localhost 端口，避免端口被占用时跨域报错）
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"
        ));

        // 允许的方法
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // 允许的请求头
        config.setAllowedHeaders(List.of("*"));

        // 暴露的响应头（文件下载需要）
        config.setExposedHeaders(List.of("Content-Disposition"));

        // 允许携带凭证
        config.setAllowCredentials(true);

        // 预检请求缓存时间（秒）
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
