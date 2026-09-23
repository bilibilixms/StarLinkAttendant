package com.starlink.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.session.entity.BillingRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计费记录 Mapper 接口。
 *
 */
@Mapper
public interface BillingRecordMapper extends BaseMapper<BillingRecord> {
}
