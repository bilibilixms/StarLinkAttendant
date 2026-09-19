package com.starlink.member.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BlacklistResponse {

    private Long id;

    private String memberNo;

    private String realName;

    private String phone;

    private String blacklistReason;

    private LocalDateTime createdAt;
}