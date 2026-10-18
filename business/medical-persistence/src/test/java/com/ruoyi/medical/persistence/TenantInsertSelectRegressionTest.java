package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual tenant SQL rewriting, not a substituted mapper or database. */
class TenantInsertSelectRegressionTest {
    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final String ACTOR = "00000000-0000-4000-8000-000000000002";
    private TenantScope scope() { return TenantScope.open(new TenantScope.Identity(TENANT, ACTOR,
        BigInteger.ONE, BigInteger.ONE, BigInteger.ONE)); }

    @Test void valuesAndOwnerFilteredInsertSelectBothRetainTenantProtection() throws Exception {
        try (var ignored = scope()) {
            var interceptor = new ProtectedTenantInterceptor();
            for (String sql : List.of(
                "INSERT INTO operation_record(subject_id) VALUES ('" + ACTOR + "')",
                "INSERT INTO operation_record(subject_id) SELECT created_by FROM conversation WHERE owner_id='" + ACTOR + "'")) {
                String rewritten = interceptor.parserSingle(sql, null);
                assertTrue(rewritten.contains("tenant_id"));
                assertTrue(rewritten.contains(TENANT));
                assertTrue(rewritten.contains(ACTOR));
                assertNotNull(CCJSqlParserUtil.parse(rewritten));
                if (sql.contains("SELECT")) assertTrue(rewritten.contains("owner_id"));
            }
        }
    }

    @Test void unsupportedSelectFormsAndCallerTenantColumnsRemainDenied() {
        try (var ignored = scope()) {
            var interceptor = new ProtectedTenantInterceptor();
            for (String sql : List.of(
                "INSERT INTO operation_record(tenant_id,subject_id) VALUES ('" + TENANT + "','" + ACTOR + "')",
                "INSERT INTO operation_record(subject_id) SELECT created_by FROM conversation UNION SELECT created_by FROM conversation",
                "INSERT INTO operation_record(subject_id) SELECT * FROM conversation",
                "INSERT INTO operation_record(subject_id) SELECT c.* FROM conversation c",
                "INSERT INTO operation_record(subject_id) SELECT '" + ACTOR + "'")) {
                assertThrows(TenantAccessException.class, () -> interceptor.parserSingle(sql, null), sql);
            }
        }
    }

    @Test void derivedUnionFiltersEveryPhysicalBranchAndAppendsServerTenantConstant() throws Exception {
        var config = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        String original = config.getMappedStatement("com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper.insertDeletionItems").getBoundSql(new HashMap<>()).getSql();
        try (var ignored = scope()) {
            var insert = assertInstanceOf(net.sf.jsqlparser.statement.insert.Insert.class,
                CCJSqlParserUtil.parse(new ProtectedTenantInterceptor().parserSingle(original, null)));
            var outer = assertInstanceOf(net.sf.jsqlparser.statement.select.PlainSelect.class, insert.getSelect());
            assertEquals(insert.getColumns().size(), outer.getSelectItems().size());
            var tenant = assertInstanceOf(net.sf.jsqlparser.expression.StringValue.class,
                outer.getSelectItems().get(outer.getSelectItems().size() - 1).getExpression());
            assertEquals(TENANT, tenant.getValue());
            var nested = assertInstanceOf(net.sf.jsqlparser.statement.select.ParenthesedSelect.class, outer.getFromItem());
            var union = assertInstanceOf(net.sf.jsqlparser.statement.select.SetOperationList.class, nested.getSelect());
            assertEquals(6, union.getSelects().size());
            for (var branch : union.getSelects()) {
                var body = assertInstanceOf(net.sf.jsqlparser.statement.select.PlainSelect.class, branch);
                String where = body.getWhere().toString();
                assertTrue(where.contains("tenant_id = '" + TENANT + "'"), where);
                assertTrue(where.contains("source_version = ?"), where);
            }
        }
    }

    @Test void allThreeExistingInsertSelectMapperPathsRewriteWithoutValuesCast() throws Exception {
        var config = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        try (var ignored = scope()) {
            for (String method : List.of("SourceGovernanceMapper.insertDeletionItems", "TaskPlanWriteMapper.copyTaskInput", "SourceCompletionMapper.saveCheckpoint")) {
                var statement = config.getMappedStatement("com.ruoyi.medical.persistence.mapper." + method);
                String sql = statement.getBoundSql(new HashMap<>()).getSql();
                String rewritten = new ProtectedTenantInterceptor().parserSingle(sql, null);
                assertTrue(rewritten.contains(TENANT), method);
                assertTrue(rewritten.contains("tenant_id"), method);
                assertNotNull(CCJSqlParserUtil.parse(rewritten), method);
            }
        }
    }
}
