package com.starlink.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/** 本地模式下让已上传图片可以通过 /uploads/ 访问。 */
@Configuration
@ConditionalOnProperty(name = "file.storage", havingValue = "local", matchIfMissing = true)
public class LocalUploadWebConfig implements WebMvcConfigurer {
    @Value("${file.local.base-path:./uploads}")
    private String basePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Path.of(basePath).toAbsolutePath().normalize();
        String location = root.toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
