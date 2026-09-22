package com.starlink.session.controller;

import com.starlink.common.result.Result;
import com.starlink.session.dto.resp.MemberSeatAreaResponse;
import com.starlink.session.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会员端机位查询。
 * <p>
 * 供小程序「预约订座 / 扫码上机」展示座位图使用：
 * <ul>
 *   <li>{@code GET /api/member/seats/areas} — 区域摘要（空闲/总数统计）</li>
 *   <li>{@code GET /api/member/seats/map?areaId=} — 指定区域座位图（含机位明细）</li>
 * </ul>
 * 状态由活跃会话驱动，与 Web 管理端 {@link SeatController#getSeatMap()} 保持一致；
 * 响应隐藏 MAC/IP/费率方案等敏感字段。
 * <p>
 * 安全：GET 公开（与商品浏览一致，未登录也可查看座位情况），见 SecurityConfig。
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/member/seats")
@RequiredArgsConstructor
public class MemberSeatController {

    private final SeatService seatService;

    /**
     * 区域摘要列表：返回启用区域 + 空闲/总数统计。
     */
    @GetMapping("/areas")
    public Result<List<MemberSeatAreaResponse>> areas() {
        return Result.ok(seatService.getMemberSeatAreas());
    }

    /**
     * 座位图：返回指定区域 + 机位明细。
     *
     * @param areaId 区域 ID；为空时取首个启用区域
     */
    @GetMapping("/map")
    public Result<MemberSeatAreaResponse> seatMap(@RequestParam(required = false) Long areaId) {
        return Result.ok(seatService.getMemberSeatMap(areaId));
    }
}
