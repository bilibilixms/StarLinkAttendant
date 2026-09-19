package com.starlink.marketing.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 短信通知 Mapper（用于短信通道的 notification 表操作）。
 * <p>
 * 命名为 SmsNotificationMapper 以避免与 starlink-system 模块中的 NotificationMapper Bean 名冲突。
 */
@Mapper
public interface SmsNotificationMapper {

    /** 频率校验：查最近60秒同手机号验证码记录 */
    @Select("SELECT COUNT(*) FROM notification " +
            "WHERE target_ids LIKE CONCAT('%', #{phone}, '%') AND notify_type = 3 " +
            "AND created_at > DATE_SUB(NOW(), INTERVAL 60 SECOND) AND deleted_at IS NULL")
    int countRecentSms(@Param("phone") String phone);

    /** 插入短信通知记录 */
    @Insert("INSERT INTO notification (notify_type, title, content, target_type, target_ids, is_read, published_at, expired_at, created_at, updated_at, version) " +
            "VALUES (3, #{title}, #{content}, 3, #{targetIds}, 0, NOW(), #{expiredAt}, NOW(3), NOW(3), 1)")
    void insertSmsNotification(@Param("title") String title,
                               @Param("content") String content,
                               @Param("targetIds") String targetIds,
                               @Param("expiredAt") String expiredAt);

    /** 查询短信日志（分页用 COUNT） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM notification WHERE notify_type = 3 AND deleted_at IS NULL" +
            "<if test='phone != null and phone != \"\"'> AND target_ids LIKE CONCAT('%', #{phone}, '%')</if>" +
            "</script>")
    long countSmsLogs(@Param("phone") String phone);

    /** 查询短信日志（分页数据） */
    @Select("<script>" +
            "SELECT id, title, content, '' AS templateCode, 1 AS smsType, " +
            "target_ids AS phone, " +
            "CASE WHEN published_at IS NOT NULL THEN 1 ELSE 0 END AS status, " +
            "published_at AS sendTime, '' AS errorMessage, created_at AS createdAt " +
            "FROM notification WHERE notify_type = 3 AND deleted_at IS NULL " +
            "<if test='phone != null and phone != \"\"'> AND target_ids LIKE CONCAT('%', #{phone}, '%')</if>" +
            "ORDER BY created_at DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<Map<String, Object>> selectSmsLogs(@Param("phone") String phone,
                                            @Param("offset") int offset,
                                            @Param("size") int size);
}
