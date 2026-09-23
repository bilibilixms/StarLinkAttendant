package com.starlink.session.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.common.utils.NumberGenerator;
import com.starlink.session.dto.req.ReservationCreateRequest;
import com.starlink.session.dto.resp.ReservationResponse;
import com.starlink.session.entity.Computer;
import com.starlink.session.entity.Reservation;
import com.starlink.session.mapper.ComputerMapper;
import com.starlink.session.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 预约管理服务。
 * <p>
 * 实现预约创建、取消、列表查询，以及预约时段冲突检测。
 * 使用 MyBatis-Plus 内置方法，不依赖 Mapper XML。
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationMapper reservationMapper;
    private final ComputerMapper computerMapper;
    /**
     * 时间来源：与上机/计费模块同一时钟，保证预约状态流转时间口径一致。
     */
    private final Clock clock;

    /**
     * 创建预约（COM-05）。
     * <p>
     * 流程：检查机位可用性 → 检查时段冲突 → 创建预约
     */
    @Transactional
    public ReservationResponse createReservation(ReservationCreateRequest request) {
        // 如果指定了机位，检查可用性
        if (request.getComputerId() != null) {
            Computer computer = computerMapper.selectById(request.getComputerId());
            if (computer == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "机位不存在");
            }

            // 检查时段冲突：查询指定机位在该时段内是否有有效预约
            long conflicts = reservationMapper.selectCount(
                    new LambdaQueryWrapper<Reservation>()
                            .eq(Reservation::getComputerId, request.getComputerId())
                            .in(Reservation::getStatus, 0, 1) // 待确认、已确认
                            .lt(Reservation::getStartTime, request.getEndTime())
                            .gt(Reservation::getEndTime, request.getStartTime()));
            if (conflicts > 0) {
                throw new BusinessException(ErrorCode.RESERVATION_CONFLICT);
            }
        }

        // 创建预约
        Reservation reservation = new Reservation();
        reservation.setReservationNo(NumberGenerator.generateRecordNo("RSV"));
        reservation.setMemberId(request.getMemberId());
        reservation.setComputerId(request.getComputerId());
        reservation.setReservationDate(request.getReservationDate());
        reservation.setStartTime(request.getStartTime());
        reservation.setEndTime(request.getEndTime());
        reservation.setDepositAmount(request.getDepositAmount() != null ? request.getDepositAmount() : BigDecimal.ZERO);
        reservation.setStatus((byte) 0); // 待确认

        reservationMapper.insert(reservation);
        log.info("创建预约: reservationNo={}, memberId={}", reservation.getReservationNo(), request.getMemberId());

        return buildReservationResponse(reservation.getId());
    }

    /**
     * 取消预约。
     */
    @Transactional
    public void cancelReservation(Long id, String cancelReason) {
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        if (reservation.getStatus() == 2) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CHECKED_IN.getCode(), "预约已上机，无法取消");
        }
        if (reservation.getStatus() == 3) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "预约已取消");
        }

        reservation.setStatus((byte) 3); // 已取消
        reservation.setCancelReason(cancelReason);
        reservationMapper.updateById(reservation);
        log.info("取消预约: id={}", id);
    }

    /**
     * 会员到店上机后，将其有效的预约自动置为「已上机」（COM-02 上机联动预约状态）。
     * <p>
     * 匹配规则：同一会员、状态为待确认(0)/已确认(1)、预约尚未结束（endTime >= now），
     * 允许提前上机（即使预约开始时间还未到也匹配）；预约未指定机位（到店分配）
     * 或指定机位与实际上机机位一致；取开始时间最早的一条，避免一天多场预约时误关联。
     * 无匹配预约（散客/无预约直接上机）时静默返回，不影响上机。
     * <p>
     * 本方法加入上机事务（默认 REQUIRED 传播），预约更新失败会随上机一起回滚，
     * 避免出现「已开机但预约仍待确认」的不一致。
     *
     * @param memberId   上机会员 ID（散客为 null，直接忽略）
     * @param computerId 实际开机的机位 ID
     * @param sessionId  本次上机会话 ID
     */
    @Transactional
    public void markCheckedIn(Long memberId, Long computerId, Long sessionId) {
        if (memberId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);

        Reservation reservation = reservationMapper.selectOne(
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getMemberId, memberId)
                        .in(Reservation::getStatus, 0, 1) // 待确认、已确认
                        .ge(Reservation::getEndTime, now) // 预约尚未结束（允许提前上机，即使开始时间还未到）
                        .and(w -> w.isNull(Reservation::getComputerId) // 到店分配
                                .or().eq(Reservation::getComputerId, computerId)) // 或机位一致
                        .orderByAsc(Reservation::getStartTime)
                        .last("LIMIT 1"));
        if (reservation == null) {
            return;
        }

        reservation.setStatus((byte) 2); // 已上机
        reservation.setCheckedInAt(now);
        reservation.setSessionId(sessionId);
        reservationMapper.updateById(reservation);
        log.info("预约已转为已上机: reservationId={}, reservationNo={}, memberId={}, sessionId={}",
                reservation.getId(), reservation.getReservationNo(), memberId, sessionId);
    }

    /**
     * 分页查询预约列表。
     */
    public PageResult<ReservationResponse> listReservations(PageQuery pageQuery,
                                                             String memberName,
                                                             String computerNo,
                                                             Byte status,
                                                             LocalDate reservationDate) {
        // 构建查询条件
        LambdaQueryWrapper<Reservation> wrapper = new LambdaQueryWrapper<Reservation>()
                .orderByDesc(Reservation::getStartTime);

        if (status != null) {
            wrapper.eq(Reservation::getStatus, status);
        }
        if (reservationDate != null) {
            wrapper.eq(Reservation::getReservationDate, reservationDate);
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
            wrapper.in(Reservation::getComputerId, computerIds);
        }

        // 注：memberName 过滤需要跨模块查询，此处暂不支持，后续可集成

        Page<Reservation> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<Reservation> result = reservationMapper.selectPage(page, wrapper);

        // 批量构建响应
        List<Reservation> reservations = result.getRecords();
        List<ReservationResponse> responses = buildReservationResponses(reservations);
        for (ReservationResponse r : responses) {
            r.setStatusLabel(getStatusLabel(r.getStatus()));
        }

        return new PageResult<>(responses, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 获取预约详情。
     */
    public ReservationResponse getReservationById(Long id) {
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }

        ReservationResponse response = buildReservationResponseFromEntity(reservation);
        response.setStatusLabel(getStatusLabel(response.getStatus()));
        return response;
    }

    // ==================== 私有方法 ====================

    /**
     * 批量构建预约响应（含关联数据加载）。
     */
    private List<ReservationResponse> buildReservationResponses(List<Reservation> reservations) {
        if (reservations.isEmpty()) return new ArrayList<>();

        // 批量加载关联的机位信息
        List<Long> computerIds = reservations.stream()
                .map(Reservation::getComputerId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        Map<Long, Computer> computerMap = computerIds.isEmpty()
                ? Map.of()
                : computerMapper.selectBatchIds(computerIds)
                        .stream()
                        .collect(Collectors.toMap(Computer::getId, c -> c));

        // 批量加载关联的会员信息
        List<Long> memberIds = reservations.stream()
                .map(Reservation::getMemberId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        Map<Long, Map<String, String>> memberMap = new java.util.HashMap<>();
        if (!memberIds.isEmpty()) {
            List<Map<String, Object>> memberInfoList = reservationMapper.selectMemberInfoBatch(memberIds);
            for (Map<String, Object> m : memberInfoList) {
                Long id = ((Number) m.get("id")).longValue();
                Map<String, String> info = new java.util.HashMap<>();
                info.put("realName", (String) m.get("realName"));
                info.put("phone", (String) m.get("phone"));
                memberMap.put(id, info);
            }
        }

        List<ReservationResponse> responses = new ArrayList<>();
        for (Reservation r : reservations) {
            ReservationResponse resp = new ReservationResponse();
            BeanUtils.copyProperties(r, resp);

            // 填充机位信息
            if (r.getComputerId() != null) {
                Computer computer = computerMap.get(r.getComputerId());
                if (computer != null) {
                    resp.setComputerNo(computer.getComputerNo());
                }
            }

            // 填充会员信息（跨模块查询 member 表）
            if (r.getMemberId() != null) {
                Map<String, String> memberInfo = memberMap.get(r.getMemberId());
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
     * 从预约 ID 构建完整的 ReservationResponse。
     */
    private ReservationResponse buildReservationResponse(Long reservationId) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        ReservationResponse response = buildReservationResponseFromEntity(reservation);
        response.setStatusLabel(getStatusLabel(response.getStatus()));
        return response;
    }

    /**
     * 从 Reservation 实体构建 ReservationResponse。
     */
    private ReservationResponse buildReservationResponseFromEntity(Reservation reservation) {
        ReservationResponse resp = new ReservationResponse();
        BeanUtils.copyProperties(reservation, resp);

        // 填充机位信息
        if (reservation.getComputerId() != null) {
            Computer computer = computerMapper.selectById(reservation.getComputerId());
            if (computer != null) {
                resp.setComputerNo(computer.getComputerNo());
            }
        }

        // 填充会员信息（跨模块查询 member 表）
        if (reservation.getMemberId() != null) {
            Map<String, String> memberInfo = reservationMapper.selectMemberInfo(reservation.getMemberId());
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
            case 0 -> "待确认";
            case 1 -> "已确认";
            case 2 -> "已上机";
            case 3 -> "已取消";
            case 4 -> "超时未到";
            default -> "未知";
        };
    }
}
