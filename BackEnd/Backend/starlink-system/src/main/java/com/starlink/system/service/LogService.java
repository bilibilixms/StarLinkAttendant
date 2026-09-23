package com.starlink.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.system.dto.resp.LogResponse;
import com.starlink.system.entity.AuditLog;
import com.starlink.system.mapper.AuditLogMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogService {

    private final AuditLogMapper auditLogMapper;

    /** 业务类型英文 → 中文映射（与数据库表名对应） */
    private static final Map<String, String> BIZ_TYPE_LABELS = Map.ofEntries(
            // 会员域
            Map.entry("member", "会员"),
            Map.entry("member_level", "会员等级"),
            Map.entry("member_points_log", "积分记录"),
            Map.entry("member_recharge", "充值记录"),
            // 上机域
            Map.entry("seat_area", "座位区域"),
            Map.entry("computer", "机位"),
            Map.entry("session", "会话"),
            Map.entry("session_timing", "会话计时"),
            Map.entry("reservation", "预约"),
            Map.entry("tariff_plan", "资费方案"),
            Map.entry("tariff_rate", "资费费率"),
            Map.entry("billing_record", "计费记录"),
            // 商品/库存域
            Map.entry("product", "商品"),
            Map.entry("product_category", "商品分类"),
            Map.entry("supplier", "供应商"),
            Map.entry("inventory", "库存"),
            Map.entry("inventory_log", "库存变动"),
            Map.entry("purchase_order", "采购单"),
            Map.entry("purchase_order_item", "采购明细"),
            // 订单/收银域
            Map.entry("order", "订单"),
            Map.entry("orders", "订单"),
            Map.entry("order_item", "订单明细"),
            Map.entry("payment_record", "支付记录"),
            Map.entry("refund_record", "退款记录"),
            Map.entry("cashier_shift", "收银班次"),
            Map.entry("daily_settlement", "日结"),
            // 员工域
            Map.entry("employee", "员工"),
            Map.entry("attendance_record", "考勤记录"),
            Map.entry("commission_rule", "提成规则"),
            Map.entry("commission_record", "提成记录"),
            // 营销域
            Map.entry("campaign", "活动"),
            Map.entry("coupon_template", "优惠券模板"),
            Map.entry("coupon", "优惠券"),
            // 系统/权限域
            Map.entry("role", "角色"),
            Map.entry("permission", "权限"),
            Map.entry("role_permission", "角色权限"),
            Map.entry("employee_role", "员工角色"),
            Map.entry("system_config", "系统配置"),
            Map.entry("audit_log", "审计日志"),
            // 别名兼容
            Map.entry("billing", "计费"),
            Map.entry("notice", "通知"),
            Map.entry("tariff", "资费"),
            Map.entry("recharge", "充值"),
            Map.entry("shift", "班次"),
            Map.entry("settlement", "日结"),
            Map.entry("menu", "菜单"),
            Map.entry("user", "用户"),
            Map.entry("seat", "机位"),
            Map.entry("stock_record", "库存记录"),
            Map.entry("points_record", "积分记录")
    );

    public PageResult<LogResponse> listLogs(PageQuery pageQuery, String operatorName, 
                                            String bizType, String action, 
                                            String startTime, String endTime) {
        Page<LogResponse> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<LogResponse> result = auditLogMapper.selectLogPage(page, operatorName, bizType, action, startTime, endTime);

        for (LogResponse item : result.getRecords()) {
            item.setActionLabel(getActionLabel(item.getAction()));
            item.setBizTypeLabel(getBizTypeLabel(item.getBizType()));
        }

       return new PageResult<>(result.getRecords(), result.getTotal(),
               (int) result.getCurrent(), (int) result.getSize());
    }

    public void saveLog(AuditLog auditLog) {
        auditLogMapper.insert(auditLog);
    }

    private String getActionLabel(String action) {
        if (action == null) return "";
        return switch (action) {
            case "create" -> "新增";
            case "update" -> "修改";
            case "delete" -> "删除";
            case "audit" -> "审核";
            case "login" -> "登录";
            default -> action;
        };
    }

    private String getBizTypeLabel(String bizType) {
        if (bizType == null) return "";
        return BIZ_TYPE_LABELS.getOrDefault(bizType, bizType);
    }
}
