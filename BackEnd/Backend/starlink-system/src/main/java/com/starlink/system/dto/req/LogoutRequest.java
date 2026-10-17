package com.starlink.system.dto.req;

import lombok.Data;

@Data
public class LogoutRequest {
    private String refreshToken;
}
