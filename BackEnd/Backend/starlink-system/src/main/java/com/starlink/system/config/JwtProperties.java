package com.starlink.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret = "starlink-attendant-management-system-jwt-secret-key";

    private Long accessTokenExpire = 7200L;

    private Long refreshTokenExpire = 604800L;
}
