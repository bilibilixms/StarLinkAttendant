package com.starlink;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.starlink.system.config.JwtProperties;
import com.starlink.system.security.JwtSecretValidator;
import com.starlink.system.security.JwtTokenUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-3 运行期测试：密钥合法时应用可启动、可登录、可签发并校验 token；
 * 且密钥不会出现在日志或配置文件里。
 * <p>
 * 覆盖 TEST-P0-3-01（正常配置全链路）、TEST-P0-3-05（测试密钥生效）、
 * TEST-P0-3-06（日志不泄露密钥）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
class JwtSecretRuntimeTest {

    private static final String STAFF_ADMIN = "13800000001";
    private static final String STAFF_PWD = "123456";
    private static final int MIN_SECRET_BYTES = 32;

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private TestRestTemplate rest;

    private ResponseEntity<String> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange("http://localhost:" + port + path, method,
                new HttpEntity<>(body, headers), String.class);
    }

    /* ==================== TEST-P0-3-01 ==================== */

    @Test
    @DisplayName("TEST-P0-3-01 密钥正常配置：应用启动、登录正常、token 可生成且可验证")
    void validSecretFullRoundTrip() {
        // 1) 应用已启动（能执行到此处即已启动），且密钥合法
        String secret = jwtProperties.getSecret();
        assertNotNull(secret, "密钥不应为 null");
        assertTrue(secret.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_BYTES,
                "密钥长度应满足 HS256 要求");

        // 2) token 可生成
        String token = jwtTokenUtil.generateAccessToken(1L, "13800000001");
        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals(3, token.split("\\.").length, "应为标准三段式 JWT");

        // 3) token 可验证（同一密钥），claims 可还原
        assertEquals(1L, jwtTokenUtil.getUserIdFromToken(token), "token 校验后 userId 应一致");
        assertEquals("13800000001", jwtTokenUtil.getUsernameFromToken(token));
        assertFalse(jwtTokenUtil.isTokenExpired(token), "刚签发的 token 不应过期");

        // 4) 真实登录链路：签发 → 携带 → 通过过滤器 → 访问受保护接口
        ResponseEntity<String> login = call(HttpMethod.POST, "/api/auth/login", null,
                Map.of("username", STAFF_ADMIN, "password", STAFF_PWD));
        assertEquals(200, login.getStatusCode().value(), "登录应成功: " + login.getBody());
        String accessToken = readJson(login.getBody(), "accessToken");
        assertFalse(accessToken.isBlank(), "登录应返回 accessToken");

        ResponseEntity<String> protected_ = call(HttpMethod.GET, "/api/system/users", accessToken, null);
        assertEquals(200, protected_.getStatusCode().value(),
                "携带合法 token 应可访问受保护接口: " + protected_.getBody());

        // 5) 篡改签名后必须被拒绝（证明确实在做签名校验，而不只是解析）
        String tampered = tamperSignature(accessToken);
        assertEquals(401, call(HttpMethod.GET, "/api/system/users", tampered, null).getStatusCode().value(),
                "签名被篡改的 token 必须 401");
    }

    /**
     * 篡改 JWT 签名段。
     * <p>
     * 必须改签名段的<b>第一个</b>字符：base64url 的最后一个字符只携带 2 个有效位，
     * 改动它可能解码出完全相同的签名字节，导致"篡改"其实没生效、测试假失败。
     * 第一个字符的 6 位必然影响签名的第 0 个字节，改动一定使签名失效。
     */
    private String tamperSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char c = token.charAt(signatureStart);
        char replaced = (c == 'A') ? 'B' : 'A';
        return token.substring(0, signatureStart) + replaced + token.substring(signatureStart + 1);
    }

    private String readJson(String body, String field) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(body).path("data").path(field).asText("");
        } catch (Exception e) {
            return "";
        }
    }

    /* ==================== TEST-P0-3-05 ==================== */

    @Test
    @DisplayName("TEST-P0-3-05 测试环境可正常启动，且测试密钥不会成为生产默认值")
    void testEnvironmentRunsAndTestSecretIsNotAProductionDefault() {
        // 1) 测试环境正常启动并持有可用密钥（来源可以是测试 profile，也可以是外部环境变量）
        String effective = jwtProperties.getSecret();
        assertNotNull(effective, "测试环境必须能获得 JWT 密钥");
        assertTrue(effective.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_BYTES,
                "生效密钥长度应满足 HS256 要求");
        assertDoesNotThrow(() -> new JwtSecretValidator(jwtProperties).validate(),
                "测试环境生效的密钥应能通过启动校验");

        // 2) 测试专用密钥只存在于测试类路径
        String testConfig = readClasspath("application-test.yml");
        assertTrue(testConfig.contains("starlink-attendant-test-only-secret"),
                "测试 profile 应提供测试专用密钥");

        // 3) 该测试密钥绝不能出现在生产配置中（否则即成为生产默认值）
        String mainConfig = readClasspath("application.yml");
        assertFalse(mainConfig.contains("starlink-attendant-test-only-secret"),
                "测试密钥不得出现在主配置 application.yml 中");

        // 4) 生产主配置必须由环境变量注入，且不得存在字面量密钥
        assertTrue(mainConfig.contains("${JWT_SECRET:}"),
                "application.yml 的 jwt.secret 应由环境变量 JWT_SECRET 注入");
        assertFalse(
                Pattern.compile("(?m)^\\s*secret:\\s+(?!\\$\\{)[A-Za-z0-9+/=_-]{16,}\\s*$")
                        .matcher(mainConfig).find(),
                "application.yml 中不得存在字面量密钥");
    }

    private String readClasspath(String name) {
        try (var in = new ClassPathResource(name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取 " + name + " 失败", e);
        }
    }

    /* ==================== TEST-P0-3-06 ==================== */

    @Test
    @DisplayName("TEST-P0-3-06 密钥不得出现在日志（成功路径与失败路径均检查）")
    void secretNeverAppearsInLogs() {
        // 成功路径：合法密钥只输出算法与长度，不回显密钥
        String distinctive = "distinctive-log-probe-secret-7c2f9a4e1b6d8035";
        assertTrue(capturedLogs(distinctive, false).stream()
                        .noneMatch(m -> m.contains(distinctive)),
                "成功路径的日志泄露了密钥");

        // 失败路径 1：命中禁用值（"change-me" 先于长度检查被拒绝）
        assertTrue(capturedLogs("change-me", true).stream()
                        .noneMatch(m -> m.contains("change-me")),
                "禁用值失败路径的日志泄露了密钥");
        Exception ex = assertThrows(RuntimeException.class,
                () -> new JwtSecretValidator(propsOf("change-me")).validate());
        assertFalse(String.valueOf(ex.getMessage()).contains("change-me"),
                "失败信息回显了被拒绝的密钥值");

        // 失败路径 2：长度不足（密钥足够"独特"，若被回显必被发现）
        String shortButDistinct = "distinctive-short-secret-abc";
        assertTrue(capturedLogs(shortButDistinct, true).stream()
                        .noneMatch(m -> m.contains(shortButDistinct)),
                "长度不足失败路径的日志泄露了密钥");
        Exception ex2 = assertThrows(RuntimeException.class,
                () -> new JwtSecretValidator(propsOf(shortButDistinct)).validate());
        assertFalse(String.valueOf(ex2.getMessage()).contains(shortButDistinct),
                "失败信息回显了长度不足的密钥值");
    }

    private JwtProperties propsOf(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        return properties;
    }

    /**
     * 用 ListAppender 捕获 {@link JwtSecretValidator} 的日志并返回消息列表。
     *
     * @param secretValue 用于校验的密钥值
     * @param expectFail  是否预期校验失败
     */
    private java.util.List<String> capturedLogs(String secretValue, boolean expectFail) {
        Logger logger = (Logger) LoggerFactory.getLogger(JwtSecretValidator.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            JwtProperties props = new JwtProperties();
            props.setSecret(secretValue);
            if (expectFail) {
                assertThrows(RuntimeException.class, () -> new JwtSecretValidator(props).validate());
            } else {
                new JwtSecretValidator(props).validate();
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
