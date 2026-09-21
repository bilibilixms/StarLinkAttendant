package com.starlink.member.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.member.dto.req.BlacklistRequest;
import com.starlink.member.dto.req.MemberLoginRequest;
import com.starlink.member.dto.req.MemberQueryRequest;
import com.starlink.member.dto.req.MemberRegisterRequest;
import com.starlink.member.dto.req.MemberUpdateRequest;
import com.starlink.member.dto.resp.BlacklistResponse;
import com.starlink.member.dto.resp.MemberLoginResponse;
import com.starlink.member.dto.resp.MemberResponse;
import com.starlink.member.entity.Member;
import com.starlink.member.entity.MemberLevel;
import com.starlink.member.mapper.MemberLevelMapper;
import com.starlink.member.mapper.MemberMapper;
import com.starlink.member.security.MemberAccessGuard;
import com.starlink.system.security.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberMapper memberMapper;
    private final MemberLevelMapper memberLevelMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    /** P0-4：会员域资源归属校验（身份取自 SecurityContext，不信任请求参数） */
    private final MemberAccessGuard accessGuard;

    /**
     * 会员端登录（小程序）：手机号 + 密码。
     * <p>
     * token 的 userId 使用负数（-memberId）+ 用户名加 "m:" 前缀，
     * 避免被员工端 JwtAuthenticationFilter 误解析成员工身份。
     */
    public MemberLoginResponse loginMember(MemberLoginRequest request) {
        Member member = memberMapper.selectByPhone(request.getPhone());
        if (member == null || !passwordEncoder.matches(request.getPassword(), member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (member.getStatus() != null && member.getStatus() == 2) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED.getCode(), "账号已注销");
        }
        if (member.getStatus() != null && member.getStatus() == 3) {
            throw new BusinessException(ErrorCode.MEMBER_BLACKLISTED);
        }

        String token = jwtTokenUtil.generateAccessToken(-member.getId(), "m:" + member.getPhone());

        member.setLastLoginTime(LocalDateTime.now());
        memberMapper.updateById(member);

        MemberLoginResponse resp = new MemberLoginResponse();
        resp.setToken(token);
        resp.setId(member.getId());
        resp.setMemberNo(member.getMemberNo());
        resp.setRealName(member.getRealName());
        resp.setPhone(member.getPhone());
        resp.setTotalPoints(member.getTotalPoints());
        resp.setBalance(member.getBalance());
        MemberLevel level = memberLevelMapper.selectById(member.getLevelId());
        resp.setLevelName(level != null ? level.getLevelName() : "普通会员");
        return resp;
    }

    public PageResult<MemberResponse> listMembers(PageQuery pageQuery, MemberQueryRequest query) {
        // P0-4：会员名册仅对具备会员查阅权限的员工开放（会员身份一律拒绝）
        accessGuard.assertMemberReader();

        Page<MemberResponse> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<MemberResponse> result = memberMapper.selectMemberPage(
                page, query.getMemberNo(), query.getRealName(), 
                query.getPhone(), query.getStatus(), query.getLevelId());

        for (MemberResponse item : result.getRecords()) {
            item.setGenderLabel(getGenderLabel(item.getGender()));
            item.setStatusLabel(getStatusLabel(item.getStatus()));
            item.setRegisterSourceLabel(getRegisterSourceLabel(item.getRegisterSource()));
        }

        return PageResult.of(result);
    }

    /**
     * 按 ID 查询会员详情。
     * <p>
     * <b>P0-4 授权说明</b>：本方法同时被匿名注册流程内部调用（{@code registerMember} 需回读刚创建的会员），
     * 因此不能在此加归属校验（否则匿名注册将失败）。其对外暴露的
     * {@code GET /api/member/{id}} 已由 {@code SecurityConfig} 限定为
     * {@code super_admin / store_manager / cashier} —— 会员身份无法访问该路径，
     * 故不存在「会员读取他人资料」的越权面。
     */
    public MemberResponse getMemberById(Long id) {
        MemberResponse response = memberMapper.selectMemberDetail(id);
        if (response == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        response.setGenderLabel(getGenderLabel(response.getGender()));
        response.setStatusLabel(getStatusLabel(response.getStatus()));
        response.setRegisterSourceLabel(getRegisterSourceLabel(response.getRegisterSource()));
        return response;
    }

    @Transactional
    public MemberResponse registerMember(MemberRegisterRequest request) {
        Member existing = memberMapper.selectByPhoneIncludeDeleted(request.getPhone());
        if (existing != null) {
            if (existing.getDeletedAt() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "手机号已注册");
            }

            // 手机号被已注销（软删除）账号占用。
            //
            // 安全约束：本接口是匿名可访问的（SecurityConfig permitAll），因此
            // **绝不能**凭「请求方提供的手机号」恢复、覆盖或接管既有账号：
            //   - 不得用 request.password / realName / idCard 覆盖原账号身份；
            //   - 不得把 balance / total_points / total_consumption 清零；
            //   - 不得解除 deleted_at。
            // 恢复账号属于受认证的账户恢复业务（见 doc/Front_Back-API.md §6.4 预留的
            // PATCH /api/member/{id}/restore，当前尚未实现），必须先完成身份认证与账号归属校验，
            // 并持有足以证明所有权的凭据（如原密码 / 短信验证码）。
            log.warn("拒绝通过匿名注册接口恢复已注销会员账号: phone={}, deletedMemberId={}",
                    request.getPhone(), existing.getId());
            throw new BusinessException(ErrorCode.MEMBER_PHONE_RECLAIM_FORBIDDEN);
        }

        Member member = new Member();
        member.setMemberNo(generateMemberNo());
        member.setPhone(request.getPhone());
        member.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        member.setRealName(request.getRealName());
        member.setGender(request.getGender());
        member.setIdCard(request.getIdCard());
        member.setBirthday(request.getBirthday());
        member.setLevelId(getDefaultLevelId());
        member.setTotalPoints(0L);
        member.setAvailablePoints(0L);
        member.setTotalRecharge(BigDecimal.ZERO);
        member.setBalance(BigDecimal.ZERO);
        member.setTotalConsumption(BigDecimal.ZERO);
        member.setRegisterSource((byte) 1);
        member.setStatus((byte) 1);

        memberMapper.insert(member);
        log.info("会员注册成功: {}", member.getMemberNo());

        return getMemberById(member.getId());
    }

    /**
     * 恢复已注销会员：仅解除软删除并置为正常状态。
     * <p>
     * <b>安全约束（修改本方法前必读）</b>
     * <ul>
     *   <li><b>当前无调用方</b>。匿名注册接口 {@code POST /api/member/register} 已明确禁止恢复账号，
     *       不得在此重新引入「凭手机号即可恢复」的路径 —— 否则即恢复本次修复的 P0 漏洞。</li>
     *   <li>保留给未来的<b>受认证</b>账户恢复接口（doc/Front_Back-API.md §6.4 预留的
     *       {@code PATCH /api/member/{id}/restore}）。调用方必须先完成身份认证与账号归属校验，
     *       并持有足以证明所有权的凭据。</li>
     *   <li>恢复 ≠ 新建，也 ≠ 清空资产：原 member_no / password_hash / real_name / id_card /
     *       balance / total_points / total_consumption 一律保持不变。</li>
     * </ul>
     * 走手写 SQL（{@code reviveDeletedMember}）：实体 deletedAt 标注 {@code @TableLogic}，
     * MyBatis-Plus 内置 update 会自动追加 {@code deleted_at IS NULL} 条件，导致对已删除行的更新匹配 0 行。
     *
     * @param memberId 已注销会员主键
     * @return 恢复后的会员信息
     */
    @SuppressWarnings("unused")
    private MemberResponse reviveMember(Long memberId) {
        int rows = memberMapper.reviveDeletedMember(memberId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在或未处于已注销状态");
        }
        log.info("恢复已注销会员: id={}", memberId);
        return getMemberById(memberId);
    }

    @Transactional
    public MemberResponse updateMember(Long id, MemberUpdateRequest request) {
        // P0-4 资源归属：员工须有会员管理权限；会员只能改自己（且字段受限，见下）
        accessGuard.assertMemberSelfOrManager(id);

        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 会员自助仅允许修改非敏感个人字段；手机号/身份证/等级/标签属受控字段
        if (accessGuard.mustRestrictToSelfEditableFields()) {
            if (StringUtils.hasText(request.getPhone())) {
                accessGuard.denyNonSelfEditableField("phone");
            }
            if (StringUtils.hasText(request.getIdCard())) {
                accessGuard.denyNonSelfEditableField("idCard");
            }
            if (request.getLevelId() != null) {
                accessGuard.denyNonSelfEditableField("levelId");
            }
            if (request.getTag() != null) {
                accessGuard.denyNonSelfEditableField("tag");
            }
        }

        if (StringUtils.hasText(request.getRealName())) {
            member.setRealName(request.getRealName());
        }
        if (request.getGender() != null) {
            member.setGender(request.getGender());
        }
        if (StringUtils.hasText(request.getPhone())) {
            Member existing = memberMapper.selectByPhone(request.getPhone());
            if (existing != null && !existing.getId().equals(id)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "手机号已被使用");
            }
            member.setPhone(request.getPhone());
        }
        if (StringUtils.hasText(request.getIdCard())) {
            member.setIdCard(request.getIdCard());
        }
        if (request.getBirthday() != null) {
            member.setBirthday(request.getBirthday());
        }
        if (request.getLevelId() != null) {
            member.setLevelId(request.getLevelId());
        }
        if (request.getTag() != null) {
            member.setTag(request.getTag());
        }

        memberMapper.updateById(member);
        log.info("更新会员信息: id={}", id);

        return getMemberById(id);
    }

    @Transactional
    public void deleteMember(Long id) {
        // P0-4：注销属会员管理动作，会员身份与无管理权限的员工一律拒绝
        accessGuard.assertMemberManager();

        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Member> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        wrapper.eq(Member::getId, id)
                .set(Member::getStatus, (byte) 4)
                .set(Member::getDeletedAt, LocalDateTime.now());
        memberMapper.update(null, wrapper);
        log.info("注销会员: id={}", id);
    }

    @Transactional
    public void addToBlacklist(Long id, BlacklistRequest request) {
        // P0-4：拉黑属会员管理动作
        accessGuard.assertMemberManager();

        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        member.setStatus((byte) 3);
        member.setBlacklistReason(request.getBlacklistReason());
        memberMapper.updateById(member);
        log.info("会员加入黑名单: id={}", id);
    }

    @Transactional
    public void removeFromBlacklist(Long id) {
        // P0-4：移出黑名单属会员管理动作
        accessGuard.assertMemberManager();

        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Member> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        wrapper.eq(Member::getId, id)
                .set(Member::getStatus, (byte) 1)
                .set(Member::getBlacklistReason, null);
        memberMapper.update(null, wrapper);
        log.info("会员移出黑名单: id={}", id);
    }

    public List<BlacklistResponse> getBlacklist() {
        return memberMapper.selectBlacklist();
    }

    private String generateMemberNo() {
        return "MBR" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private Long getDefaultLevelId() {
        List<MemberLevel> levels = memberLevelMapper.selectAllOrderByLevel();
        return levels.isEmpty() ? null : levels.get(0).getId();
    }

    private String getGenderLabel(Byte gender) {
        if (gender == null) return "";
        return switch (gender) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "未知";
        };
    }

    private String getStatusLabel(Byte status) {
        if (status == null) return "";
        return switch (status) {
            case 1 -> "正常";
            case 2 -> "冻结";
            case 3 -> "黑名单";
            case 4 -> "已注销";
            default -> "未知";
        };
    }

    private String getRegisterSourceLabel(Byte source) {
        if (source == null) return "";
        return switch (source) {
            case 1 -> "前台注册";
            case 2 -> "后台导入";
            default -> "未知";
        };
    }
}