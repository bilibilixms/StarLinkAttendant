package com.starlink.marketing.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 营销模块配置类。
 * <p>
 * 配置MyBatis-Plus的Mapper扫描。
 *
 */
@Configuration
@MapperScan("com.starlink.marketing.mapper")
public class MarketingConfig {
}
