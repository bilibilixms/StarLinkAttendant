package com.starlink.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.session.entity.Reservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 预约记录 Mapper。
 *
 */
@Mapper
public interface ReservationMapper extends BaseMapper<Reservation> {

    /**
     * 根据会员 ID 查询会员姓名和手机号（跨模块查询 member 表）。
     */
    @Select("SELECT real_name AS realName, phone FROM member WHERE id = #{memberId} AND deleted_at IS NULL")
    Map<String, String> selectMemberInfo(@Param("memberId") Long memberId);

    /**
     * 批量查询会员信息（返回 id → {realName, phone} 列表）。
     */
    @Select("<script>SELECT id, real_name AS realName, phone FROM member WHERE deleted_at IS NULL AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String, Object>> selectMemberInfoBatch(@Param("ids") List<Long> ids);
}
