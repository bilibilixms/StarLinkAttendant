package com.starlink.session.controller;

import com.starlink.common.result.Result;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.session.dto.req.ReservationCreateRequest;
import com.starlink.session.dto.resp.ReservationResponse;
import com.starlink.session.service.ReservationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 预约管理 Controller。
 * <p>
 * 提供预约列表、创建预约、取消预约等接口。
 *
 */
@RestController
@RequestMapping("/api/session/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * 预约列表（分页）。
     */
    @GetMapping
    public Result<PageResult<ReservationResponse>> listReservations(
            PageQuery pageQuery,
            @RequestParam(required = false) String memberName,
            @RequestParam(required = false) String computerNo,
            @RequestParam(required = false) Byte status,
            @RequestParam(required = false) LocalDate reservationDate) {
        return Result.ok(reservationService.listReservations(
                pageQuery, memberName, computerNo, status, reservationDate));
    }

    /**
     * 创建预约。
     */
    @PostMapping
    public Result<ReservationResponse> createReservation(@Valid @RequestBody ReservationCreateRequest request) {
        return Result.ok(reservationService.createReservation(request));
    }

    /**
     * 取消预约。
     */
    @DeleteMapping("/{id}")
    public Result<Void> cancelReservation(
            @PathVariable Long id,
            @RequestParam(required = false) String cancelReason) {
        reservationService.cancelReservation(id, cancelReason);
        return Result.ok();
    }
}
