package com.starlink;

import com.starlink.system.config.JwtProperties;
import com.starlink.system.security.JwtSecretValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P0-3 启动校验测试：JWT 密钥不合法时，<b>应用上下文必须启动失败</b>。
 * <p>
 * 使用 {@link ApplicationContextRunner} 只装载「JwtProperties + JwtSecretValidator」两个 bean，
 * 不引入数据源/Web 环境 —— 这样判定「启动失败」时不会与数据库或端口问题混淆，
 * 失败原因可精确归因到密钥校验。
 * <p>
 * 覆盖 TEST-P0-3-02（缺失）、TEST-P0-3-03（空串）、TEST-P0-3-04（过短），
 * 并额外覆盖示例值/占位符场景。
 */
class JwtSecretStartupTest {

    /** 只装配校验所需的两个 bean，避免引入数据源等无关依赖。 */
    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class ValidatorOnlyConfig {
        @Bean
        JwtSecretValidator jwtSecretValidator(JwtProperties properties) {
            return new JwtSecretValidator(properties);
        }
    }

    /** 合法密钥（≥32 字节、非禁用值、非占位符） */
    private static final String VALID_SECRET = "unit-test-valid-jwt-secret-9f3a7c1e5b8d2046af71c3";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ValidatorOnlyConfig.class);

    /**
     * 屏蔽宿主机的 {@code JWT_SECRET} 环境变量后运行。
     * <p>
     * 必要性：操作系统环境变量的优先级<b>高于</b> profile 配置文件，
     * 若开发机已设置 {@code JWT_SECRET}（本项目开发机确实设置了），
     * 则"密钥缺失"场景会被环境变量悄然补上，导致测试假失败。
     * 这里移除 systemEnvironment 属性源，以确定性复现"未提供密钥"。
     */
    private final ApplicationContextRunner runnerWithoutAmbientSecret = new ApplicationContextRunner()
            .withUserConfiguration(ValidatorOnlyConfig.class)
            .withInitializer(context -> ((ConfigurableEnvironment) context.getEnvironment())
                    .getPropertySources()
                    .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME));

    /* ==================== TEST-P0-3-02 ==================== */

    @Test
    @DisplayName("TEST-P0-3-02 JWT 密钥缺失 → 应用无法启动")
    void missingSecretFailsStartup() {
        runnerWithoutAmbientSecret.run(context -> {
            assertThat(context).hasFailed();
            // 根因必须是密钥校验异常，而不是别的启动问题
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class);
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("JWT 配置校验失败");
        });
    }

    @Test
    @DisplayName("TEST-P0-3-02b 密钥为 null（直接校验）→ 抛 IllegalStateException")
    void nullSecretFailsValidation() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(null);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtSecretValidator(properties).validate());
        assertThat(ex.getMessage()).contains("JWT 配置校验失败").contains("未配置");
    }

    /* ==================== TEST-P0-3-03 ==================== */

    @Test
    @DisplayName("TEST-P0-3-03 JWT 密钥为空字符串 → 应用无法启动")
    void blankSecretFailsStartup() {
        runner.withPropertyValues("jwt.secret=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    @DisplayName("TEST-P0-3-03b JWT 密钥为纯空白 → 应用无法启动")
    void whitespaceSecretFailsStartup() {
        runner.withPropertyValues("jwt.secret=    ").run(context -> assertThat(context).hasFailed());
    }

    /* ==================== TEST-P0-3-04 ==================== */

    @Test
    @DisplayName("TEST-P0-3-04 JWT 密钥明显过短 → 应用无法启动")
    void shortSecretFailsStartup() {
        runner.withPropertyValues("jwt.secret=short-secret-16bytes").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("长度不足");
        });
    }

    @Test
    @DisplayName("TEST-P0-3-04b 恰好 31 字节（差 1 字节）→ 应用无法启动")
    void offByOneShortSecretFailsStartup() {
        String s31 = "0123456789012345678901234567890"; // 31 字节
        assertThat(s31.getBytes()).hasSize(31);
        runner.withPropertyValues("jwt.secret=" + s31).run(context -> assertThat(context).hasFailed());
    }

    /* ==================== 额外：禁止示例值/占位符 ==================== */

    @Test
    @DisplayName("示例值/弱值密钥（secret、123456、change-me、starlink、dev-secret）→ 应用无法启动")
    void forbiddenSampleSecretsFailStartup() {
        for (String weak : new String[]{"secret", "123456", "jwt-secret", "change-me",
                "starlink", "dev-secret", "password"}) {
            runner.withPropertyValues("jwt.secret=" + weak)
                    .run(context -> assertThat(context)
                            .as("弱值密钥 %s 不应被接受", weak)
                            .hasFailed());
        }
    }

    @Test
    @DisplayName("未替换的占位符（README 示例直接粘贴）→ 应用无法启动")
    void unreplacedPlaceholderFailsStartup() {
        runner.withPropertyValues("jwt.secret=<your-random-secret>")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("占位符");
                });
    }

    /* ==================== 正向对照：合法密钥必须能启动 ==================== */

    @Test
    @DisplayName("对照：合法密钥 → 应用正常启动（校验本身不会误杀）")
    void validSecretStartsSuccessfully() {
        runner.withPropertyValues("jwt.secret=" + VALID_SECRET)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("对照：48 字节密钥 → 正常启动（HS384 长度同样被接受）")
    void validLongerSecretStartsSuccessfully() {
        runner.withPropertyValues("jwt.secret=012345678901234567890123456789012345678901234567")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
