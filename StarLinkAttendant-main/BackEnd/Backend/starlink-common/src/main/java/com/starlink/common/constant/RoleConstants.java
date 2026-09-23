package com.starlink.common.constant;

import java.util.Set;

/**
 * 角色与权限主体常量。
 * <p>
 * 角色码取自 {@code role.role_code}（种子数据）：{@code super_admin} / {@code store_manager}
 * / {@code cashier} / {@code net_admin}；会员身份不在 employee/role 体系内，
 * 由 {@code JwtAuthenticationFilter} 依据 token 中的负数 userId 固定赋予 {@code ROLE_MEMBER}。
 * <p>
 * Spring Security 主体名统一为 {@code "ROLE_" + role_code}。由于 {@code @PreAuthorize}
 * 的注解值必须是编译期常量表达式，注解内仍使用字符串字面量；本类用于 Service 层的
 * 资源归属/角色判定，避免魔法字符串散落。
 */
public final class RoleConstants {

    private RoleConstants() {
    }

    /* ==================== 角色码（role.role_code） ==================== */

    public static final String SUPER_ADMIN = "super_admin";
    public static final String STORE_MANAGER = "store_manager";
    public static final String CASHIER = "cashier";
    public static final String NET_ADMIN = "net_admin";

    /* ==================== 会员身份 ==================== */

    /** 会员身份的角色码（不落库，仅由 JWT 过滤器赋予） */
    public static final String MEMBER = "member";

    /** Spring Security 角色主体前缀 */
    public static final String ROLE_PREFIX = "ROLE_";

    /**
     * 会员端身份主体名。
     * 与 {@code JwtAuthenticationFilter} 中对会员 token 赋予的 authority 完全一致。
     */
    public static final String AUTHORITY_MEMBER = ROLE_PREFIX + MEMBER;

    /* ==================== 按业务能力划分的角色集合 ==================== */

    /**
     * 会员数据读取（对应种子权限 {@code member:query}）。
     * 可查看会员列表/详情，但不含变更、注销、拉黑。
     */
    public static final Set<String> MEMBER_READERS = Set.of(
            ROLE_PREFIX + SUPER_ADMIN,
            ROLE_PREFIX + STORE_MANAGER,
            ROLE_PREFIX + CASHIER);

    /**
     * 会员变更（对应种子权限 {@code member:update}）与注销/黑名单（{@code member:freeze}）。
     * 收银员不具备该能力（种子 role_permission 未授予）。
     */
    public static final Set<String> MEMBER_MANAGERS = Set.of(
            ROLE_PREFIX + SUPER_ADMIN,
            ROLE_PREFIX + STORE_MANAGER);

    /**
     * 充值操作（对应种子权限 {@code member:recharge}）与会员账务/积分流水读取。
     */
    public static final Set<String> RECHARGE_OPERATORS = Set.of(
            ROLE_PREFIX + SUPER_ADMIN,
            ROLE_PREFIX + STORE_MANAGER,
            ROLE_PREFIX + CASHIER);

    /** 全部员工角色（不含会员）。 */
    public static final Set<String> ALL_STAFF = Set.of(
            ROLE_PREFIX + SUPER_ADMIN,
            ROLE_PREFIX + STORE_MANAGER,
            ROLE_PREFIX + CASHIER,
            ROLE_PREFIX + NET_ADMIN);
}
