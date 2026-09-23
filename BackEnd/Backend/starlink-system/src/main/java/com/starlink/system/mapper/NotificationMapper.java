package com.starlink.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.system.dto.resp.NoticeResponse;
import com.starlink.system.entity.Notification;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    IPage<NoticeResponse> selectNoticePage(Page<NoticeResponse> page,
                                           @Param("title") String title,
                                           @Param("notifyType") Byte notifyType);
}
