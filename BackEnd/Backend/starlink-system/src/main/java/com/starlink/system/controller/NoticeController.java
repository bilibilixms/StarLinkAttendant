package com.starlink.system.controller;

import com.starlink.common.result.Result;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.system.dto.req.NoticeCreateRequest;
import com.starlink.system.dto.req.NoticeUpdateRequest;
import com.starlink.system.dto.resp.NoticeResponse;
import com.starlink.system.service.NoticeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public Result<PageResult<NoticeResponse>> listNotices(PageQuery pageQuery,
                                                          @RequestParam(required = false) String title,
                                                          @RequestParam(required = false) Byte notifyType) {
        return Result.ok(noticeService.listNotices(pageQuery, title, notifyType));
    }

    @PostMapping
    public Result<NoticeResponse> createNotice(@Valid @RequestBody NoticeCreateRequest request) {
        return Result.ok(noticeService.createNotice(request));
    }

    @GetMapping("/{id}")
    public Result<NoticeResponse> getNoticeById(@PathVariable Long id) {
        return Result.ok(noticeService.getNoticeById(id));
    }

    @PutMapping("/{id}")
    public Result<NoticeResponse> updateNotice(@PathVariable Long id,
                                               @Valid @RequestBody NoticeUpdateRequest request) {
        return Result.ok(noticeService.updateNotice(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteNotice(@PathVariable Long id) {
        noticeService.deleteNotice(id);
        return Result.ok();
    }
}
