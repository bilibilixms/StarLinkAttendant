package com.starlink;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class TestSQL {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("数据源连接测试")
    void shouldConnectToDatabase() throws SQLException {
        assertNotNull(dataSource, "DataSource 不应为 null");
        assertTrue(dataSource instanceof HikariDataSource,
                "数据源应为 HikariDataSource 类型，实际: " + dataSource.getClass().getName());

        HikariDataSource hikari = (HikariDataSource) dataSource;
        assertEquals(20, hikari.getMaximumPoolSize(), "最大连接池大小应为 20");

        try (Connection conn = hikari.getConnection()) {
            assertNotNull(conn, "应能获取到非 null 的 Connection");
            assertFalse(conn.isClosed(), "Connection 不应已关闭");

            try (Statement stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT 1 AS ok")) {
                assertTrue(rs.next(), "SELECT 1 应返回一行结果");
                assertEquals(1, rs.getInt("ok"), "返回值应为 1");
            }
        }
    }

    @Test
    @DisplayName("数据库版本信息")
    void shouldShowDatabaseVersion() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT VERSION() AS v")) {
            assertTrue(rs.next(), "应能查询数据库版本");
            String version = rs.getString("v");
            assertNotNull(version, "版本号不应为 null");
            System.out.println("=== MySQL 版本: " + version + " ===");
        }
    }
}
