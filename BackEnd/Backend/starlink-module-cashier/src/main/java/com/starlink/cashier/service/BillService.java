package com.starlink.cashier.service;

import com.starlink.cashier.dto.request.BillQueryRequest;
import com.starlink.cashier.dto.response.BillResponse;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only view over the existing order, session billing and recharge records. */
@Service
@RequiredArgsConstructor
public class BillService {

    private final NamedParameterJdbcTemplate jdbc;

    // Recharge is read from member_recharge, its canonical receipt. The cashier currently
    // creates only product/package orders. Historic recharge-type orders are excluded so
    // the same cash inflow is never presented twice in this ledger.
    private static final String ORDER_SQL = """
            SELECT o.id, 'ORDER' AS source,
                   CASE WHEN o.order_type = 2 THEN 'SESSION' ELSE 'PRODUCT' END AS category,
                   o.order_no AS bill_no,
                   o.member_id, m.real_name AS member_name, o.session_id,
                   c.computer_no, o.total_amount, o.discount_amount,
                   o.payable_amount, o.paid_amount,
                   COALESCE((SELECT SUM(rr.refund_amount) FROM refund_record rr
                             WHERE rr.order_id = o.id AND rr.status = 2 AND rr.deleted_at IS NULL), 0) AS refund_amount,
                   0.00 AS bonus_amount, NULL AS balance_before, NULL AS balance_after,
                   (SELECT p.payment_method FROM payment_record p
                    WHERE p.order_id = o.id AND p.payment_status IN (1, 3) AND p.deleted_at IS NULL
                    ORDER BY p.id DESC LIMIT 1) AS payment_method,
                   o.status, NULL AS billed_minutes, NULL AS billing_start,
                   NULL AS billing_end, o.paid_at, o.created_at, o.remark
            FROM orders o
            LEFT JOIN member m ON m.id = o.member_id AND m.deleted_at IS NULL
            LEFT JOIN session s ON s.id = o.session_id
            LEFT JOIN computer c ON c.id = s.computer_id
            WHERE o.deleted_at IS NULL AND o.order_type IN (1, 2, 4)
            """;

    // A session order, when present, owns its payment/refund history. Show that order
    // instead of a second row for the same session's billing_record.
    private static final String SESSION_SQL = """
            SELECT b.id, 'SESSION' AS source, 'SESSION' AS category, b.record_no AS bill_no,
                   b.member_id, m.real_name AS member_name, b.session_id,
                   c.computer_no, b.total_amount, b.discount_amount,
                   b.final_amount AS payable_amount,
                   COALESCE(s.paid_amount, 0) AS paid_amount,
                   0.00 AS refund_amount, 0.00 AS bonus_amount,
                   NULL AS balance_before, NULL AS balance_after,
                   CASE WHEN COALESCE(s.paid_amount, 0) > 0 THEN 4 ELSE NULL END AS payment_method,
                   CASE WHEN b.is_settled = 1 THEN 1
                        WHEN COALESCE(s.paid_amount, 0) > 0 THEN 5 ELSE 0 END AS status,
                   b.maintenance_minutes AS billed_minutes,
                   b.billing_start, b.billing_end, b.settled_at AS paid_at,
                   b.created_at, s.remark
            FROM billing_record b
            LEFT JOIN session s ON s.id = b.session_id
            LEFT JOIN member m ON m.id = b.member_id AND m.deleted_at IS NULL
            LEFT JOIN computer c ON c.id = b.computer_id
            WHERE b.deleted_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM orders so
                              WHERE so.session_id = b.session_id AND so.order_type = 2
                                AND so.status != 4 AND so.deleted_at IS NULL)
            """;

    // The bonus is wallet credit, not cash received. It is kept separate from payable/paid.
    private static final String RECHARGE_SQL = """
            SELECT r.id, 'RECHARGE' AS source, 'RECHARGE' AS category, r.recharge_no AS bill_no,
                   r.member_id, m.real_name AS member_name, NULL AS session_id,
                   NULL AS computer_no, r.recharge_amount AS total_amount,
                   0.00 AS discount_amount, r.recharge_amount AS payable_amount,
                   CASE WHEN r.status IN (1, 3) THEN r.recharge_amount ELSE 0.00 END AS paid_amount,
                   CASE WHEN r.status = 3 THEN r.recharge_amount ELSE 0.00 END AS refund_amount,
                   r.bonus_amount, r.balance_before, r.balance_after,
                   r.payment_method,
                   CASE r.status WHEN 0 THEN 0 WHEN 1 THEN 1 WHEN 2 THEN 6 WHEN 3 THEN 3 END AS status,
                   NULL AS billed_minutes, NULL AS billing_start, NULL AS billing_end,
                   r.paid_at, r.created_at, NULL AS remark
            FROM member_recharge r
            LEFT JOIN member m ON m.id = r.member_id AND m.deleted_at IS NULL
            WHERE r.deleted_at IS NULL
            """;

