package com.starlink.member.dto.resp;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员端登录响应（小程序）
 */
@Data
public class MemberLoginResponse {

    private String token;

    private Long id;

    private String memberNo;

    private String realName;

    private String phone;

    private String levelName;

    private Long totalPoints;

    private BigDecimal balance;
}
