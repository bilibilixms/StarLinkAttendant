package com.starlink.system.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserCreateRequest {

    @NotBlank(message = "工号不能为空")
    @Size(max = 32, message = "工号长度不能超过32")
    private String employeeNo;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 32, message = "姓名长度不能超过32")
    private String realName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度6-32位")
    private String password;

    private String position;

    private Byte employmentType;

    private Byte status = 1;
}
