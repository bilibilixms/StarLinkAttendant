package com.starlink.system.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.starlink.system.config.JwtProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenUtil {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Long userId, String username) {
        return generateToken(userId, username, jwtProperties.getAccessTokenExpire(), "access");
    }

    public String generateRefreshToken(Long userId, String username) {
        return generateToken(userId, username, jwtProperties.getRefreshTokenExpire(), "refresh");
    }

    private String generateToken(Long userId, String username, Long expireSeconds, String purpose) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("tokenUse", purpose);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expireSeconds * 1000))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
            throw new RuntimeException("Token已过期");
        } catch (JwtException e) {
            log.warn("Token无效: {}", e.getMessage());
            throw new RuntimeException("Token无效");
        }
    }

    public Claims parseAccessToken(String token) {
        return parseForPurpose(token, "access");
    }

    public Claims parseRefreshToken(String token) {
        return parseForPurpose(token, "refresh");
    }

    private Claims parseForPurpose(String token, String purpose) {
        Claims claims = parseToken(token);
        if (!purpose.equals(claims.get("tokenUse", String.class))) {
            throw new IllegalArgumentException("令牌用途无效");
        }
        return claims;
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /** 取 token 剩余有效期秒数（已过期返回 0） */
    public long getRemainingSeconds(String token) {
        try {
            Claims claims = parseToken(token);
            long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            return remainingMs > 0 ? remainingMs / 1000 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
