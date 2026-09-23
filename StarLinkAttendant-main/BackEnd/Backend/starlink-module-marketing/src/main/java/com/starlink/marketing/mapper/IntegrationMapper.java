package com.starlink.marketing.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 公安审计 Mapper — 复用现有表（member / audit_log / system_config / session / notification）。
 * <p>
 * 不新建任何表，完全基于 DataBase.sql 中已有的 40 张表实现所有集成功能。
 */
@Mapper
public interface IntegrationMapper {

    // ==================== system_config：存储集成配置 ====================

    /** 查询所有集成配置（config_key 以 integration. 开头） */
    @Select("SELECT id, config_key, config_value, description, created_at, updated_at " +
            "FROM system_config WHERE config_key LIKE 'integration.%' AND deleted_at IS NULL " +
            "ORDER BY id")
    List<Map<String, Object>> selectIntegrationConfigs();

    /** 按 config_key 查单条配置 */
    @Select("SELECT id, config_key, config_value, description, created_at, updated_at " +
            "FROM system_config WHERE config_key = #{key} AND deleted_at IS NULL")
    Map<String, Object> selectConfigByKey(@Param("key") String key);

    /** 按 id 查单条配置 */
    @Select("SELECT id, config_key, config_value, description, created_at, updated_at " +
            "FROM system_config WHERE id = #{id} AND deleted_at IS NULL")
    Map<String, Object> selectConfigById(@Param("id") Long id);

    /** 新增集成配置 */
    @Insert("INSERT INTO system_config (config_key, config_value, config_type, description, is_encrypted, created_at, updated_at, version) " +
            "VALUES (#{key}, #{value}, 3, #{description}, 0, NOW(3), NOW(3), 1)")
    void insertIntegrationConfig(@Param("key") String key,
                                  @Param("value") String value,
                                  @Param("description") String description);

    /** 更新集成配置值 */
    @Update("UPDATE system_config SET config_value = #{value}, description = #{description}, updated_at = NOW(3) " +
            "WHERE config_key = #{key} AND deleted_at IS NULL")
    int updateIntegrationConfig(@Param("key") String key,
                                 @Param("value") String value,
                                 @Param("description") String description);

    /** 更新集成配置值（按 id） */
    @Update("UPDATE system_config SET config_value = #{value}, description = #{description}, updated_at = NOW(3) " +
            "WHERE id = #{id} AND deleted_at IS NULL")
    int updateIntegrationConfigById(@Param("id") Long id,
                                     @Param("value") String value,
                                     @Param("description") String description);

    /** 更新 config_value（按 id） */
    @Update("UPDATE system_config SET config_value = #{value}, updated_at = NOW(3) " +
            "WHERE id = #{id} AND deleted_at IS NULL")
    int updateConfigValueById(@Param("id") Long id, @Param("value") String value);

    /** 更新 config_value（按 key） */
    @Update("UPDATE system_config SET config_value = #{value}, updated_at = NOW(3) " +
            "WHERE config_key = #{key} AND deleted_at IS NULL")
    int updateConfigValue(@Param("key") String key, @Param("value") String value);

    // ==================== member：实名认证核验 ====================

    /** 按姓名 + 身份证号查找会员 */
    @Select("SELECT id, member_no, real_name, phone, id_card, status " +
            "FROM member WHERE real_name = #{realName} AND id_card = #{idCard} AND deleted_at IS NULL LIMIT 1")
    Map<String, Object> selectMemberByNameAndIdCard(@Param("realName") String realName,
                                                      @Param("idCard") String idCard);

    // ==================== audit_log：通用操作日志（实名认证 / 公安审计 / 统一集成日志） ====================

    /** 写入一条操作日志 */
    @Insert("INSERT INTO audit_log (operator_id, operator_name, biz_type, biz_id, action, detail, created_at, updated_at, version) " +
            "VALUES (NULL, '系统', #{bizType}, #{bizId}, #{action}, #{detail}, NOW(3), NOW(3), 1)")
    void insertAuditLog(@Param("bizType") String bizType,
                        @Param("bizId") Long bizId,
                        @Param("action") String action,
                        @Param("detail") String detail);

    /** 更新 audit_log 的 detail 字段 */
    @Update("UPDATE audit_log SET detail = #{detail}, updated_at = NOW(3) WHERE id = #{id} AND deleted_at IS NULL")
    int updateAuditLogDetail(@Param("id") Long id, @Param("detail") String detail);