    private static final String ALL_SQL = ORDER_SQL + " UNION ALL " + SESSION_SQL + " UNION ALL " + RECHARGE_SQL;

    private static final RowMapper<BillResponse> BILL_MAPPER = (rs, rowNum) -> {
        BillResponse bill = new BillResponse();
        bill.setId(rs.getLong("id"));
        bill.setSource(rs.getString("source"));
        bill.setCategory(rs.getString("category"));
        bill.setBillNo(rs.getString("bill_no"));
        bill.setMemberId(nullableLong(rs, "member_id"));
        bill.setMemberName(rs.getString("member_name"));
        bill.setSessionId(nullableLong(rs, "session_id"));
        bill.setComputerNo(rs.getString("computer_no"));
        bill.setTotalAmount(rs.getBigDecimal("total_amount"));
        bill.setDiscountAmount(rs.getBigDecimal("discount_amount"));
        bill.setPayableAmount(rs.getBigDecimal("payable_amount"));
        BigDecimal paid = rs.getBigDecimal("paid_amount");
        BigDecimal refunded = rs.getBigDecimal("refund_amount");
        bill.setPaidAmount(paid);
        bill.setRefundAmount(refunded);
        bill.setNetAmount(paid.subtract(refunded));
        bill.setBonusAmount(rs.getBigDecimal("bonus_amount"));
        bill.setBalanceBefore(rs.getBigDecimal("balance_before"));
        bill.setBalanceAfter(rs.getBigDecimal("balance_after"));
        bill.setPaymentMethod(nullableInteger(rs, "payment_method"));
        bill.setStatus(rs.getInt("status"));
        bill.setBilledMinutes(nullableInteger(rs, "billed_minutes"));
        bill.setBillingStart(dateTime(rs, "billing_start"));
        bill.setBillingEnd(dateTime(rs, "billing_end"));
        bill.setPaidAt(dateTime(rs, "paid_at"));
        bill.setCreatedAt(dateTime(rs, "created_at"));
        bill.setRemark(rs.getString("remark"));
        return bill;
    };

    public PageResult<BillResponse> list(BillQueryRequest query) {
        if (query.getStartDate() != null && query.getEndDate() != null
                && query.getEndDate().isBefore(query.getStartDate())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结束日期不能早于开始日期");
        }
        if (query.getStatus() != null && (query.getStatus() < 0 || query.getStatus() > 6)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "账单状态无效");
        }

        StringBuilder filter = new StringBuilder();
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (query.getSource() != null) {
            filter.append(" AND bills.source = :source");
            params.addValue("source", query.getSource());
        }
        if (query.getCategory() != null) {
            filter.append(" AND bills.category = :category");
            params.addValue("category", query.getCategory());
        }
        if (query.getBillNo() != null && !query.getBillNo().isBlank()) {
            filter.append(" AND bills.bill_no LIKE :billNo");
            params.addValue("billNo", "%" + query.getBillNo().trim() + "%");
        }
        if (query.getMemberId() != null) {
            filter.append(" AND bills.member_id = :memberId");
            params.addValue("memberId", query.getMemberId());
        }
        if (query.getStatus() != null) {
            filter.append(" AND bills.status = :status");
            params.addValue("status", query.getStatus());
        }
        if (query.getStartDate() != null) {
            filter.append(" AND bills.created_at >= :startAt");
            params.addValue("startAt", query.getStartDate().atStartOfDay());
        }
        if (query.getEndDate() != null) {
            filter.append(" AND bills.created_at < :endBefore");
            params.addValue("endBefore", query.getEndDate().plusDays(1).atStartOfDay());
        }

        String from = " FROM (" + ALL_SQL + ") bills WHERE 1 = 1" + filter;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, params, Long.class);
        params.addValue("limit", query.getSize());
        params.addValue("offset", query.getOffset());
        List<BillResponse> records = jdbc.query("SELECT bills.*" + from
                + " ORDER BY bills.created_at DESC, bills.source, bills.id DESC LIMIT :limit OFFSET :offset",
                params, BILL_MAPPER);
        return new PageResult<>(records, total == null ? 0 : total, query.getPage(), query.getSize());
    }

    public BillResponse detail(String source, Long id) {
        String sourceSql = switch (source) {
            case "ORDER" -> ORDER_SQL;
            case "SESSION" -> SESSION_SQL;
            case "RECHARGE" -> RECHARGE_SQL;
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "账单类型无效");
        };
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "账单 ID 无效");
        }
        List<BillResponse> records = jdbc.query("SELECT bills.* FROM (" + sourceSql
                        + ") bills WHERE bills.id = :id",
                new MapSqlParameterSource("id", id), BILL_MAPPER);
        if (records.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账单不存在");
        }
        return records.get(0);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
