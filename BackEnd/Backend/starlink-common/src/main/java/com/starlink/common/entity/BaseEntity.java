package com.starlink.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通用实体基类。
 * <p>
 * 所有数据库表实体必须继承此类，对应数据库的四个公共字段：
 * <ul>
 *   <li>{@code created_at} — 创建时间（自动填充）</li>
 *   <li>{@code updated_at} — 更新时间（自动填充）</li>
 *   <li>{@code deleted_at} — 逻辑删除标记（NULL = 未删除，有值 = 已删除时间）</li>
 *   <li>{@code version} — 乐观锁版本号</li>
 * </ul>
 *
 */
@Data
public abstract class BaseEntity implements Serializable {

    /**
     * 主键（bigint unsigned, 自增）。
     * <p>
     * 子类需要声明此字段并添加 {@code @TableId} 注解（如果字段名不是 id，需指定）。
     * 这里使用 {@link IdType#AUTO} 对应 MySQL AUTO_INCREMENT。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建时间 — MyBatis-Plus 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 — MyBatis-Plus 自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除字段。
     * <p>
     * NULL = 未删除，有值 = 删除时间。
     * MyBatis-Plus 自动在查询时追加 {@code WHERE deleted_at IS NULL}，
     * 在删除时设置为当前时间。
     */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
