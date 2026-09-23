package com.starlink.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置。
 * <p>
 * 包含分页插件、乐观锁插件、公共字段自动填充。
 * <p>
 * 审计日志拦截器 {@link com.starlink.config.interceptor.AuditLogInterceptor}
 * 作为 Spring Bean 自动被 MyBatis-Plus 收集注册，无需手动配置。
 *
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus 插件配置。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件（必须在最前面）
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInterceptor.setMaxLimit(100L); // 限制最大每页条数
        interceptor.addInnerInterceptor(paginationInterceptor);

        // 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }

    /**
     * 公共字段自动填充处理器。
     * <p>
     * 自动为 {@code createdAt} 和 {@code updatedAt} 填充时间。
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {

            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "version", Integer.class, 1);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                // strictUpdateFill 仅在字段为 null 时填充，这里用 setFieldValByName 强制覆盖
                this.setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
            }
        };
    }
}
