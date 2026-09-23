package com.starlink.system.security;

import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.starlink.system.entity.Employee;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 安全上下文用户。
 * <p>
 * 同时支持两种身份：
 * <ul>
 *   <li>员工身份：从 employee 表加载，携带角色与权限（后台管理用）。</li>
 *   <li>会员身份：小程序登录后 token 里 userId 为负数（-memberId），不查数据库，
 *       仅用 token claims 里的 username（"m:phone"）构造，authorities 固定为 ROLE_MEMBER。</li>
 * </ul>
 */
@Data
public class SecurityUser implements UserDetails {

    /** 员工身份时非空；会员身份时为 null */
    private Employee employee;

    /** 员工身份时的权限码列表；会员身份时为空 */
    private List<String> permissions;

    /** 员工身份时的角色列表（已带 ROLE_ 前缀）；会员身份时为 [ROLE_MEMBER] */
    private List<String> roles;

    /* ==================== 会员身份字段 ==================== */

    /** 会员 ID（正数）；仅会员身份时非空 */
    private Long memberId;

    /** 会员手机号；仅会员身份时非空 */
    private String memberPhone;

    /** 会员状态：1-正常 2-冻结 3-黑名单 4-已注销；仅会员身份时非空 */
    private Byte memberStatus;

    /* ==================== 构造器 ==================== */

    /** 员工身份构造器 */
    public SecurityUser(Employee employee, List<String> permissions, List<String> roles) {
        this.employee = employee;
        this.permissions = permissions;
        this.roles = roles;
    }

    /** 会员身份构造器：不查数据库，仅用 token claims 构造 */
    public SecurityUser(Long memberId, String memberPhone, Byte memberStatus) {
        this.memberId = memberId;
        this.memberPhone = memberPhone;
        this.memberStatus = memberStatus;
        this.permissions = List.of();
        this.roles = List.of("ROLE_MEMBER");
    }

    /* ==================== UserDetails 实现 ==================== */

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // authorities 由 permissions + roles 共同组成，确保 hasAnyRole/hasAuthority 都能命中
        java.util.List<GrantedAuthority> list = new java.util.ArrayList<>(
                permissions.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList())
        );
        for (String role : roles) {
            list.add(new SimpleGrantedAuthority(role));
        }
        return list;
    }

    @Override
    public String getPassword() {
        return employee != null ? employee.getPasswordHash() : null;
    }

    @Override
    public String getUsername() {
        if (employee != null) return employee.getPhone();
        return memberPhone;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        if (employee != null) {
            return employee.getStatus() == 1;
        }
        // 会员：status=1 正常；其他视为锁定
        return memberStatus != null && memberStatus == 1;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        if (employee != null) {
            return employee.getIsActive() == 1;
        }
        // 会员：status=1 视为启用
        return memberStatus != null && memberStatus == 1;
    }

    /** 当前用户 ID：员工返回 employee.id，会员返回 memberId */
    public Long getUserId() {
        if (employee != null) return employee.getId();
        return memberId;
    }

    /** 是否为会员身份 */
    public boolean isMember() {
        return memberId != null;
    }
}
