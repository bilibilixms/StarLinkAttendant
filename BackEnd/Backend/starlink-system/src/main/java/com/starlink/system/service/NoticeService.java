package com.starlink.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.system.dto.req.NoticeCreateRequest;
import com.starlink.system.dto.req.NoticeUpdateRequest;
import com.starlink.system.dto.resp.NoticeResponse;
import com.starlink.system.entity.Notification;
import com.starlink.system.mapper.NotificationMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NotificationMapper notificationMapper;

    public PageResult<NoticeResponse> listNotices(PageQuery pageQuery, String title, Byte notifyType) {
        Page<NoticeResponse> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<NoticeResponse> result = notificationMapper.selectNoticePage(page, title, notifyType);
        return PageResult.of(result);
    }

    @Transactional
    public NoticeResponse createNotice(NoticeCreateRequest request) {
        Notification notification = new Notification();
        notification.setNotifyType(request.getNotifyType());
        notification.setTitle(request.getTitle());
        notification.setContent(request.getContent());
        notification.setTargetType(request.getTargetType());
        notification.setTargetIds(request.getTargetIds());
        notification.setIsRead((byte) 0);
        notification.setPublishedAt(request.getPublishedAt() != null ? request.getPublishedAt() : LocalDateTime.now());
        notification.setExpiredAt(request.getExpiredAt());

        notificationMapper.insert(notification);
        log.info("发布公告成功: {}", notification.getTitle());

        return convertToResponse(notification);
    }

    public NoticeResponse getNoticeById(Long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return convertToResponse(notification);
    }

    @Transactional
    public NoticeResponse updateNotice(Long id, NoticeUpdateRequest request) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        if (request.getTitle() != null) {
            notification.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            notification.setContent(request.getContent());
        }
        if (request.getPublishedAt() != null) {
            notification.setPublishedAt(request.getPublishedAt());
        }
        if (request.getExpiredAt() != null) {
            notification.setExpiredAt(request.getExpiredAt());
        }

        notificationMapper.updateById(notification);
        log.info("更新公告成功: id={}", id);

        return convertToResponse(notification);
    }

    @Transactional
    public void deleteNotice(Long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        notificationMapper.deleteById(id);
        log.info("删除公告成功: id={}", id);
    }

    private NoticeResponse convertToResponse(Notification notification) {
        NoticeResponse response = new NoticeResponse();
        response.setId(notification.getId());
        response.setNotifyType(notification.getNotifyType());
        response.setTitle(notification.getTitle());
        response.setContent(notification.getContent());
        response.setTargetType(notification.getTargetType());
        response.setTargetIds(notification.getTargetIds());
        response.setIsRead(notification.getIsRead());
        response.setPublishedAt(notification.getPublishedAt());
        response.setExpiredAt(notification.getExpiredAt());
        response.setCreatedAt(notification.getCreatedAt());
        response.setUpdatedAt(notification.getUpdatedAt());
        return response;
    }
}
