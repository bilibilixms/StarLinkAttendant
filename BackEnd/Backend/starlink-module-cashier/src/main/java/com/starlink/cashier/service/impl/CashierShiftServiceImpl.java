package com.starlink.cashier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.cashier.dto.request.ShiftEndRequest;
import com.starlink.cashier.dto.request.ShiftQueryRequest;
import com.starlink.cashier.dto.request.ShiftStartRequest;
import com.starlink.cashier.dto.response.ShiftResponse;
import com.starlink.cashier.entity.CashierShift;
import com.starlink.cashier.entity.PaymentRecord;
import com.starlink.cashier.entity.Order;
import com.starlink.cashier.mapper.CashierShiftMapper;
import com.starlink.cashier.mapper.OrderMapper;
import com.starlink.cashier.mapper.PaymentRecordMapper;
import com.starlink.cashier.service.CashierShiftService;
import com.starlink.common.constant.CommonConstants;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageResult;
import com.starlink.common.utils.NumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashierShiftServiceImpl implements CashierShiftService {

    private final CashierShiftMapper shiftMapper;
    private final OrderMapper orderMapper;
    private final PaymentRecordMapper paymentRecordMapper;

    @Override
    @Transactional
    public ShiftResponse startShift(ShiftStartRequest request, Long employeeId) {
        CashierShift active = shiftMapper.selectActiveShift(employeeId);
        if (active != null) {
            throw new BusinessException(ErrorCode.SHIFT_ACTIVE_EXISTS);
        }

        CashierShift shift = new CashierShift();
        shift.setEmployeeId(employeeId);
        shift.setShiftNo(NumberGenerator.generateShiftNo());
        shift.setOpenAt(LocalDateTime.now());
        shift.setOpeningBalance(request.getOpeningBalance());
        shift.setCashIncome(BigDecimal.ZERO);
        shift.setCashExpenditure(BigDecimal.ZERO);
        shift.setTotalIncome(BigDecimal.ZERO);
        shift.setOrderCount(0);
        shift.setStatus((byte) CommonConstants.SHIFT_STATUS_ACTIVE);

        shiftMapper.insert(shift);
        log.info("start shift: id={}, shiftNo={}, employeeId={}", shift.getId(), shift.getShiftNo(), employeeId);

        return convertToResponse(shift);
    }

    @Override
    @Transactional
    public ShiftResponse endShift(ShiftEndRequest request, Long employeeId) {
        CashierShift shift = shiftMapper.selectActiveShift(employeeId);
        if (shift == null) {
            throw new BusinessException(ErrorCode.SHIFT_NOT_FOUND);
        }

        shift.setCloseAt(LocalDateTime.now());

        // Calculate expected cash: opening + income - expenditure
        BigDecimal cashExpected = shift.getOpeningBalance()
                .add(shift.getCashIncome())
                .subtract(shift.getCashExpenditure());
        shift.setCashExpected(cashExpected);
        shift.setCashActual(request.getCashActual());
        shift.setCashDiff(request.getCashActual().subtract(cashExpected));
        shift.setStatus((byte) CommonConstants.SHIFT_STATUS_CLOSED);

        shiftMapper.updateById(shift);
        log.info("end shift: id={}, shiftNo={}, cashDiff={}", shift.getId(), shift.getShiftNo(), shift.getCashDiff());

        return convertToResponse(shift);
    }

    @Override
    public PageResult<ShiftResponse> getShiftPage(ShiftQueryRequest query) {
        Page<ShiftResponse> page = new Page<>(query.getPage(), query.getSize());
        IPage<ShiftResponse> result = shiftMapper.selectShiftPage(page,
                query.getEmployeeId(), query.getStatus());
        result.getRecords().forEach(this::enrichShiftLabels);
        return PageResult.from(result);
    }

    @Override
    public ShiftResponse getShiftById(Long id) {
        CashierShift shift = shiftMapper.selectById(id);
        if (shift == null) {
            throw new BusinessException(ErrorCode.SHIFT_NOT_FOUND);
        }
        return convertToResponse(shift);
    }

    private ShiftResponse convertToResponse(CashierShift s) {
        ShiftResponse r = new ShiftResponse();
        r.setId(s.getId());
        r.setEmployeeId(s.getEmployeeId());
        r.setShiftNo(s.getShiftNo());
        r.setOpenAt(s.getOpenAt());
        r.setCloseAt(s.getCloseAt());
        r.setOpeningBalance(s.getOpeningBalance());
        r.setCashIncome(s.getCashIncome());
        r.setCashExpenditure(s.getCashExpenditure());
        r.setCashExpected(s.getCashExpected());
        r.setCashActual(s.getCashActual());
        r.setCashDiff(s.getCashDiff());
        r.setTotalIncome(s.getTotalIncome());
        r.setOrderCount(s.getOrderCount());
        r.setStatus(s.getStatus() != null ? s.getStatus().intValue() : null);
        r.setStatusLabel(getShiftStatusLabel(s.getStatus()));
        r.setAuditBy(s.getAuditBy());
        r.setCreatedAt(s.getCreatedAt());
        return r;
    }

    private void enrichShiftLabels(ShiftResponse r) {
        if (r.getStatus() != null) {
            r.setStatusLabel(getShiftStatusLabel(r.getStatus().byteValue()));
        }
    }

    private String getShiftStatusLabel(Byte status) {
        if (status == null) return "\u672a\u77e5";
        return switch (status) {
            case 0 -> "\u8fdb\u884c\u4e2d";
            case 1 -> "\u5df2\u7ed3\u73ed";
            case 2 -> "\u5df2\u5ba1\u6838";
            default -> "\u672a\u77e5";
        };
    }
}
