package com.starlink.session.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.starlink.session.entity.Reservation;
import com.starlink.session.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 预约超时自动失效定时任务。
 * <p>
 * 每分钟扫描一次已确认（status=1）或待确认（status=0）的预约，
 * 若当前时间已超过预约开始时间 15 分钟且用户未到店（checkedInAt 为空），
 * 则将预约状态自动更新为「超时未到」（status=4）。
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTimeoutScheduler {

    private final ReservationMapper reservationMapper;
    /** 时间来源：生产为系统时钟，测试/调试可注入可推进的仿真时钟 */
    private final Clock clock;

    /** 超时容忍窗口（分钟） */
    private static final int TIMEOUT_MINUTES = 15;

    /**
     * 每分钟执行一次预约超时检查。
     */
    @Scheduled(fixedRate = 60_000)
    public void checkReservationTimeout() {
        LocalDateTime now = LocalDateTime.now(clock);
        // 超时阈值：预约开始时间 + 15 分钟
        LocalDateTime threshold = now.minusMinutes(TIMEOUT_MINUTES);

        // 查询所有已确认或待确认、且预约开始时间已超过阈值的预约
        List<Reservation> expiredReservations = reservationMapper.selectList(
                new LambdaQueryWrapper<Reservation>()
                        .in(Reservation::getStatus, 0, 1)
                        .le(Reservation::getStartTime, threshold)
                        .isNull(Reservation::getCheckedInAt));

        if (expiredReservations.isEmpty()) {
            return;
        }

        for (Reservation reservation : expiredReservations) {
            reservation.setStatus((byte) 4); // 超时未到
            reservation.setCancelReason("用户超过预约时间" + TIMEOUT_MINUTES + "分钟未到店，系统自动取消");
            reservationMapper.updateById(reservation);

            log.warn("预约超时自动失效: reservationId={}, reservationNo={}, startTime={}",
                    reservation.getId(), reservation.getReservationNo(), reservation.getStartTime());
        }

        log.info("预约超时检查完成，本次处理 {} 条超时预约", expiredReservations.size());
    }
}
