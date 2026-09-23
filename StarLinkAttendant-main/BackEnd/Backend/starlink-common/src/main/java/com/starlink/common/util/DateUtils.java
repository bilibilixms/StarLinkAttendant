package com.starlink.common.util;

import cn.hutool.core.date.LocalDateTimeUtil;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * 日期时间工具类。
 * <p>
 * 封装常用的日期操作，统一使用 {@link LocalDateTime}，格式遵循 API 规范
 * {@code yyyy-MM-dd HH:mm:ss}。底层基于 Hutool 的 LocalDateTimeUtil 简化调用。
 *
 */
public final class DateUtils {

    private DateUtils() {
    }

    /** API 规范日期时间格式 */
    public static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 日期格式 */
    public static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 时间格式 */
    public static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // ==================== 格式化 ====================

    /** 格式化日期时间为 API 规范字符串 */
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMATTER) : null;
    }

    /** 格式化日期 */
    public static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : null;
    }

    // ==================== 解析 ====================

    /** 解析日期时间字符串 */
    public static LocalDateTime parseDateTime(String dateTimeStr) {
        return LocalDateTimeUtil.parse(dateTimeStr, DATE_TIME_FORMATTER);
    }

    /** 解析日期字符串 */
    public static LocalDate parseDate(String dateStr) {
        return LocalDate.parse(dateStr, DATE_FORMATTER);
    }

    // ==================== 计算 ====================

    /** 获取今天开始时间（00:00:00） */
    public static LocalDateTime startOfToday() {
        return LocalDate.now().atStartOfDay();
    }

    /** 获取今天结束时间（23:59:59） */
    public static LocalDateTime endOfToday() {
        return LocalDate.now().atTime(23, 59, 59);
    }

    /** 获取指定日期的开始时间 */
    public static LocalDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay();
    }

    /** 获取指定日期的结束时间 */
    public static LocalDateTime endOfDay(LocalDate date) {
        return date.atTime(23, 59, 59);
    }

    /** 获取本周开始（周一） */
    public static LocalDateTime startOfWeek() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
    }

    /** 获取本月开始 */
    public static LocalDateTime startOfMonth() {
        return LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
    }

    /** 计算两个时间之间的分钟数（上机时长等） */
    public static long minutesBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.MINUTES.between(start, end);
    }

    /** 计算两个时间之间的小时数 */
    public static double hoursBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.MINUTES.between(start, end) / 60.0;
    }

    /** 当前时间 */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /** 当前日期 */
    public static LocalDate today() {
        return LocalDate.now();
    }
}
