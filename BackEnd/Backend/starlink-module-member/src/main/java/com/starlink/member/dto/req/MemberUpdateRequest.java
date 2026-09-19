package com.starlink.member.dto.req;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MemberUpdateRequest {

    private String realName;

    private Byte gender;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    private String idCard;

    private LocalDate birthday;

    private Long levelId;

    private String tag;
}