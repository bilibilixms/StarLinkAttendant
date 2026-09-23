package com.starlink.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.session.entity.SessionTiming;
import org.apache.ibatis.annotations.Mapper;

/**
 * 上机计时明细 Mapper。
 *
 */
@Mapper
public interface SessionTimingMapper extends BaseMapper<SessionTiming> {
}
