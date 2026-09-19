package com.starlink.member.dto.req;

import lombok.Data;

@Data
public class MemberQueryRequest {

    private String memberNo;

    private String realName;

    private String phone;

    private Byte status;

    private Long levelId;
}