package com.starlink.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.system.dto.resp.LogResponse;
import com.starlink.system.entity.AuditLog;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    IPage<LogResponse> selectLogPage(Page<LogResponse> page,
                                     @Param("operatorName") String operatorName,
                                     @Param("bizType") String bizType,
                                     @Param("action") String action,
                                     @Param("startTime") String startTime,
                                     @Param("endTime") String endTime);
}
