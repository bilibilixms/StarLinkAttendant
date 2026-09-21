package com.starlink;

import com.starlink.member.entity.MemberBalanceLog;
import com.starlink.member.service.BalanceService;
import com.starlink.product.entity.ProductCombo;
import com.starlink.product.service.ProductComboService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-2 数据库基线完整性测试。
 * <p>
 * 覆盖 TEST-P0-2-01 ~ 06：
 * <ol>
 *   <li>三张代码依赖表在库中存在；</li>
 *   <li>关键字段就位；</li>
 *   <li>关键索引就位；</li>
 *   <li>DataBase.sql 从空库执行后三张表均被创建（真正的「从零建库」验证，用临时库）；</li>
 *   <li>三张表可被真实业务写入/读取（§18 最小真实验证）。</li>
 * </ol>
 * 从零建库测试只操作临时库 {@code starlink_schema_test_junit}，绝不触碰业务库。
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
class DatabaseBaselineTest {

    private static final String REAL_DB = "starlink_attendant";
    private static final String TEMP_DB = "starlink_schema_test_junit";

    private static final String TEST_PHONE = "13910000001";
    private static final String TEST_MEMBER_NO = "TESTP2B001";
    private static final String TEST_COMBO_NAME = "TESTP2-套餐名";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private BalanceService balanceService;
    @Autowired
    private ProductComboService productComboService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long testMemberId;

    /* ==================== 夹具 ==================== */

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("INSERT INTO member (member_no, phone, password_hash, real_name, gender, level_id, "
                        + "total_points, available_points, total_recharge, balance, total_consumption, "
                        + "register_source, status) VALUES (?, ?, ?, ?, 1, 1, 0, 0, 100.00, 100.00, 0.00, 1, 1)",
                TEST_MEMBER_NO, TEST_PHONE, passwordEncoder.encode("Test@123456"), "基线测试会员");
        testMemberId = jdbc.queryForObject("SELECT id FROM member WHERE phone = ?", Long.class, TEST_PHONE);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        // 先删子表（外键 RESTRICT/CASCADE 顺序），全部针对测试数据，绝不动种子数据
        List<Long> memberIds = jdbc.queryForList(
                "SELECT id FROM member WHERE member_no LIKE 'TESTP2%'", Long.class);
        for (Long id : memberIds) {
            jdbc.update("DELETE FROM member_balance_log WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member_recharge WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member_points_log WHERE member_id = ?", id);
        }
        jdbc.update("DELETE FROM member WHERE member_no LIKE 'TESTP2%'");
        jdbc.update("DELETE FROM member WHERE phone = ?", TEST_PHONE);

