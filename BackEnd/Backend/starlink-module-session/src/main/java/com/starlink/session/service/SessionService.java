package com.starlink.session.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.common.utils.NumberGenerator;
import com.starlink.session.dto.req.*;
import com.starlink.session.dto.resp.SessionResponse;
import com.starlink.session.dto.resp.SessionTimingResponse;
import com.starlink.session.entity.BillingRecord;
import com.starlink.session.entity.Computer;
import com.starlink.session.entity.Session;
import com.starlink.session.entity.SessionTiming;
import com.starlink.session.mapper.BillingRecordMapper;
import com.starlink.session.mapper.ComputerMapper;
import com.starlink.session.mapper.SessionMapper;
import com.starlink.session.mapper.SessionTimingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 上机会话服务（核心状态机）。
 * <p>
 * 实现上机、下机、临时下机/恢复、换机、强制下机等业务场景，
 * 通过状态机保障并发下状态一致性。
 * 使用 MyBatis-Plus 内置方法，不依赖 Mapper XML。
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionMapper sessionMapper;
    private final SessionTimingMapper sessionTimingMapper;
    private final ComputerMapper computerMapper;
    private final BillingRecordMapper billingRecordMapper;
    /**
     * 统一计费器：所有费用计算（下机/强制下机/实时估价）走同一套规则，
     * 避免与 SessionBillingScheduler、会员端页面口径漂移。
     */
    private final TariffCalculator tariffCalculator;
    /**
     * 时间来源：生产为系统时钟，测试/调试可注入可推进的仿真时钟。
     * 上机/计费的所有时间点（startTime/endTime/计时段）都取自它，
     * 因此「时间快进」能真实驱动本模块，而计费规则本身不变。
     */
    private final Clock clock;

    /**
     * 上机（COM-02）。
     * <p>
     * 流程：验证会员状态 → 检查机位空闲 → 创建会话 → 创建首个计时段 → 更新机位状态
     */
    @Transactional
    public SessionResponse startSession(SessionStartRequest request) {
        // 1. 检查机位是否存在
        Computer computer = computerMapper.selectById(request.getComputerId());
        if (computer == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "机位不存在");
        }

        // 2. 基于活跃会话判断机位是否真正被占用（与 getSeatMap/getSeatStatus 逻辑一致）
        Session computerActiveSession = sessionMapper.selectOne(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getComputerId, request.getComputerId())
                        .in(Session::getStatus, 0, 1)
                        .last("LIMIT 1"));
        if (computerActiveSession != null) {
            throw new BusinessException(ErrorCode.SEAT_OCCUPIED);
        }
        // 无活跃会话时，自愈 Computer.status（可能与实际不一致）
        if (computer.getStatus() != 0) {
            computer.setStatus((byte) 0);
            computerMapper.updateById(computer);
        }

        // 3. 检查会员是否已有进行中的会话
        Session activeSession = sessionMapper.selectOne(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getMemberId, request.getMemberId())
                        .in(Session::getStatus, 0, 1)
                        .orderByDesc(Session::getStartTime)
                        .last("LIMIT 1"));
        if (activeSession != null) {
            throw new BusinessException(ErrorCode.SESSION_ACTIVE);
        }

        // 4. 创建会话
        Session session = new Session();
        session.setSessionNo(NumberGenerator.generateSessionNo());
        session.setComputerId(request.getComputerId());
        session.setMemberId(request.getMemberId());
        session.setAuthMethod(request.getAuthMethod());
        session.setTariffPlanId(request.getTariffPlanId() != null ? request.getTariffPlanId() : computer.getTariffPlanId());
        session.setStartTime(LocalDateTime.now(clock));
        session.setExpectedMinutes(request.getExpectedMinutes());
        session.setBilledMinutes(0);
        session.setFreeMinutes(0);
        session.setTotalAmount(BigDecimal.ZERO);
        session.setDiscountAmount(BigDecimal.ZERO);
        session.setPaidAmount(BigDecimal.ZERO);
        session.setStatus((byte) 0); // 上机中
        session.setPauseCount(0);
        session.setPauseDuration(0);

        sessionMapper.insert(session);

        // 5. 创建首个计时段
        SessionTiming timing = new SessionTiming();
        timing.setSessionId(session.getId());
        timing.setTimingType((byte) 1); // 正常计费
        timing.setRateType((byte) 1);   // 按时
        timing.setStartTime(LocalDateTime.now(clock));
        timing.setDurationMinutes(0);
        timing.setAmount(BigDecimal.ZERO);

        sessionTimingMapper.insert(timing);

        // 6. 更新机位状态为使用中
        computer.setStatus((byte) 1);
        computerMapper.updateById(computer);

        log.info("上机成功: sessionNo={}, memberId={}, computerId={}",
                session.getSessionNo(), request.getMemberId(), request.getComputerId());

        return buildSessionResponse(session.getId());
    }

    /**
     * 下机（COM-02）。
     * <p>
     * 流程：结束计时段 → 结算费用 → 释放机位 → 更新会话状态
     */
    @Transactional
    public SessionResponse endSession(SessionEndRequest request) {
        Session session = sessionMapper.selectById(request.getSessionId());
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (session.getStatus() == 2 || session.getStatus() == 3) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_ENDED);
        }

        LocalDateTime now = LocalDateTime.now(clock);

        // 1. 结束当前计时段
        endCurrentTiming(session.getId(), now);

        // 2. 计算总时长和费用（基于累计时长整体计算）
        // 不足 1 分钟按 1 分钟起计，且不足 1 小时按 1 小时计费（规则见 TariffCalculator）
        int totalMinutes = Math.max(calculateTotalMinutes(session.getId()), 1);
        BigDecimal totalAmount = tariffCalculator.calculate(totalMinutes, session.getTariffPlanId());

        // 3. 分配费用到各计时段
        distributeAmountToTimings(session.getId(), totalAmount);

        // 4. 更新会话
        session.setEndTime(now);
        session.setBilledMinutes(totalMinutes);
        session.setTotalAmount(totalAmount);

        // 5. 钱包扣款
        if (session.getMemberId() != null && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            int rows = sessionMapper.deductMemberBalance(session.getMemberId(), totalAmount);
            if (rows > 0) {
                session.setPaidAmount(totalAmount);
                log.info("下机扣款成功: memberId={}, amount={}", session.getMemberId(), totalAmount);
            } else {
                log.warn("下机扣款失败（余额不足）: memberId={}, amount={}", session.getMemberId(), totalAmount);
                String remark = (session.getRemark() != null ? session.getRemark() + "; " : "") + "余额不足，未扣款";
                session.setRemark(remark);
            }
        }
        if (request.getPaidAmount() != null) {
            session.setPaidAmount(request.getPaidAmount());
        }
        session.setStatus((byte) 2); // 已下机
        sessionMapper.updateById(session);

        // 6. 写入计费记录
        createBillingRecord(session, totalMinutes, totalAmount);

        // 7. 释放机位
        Computer computer = computerMapper.selectById(session.getComputerId());
        if (computer != null) {
            computer.setStatus((byte) 0); // 空闲
            computerMapper.updateById(computer);
        }

        log.info("下机成功: sessionId={}, billedMinutes={}, totalAmount={}",
                session.getId(), totalMinutes, totalAmount);

        return buildSessionResponse(session.getId());
    }

    /**
     * 临时下机（COM-03）。
     * <p>
     * 流程：结束计费段 → 创建临时下机段 → 更新会话状态
     */
    @Transactional
    public SessionResponse pauseSession(SessionPauseRequest request) {
        Session session = sessionMapper.selectById(request.getSessionId());
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (session.getStatus() != 0) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_ENDED.getCode(), "会话非上机中状态，无法暂停");
        }

        LocalDateTime now = LocalDateTime.now(clock);

        // 1. 结束当前计费段
        endCurrentTiming(session.getId(), now);

        // 2. 创建临时下机段
        SessionTiming pauseTiming = new SessionTiming();
        pauseTiming.setSessionId(session.getId());
        pauseTiming.setTimingType((byte) 2); // 临时下机
        pauseTiming.setStartTime(now);
        sessionTimingMapper.insert(pauseTiming);

        // 3. 更新会话状态
        session.setStatus((byte) 1); // 临时下机
        session.setPauseCount(session.getPauseCount() + 1);
        sessionMapper.updateById(session);

        log.info("临时下机: sessionId={}, pauseCount={}", session.getId(), session.getPauseCount());

        return buildSessionResponse(session.getId());
    }

    /**
     * 恢复上机（COM-03）。
     * <p>
     * 流程：结束临时下机段 → 恢复计费段
     */
    @Transactional
    public SessionResponse resumeSession(SessionResumeRequest request) {
        Session session = sessionMapper.selectById(request.getSessionId());
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (session.getStatus() != 1) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_ENDED.getCode(), "会话非临时下机状态，无法恢复");
        }

        LocalDateTime now = LocalDateTime.now(clock);

        // 1. 结束临时下机段，计算暂停时长
        List<SessionTiming> timings = sessionTimingMapper.selectList(
                new LambdaQueryWrapper<SessionTiming>()
                        .eq(SessionTiming::getSessionId, session.getId())
                        .orderByAsc(SessionTiming::getStartTime));

        int pauseDuration = 0;
        // 从后往前找最后一个未结束的临时下机段
        for (int i = timings.size() - 1; i >= 0; i--) {
            SessionTiming t = timings.get(i);
            if (t.getTimingType() == 2 && t.getEndTime() == null) {
                t.setEndTime(now);
                pauseDuration = (int) ChronoUnit.MINUTES.between(t.getStartTime(), now);
                t.setDurationMinutes(pauseDuration);
                sessionTimingMapper.updateById(t);
                break;
            }
        }

        // 2. 创建新的计费段
        SessionTiming resumeTiming = new SessionTiming();
        resumeTiming.setSessionId(session.getId());
        resumeTiming.setTimingType((byte) 1); // 正常计费
        resumeTiming.setRateType((byte) 1);   // 按时
        resumeTiming.setStartTime(now);
        sessionTimingMapper.insert(resumeTiming);

        // 3. 更新会话状态
        session.setStatus((byte) 0); // 上机中
        session.setPauseDuration(session.getPauseDuration() + pauseDuration);
        sessionMapper.updateById(session);

        log.info("恢复上机: sessionId={}, pauseDuration={}min", session.getId(), pauseDuration);

        return buildSessionResponse(session.getId());
    }

    /**
     * 换机（COM-04）。
     * <p>
     * 流程：结束当前计费段 → 释放原机位 → 绑定新机位 → 创建新计费段
     */
    @Transactional
    public SessionResponse transferSession(SessionTransferRequest request) {
        Session session = sessionMapper.selectById(request.getSessionId());
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (session.getStatus() != 0 && session.getStatus() != 1) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_ENDED.getCode(), "会话已结束，无法换机");
        }

        // 1. 验证目标机位
        Computer targetComputer = computerMapper.selectById(request.getTargetComputerId());
        if (targetComputer == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标机位不存在");
        }
        // 基于活跃会话判断目标机位是否真正被占用（与 getSeatMap 逻辑一致）
        Session targetActiveSession = sessionMapper.selectOne(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getComputerId, request.getTargetComputerId())
                        .in(Session::getStatus, 0, 1)
                        .last("LIMIT 1"));
        if (targetActiveSession != null) {
            throw new BusinessException(ErrorCode.SEAT_OCCUPIED);
        }
        // 无活跃会话时，自愈 Computer.status
        if (targetComputer.getStatus() != 0) {
            targetComputer.setStatus((byte) 0);
            computerMapper.updateById(targetComputer);
        }

        LocalDateTime now = LocalDateTime.now(clock);

        // 2. 结束当前计费段
        endCurrentTiming(session.getId(), now);

        // 3. 释放原机位
        Computer originalComputer = computerMapper.selectById(session.getComputerId());
        if (originalComputer != null) {
            originalComputer.setStatus((byte) 0);
            computerMapper.updateById(originalComputer);
        }

        // 4. 绑定新机位
        targetComputer.setStatus((byte) 1);
        computerMapper.updateById(targetComputer);

        session.setComputerId(request.getTargetComputerId());
        sessionMapper.updateById(session);

        // 5. 创建新计费段
        SessionTiming newTiming = new SessionTiming();
        newTiming.setSessionId(session.getId());
        newTiming.setTimingType((byte) 1);
        newTiming.setRateType((byte) 1);
        newTiming.setStartTime(now);
        sessionTimingMapper.insert(newTiming);

        log.info("换机成功: sessionId={}, from={}, to={}",
                session.getId(), originalComputer != null ? originalComputer.getComputerNo() : "null",
                targetComputer.getComputerNo());

        return buildSessionResponse(session.getId());
    }

    /**
     * 强制下机（COM-07）。
     */
    @Transactional
    public SessionResponse forceEndSession(ForceEndRequest request) {
        Session session = sessionMapper.selectById(request.getSessionId());
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (session.getStatus() == 2 || session.getStatus() == 3) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_ENDED);
        }

        LocalDateTime now = LocalDateTime.now(clock);

        // 1. 结束当前计时段
        endCurrentTiming(session.getId(), now);

        // 2. 计算费用（基于累计时长整体计算；不足 1 分钟按 1 分钟、不足 1 小时按 1 小时）
        int totalMinutes = Math.max(calculateTotalMinutes(session.getId()), 1);
        BigDecimal totalAmount = tariffCalculator.calculate(totalMinutes, session.getTariffPlanId());

        // 3. 分配费用到各计时段
        distributeAmountToTimings(session.getId(), totalAmount);

        // 4. 更新会话
        session.setEndTime(now);
        session.setBilledMinutes(totalMinutes);
        session.setTotalAmount(totalAmount);

        // 5. 钱包扣款
        if (session.getMemberId() != null && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            int rows = sessionMapper.deductMemberBalance(session.getMemberId(), totalAmount);
            if (rows > 0) {
                session.setPaidAmount(totalAmount);
                log.info("强制下机扣款成功: memberId={}, amount={}", session.getMemberId(), totalAmount);
            } else {
                log.warn("强制下机扣款失败（余额不足）: memberId={}, amount={}", session.getMemberId(), totalAmount);
            }
        }

        String reason = request.getReason() != null ? request.getReason() : "";
        session.setStatus((byte) 3); // 强制下机
        session.setRemark(reason);
        sessionMapper.updateById(session);

        // 6. 写入计费记录
        createBillingRecord(session, totalMinutes, totalAmount);

        // 7. 释放机位
        Computer computer = computerMapper.selectById(session.getComputerId());
        if (computer != null) {
            computer.setStatus((byte) 0);
            computerMapper.updateById(computer);
        }

        log.info("强制下机: sessionId={}, reason={}", session.getId(), request.getReason());

        return buildSessionResponse(session.getId());
    }

    /**
     * 会员端：查询某会员当前活跃会话（上机中/临时下机），无活跃会话时返回 null。
     * <p>
     * 供小程序「当前上机」轮询使用；响应中的时长/费用为实时计算值。
     */
    public SessionResponse getActiveSessionByMember(Long memberId) {
        Session session = sessionMapper.selectOne(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getMemberId, memberId)
                        .in(Session::getStatus, 0, 1)
                        .orderByDesc(Session::getStartTime)
                        .last("LIMIT 1"));
        return session == null ? null : buildSessionResponseFromEntity(session);
    }

    /**
     * 获取活跃会话列表。
     */
    public List<SessionResponse> getActiveSessions() {
        List<Session> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<Session>()
                        .in(Session::getStatus, 0, 1)
                        .orderByDesc(Session::getStartTime));

        // 批量加载关联数据
        List<SessionResponse> responses = buildSessionResponses(sessions);
        for (SessionResponse r : responses) {
            r.setStatusLabel(getStatusLabel(r.getStatus()));
        }
        return responses;
    }

    /**
     * 获取会话详情。
     */
    public SessionResponse getSessionDetail(Long id) {
        Session session = sessionMapper.selectById(id);
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        SessionResponse response = buildSessionResponseFromEntity(session);
        response.setStatusLabel(getStatusLabel(response.getStatus()));

        // 加载计时段
        List<SessionTiming> timings = sessionTimingMapper.selectList(
                new LambdaQueryWrapper<SessionTiming>()
                        .eq(SessionTiming::getSessionId, id)
                        .orderByAsc(SessionTiming::getStartTime));

        List<SessionTimingResponse> timingResponses = new ArrayList<>();
        for (SessionTiming t : timings) {
            SessionTimingResponse tr = new SessionTimingResponse();
            BeanUtils.copyProperties(t, tr);
            tr.setTimingTypeLabel(getTimingTypeLabel(t.getTimingType()));
            timingResponses.add(tr);
        }
        response.setTimings(timingResponses);

        return response;
    }

    /**
     * 删除会话记录（软删除）。
     * <p>
     * 仅允许删除已结束的会话（已下机/强制下机/异常中断），
     * 同时删除关联的计时段记录。
     */
    @Transactional
    public void deleteSession(Long id) {
        Session session = sessionMapper.selectById(id);
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (session.getStatus() == 0 || session.getStatus() == 1) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_ENDED.getCode(), "会话尚未结束，无法删除");
        }

        // 1. 删除关联的计时段记录
        sessionTimingMapper.delete(
                new LambdaQueryWrapper<SessionTiming>()
                        .eq(SessionTiming::getSessionId, id));

        // 2. 软删除会话
        sessionMapper.deleteById(id);

        log.info("删除会话: sessionId={}, sessionNo={}", id, session.getSessionNo());
    }

    /**
     * 分页查询会话列表。
     */
    public PageResult<SessionResponse> listSessions(PageQuery pageQuery,
                                                      String memberName,
                                                      String computerNo,
                                                      Byte status) {
        // 构建查询条件
        LambdaQueryWrapper<Session> wrapper = new LambdaQueryWrapper<Session>()
                .orderByDesc(Session::getCreatedAt);

        if (status != null) {
            wrapper.eq(Session::getStatus, status);
        }

        // 如果传了机位编号过滤，先查出匹配的机位 ID 列表
        if (computerNo != null && !computerNo.isEmpty()) {
            List<Computer> computers = computerMapper.selectList(
                    new LambdaQueryWrapper<Computer>()
                            .like(Computer::getComputerNo, computerNo));
            List<Long> computerIds = computers.stream().map(Computer::getId).toList();
            if (computerIds.isEmpty()) {
                return PageResult.empty(pageQuery.getPage(), pageQuery.getSize());
            }
            wrapper.in(Session::getComputerId, computerIds);
        }

        // 注：memberName 过滤需要跨模块查询，此处暂不支持，后续可集成

        Page<Session> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<Session> result = sessionMapper.selectPage(page, wrapper);

        // 批量构建响应
        List<Session> sessions = result.getRecords();
        List<SessionResponse> responses = buildSessionResponses(sessions);
        for (SessionResponse r : responses) {
            r.setStatusLabel(getStatusLabel(r.getStatus()));
        }

        return new PageResult<>(responses, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    // ==================== 私有方法 ====================

    /**
     * 结束当前未结束的计时段（仅关闭段，不单独计算费用）。
     * <p>
     * 费用统一由会话级累计时长计算，各段金额在后续步骤中分配。
     *
     * @param sessionId 会话 ID
     * @param endTime   段结束时间
     */
    private void endCurrentTiming(Long sessionId, LocalDateTime endTime) {
        List<SessionTiming> timings = sessionTimingMapper.selectList(
                new LambdaQueryWrapper<SessionTiming>()
                        .eq(SessionTiming::getSessionId, sessionId)
                        .isNull(SessionTiming::getEndTime)
                        .orderByDesc(SessionTiming::getStartTime));

        if (!timings.isEmpty()) {
            SessionTiming timing = timings.get(0);
            timing.setEndTime(endTime);
            int minutes = (int) ChronoUnit.MINUTES.between(timing.getStartTime(), endTime);
            timing.setDurationMinutes(Math.max(minutes, 0));
            // 非计费段金额始终为 0；计费段金额在后续统一分配
            if (timing.getTimingType() != null && timing.getTimingType() != 1) {
                timing.setAmount(BigDecimal.ZERO);
            }
            sessionTimingMapper.updateById(timing);
        }
    }

    /**
     * 费用计算统一委托给 {@link TariffCalculator}（不足 1 分钟按 1 分钟、
     * 不足 1 小时按 1 小时），本类不再保留私有计费公式。
     */

    /**
     * 将会话总费用分配到各计费段，并更新段金额。
     * <p>
     * 策略：已结束段按各自时长占总时长的比例分配金额，
     * 当前开放段（未结束）承担剩余金额。
     */
    private void distributeAmountToTimings(Long sessionId, BigDecimal totalAmount) {
        List<SessionTiming> timings = sessionTimingMapper.selectList(
                new LambdaQueryWrapper<SessionTiming>()
                        .eq(SessionTiming::getSessionId, sessionId)
                        .eq(SessionTiming::getTimingType, 1)
                        .orderByAsc(SessionTiming::getStartTime));

        if (timings.isEmpty()) return;

        // 计算总分钟数
        LocalDateTime now = LocalDateTime.now(clock);
        int totalMinutes = 0;
        for (SessionTiming t : timings) {
            int mins = (t.getEndTime() != null && t.getDurationMinutes() != null)
                    ? t.getDurationMinutes()
                    : (int) ChronoUnit.MINUTES.between(t.getStartTime(), now);
            totalMinutes += Math.max(mins, 0);
        }

        if (totalMinutes <= 0) return;

        BigDecimal distributed = BigDecimal.ZERO;
        for (int i = 0; i < timings.size(); i++) {
            SessionTiming t = timings.get(i);
            int mins = (t.getEndTime() != null && t.getDurationMinutes() != null)
                    ? t.getDurationMinutes()
                    : (int) ChronoUnit.MINUTES.between(t.getStartTime(), now);
            mins = Math.max(mins, 0);

            BigDecimal segmentAmount;
            if (i == timings.size() - 1) {
                // 最后一段承担剩余金额
                segmentAmount = totalAmount.subtract(distributed);
            } else {
                // 按比例分配
                segmentAmount = totalAmount.multiply(BigDecimal.valueOf(mins))
                        .divide(BigDecimal.valueOf(totalMinutes), 2, RoundingMode.HALF_UP);
                distributed = distributed.add(segmentAmount);
            }

            if (t.getAmount() == null || t.getAmount().compareTo(segmentAmount) != 0) {
                t.setAmount(segmentAmount);
                sessionTimingMapper.updateById(t);
            }
        }
    }

    /**
     * 计算会话总时长（分钟）。
     */
    private int calculateTotalMinutes(Long sessionId) {
        List<SessionTiming> timings = sessionTimingMapper.selectList(
                new LambdaQueryWrapper<SessionTiming>()
                        .eq(SessionTiming::getSessionId, sessionId)
                        .eq(SessionTiming::getTimingType, 1));

        int total = 0;
        for (SessionTiming t : timings) {
            if (t.getDurationMinutes() != null) {
                total += t.getDurationMinutes();
            }
        }
        return total;
    }

    /**
     * 实时计算活跃会话的已计费时长（分钟）。
     * <p>
     * 对已结束的计时段直接累加 durationMinutes；
     * 对尚未结束（endTime=null）的计费段，按 now - startTime 动态计算。
     */
    private int calculateRealtimeBilledMinutes(Long sessionId) {
        List<SessionTiming> timings = sessionTimingMapper.selectList(
                new LambdaQueryWrapper<SessionTiming>()
                        .eq(SessionTiming::getSessionId, sessionId)
                        .eq(SessionTiming::getTimingType, 1));

        LocalDateTime now = LocalDateTime.now(clock);
        int total = 0;
        for (SessionTiming t : timings) {
            if (t.getEndTime() != null && t.getDurationMinutes() != null) {
                // 已结束的计时段，直接使用存储的时长
                total += t.getDurationMinutes();
            } else if (t.getEndTime() == null) {
                // 仍在进行中的计时段，实时计算
                total += (int) ChronoUnit.MINUTES.between(t.getStartTime(), now);
            }
        }
        return total;
    }

    /**
     * 从 Session 实体构建 SessionResponse（批量，含关联数据加载）。
     */
    private List<SessionResponse> buildSessionResponses(List<Session> sessions) {
        if (sessions.isEmpty()) return new ArrayList<>();

        // 批量加载关联的机位信息
        List<Long> computerIds = sessions.stream()
                .map(Session::getComputerId)
                .distinct()
                .toList();
        Map<Long, Computer> computerMap = computerMapper.selectBatchIds(computerIds)
                .stream()
                .collect(Collectors.toMap(Computer::getId, c -> c));

        // 批量加载关联的会员信息
        List<Long> memberIds = sessions.stream()
                .map(Session::getMemberId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        Map<Long, Map<String, String>> memberMap = new java.util.HashMap<>();
        if (!memberIds.isEmpty()) {
            List<Map<String, Object>> memberInfoList = sessionMapper.selectMemberInfoBatch(memberIds);
            for (Map<String, Object> m : memberInfoList) {
                Long id = ((Number) m.get("id")).longValue();
                Map<String, String> info = new java.util.HashMap<>();
                info.put("realName", (String) m.get("realName"));
                info.put("phone", (String) m.get("phone"));
                memberMap.put(id, info);
            }
        }

        List<SessionResponse> responses = new ArrayList<>();
        for (Session s : sessions) {
            SessionResponse resp = new SessionResponse();
            BeanUtils.copyProperties(s, resp);

            // 填充机位信息
            Computer computer = computerMap.get(s.getComputerId());
            if (computer != null) {
                resp.setComputerNo(computer.getComputerNo());
                resp.setComputerName(computer.getComputerName());
            }

            // 活跃会话实时计算已计费时长和费用（不足 1 小时按 1 小时，计费器内部兜底）
            if (s.getStatus() == 0 || s.getStatus() == 1) {
                int realtimeMinutes = calculateRealtimeBilledMinutes(s.getId());
                resp.setBilledMinutes(realtimeMinutes);
                resp.setTotalAmount(tariffCalculator.calculate(realtimeMinutes, s.getTariffPlanId()));
            }

            // 填充会员信息（跨模块查询 member 表）
            if (s.getMemberId() != null) {
                Map<String, String> memberInfo = memberMap.get(s.getMemberId());
                if (memberInfo != null) {
                    resp.setMemberName(memberInfo.get("realName"));
                    resp.setMemberPhone(memberInfo.get("phone"));
                }
            }

            responses.add(resp);
        }
        return responses;
    }

    /**
     * 从 Session ID 构建完整的 SessionResponse。
     */
    private SessionResponse buildSessionResponse(Long sessionId) {
        Session session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        return buildSessionResponseFromEntity(session);
    }

    /**
     * 从 Session 实体构建完整的 SessionResponse。
     */
    private SessionResponse buildSessionResponseFromEntity(Session session) {
        SessionResponse resp = new SessionResponse();
        BeanUtils.copyProperties(session, resp);

        // 填充机位信息
        Computer computer = computerMapper.selectById(session.getComputerId());
        if (computer != null) {
            resp.setComputerNo(computer.getComputerNo());
            resp.setComputerName(computer.getComputerName());
        }

        // 活跃会话实时计算已计费时长和费用（不足 1 小时按 1 小时，计费器内部兜底）
        if (session.getStatus() == 0 || session.getStatus() == 1) {
            int realtimeMinutes = calculateRealtimeBilledMinutes(session.getId());
            resp.setBilledMinutes(realtimeMinutes);
            resp.setTotalAmount(tariffCalculator.calculate(realtimeMinutes, session.getTariffPlanId()));
        }

        // 填充会员信息（跨模块查询 member 表）
        if (session.getMemberId() != null) {
            Map<String, String> memberInfo = sessionMapper.selectMemberInfo(session.getMemberId());
            if (memberInfo != null) {
                resp.setMemberName(memberInfo.get("realName"));
                resp.setMemberPhone(memberInfo.get("phone"));
            }
        }
        return resp;
    }

    private String getStatusLabel(Byte status) {
        if (status == null) return "";
        return switch (status) {
            case 0 -> "上机中";
            case 1 -> "临时下机";
            case 2 -> "已下机";
            case 3 -> "强制下机";
            case 4 -> "异常中断";
            default -> "未知";
        };
    }

    /**
     * 写入计费记录到 billing_record 表。
     *
     * @param session      会话实体（需包含最新的 totalAmount / discountAmount / endTime 等）
     * @param totalMinutes 计费总时长（分钟）
     * @param totalAmount  总费用
     */
    private void createBillingRecord(Session session, int totalMinutes, BigDecimal totalAmount) {
        BillingRecord record = new BillingRecord();
        record.setSessionId(session.getId());
        record.setMemberId(session.getMemberId());
        record.setComputerId(session.getComputerId());
        record.setRecordNo(NumberGenerator.generateBillingNo());
        record.setTariffPlanId(session.getTariffPlanId());
        record.setMaintenanceMinutes(totalMinutes);

        // 计算均价（元/分钟，保留4位小数）
        if (totalMinutes > 0 && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            record.setUnitPrice(totalAmount.divide(BigDecimal.valueOf(totalMinutes), 4, RoundingMode.HALF_UP));
        } else {
            record.setUnitPrice(BigDecimal.ZERO);
        }

        BigDecimal discountAmount = session.getDiscountAmount() != null ? session.getDiscountAmount() : BigDecimal.ZERO;
        record.setTotalAmount(totalAmount);
        record.setDiscountAmount(discountAmount);
        record.setFinalAmount(totalAmount.subtract(discountAmount));
        record.setBillingStart(session.getStartTime());
        record.setBillingEnd(session.getEndTime());
        record.setIsSettled((byte) 0);

        billingRecordMapper.insert(record);
        log.info("计费记录已写入: sessionId={}, recordNo={}, totalAmount={}, finalAmount={}",
                session.getId(), record.getRecordNo(), totalAmount, record.getFinalAmount());
    }

    private String getTimingTypeLabel(Byte timingType) {
        if (timingType == null) return "";
        return switch (timingType) {
            case 1 -> "正常计费";
            case 2 -> "临时下机";
            case 3 -> "免计费时段";
            default -> "未知";
        };
    }
}
