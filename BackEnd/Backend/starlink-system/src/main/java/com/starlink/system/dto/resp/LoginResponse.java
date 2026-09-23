package com.starlink.system.dto.resp;

import lombok.Data;

@Data
public class LoginResponse {

    private String accessToken;

    private String refreshToken;

    private Long expiresIn;

    private UserInfo userInfo;

    @Data
    public static class UserInfo {
        private Long id;
        private String employeeNo;
        private String realName;
        private String phone;
        private String position;
    }
}
