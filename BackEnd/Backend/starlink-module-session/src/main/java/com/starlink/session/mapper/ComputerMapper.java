package com.starlink.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.session.entity.Computer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 终端设备 Mapper。
 *
 */
@Mapper
public interface ComputerMapper extends BaseMapper<Computer> {
    @Select("SELECT * FROM computer WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    Computer selectByIdForUpdate(@Param("id") Long id);
}
