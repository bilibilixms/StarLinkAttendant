package com.starlink.system.dto.resp;

import lombok.Data;

import java.util.List;

@Data
public class UserInfoResponse {

    private Long id;

    private String employeeNo;

    private String realName;

    private String phone;

    private String position;

    private List<String> roles;

    private List<String> permissions;
}
