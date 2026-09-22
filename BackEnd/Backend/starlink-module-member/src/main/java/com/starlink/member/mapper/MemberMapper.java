package com.starlink.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.member.dto.resp.BlacklistResponse;
import com.starlink.member.dto.resp.MemberResponse;
import com.starlink.member.entity.Member;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface MemberMapper extends BaseMapper<Member> {

    IPage<MemberResponse> selectMemberPage(Page<MemberResponse> page,
                                           @Param("memberNo") String memberNo,
                                           @Param("realName") String realName,
                                           @Param("phone") String phone,
                                           @Param("status") Byte status,
                                           @Param("levelId") Long levelId);

    Member selectByPhone(@Param("phone") String phone);

    /**
     * 含已注销（软删除）记录。
     * <p>
     * 用途：注册时检测手机号是否被已注销账号占用 —— 命中即<b>拒绝注册</b>
     * （见 {@code MemberService.registerMember}），不允许凭该手机号恢复账号。
     */
    Member selectByPhoneIncludeDeleted(@Param("phone") String phone);

    /**
     * 恢复已注销会员：仅解除软删除，保留原编号、身份与全部资产。
     * <p>
     * 手写 SQL 绕过 {@code @TableLogic} 的自动 {@code deleted_at IS NULL} 条件。
     * <b>仅供受认证的账户恢复流程调用</b>，匿名注册路径严禁调用。
     *
     * @param id 已注销会员主键
     * @return 影响行数（0 = 不存在或未处于已注销状态）
     */
    int reviveDeletedMember(@Param("id") Long id);

    Member selectByMemberNo(@Param("memberNo") String memberNo);

    MemberResponse selectMemberDetail(@Param("id") Long id);

    List<BlacklistResponse> selectBlacklist();

    /**
     * 按主键查询会员并<b>加行锁</b>（{@code SELECT ... FOR UPDATE}）。
     * <p>
     * 余额变动的唯一正确起点：在事务内先锁定该会员行，再「读 → 算 → 写」余额并写流水，
     * 从而保证：
     * <ul>
     *   <li>流水的 {@code balance_before/balance_after} 精确、无并发穿插；</li>
     *   <li>{@code member.balance == 最新一条流水.balance_after}；</li>
     *   <li>并发的余额变动被串行化（不会出现乐观锁冲突导致的假失败，也不会丢更新）。</li>
     * </ul>
     * 必须与 {@code @Transactional} 同时使用：行锁在事务提交/回滚时释放。
     * 手写 SQL 绕过 {@code @TableLogic}，故显式带上 {@code deleted_at IS NULL}。
     *
     * @param id 会员主键
     * @return 会员实体；不存在或已注销时返回 null
     */
    Member selectByIdForUpdate(@Param("id") Long id);

    /**
     * 累加会员「累计充值金额」（原子自增，只改 total_recharge，不触碰 balance）。
     * <p>
     * 存在原因：{@code member.balance} 由 {@code BalanceService} 以「读-算-写 + 乐观锁」
     * 方式更新并写流水；若累计充值也用整实体 {@code updateById} 写回，就会用<b>过期的余额</b>
     * 覆盖掉刚入账的余额。故累计充值改为列级原子自增，互不干扰。
     * <p>
     * 同时递增 {@code version}，使并发的乐观锁写入能够感知到本次变更。
     *
     * @param id     会员 ID
     * @param amount 累加金额（实付充值额，不含赠送）
     * @return 影响行数（0 = 会员不存在或已注销）
     */
    int incrementTotalRecharge(@Param("id") Long id, @Param("amount") BigDecimal amount);
}