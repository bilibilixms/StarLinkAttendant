package com.starlink.member.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.member.dto.req.BlacklistRequest;
import com.starlink.member.dto.req.MemberQueryRequest;
import com.starlink.member.dto.req.MemberRegisterRequest;
import com.starlink.member.dto.req.MemberUpdateRequest;
import com.starlink.member.dto.resp.BlacklistResponse;
import com.starlink.member.dto.resp.MemberResponse;
import com.starlink.member.entity.Member;
import com.starlink.member.entity.MemberLevel;
import com.starlink.member.mapper.MemberLevelMapper;
import com.starlink.member.mapper.MemberMapper;
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

    public PageResult<MemberResponse> listMembers(PageQuery pageQuery, MemberQueryRequest query) {
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
        if (memberMapper.selectByPhone(request.getPhone()) != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "手机号已注册");
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

    @Transactional
    public MemberResponse updateMember(Long id, MemberUpdateRequest request) {
        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
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