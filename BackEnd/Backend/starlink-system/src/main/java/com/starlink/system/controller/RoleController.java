package com.starlink.system.controller;

import com.starlink.common.result.Result;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.system.dto.req.RoleCreateRequest;
import com.starlink.system.dto.req.RolePermissionRequest;
import com.starlink.system.dto.req.RoleUpdateRequest;
import com.starlink.system.dto.resp.RoleResponse;
import com.starlink.system.service.RoleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public Result<PageResult<RoleResponse>> listRoles(PageQuery pageQuery) {
        return Result.ok(roleService.listRoles(pageQuery));
    }

    @GetMapping("/all")
    public Result<List<RoleResponse>> getAllRoles() {
        return Result.ok(roleService.getAllRoles());
    }

    @PostMapping
    public Result<RoleResponse> createRole(@Valid @RequestBody RoleCreateRequest request) {
        return Result.ok(roleService.createRole(request));
    }

    @GetMapping("/{id}")
    public Result<RoleResponse> getRoleById(@PathVariable Long id) {
        return Result.ok(roleService.getRoleById(id));
    }

    @PutMapping("/{id}")
    public Result<RoleResponse> updateRole(@PathVariable Long id,
                                           @Valid @RequestBody RoleUpdateRequest request) {
        return Result.ok(roleService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.ok();
    }

    @PutMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @Valid @RequestBody RolePermissionRequest request) {
        roleService.assignPermissions(id, request);
        return Result.ok();
    }
}
