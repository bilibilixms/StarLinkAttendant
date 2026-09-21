package com.starlink.system.security;

import com.starlink.system.config.JwtProperties;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * JWT 签名密钥启动校验（P0-3）。
 * <p>
 * 目的：把「密钥缺失/为空/过短/使用示例值」这类配置错误<b>在应用启动时就暴露</b>，
 * 而不是等到用户登录时才抛 {@code WeakKeyException} 变成 500 ——
 * 后者会把配置问题伪装成业务故障。
 * <p>
 * 校验项：
 * <ol>
 *   <li>必须通过外部配置提供（{@code jwt.secret} ← 环境变量 {@code JWT_SECRET}），不得为空；</li>
 *   <li>长度须满足当前算法要求（HS256 至少 32 字节）；</li>
 *   <li>不得是示例值/占位符/弱值；</li>
 *   <li>用与 {@link JwtTokenUtil} 完全相同的方式构造一次 {@link SecretKey}，
 *       使算法与密钥长度不匹配等问题在启动期即失败。</li>
 * </ol>
 * <b>安全约束</b>：本类任何失败信息与日志<b>都不得包含密钥内容</b>，只报告构成问题
 * （是否配置、长度、是否命中禁用值）。算法沿用项目现有实现（{@code signWith(SecretKey)}
 * 按密钥长度推导：32/48/64 字节 → HS256/HS384/HS512），本类不做算法变更。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtSecretValidator {

    /** HS256 要求的最小密钥字节数（256 bit）。 */
    private static final int MIN_SECRET_BYTES = 32;

    /**
     * 禁止使用的示例值/弱值（小写、去空格后精确匹配）。
     * 覆盖任务要求的 "secret" / "123456" / "jwt-secret" / "change-me" / "starlink" / "dev-secret"
     * 及常见变体。
     */
    private static final Set<String> FORBIDDEN_SECRETS = Set.of(
            "secret", "jwt-secret", "jwtsecret", "jwt_secret", "mysecret",
            "123456", "12345678", "12345678901234567890123456789012",
            "change-me", "changeme", "change_me", "please-change", "pleasechangeme",
            "starlink", "starlink-secret", "starlinksecret",
            "dev-secret", "devsecret", "test-secret", "testsecret", "prod-secret",
            "password", "passw0rd", "admin", "root", "default",
            "your-secret", "your-random-secret", "your-secret-key", "replace-me");

    private final JwtProperties jwtProperties;

    @PostConstruct
    public void validate() {
        String secret = jwtProperties.getSecret();

        // 1) 是否配置
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(failMessage(
                    "JWT 签名密钥未配置（jwt.secret 为空）"));
        }

        // 2) 是否未替换的占位符（例如直接粘贴了文档里的 <your-random-secret>）
        if (secret.contains("<") || secret.contains(">")) {
            throw new IllegalStateException(failMessage(
                    "检测到未替换的占位符（包含 '<' 或 '>'），请填入真实的随机密钥"));
        }

        // 3) 是否弱值/示例值
        String normalized = secret.trim().toLowerCase(Locale.ROOT);
        if (FORBIDDEN_SECRETS.contains(normalized)) {
            throw new IllegalStateException(failMessage(
                    "使用了被禁用的示例值/弱值密钥"));
        }
        if (normalized.chars().distinct().count() == 1) {
            throw new IllegalStateException(failMessage(
                    "密钥为单一字符重复，强度不足"));
        }

        // 4) 长度是否满足算法要求
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(failMessage(
                    "密钥长度不足：当前 " + keyBytes.length + " 字节，HS256 至少需要 "
                            + MIN_SECRET_BYTES + " 字节"));
        }

        // 5) 用与签发/校验完全相同的方式构造密钥，确保启动期即可发现算法/长度不匹配
        SecretKey key;
        try {
            key = Keys.hmacShaKeyFor(keyBytes);
        } catch (RuntimeException e) {
            // 只报告异常类型与说明，不回显密钥内容
            throw new IllegalStateException(failMessage(
                    "密钥无法用于当前签名算法（" + e.getClass().getSimpleName() + "）"), e);
        }

        // 成功日志：只输出算法与长度，绝不输出密钥本身
        log.info("JWT 签名密钥校验通过：算法={}, 密钥长度={} bit",
                resolveAlgorithmName(keyBytes.length), key.getEncoded().length * 8);
    }

    /** 按密钥长度推导 HMAC 算法名（与 jjwt signWith(SecretKey) 的推导规则一致）。 */
    private String resolveAlgorithmName(int keyLength) {
        if (keyLength >= 64) {
            return "HS512";
        }
        if (keyLength >= 48) {
            return "HS384";
        }
        return "HS256";
    }

    /**
     * 构造失败信息。统一追加配置指引，且<b>绝不</b>包含密钥值。
     */
    private String failMessage(String reason) {
        return "JWT 配置校验失败：" + reason
                + "。请通过环境变量 JWT_SECRET 提供长度不少于 " + MIN_SECRET_BYTES
                + " 字节的随机密钥后重新启动（参见 README「启动前必读：JWT_SECRET」）。";
    }
}