    /** 统计指定 biz_type 的日志条数（支持可选 status 过滤，status 存于 detail JSON 中） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM audit_log WHERE biz_type = #{bizType} AND deleted_at IS NULL" +
            "<if test='statusFilter != null'> AND JSON_EXTRACT(detail, '$.status') = #{statusFilter}</if>" +
            "</script>")
    long countAuditLogs(@Param("bizType") String bizType,
                        @Param("statusFilter") Integer statusFilter);

    /** 分页查询指定 biz_type 的日志 */
    @Select("<script>" +
            "SELECT id, biz_type AS bizType, biz_id AS bizId, action, detail, created_at AS createdAt " +
            "FROM audit_log WHERE biz_type = #{bizType} AND deleted_at IS NULL" +
            "<if test='statusFilter != null'> AND JSON_EXTRACT(detail, '$.status') = #{statusFilter}</if>" +
            " ORDER BY created_at DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<Map<String, Object>> selectAuditLogs(@Param("bizType") String bizType,
                                               @Param("statusFilter") Integer statusFilter,
                                               @Param("offset") int offset,
                                               @Param("size") int size);

    /** 统计多 biz_type 的日志条数（统一集成日志） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM audit_log WHERE biz_type IN ('identity_verify', 'sms_send', 'police_audit') AND deleted_at IS NULL" +
            "<if test='bizType != null'> AND biz_type = #{bizType}</if>" +
            "<if test='statusFilter != null'> AND JSON_EXTRACT(detail, '$.status') = #{statusFilter}</if>" +
            "</script>")
    long countUnifiedLogs(@Param("bizType") String bizType,
                          @Param("statusFilter") Integer statusFilter);

    /** 分页查询统一集成日志（多 biz_type） */
    @Select("<script>" +
            "SELECT id, biz_type AS bizType, biz_id AS bizId, action, detail, created_at AS createdAt " +
            "FROM audit_log WHERE biz_type IN ('identity_verify', 'sms_send', 'police_audit') AND deleted_at IS NULL" +
            "<if test='bizType != null'> AND biz_type = #{bizType}</if>" +
            "<if test='statusFilter != null'> AND JSON_EXTRACT(detail, '$.status') = #{statusFilter}</if>" +
            " ORDER BY created_at DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<Map<String, Object>> selectUnifiedLogs(@Param("bizType") String bizType,
                                                  @Param("statusFilter") Integer statusFilter,
                                                  @Param("offset") int offset,
                                                  @Param("size") int size);

    /** 按 ID 查单条 audit_log */
    @Select("SELECT id, biz_type AS bizType, biz_id AS bizId, action, detail, created_at AS createdAt " +
            "FROM audit_log WHERE id = #{id} AND deleted_at IS NULL")
    Map<String, Object> selectAuditLogById(@Param("id") Long id);

    // ==================== session：公安审计数据源 ====================

    /** 查询已下机但尚未生成审计记录的会话 */
    @Select("SELECT s.id AS session_id, s.computer_id, s.member_id, s.start_time AS login_time, " +
            "s.end_time AS logout_time, s.status " +
            "FROM session s " +
            "WHERE s.status IN (2, 3) AND s.deleted_at IS NULL " +
            "AND s.id NOT IN (SELECT biz_id FROM audit_log WHERE biz_type = 'police_audit' AND deleted_at IS NULL) " +
            "ORDER BY s.end_time DESC LIMIT #{limit}")
    List<Map<String, Object>> selectPendingAuditSessions(@Param("limit") int limit);

    /** 统计公安审计未上报条数 */
    @Select("SELECT COUNT(*) FROM audit_log WHERE biz_type = 'police_audit' " +
            "AND JSON_EXTRACT(detail, '$.uploaded') = 0 AND deleted_at IS NULL")
    long countPendingUpload();

    /** 分页查询公安审计日志（支持 uploaded 状态过滤） */
    @Select("<script>" +
            "SELECT id, biz_id AS sessionId, action, detail, created_at AS createdAt " +
            "FROM audit_log WHERE biz_type = 'police_audit' AND deleted_at IS NULL" +
            "<if test='uploadedFilter != null'> AND JSON_EXTRACT(detail, '$.uploaded') = #{uploadedFilter}</if>" +
            " ORDER BY created_at DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<Map<String, Object>> selectPoliceAuditLogs(@Param("uploadedFilter") Integer uploadedFilter,
                                                      @Param("offset") int offset,
                                                      @Param("size") int size);

    /** 统计公安审计日志条数 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM audit_log WHERE biz_type = 'police_audit' AND deleted_at IS NULL" +
            "<if test='uploadedFilter != null'> AND JSON_EXTRACT(detail, '$.uploaded') = #{uploadedFilter}</if>" +
            "</script>")
    long countPoliceAuditLogs(@Param("uploadedFilter") Integer uploadedFilter);

    /** 批量更新公安审计记录的上报状态 */
    @Update("UPDATE audit_log SET detail = JSON_SET(detail, '$.uploaded', 1, '$.uploadTime', NOW()), updated_at = NOW(3) " +
            "WHERE biz_type = 'police_audit' AND JSON_EXTRACT(detail, '$.uploaded') = 0 AND deleted_at IS NULL")
    int batchMarkAuditUploaded();
}
