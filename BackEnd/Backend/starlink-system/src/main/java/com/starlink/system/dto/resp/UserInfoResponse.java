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

    /**
     * 会员端登录者的实时会员信息；员工登录时为 null。
     * <p>
     * 会员端「我的」页需要能看到<b>最新</b>余额（例如后台刚给该会员充值），
     * 因此复用本接口（当前登录者信息）返回实时数据，而不是新增一个余额接口。
     * 字段为附加项，员工端响应结构与既有字段保持不变。
     */
    private MemberInfo member;

    /** 会员实时信息（余额/积分/等级） */
    @Data
    public static class MemberInfo {

        private Long id;

        private String memberNo;

        private String realName;

        private String phone;

        private Long levelId;

        private String levelName;

        private java.math.BigDecimal balance;

        private Long availablePoints;

        private Long totalPoints;

        private java.math.BigDecimal totalRecharge;

        private java.math.BigDecimal totalConsumption;

        private Byte status;
    }
}
