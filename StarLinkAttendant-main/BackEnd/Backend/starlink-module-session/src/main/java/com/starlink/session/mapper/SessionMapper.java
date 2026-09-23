package com.starlink.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.session.entity.Session;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 上机会话 Mapper。
 *
 */
@Mapper
public interface SessionMapper extends BaseMapper<Session> {

    /**
     * 根据会员 ID 查询会员姓名和手机号（跨模块查询 member 表）。
     */
    @Select("SELECT real_name AS realName, phone FROM member WHERE id = #{memberId} AND deleted_at IS NULL")
    Map<String, String> selectMemberInfo(@Param("memberId") Long memberId);

    /**
     * 批量查询会员信息。
     */
    @Select("<script>SELECT id, real_name AS realName, phone FROM member WHERE deleted_at IS NULL AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String, Object>> selectMemberInfoBatch(@Param("ids") List<Long> ids);

    /**
     * 查询会员账户余额（跨模块查询 member 表）。
     *
     * @param memberId 会员 ID
     * @return 余额（BigDecimal），会员不存在时返回 null
     */
    @Select("SELECT balance FROM member WHERE id = #{memberId} AND deleted_at IS NULL")
    BigDecimal selectMemberBalance(@Param("memberId") Long memberId);

    /**
     * 查询费率方案下的费率明细（跨模块查询 tariff_rate 表）。
     *
     * @param planId 费率方案 ID
     * @return 费率明细列表，每条包含 rateType / firstMinutes / firstPrice / renewalPrice
     */
    @Select("SELECT rate_type AS rateType, first_minutes AS firstMinutes, " +
            "first_price AS firstPrice, renewal_price AS renewalPrice " +
            "FROM tariff_rate WHERE plan_id = #{planId} AND deleted_at IS NULL")
    List<Map<String, Object>> selectTariffRates(@Param("planId") Long planId);

    /**
     * 扣减会员钱包余额并更新累计消费（跨模块操作 member 表）。
     *
     * @param memberId 会员 ID
     * @param amount   扣减金额
     * @return 影响行数（1=成功，0=余额不足或会员不存在）
     */
    @Update("UPDATE member SET balance = balance - #{amount}, " +
            "total_consumption = total_consumption + #{amount} " +
            "WHERE id = #{memberId} AND deleted_at IS NULL AND balance >= #{amount}")
    int deductMemberBalance(@Param("memberId") Long memberId, @Param("amount") BigDecimal amount);
}
