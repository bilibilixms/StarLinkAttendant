package com.starlink.member.controller;

import com.starlink.common.result.Result;
import com.starlink.member.dto.req.LevelCreateRequest;
import com.starlink.member.dto.req.LevelUpdateRequest;
import com.starlink.member.dto.resp.LevelResponse;
import com.starlink.member.service.LevelService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/member/levels")
@RequiredArgsConstructor
public class LevelController {

    private final LevelService levelService;

    @GetMapping
    public Result<List<LevelResponse>> listLevels() {
        return Result.ok(levelService.listLevels());
    }

    @PostMapping
    public Result<LevelResponse> createLevel(@Valid @RequestBody LevelCreateRequest request) {
        return Result.ok(levelService.createLevel(request));
    }

    @GetMapping("/{id}")
    public Result<LevelResponse> getLevelById(@PathVariable Long id) {
        return Result.ok(levelService.getLevelById(id));
    }

    @PutMapping("/{id}")
    public Result<LevelResponse> updateLevel(@PathVariable Long id,
                                             @Valid @RequestBody LevelUpdateRequest request) {
        return Result.ok(levelService.updateLevel(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteLevel(@PathVariable Long id) {
        levelService.deleteLevel(id);
        return Result.ok();
    }
}