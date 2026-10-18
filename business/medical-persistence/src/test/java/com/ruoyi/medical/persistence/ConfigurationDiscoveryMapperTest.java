package com.ruoyi.medical.persistence;

import com.github.pagehelper.Page;
import com.github.pagehelper.dialect.helper.MySqlDialect;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.cache.CacheKey;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** SQL generation, tenant/subject guards and original paginator; this test does not connect to a database. */
class ConfigurationDiscoveryMapperTest {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.ConfigurationDiscoveryMapper.";
    @Test void bothReadQueriesAreBoundedByFrameworkWithoutCountsAndProtectedAtEveryJoin() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        String tenant = ConfigurationDiscoveryServiceTest.id(1000), actor = ConfigurationDiscoveryServiceTest.id(1001);
        var values = new HashMap<String,Object>(); values.put("userId", actor); values.put("after", ConfigurationDiscoveryServiceTest.id(10)); values.put("q", "%_literal' OR 1=1"); values.put("mode", "SIMULATION");
        for (String method : List.of("models", "rules", "authorizationClock")) assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(PREFIX + method));
        try (var scope = TenantScope.open(new TenantScope.Identity(tenant, actor, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE))) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE, BigInteger.ONE));
                for (String method : List.of("models", "rules")) {
                    String name = PREFIX + method; var statement = configuration.getMappedStatement(name);
                    assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
                    TenantScope.requireSqlAccess(name); TenantScope.requireSubjectParameter(name, values);
                    assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(name, Map.of("userId", tenant)));
                    for (boolean filtered : List.of(true, false)) {
                        var parameters = new HashMap<>(values); if (!filtered) { parameters.remove("q"); parameters.remove("after"); }
                        var sql = statement.getBoundSql(parameters).getSql(); assertFalse(sql.contains("%_literal"));
                        assertFalse(sql.toLowerCase().contains("count(")); assertTrue(sql.contains("ORDER BY"));
                        if ("rules".equals(method)) {
                            assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM simulation_rule_binding b"));
                            assertTrue(sql.contains("b.tenant_id=r.tenant_id AND b.rule_version=r.rule_version"));
                        } else assertFalse(sql.contains("simulation_rule_binding"));
                        if ("models".equals(method)) { assertTrue(sql.contains("m.execution_mode=h.execution_mode")); assertTrue(sql.contains("h.execution_mode=?")); }
                        if (filtered) { assertTrue(sql.contains("LOCATE(LOWER(?)")); assertFalse(sql.contains("LIKE")); assertTrue(sql.contains("> ?")); }
                        String scoped = new ProtectedTenantInterceptor().parserSingle(sql, null);
                        assertTrue(scoped.contains(tenant)); assertTrue(scoped.contains("tenant_id"));
                        String paged = new MySqlDialect().getPageSql(scoped, new Page<Object>(1, 101, false), new CacheKey());
                        assertTrue(paged.toUpperCase().contains("LIMIT"));
                        assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(paged));
                    }
                }
                String temporalName = PREFIX + "authorizationClock";
                var temporalStatement = configuration.getMappedStatement(temporalName);
                assertFalse(temporalStatement.isUseCache()); assertTrue(temporalStatement.isFlushCacheRequired());
                TenantScope.requireSqlAccess(temporalName); TenantScope.requireSubjectParameter(temporalName, values);
                assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(temporalName, Map.of("userId", tenant)));
                String temporalSql = temporalStatement.getBoundSql(values).getSql();
                assertTrue(temporalSql.contains("valid_from")); assertTrue(temporalSql.contains("valid_to"));
                assertTrue(temporalSql.contains("permission_grant")); assertTrue(temporalSql.contains("rights_revision"));
                assertTrue(temporalSql.contains("UTC_TIMESTAMP(6)"));
                String scopedTemporal = new ProtectedTenantInterceptor().parserSingle(temporalSql, null);
                assertEquals(4, scopedTemporal.split("tenant_id", -1).length - 1);
                assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(scopedTemporal));
                scope.finishTransaction();
            } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        }
    }
}
