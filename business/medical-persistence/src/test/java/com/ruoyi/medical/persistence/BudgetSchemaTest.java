package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BudgetSchemaTest {
    @Test
    void missingDuplicateAndTrailingApprovalInputsAreRejected() throws Exception {
        var schema = new BudgetSchema(new PermissionSchema());
        assertThrows(SchemaRejection.class, () -> schema.create("{}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(SchemaRejection.class, () -> schema.decide("{}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.create(
                "{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.decide("{} {}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void moneyAndUnsignedCountsRetainPublishedStoragePrecision() {
        assertEquals(new BigDecimal("9999999999999999.99999999"), BudgetSchema.money("9999999999999999.99999999"));
        assertEquals(new BigDecimal("0.00000001"), BudgetSchema.money("0.00000001"));
        for (String value : List.of("-1", "01", "1e2", "1.000000001", "10000000000000000", "NaN"))
            assertThrows(SchemaRejection.class, () -> BudgetSchema.money(value));
        assertThrows(SchemaRejection.class, () -> PermissionSchema.revision("18446744073709551616"));
        assertEquals(new BigInteger("18446744073709551615"), PermissionSchema.revision("18446744073709551615"));
    }

    @Test
    void lowerLimitsCannotEraseCommittedAmountsTokensOrCalls() {
        BudgetService.requireLimit("0.30000000", new BigDecimal("0.1"), new BigDecimal("0.2"));
        assertEquals(ErrorCode.BUDGET_EXCEEDED, assertThrows(TenantAccessException.class,
                () -> BudgetService.requireLimit("0.29999999", new BigDecimal("0.1"), new BigDecimal("0.2"))).code());
        BudgetService.requireCount("18446744073709551615", new BigInteger("18446744073709551614"), BigInteger.ONE);
        assertEquals(ErrorCode.BUDGET_EXCEEDED, assertThrows(TenantAccessException.class,
                () -> BudgetService.requireCount("18446744073709551615", new BigInteger("18446744073709551615"), BigInteger.ONE)).code());
    }

    @Test
    void budgetManifestPreservesIndependentPermissionManifest() throws Exception {
        var permissions = new PermissionSchema(); String before = permissions.manifestSha256();
        var budget = new BudgetSchema(permissions);
        assertEquals(before, permissions.manifestSha256()); assertTrue(budget.manifestSha256().matches("[0-9a-f]{64}"));
        assertNotEquals(before, budget.manifestSha256());
    }

    @Test
    void fullBudgetQueriesUseNativeParsersAndPublishedColumns() throws Exception {
        var root = java.nio.file.Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(
                new org.springframework.jdbc.datasource.DriverManagerDataSource()).getConfiguration();
        var values = new java.util.HashMap<String,Object>(); values.put("limit", 21);
        for (String key : List.of("after", "filterProject", "budgetId", "projectId", "approvalId", "oldApproval", "key", "userId"))
            values.put(key, UUID.randomUUID().toString());
        values.put("scopes", List.of(new BudgetProtocol.Scope(null, "ENTERPRISE", UUID.randomUUID().toString()),
                new BudgetProtocol.Scope(UUID.randomUUID().toString(), "PROJECT", UUID.randomUUID().toString()),
                new BudgetProtocol.Scope(null, "BUDGET", null),
                new BudgetProtocol.Scope(UUID.randomUUID().toString(), "BUDGET", UUID.randomUUID().toString())));
        var statements = new java.util.TreeMap<String,String>();
        for (String name : List.of("grants", "accounts", "count", "account", "approval", "registry", "insertAccount",
                "insertVersion", "insertApproval", "initializeApproval", "advanceApproval", "currentOperation", "saveOperation",
                "advanceGovernance", "audit", "event", "history", "historyCount")) {
            String sql = configuration.getMappedStatement("com.ruoyi.medical.persistence.mapper.BudgetMapper." + name).getBoundSql(values).getSql();
            assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql)); statements.put(name, sql);
        }
        var output = root.resolve(".implementation-work/budget-query-columns.json");
        java.nio.file.Files.write(output, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(statements));
        var process = new ProcessBuilder(root.resolve(".lld-data-work/venv/bin/python").toString(),
                root.resolve("tests/check_budget_sql_columns.py").toString(), output.toString()).directory(root.toFile()).inheritIO().start();
        assertEquals(0, process.waitFor());
    }

    @Test
    void independentHistorySchemaResolvesOriginalApprovalAndRejectsIncompleteResponses() throws Exception {
        var json = new PermissionSchema(); var budget = new BudgetSchema(json); String original = budget.manifestSha256();
        var history = new BudgetHistorySchema(json, budget);
        assertThrows(SchemaRejection.class, () -> history.verify("Entry", json.object()));
        assertThrows(SchemaRejection.class, () -> history.verify("Listing", json.object()));
        assertEquals(original, budget.manifestSha256());
    }
}
