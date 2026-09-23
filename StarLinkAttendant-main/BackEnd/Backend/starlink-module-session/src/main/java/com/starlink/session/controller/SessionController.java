package com.starlink.session.controller;

import com.starlink.common.result.Result;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.session.dto.req.*;
import com.starlink.session.dto.resp.SeatStatusResponse;
import com.starlink.session.dto.resp.SessionResponse;
import com.starlink.session.service.SessionService;
import com.starlink.session.service.SeatService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 上机管理 Controller。
 * <p>
 * 提供座位图、机位状态、上下机、换机、强制下机等接口。
 *
 */
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final SeatService seatService;

    // ==================== 座位图 ====================

    /**
     * 获取座位图配置（区域 + 机位列表）。
     */
    @GetMapping("/seat-map")
    public Result<?> getSeatMap() {
        return Result.ok(seatService.getSeatMap());
    }

    /**
     * 获取所有机位实时状态（轮询接口，5 秒间隔）。
     */
    @GetMapping("/seat-status")
    public Result<List<SeatStatusResponse>> getSeatStatus() {
        return Result.ok(seatService.getSeatStatus());
    }

    // ==================== 上机/下机 ====================

    /**
     * 上机（开台）。
     */
    @PostMapping("/start")
    public Result<SessionResponse> startSession(@Valid @RequestBody SessionStartRequest request) {
        return Result.ok(sessionService.startSession(request));
    }

    /**
     * 下机（结账）。
     */
    @PostMapping("/end")
    public Result<SessionResponse> endSession(@Valid @RequestBody SessionEndRequest request) {
        return Result.ok(sessionService.endSession(request));
    }

    /**
     * 暂停上机（临时下机）。
     */
    @PostMapping("/pause")
    public Result<SessionResponse> pauseSession(@Valid @RequestBody SessionPauseRequest request) {
        return Result.ok(sessionService.pauseSession(request));
    }

    /**
     * 恢复上机。
     */
    @PostMapping("/resume")
    public Result<SessionResponse> resumeSession(@Valid @RequestBody SessionResumeRequest request) {
        return Result.ok(sessionService.resumeSession(request));
    }

    /**
     * 换机。
     */
    @PostMapping("/transfer")
    public Result<SessionResponse> transferSession(@Valid @RequestBody SessionTransferRequest request) {
        return Result.ok(sessionService.transferSession(request));
    }

    /**
     * 强制下机（管理员操作）。
     */
    @PostMapping("/force-end")
    public Result<SessionResponse> forceEndSession(@Valid @RequestBody ForceEndRequest request) {
        return Result.ok(sessionService.forceEndSession(request));
    }

    // ==================== 会话查询 ====================

    /**
     * 当前活跃会话列表。
     */
    @GetMapping("/active")
    public Result<List<SessionResponse>> getActiveSessions() {
        return Result.ok(sessionService.getActiveSessions());
    }

    /**
     * 会话详情。
     */
    @GetMapping("/{id}")
    public Result<SessionResponse> getSessionDetail(@PathVariable Long id) {
        return Result.ok(sessionService.getSessionDetail(id));
    }

    /**
     * 会话列表（分页）。
     */
    @GetMapping("/list")
    public Result<PageResult<SessionResponse>> listSessions(
            PageQuery pageQuery,
            @RequestParam(required = false) String memberName,
            @RequestParam(required = false) String computerNo,
            @RequestParam(required = false) Byte status) {
        return Result.ok(sessionService.listSessions(pageQuery, memberName, computerNo, status));
    }

    /**
     * 删除会话记录（仅已结束会话）。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        sessionService.deleteSession(id);
        return Result.ok();
    }
}
