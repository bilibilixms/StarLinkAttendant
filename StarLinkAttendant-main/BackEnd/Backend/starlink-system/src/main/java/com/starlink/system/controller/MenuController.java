package com.starlink.system.controller;

import com.starlink.common.result.Result;
import com.starlink.system.dto.req.MenuCreateRequest;
import com.starlink.system.dto.req.MenuUpdateRequest;
import com.starlink.system.dto.resp.MenuResponse;
import com.starlink.system.service.MenuService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    public Result<List<MenuResponse>> getMenuTree() {
        return Result.ok(menuService.getMenuTree());
    }

    @PostMapping
    public Result<MenuResponse> createMenu(@Valid @RequestBody MenuCreateRequest request) {
        return Result.ok(menuService.createMenu(request));
    }

    @GetMapping("/{id}")
    public Result<MenuResponse> getMenuById(@PathVariable Long id) {
        return Result.ok(menuService.getMenuById(id));
    }

    @PutMapping("/{id}")
    public Result<MenuResponse> updateMenu(@PathVariable Long id,
                                           @Valid @RequestBody MenuUpdateRequest request) {
        return Result.ok(menuService.updateMenu(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteMenu(@PathVariable Long id) {
        menuService.deleteMenu(id);
        return Result.ok();
    }
}