        List<Long> comboIds = jdbc.queryForList(
                "SELECT id FROM product_combo WHERE combo_name LIKE 'TESTP2%'", Long.class);
        for (Long id : comboIds) {
            jdbc.update("DELETE FROM product_combo_item WHERE combo_id = ?", id);
        }
        jdbc.update("DELETE FROM product_combo WHERE combo_name LIKE 'TESTP2%'");
    }

    /* ==================== 结构断言辅助 ==================== */

    private boolean tableExists(String table) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name = ?", Integer.class, table);
        return n != null && n > 0;
    }

    private Set<String> columnsOf(String table) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = ?", String.class, table));
    }

    private boolean indexExists(String table, String index) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() "
                        + "AND table_name = ? AND index_name = ?", Integer.class, table, index);
        return n != null && n > 0;
    }

    /* ==================== TEST-P0-2-01 / 02 / 03 ==================== */

    @Test
    @DisplayName("TEST-P0-2-01 member_balance_log 表存在")
    void memberBalanceLogTableExists() {
        assertTrue(tableExists("member_balance_log"), "member_balance_log 不存在");
    }

    @Test
    @DisplayName("TEST-P0-2-02 product_combo 表存在")
    void productComboTableExists() {
        assertTrue(tableExists("product_combo"), "product_combo 不存在");
    }

    @Test
    @DisplayName("TEST-P0-2-03 product_combo_item 表存在")
    void productComboItemTableExists() {
        assertTrue(tableExists("product_combo_item"), "product_combo_item 不存在");
    }

    /* ==================== TEST-P0-2-04 ==================== */

    @Test
    @DisplayName("TEST-P0-2-04 三张表关键字段存在")
    void keyColumnsExist() {
        assertTrue(columnsOf("member_balance_log").containsAll(List.of(
                        "id", "member_id", "amount", "balance_before", "balance_after",
                        "biz_type", "biz_id", "remark", "created_at", "updated_at",
                        "deleted_at", "version")),
                "member_balance_log 字段不全: " + columnsOf("member_balance_log"));

        assertTrue(columnsOf("product_combo").containsAll(List.of(
                        "id", "combo_name", "combo_code", "description", "original_price",
                        "combo_price", "image_url", "is_active", "sort_order",
                        "created_at", "updated_at", "deleted_at", "version")),
                "product_combo 字段不全: " + columnsOf("product_combo"));

        assertTrue(columnsOf("product_combo_item").containsAll(List.of(
                        "id", "combo_id", "product_id", "product_name", "unit_price",
                        "quantity", "subtotal", "created_at", "updated_at",
                        "deleted_at", "version")),
                "product_combo_item 字段不全: " + columnsOf("product_combo_item"));
    }

    /* ==================== TEST-P0-2-05 ==================== */

    @Test
    @DisplayName("TEST-P0-2-05 关键索引存在（按实际查询推导）")
    void keyIndexesExist() {
        // member_balance_log：按会员查流水；按业务单据反查
        assertTrue(indexExists("member_balance_log", "idx_member_id"), "缺 idx_member_id");
        assertTrue(indexExists("member_balance_log", "idx_biz"), "缺 idx_biz");
        // product_combo：列表按 is_active 过滤
        assertTrue(indexExists("product_combo", "idx_is_active"), "缺 idx_is_active");
        // product_combo_item：更新/删除套餐时按 combo_id 过滤（真实 WHERE）
        assertTrue(indexExists("product_combo_item", "idx_combo_id"), "缺 idx_combo_id");
    }

    @Test
    @DisplayName("TEST-P0-2-05b product_combo_item 不得对 (combo_id, product_id) 加唯一键（会与逻辑删除冲突）")
    void noUniqueOnComboProduct() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() "
                        + "AND table_name = 'product_combo_item' AND non_unique = 0 "
                        + "AND index_name <> 'PRIMARY'", Integer.class);
        assertEquals(0, n == null ? -1 : n,
                "product_combo_item 不应存在唯一键：更新套餐为「逻辑删除旧明细 + 插入新明细」，"
                        + "唯一键会与仍占用键值的软删除行冲突");
    }

    /* ==================== TEST-P0-2-06 ==================== */

    @Test
    @DisplayName("TEST-P0-2-06 DataBase.sql 从空库执行后三张表均存在（临时库验证）")
    void databaseScriptCreatesAllTablesFromScratch() throws Exception {
        Path script = locateDatabaseSql();
        String ddl = Files.readString(script, StandardCharsets.UTF_8).replace(REAL_DB, TEMP_DB);
        assertFalse(ddl.contains(REAL_DB), "脚本改名后仍残留业务库名，拒绝执行");
        assertTrue(ddl.contains(TEMP_DB), "脚本未成功改名");

        // 必须用专用连接执行：脚本内含 `USE <临时库>`，会改写连接的默认库；
        // 若借用连接池中的连接且不还原，会把"当前库已被 DROP"的连接放回池中，
        // 导致后续测试报 1046 No database selected。故结束时还原默认库与 FK 检查开关。
        try (Connection conn = dataSource.getConnection()) {
            try (Statement st = conn.createStatement()) {
                st.execute("DROP DATABASE IF EXISTS `" + TEMP_DB + "`");
                for (String stmt : splitStatements(ddl)) {
                    st.execute(stmt);
                }
            }
            for (String table : List.of("member_balance_log", "product_combo", "product_combo_item")) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? "
                                + "AND table_name = ?")) {
                    ps.setString(1, TEMP_DB);
                    ps.setString(2, table);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertEquals(1, rs.getInt(1), "从空库执行 DataBase.sql 后缺表: " + table);
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? "
                            + "AND table_type = 'BASE TABLE'")) {
                ps.setString(1, TEMP_DB);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertTrue(rs.getInt(1) >= 43, "从空库建表数量应不少于 43，实际 " + rs.getInt(1));
                }
            }
        } finally {
            // 还原：先删临时库，再把连接恢复为业务库 + 打开外键检查
            try (Connection conn = dataSource.getConnection();
                 Statement st = conn.createStatement()) {
                st.execute("DROP DATABASE IF EXISTS `" + TEMP_DB + "`");
                st.execute("USE `" + REAL_DB + "`");
                st.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }

    /** 定位仓库中的 DataBase.sql（surefire 的工作目录为 starlink-starter 模块目录）。 */
    private Path locateDatabaseSql() {
        Path p = Paths.get("../../../resource/sql/DataBase.sql").toAbsolutePath().normalize();
        assertTrue(Files.exists(p), "未找到 DataBase.sql，期望路径: " + p);
        return p;
    }

    /** 去掉整行注释后按 ';' 切分（已确认脚本内字符串字面量不含分号）。 */
    private List<String> splitStatements(String script) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : script.split("\r?\n")) {
            if (!line.trim().startsWith("--")) {
                cleaned.append(line).append('\n');
            }
        }
        List<String> out = new ArrayList<>();
        for (String raw : cleaned.toString().split(";")) {
            String sql = raw.trim();
            if (!sql.isEmpty()) {
                out.add(sql);
            }
        }
        return out;
    }

    /* ==================== §18 最小真实验证 ==================== */

    @Test
    @DisplayName("§18-1/2 余额流水可写入：扣减（消费）与增加（退款回充）均落 member_balance_log")
    void balanceLedgerIsWritable() {
        BigDecimal before = jdbc.queryForObject(
                "SELECT balance FROM member WHERE id = ?", BigDecimal.class, testMemberId);

        // 余额扣减（等价于余额支付的核心余额动作）
        balanceService.deductBalance(testMemberId, new BigDecimal("30.00"), (byte) 1, 999L, "基线测试-消费");
        // 余额增加（等价于退款回充）
        balanceService.addBalance(testMemberId, new BigDecimal("10.00"), (byte) 2, 999L, "基线测试-退款");

        List<Map<String, Object>> logs = jdbc.queryForList(
                "SELECT amount, balance_before, balance_after, biz_type, biz_id FROM member_balance_log "
                        + "WHERE member_id = ? ORDER BY id", testMemberId);
        assertEquals(2, logs.size(), "应写入 2 条余额流水，实际 " + logs.size());

        BigDecimal after = jdbc.queryForObject(
                "SELECT balance FROM member WHERE id = ?", BigDecimal.class, testMemberId);
        assertEquals(0, before.subtract(new BigDecimal("20.00")).compareTo(after),
                "余额应为 100 - 30 + 10 = 80");

        // 流水的 before/after 应与余额变动一致
        assertEquals(0, new BigDecimal(logs.get(0).get("balance_before").toString())
                .compareTo(before), "第一条流水 balance_before 不符");
        assertEquals(0, new BigDecimal(logs.get(1).get("balance_after").toString())
                .compareTo(after), "第二条流水 balance_after 不符");
    }

    @Test
    @DisplayName("§18-3/4/5 套餐可创建并读回，明细关系与快照字段正确")
    void comboAndItemsAreWritableAndReadable() {
        List<Long> productIds = jdbc.queryForList(
                "SELECT id FROM product WHERE deleted_at IS NULL ORDER BY id LIMIT 2", Long.class);
        assertTrue(productIds.size() >= 2, "需要至少 2 个种子商品用于套餐测试");
        Map<Long, Integer> items = new LinkedHashMap<>();
        items.put(productIds.get(0), 2);
        items.put(productIds.get(1), 1);

        ProductCombo combo = new ProductCombo();
        combo.setComboName(TEST_COMBO_NAME);
        combo.setComboCode("TESTP2-CODE");
        combo.setDescription("基线测试套餐");
        combo.setComboPrice(new BigDecimal("9.90"));
        ProductCombo created = productComboService.createCombo(combo, items);
        assertNotNull(created.getId(), "套餐应创建成功");

        // 读回（套餐查询）
        ProductCombo loaded = productComboService.getComboById(created.getId());
        assertEquals(TEST_COMBO_NAME, loaded.getComboName());
        assertEquals(0, new BigDecimal("9.90").compareTo(loaded.getComboPrice()));
        // original_price 由服务端按明细零售价合计计算
        assertNotNull(loaded.getOriginalPrice());
        assertTrue(loaded.getOriginalPrice().compareTo(BigDecimal.ZERO) > 0,
                "original_price 应由明细零售价合计得出");

        // 明细关系（套餐商品关系查询）
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT product_id, product_name, unit_price, quantity, subtotal "
                        + "FROM product_combo_item WHERE combo_id = ? AND deleted_at IS NULL ORDER BY id",
                created.getId());
        assertEquals(2, rows.size(), "套餐明细应有 2 条");
        BigDecimal sum = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) {
            BigDecimal unit = new BigDecimal(r.get("unit_price").toString());
            int qty = ((Number) r.get("quantity")).intValue();
            BigDecimal subtotal = new BigDecimal(r.get("subtotal").toString());
            assertEquals(0, unit.multiply(BigDecimal.valueOf(qty)).compareTo(subtotal),
                    "明细 subtotal 应等于 unit_price × quantity");
            assertNotNull(r.get("product_name"), "应保存商品名称快照");
            sum = sum.add(subtotal);
        }
        assertEquals(0, sum.compareTo(loaded.getOriginalPrice()),
                "套餐原价应等于明细小计合计");
    }

    @Test
    @DisplayName("§18- 外键生效：明细引用不存在的套餐/商品应被拒绝")
    void foreignKeysAreEnforced() {
        assertThrows(Exception.class, () -> jdbc.update(
                "INSERT INTO product_combo_item (combo_id, product_id, product_name, unit_price, quantity, subtotal) "
                        + "VALUES (99999999, 1, 'x', 1.00, 1, 1.00)"),
                "combo_id 外键应阻止引用不存在的套餐");
    }
}
