package com.starlink.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.member.dto.resp.BlacklistResponse;
import com.starlink.member.dto.resp.MemberResponse;
import com.starlink.member.entity.Member;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MemberMapper extends BaseMapper<Member> {

    IPage<MemberResponse> selectMemberPage(Page<MemberResponse> page,
                                           @Param("memberNo") String memberNo,
                                           @Param("realName") String realName,
                                           @Param("phone") String phone,
                                           @Param("status") Byte status,
                                           @Param("levelId") Long levelId);

    Member selectByPhone(@Param("phone") String phone);

    /** 含已注销（软删除）记录，用于注册时复活检测 */
    Member selectByPhoneIncludeDeleted(@Param("phone") String phone);

    /** 复活已注销会员（手写 SQL 绕过 @TableLogic 的自动条件），字段重置见 XML */
    int reviveDeletedMember(Member member);

    Member selectByMemberNo(@Param("memberNo") String memberNo);

    MemberResponse selectMemberDetail(@Param("id") Long id);

    List<BlacklistResponse> selectBlacklist();
}