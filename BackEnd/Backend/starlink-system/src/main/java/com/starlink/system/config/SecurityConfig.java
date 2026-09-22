package com.starlink.system.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.starlink.system.security.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // ==================== 授权规则（P0-4：角色边界） ====================
                // 规则自上而下**首个匹配生效**，特例必须写在通配之前。
                // 角色主体名 = "ROLE_" + role.role_code（见 RoleConstants）。
                // 会员身份由 JwtAuthenticationFilter 固定赋予 ROLE_MEMBER：
                // 会员仅可访问下列「会员自助」白名单接口，其余管理域一律 403。
                // 说明：本层只解决「角色能否访问该 URL」；「登录者能否操作该条数据」
                //      （资源归属）由各模块 Service 层的 MemberAccessGuard 负责。
                .authorizeHttpRequests(auth -> auth
                        // ---------- 公开接口 ----------
                        .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/member/login", "/api/member/register").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()

                        // ---------- 会员自助白名单（会员或员工；归属由 Service 校验） ----------
                        .requestMatchers(HttpMethod.POST, "/api/member/recharge").authenticated()
                        // 充值试算（只读预演，不产生写入）：会员需在提交前看到活动赠送与到账金额。
                        // 归属校验同样在 Service 层完成（assertMemberLedgerAccess）。
                        .requestMatchers(HttpMethod.GET, "/api/member/recharge/preview").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/member/*/recharge-records",
                                "/api/member/*/points-records").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/member/*").authenticated()

                        // ---------- 系统管理：仅超级管理员 ----------
                        .requestMatchers("/api/system/**").hasRole("super_admin")

                        // ---------- 报表：管理员 / 店长 ----------
                        .requestMatchers("/api/report/**").hasAnyRole("super_admin", "store_manager")

                        // ---------- 收银：管理员 / 店长 / 收银员 ----------
                        // 退款与日结确认属主管动作（种子未给收银员 refund / settlement 权限），特例写在通配前
                        .requestMatchers(HttpMethod.POST, "/api/cashier/orders/*/refund")
                        .hasAnyRole("super_admin", "store_manager")
                        .requestMatchers(HttpMethod.POST, "/api/cashier/settlement/*/confirm")
                        .hasAnyRole("super_admin", "store_manager")
                        .requestMatchers("/api/cashier/**").hasAnyRole("super_admin", "store_manager", "cashier")

                        // ---------- 会员管理（员工；变更/注销/黑名单另由 Service 限定角色） ----------
                        .requestMatchers("/api/member/levels/**").hasAnyRole("super_admin", "store_manager")
                        .requestMatchers("/api/member/list").hasAnyRole("super_admin", "store_manager", "cashier")
                        .requestMatchers("/api/member/blacklist", "/api/member/blacklist/**")
                        .hasAnyRole("super_admin", "store_manager")
                        .requestMatchers("/api/member/*").hasAnyRole("super_admin", "store_manager", "cashier")
                        .requestMatchers("/api/member/**").hasAnyRole("super_admin", "store_manager", "cashier")

                        // ---------- 其他管理域 ----------
                        .requestMatchers("/api/product/**").hasAnyRole("super_admin", "store_manager")
                        // 模拟第三方集成（实名核验/短信/公安审计日志）：系统管理级能力，仅超级管理员。
                        // 注：其响应未对身份证号做脱敏（见 P0-4 报告 §8 / §15），收紧到 SA 以缩小暴露面。
                        .requestMatchers("/api/marketing/integration/**").hasRole("super_admin")
                        // 券核销是收银台动作，需放行收银员；券模板/活动维护仍为管理动作
                        .requestMatchers(HttpMethod.POST, "/api/marketing/coupons/*/redeem")
                        .hasAnyRole("super_admin", "store_manager", "cashier")
                        .requestMatchers("/api/marketing/**").hasAnyRole("super_admin", "store_manager")
                        // 强制下机在 Controller 注释中即标明为「管理员操作」，不对收银员/网管开放
                        .requestMatchers(HttpMethod.POST, "/api/session/force-end")
                        .hasAnyRole("super_admin", "store_manager")
                        // 区域/机位维护属设备管理，归网管（+管理员/店长）
                        .requestMatchers("/api/session/areas/**", "/api/session/computers/**")
                        .hasAnyRole("super_admin", "store_manager", "net_admin")
                        .requestMatchers("/api/session/**").hasAnyRole("super_admin", "store_manager", "cashier", "net_admin")
                        .requestMatchers("/api/dashboard", "/api/file/**")
                        .hasAnyRole("super_admin", "store_manager", "cashier", "net_admin")

                        // ---------- 兜底：未显式声明的路径仍需登录 ----------
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(401);
                            response.getWriter().write("{\"code\":401,\"message\":\"未认证，请先登录\",\"timestamp\":\""
                                    + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                    + "\"}");
                        })
                        // 已登录但角色/归属不足：统一 403 + 统一响应体
                        // （Service 层抛出的 AccessDeniedException 也会经此处理）
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(403);
                            response.getWriter().write("{\"code\":403,\"message\":\"权限不足\",\"timestamp\":\""
                                    + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                    + "\"}");
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
