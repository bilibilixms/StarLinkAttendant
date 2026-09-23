package com.starlink;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 星络灵侍馆 — 应用启动入口。
 * <p>
 * 聚合所有 Maven 子模块，Spring Boot 自动扫描 {@code com.starlink} 下的所有 Bean；
 * 同时扫描 {@code com.intcaf}（AI / RAG 模块复制自网吧管理系统，包名保持不变）。
 *
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.starlink.**.mapper")
@ComponentScan(basePackages = { "com.starlink", "com.intcaf" })
public class StarLinkAttendantApplication {

    public static void main(String[] args) {
        SpringApplication.run(StarLinkAttendantApplication.class, args);
    }
}
