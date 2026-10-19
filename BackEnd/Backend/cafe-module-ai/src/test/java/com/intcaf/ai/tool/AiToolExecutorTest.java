package com.intcaf.ai.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiToolExecutorTest {

    @Test
    void permitsBoundedReportingQuery() {
        assertNull(AiToolExecutor.validateSql(
                "SELECT p.name, SUM(i.quantity) FROM product p JOIN order_item i ON i.product_id = p.id GROUP BY p.name"));
    }

    @Test
    void rejectsSensitiveTablesAndSideEffects() {
        assertNotNull(AiToolExecutor.validateSql("SELECT id_card FROM member"));
        assertNotNull(AiToolExecutor.validateSql("SELECT * FROM product INTO OUTFILE '/tmp/data'"));
        assertNotNull(AiToolExecutor.validateSql("SELECT LOAD_FILE('/etc/passwd') FROM product"));
        assertNotNull(AiToolExecutor.validateSql("SELECT * FROM product; DELETE FROM member"));
        assertNotNull(AiToolExecutor.validateSql("SELECT * FROM product JOIN employee ON employee.id = product.id"));
        assertNotNull(AiToolExecutor.validateSql("SELECT * FROM product, member"));
        assertNotNull(AiToolExecutor.validateSql("SELECT * FROM product WHERE id IN (SELECT id FROM member)"));
    }
}
