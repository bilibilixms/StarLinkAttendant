package com.starlink.session.controller;

import com.starlink.common.result.Result;
import com.starlink.session.entity.Computer;
import com.starlink.session.entity.SeatArea;
import com.starlink.session.service.SeatService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 座位管理 Controller（区域/机位 CRUD）。
 * <p>
 * 提供座位图可视化编辑所需的区域和机位增删改查接口。
 *
 */
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    // ==================== 区域管理 ====================

    /**
     * 创建区域。
     */
    @PostMapping("/areas")
    public Result<SeatArea> createArea(@RequestBody SeatArea area) {
        return Result.ok(seatService.createArea(area));
    }

    /**
     * 更新区域。
     */
    @PutMapping("/areas/{id}")
    public Result<SeatArea> updateArea(@PathVariable Long id, @RequestBody SeatArea area) {
        return Result.ok(seatService.updateArea(id, area));
    }

    /**
     * 删除区域（软删除）。
     */
    @DeleteMapping("/areas/{id}")
    public Result<Void> deleteArea(@PathVariable Long id) {
        seatService.deleteArea(id);
        return Result.ok();
    }

    // ==================== 机位管理 ====================

    /**
     * 创建机位。
     */
    @PostMapping("/computers")
    public Result<Computer> createComputer(@RequestBody Computer computer) {
        return Result.ok(seatService.createComputer(computer));
    }

    /**
     * 更新机位。
     */
    @PutMapping("/computers/{id}")
    public Result<Computer> updateComputer(@PathVariable Long id, @RequestBody Computer computer) {
        return Result.ok(seatService.updateComputer(id, computer));
    }

    /**
     * 删除机位（软删除）。
     */
    @DeleteMapping("/computers/{id}")
    public Result<Void> deleteComputer(@PathVariable Long id) {
        seatService.deleteComputer(id);
        return Result.ok();
    }
}
