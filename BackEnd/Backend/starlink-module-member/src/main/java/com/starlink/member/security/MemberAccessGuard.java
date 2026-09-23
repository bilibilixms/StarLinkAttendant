package com.starlink.member.security;

import com.starlink.common.constant.RoleConstants;
import com.starlink.common.result.ErrorCode;
import com.starlink.system.security.SecurityUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/**
 * 会员域资源访问守卫（P0-4）。
 * <p>
 * 职责划分：
 * <ul>
 *   <li>{@code SecurityConfig} 解决「这个角色能不能访问这个接口」（URL 级角色边界）；</li>
 *   <li>本类解决「这个登录者能不能操作这条数据」（资源归属）。</li>
 * </ul>
 * <b>身份来源</b>：一律从 Spring Security 上下文（{@link SecurityContextHolder}）读取，
 * <b>绝不</b>信任请求体/请求参数里的 memberId、userId —— 见 P0-4 要求。
 * <p>
 * 判定失败统一抛 {@link AccessDeniedException}，由 {@code SecurityConfig} 注册的
 * accessDeniedHandler 转换为 HTTP 403 + 统一响应体。
 * <p>
 * <b>安全约束</b>：本类是会员域资源归属的唯一判定点，任何会员域数据的读写入口
 * （MemberService / RechargeService / PointsService）都必须先经过本类校验；
 * 新增入口时不得绕过。
 */
@Slf4j
@Component
public class MemberAccessGuard {

    /* ==================== 身份解析 ==================== */

    /** 当前登录主体；匿名或非本项目主体时返回 null。 */
    private SecurityUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof SecurityUser su ? su : null;
    }

    /** 当前登录者是否为会员端身份。 */
    public boolean isMemberCaller() {
        SecurityUser user = currentUser();
        return user != null && user.isMember();
    }

    /** 当前登录者是否为员工身份。 */
    public boolean isStaffCaller() {
        SecurityUser user = currentUser();
        return user != null && !user.isMember();
    }

    /** 当前会员身份对应的 memberId；非会员身份返回 null。 */
    public Long currentMemberId() {
        SecurityUser user = currentUser();
        return user != null && user.isMember() ? user.getMemberId() : null;
    }

    /** 当前登录者是否持有给定角色集合中的任一角色。 */
    private boolean hasAnyRole(Set<String> roles) {
        SecurityUser user = currentUser();
        if (user == null) {
            return false;
        }
        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();
        if (authorities == null) {
            return false;
        }
        return authorities.stream().anyMatch(a -> roles.contains(a.getAuthority()));
    }

    private void deny(String action, String detail) {
        log.warn("会员域越权访问被拒绝: action={}, caller={}, detail={}",
                action, describeCaller(), detail);
        throw new AccessDeniedException(ErrorCode.FORBIDDEN.getMessage());
    }

    private String describeCaller() {
        SecurityUser user = currentUser();
        if (user == null) {
            return "anonymous";
        }
        return user.isMember() ? ("member#" + user.getMemberId()) : ("staff#" + user.getUserId());
    }

    /* ==================== 校验入口 ==================== */

    /**
     * 读取会员列表/详情等会员数据：仅具备会员查阅能力的员工。
     */
    public void assertMemberReader() {
        if (!hasAnyRole(RoleConstants.MEMBER_READERS)) {
            deny("member:query", "需要会员查阅权限");
        }
    }

    /**
     * 会员变更（编辑/注销/黑名单）：仅具备会员管理能力的员工。
     */
    public void assertMemberManager() {
        if (!hasAnyRole(RoleConstants.MEMBER_MANAGERS)) {
            deny("member:manage", "需要会员管理权限");
        }
    }

    /**
     * 会员账务/积分流水访问（充值、充值记录、积分记录）：
     * 员工须具备充值操作权限；会员只能访问<b>自己</b>的数据。
     *
     * @param targetMemberId 目标会员 ID（来自请求）
     */
    public void assertMemberLedgerAccess(Long targetMemberId) {
        if (hasAnyRole(RoleConstants.RECHARGE_OPERATORS)) {
            return;
        }
        Long self = currentMemberId();
        if (self != null && targetMemberId != null && self.equals(targetMemberId)) {
            return;
        }
        deny("member:ledger", "targetMemberId=" + targetMemberId + " 非本人且无充值操作权限");
    }

    /**
     * 会员资料变更（PUT /api/member/{id}）：
     * 员工须具备会员管理权限；会员只能修改<b>自己</b>的资料（可改字段另由 Service 限制）。
     *
     * @param targetMemberId 目标会员 ID（来自请求）
     */
    public void assertMemberSelfOrManager(Long targetMemberId) {
        if (hasAnyRole(RoleConstants.MEMBER_MANAGERS)) {
            return;
        }
        Long self = currentMemberId();
        if (self != null && targetMemberId != null && self.equals(targetMemberId)) {
            return;
        }
        deny("member:update", "targetMemberId=" + targetMemberId + " 非本人且无会员管理权限");
    }

    /**
     * 仅会员自助场景下需要限制可改字段时使用（员工不限制）。
     *
     * @return true 表示调用者是会员本人，仅允许修改自助字段
     */
    public boolean mustRestrictToSelfEditableFields() {
        return isMemberCaller();
    }

    /**
     * 若调用者试图修改非自助字段则拒绝（会员自助仅允许 name/gender/birthday 等）。
     *
     * @param field 字段名（仅用于日志）
     */
    public void denyNonSelfEditableField(String field) {
        log.warn("会员自助修改受限字段被拒绝: field={}, caller={}", field, describeCaller());
        throw new AccessDeniedException(ErrorCode.FORBIDDEN.getMessage());
    }
}
