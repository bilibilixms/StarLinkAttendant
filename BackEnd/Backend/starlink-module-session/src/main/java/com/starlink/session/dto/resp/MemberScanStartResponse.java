package com.starlink.session.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会员端一键上机响应（小程序）。
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberScanStartResponse {

    /** 新开的会话详情 */
    private MemberCurrentSessionResponse session;

    /** 是否复用了已存在的会话（自助开机不允许重复会话，固定 false） */
    private boolean resumed;
}
