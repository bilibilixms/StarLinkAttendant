package com.starlink.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 会员实时信息只读查询（供「当前登录者信息」接口返回会员余额/积分）。
 * <p>
 * 跨模块读取 {@code member} / {@code member_level}：会员数据归属 starlink-module-member，
 * 而该模块依赖 starlink-system（登录签发 JWT），反向依赖会形成循环，
 * 故按本项目既有做法用注解 SQL 直查（{@code SessionMapper} 同样如此跨模块读取 member）。
 * <p>
 * 只读、无写入：余额变动仍然只能经由会员模块的 {@code BalanceService}。
 */
@Mapper
public interface MemberProfileMapper {

    /**
     * 按会员 ID 查询实时会员信息。
     *
     * @param id 会员 ID
     * @return {id, memberNo, realName, phone, levelId, levelName, balance,
     *         availablePoints, totalPoints, totalRecharge, totalConsumption, status}
     *         会员不存在或已注销时返回 null
     */
    @Select("SELECT m.id AS id, m.member_no AS memberNo, m.real_name AS realName, m.phone AS phone, "
            + "m.level_id AS levelId, ml.level_name AS levelName, m.balance AS balance, "
            + "m.available_points AS availablePoints, m.total_points AS totalPoints, "
            + "m.total_recharge AS totalRecharge, m.total_consumption AS totalConsumption, "
            + "m.status AS status "
            + "FROM member m LEFT JOIN member_level ml ON m.level_id = ml.id "
            + "WHERE m.id = #{id} AND m.deleted_at IS NULL")
    Map<String, Object> selectMemberProfile(@Param("id") Long id);
}
