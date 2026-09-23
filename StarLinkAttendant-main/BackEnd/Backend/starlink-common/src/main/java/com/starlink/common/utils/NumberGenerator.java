package com.starlink.common.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class NumberGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static String generateRecordNo(String prefix) {
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return prefix + timestamp + random;
    }

    public static String generateOrderNo() {
        return generateRecordNo("ORD");
    }

    public static String generatePaymentNo() {
        return generateRecordNo("PAY");
    }

    public static String generateRechargeNo() {
        return generateRecordNo("RCG");
    }

    public static String generateSessionNo() {
        return generateRecordNo("SES");
    }

    public static String generateBillingNo() {
        return generateRecordNo("BIL");
    }

    public static String generatePurchaseNo() {
        return generateRecordNo("PO");
    }

    public static String generateRefundNo() {
        return generateRecordNo("RFD");
    }

    public static String generateShiftNo() {
        return generateRecordNo("SFT");
    }

    public static String generateSettleNo() {
        return "STL" + LocalDateTime.now().format(DATE_ONLY_FORMATTER);
    }

    public static String generateIdempotentKey() {
        return generateRecordNo("IDE");
    }
}
