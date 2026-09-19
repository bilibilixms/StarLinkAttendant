package com.starlink.system.controller;

import com.starlink.common.result.Result;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.system.dto.req.UserCreateRequest;
import com.starlink.system.dto.req.UserStatusRequest;
import com.starlink.system.dto.req.UserUpdateRequest;
import com.starlink.system.dto.resp.UserResponse;
import com.starlink.system.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public Result<PageResult<UserResponse>> listUsers(PageQuery pageQuery,
                                                      @RequestParam(required = false) String realName,
                                                      @RequestParam(required = false) String phone,
                                                      @RequestParam(required = false) Byte status) {
        return Result.ok(userService.listUsers(pageQuery, realName, phone, status));
    }

    @PostMapping
    public Result<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(userService.createUser(request));
    }

    @GetMapping("/{id}")
    public Result<UserResponse> getUserById(@PathVariable Long id) {
        return Result.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    public Result<UserResponse> updateUser(@PathVariable Long id,
                                           @Valid @RequestBody UserUpdateRequest request) {
        return Result.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.ok();
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id,
                                         @Valid @RequestBody UserStatusRequest request) {
        userService.updateUserStatus(id, request);
        return Result.ok();
    }

    @PatchMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return Result.ok();
    }
}
