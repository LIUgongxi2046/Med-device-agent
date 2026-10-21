package com.ruoyi.medical.persistence;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.TenantWatermarkMapper;
import com.ruoyi.medical.persistence.mapper.TenantMemberMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.schema.Column;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.LocalCacheScope;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TenantAccessTest {
    private TenantLineInnerInterceptor interceptor() throws Exception {
        String name = "com.ruoyi.medical.persistence.ProtectedTenantInterceptor";
        assertNotNull(getClass().getClassLoader().getResource(name.replace('.', '/') + ".class"), "业务模块必须提供企业 SQL 控制入口");
        return (TenantLineInnerInterceptor) Class.forName(name).getConstructor().newInstance();
    }

    private String publishedTransactionSql() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        List<String> statements = new ArrayList<>();
        Parser.builder().build().parse(Files.readString(root.resolve("LLD-后端.md"))).accept(new AbstractVisitor() {
            @Override public void visit(FencedCodeBlock block) {
                if (block.getInfo().equals("sql")) statements.add(block.getLiteral());
            }
        });
        assertEquals(1, statements.size());
        return statements.get(0);
    }

    @Test
    void publishedTransactionSqlCannotRunWithoutVerifiedContext() throws Exception {
        String sql = publishedTransactionSql();
        TenantLineInnerInterceptor interceptor = interceptor();
        RuntimeException failure = assertThrows(RuntimeException.class, () -> interceptor.parserMulti(sql, null));
        assertEquals("ACCESS_DENIED", failure.getMessage());
        assertNull(failure.getCause());
    }

    @Test
    void everyPhysicalBusinessTableRequiresEnterpriseIdentity() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var manifest = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readAllBytes(root.resolve("business/medical-data/src/main/resources/db/migration/coverage.json")));
        TenantLineInnerInterceptor interceptor = interceptor();
        int checked = 0;
        for (var group : manifest.required("required_tables")) {
            for (var name : group) {
                assertFalse(interceptor.getTenantLineHandler().ignoreTable(name.asText()));
                checked++;
            }
        }
        assertEquals(526, checked); // Verified unique coverage manifest entries, including package authoring originals.
        RuntimeException failure = assertThrows(RuntimeException.class, () -> interceptor.getTenantLineHandler().getTenantId());
        assertEquals("ACCESS_DENIED", failure.getMessage());
    }

    @Test
    void callerCannotProvideEnterpriseInsertColumn() throws Exception {
        var statements = CCJSqlParserUtil.parseStatements(publishedTransactionSql());
        assertEquals(4, statements.size());
        var select = assertInstanceOf(PlainSelect.class, statements.get(0));
        var predicate = assertInstanceOf(EqualsTo.class, select.getWhere());
        var column = assertInstanceOf(Column.class, predicate.getLeftExpression());
        assertEquals("tenant_id", column.getColumnName());
        var handler = interceptor().getTenantLineHandler();
        for (String name : List.of(column.getColumnName(), "`" + column.getColumnName() + "`",
                                  "\"" + column.getColumnName().toUpperCase(java.util.Locale.ROOT) + "\"")) {
            var failure = assertThrows(TenantAccessException.class,
                () -> handler.ignoreInsert(List.of(new Column(name)), "tenant_id"));
            assertEquals(ErrorCode.ACCESS_DENIED, failure.code());
            assertNull(failure.getCause());
        }
    }

    @Test
    void enterpriseHandlerCannotBeReplaced() throws Exception {
        var interceptor = interceptor();
        assertThrows(TenantAccessException.class, () -> interceptor.setTenantLineHandler(interceptor.getTenantLineHandler()));
        Properties configuration = new Properties();
        configuration.setProperty("tenantLineHandler", TenantLineInnerInterceptor.class.getName());
        assertThrows(TenantAccessException.class, () -> interceptor.setProperties(configuration));
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, () -> TenantScope.open(null)).code());
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, TenantScope::requireCurrent).code());
    }

    @Test
    void productionMapperUsesProtectedFactoryWithoutQueryCache() throws Exception {
        var dataSource = new DriverManagerDataSource();
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(dataSource);
        var configuration = factory.getConfiguration();
        assertSame(dataSource, configuration.getEnvironment().getDataSource());
        assertFalse(configuration.isCacheEnabled());
        assertEquals(LocalCacheScope.STATEMENT, configuration.getLocalCacheScope());
        assertEquals(NoLoggingImpl.class, configuration.getLogImpl());
        assertEquals(2, configuration.getInterceptors().size());
        var plugin = assertInstanceOf(MybatisPlusInterceptor.class, configuration.getInterceptors().get(0));
        assertEquals(1, plugin.getInterceptors().size());
        assertInstanceOf(ProtectedTenantInterceptor.class, plugin.getInterceptors().get(0));
        assertInstanceOf(com.github.pagehelper.PageInterceptor.class, configuration.getInterceptors().get(1));
        var statement = configuration.getMappedStatement(TenantWatermarkMapper.class.getName() + ".lockCurrentTenant");
        assertFalse(statement.isUseCache());
        assertTrue(statement.isFlushCacheRequired());
        var sql = assertInstanceOf(net.sf.jsqlparser.statement.select.PlainSelect.class,
            CCJSqlParserUtil.parse(statement.getBoundSql(null).getSql()));
        assertNotNull(sql.getForMode());
        MapperScan scan = MedicalPersistenceConfiguration.class.getAnnotation(MapperScan.class);
        assertEquals("medicalSqlSessionFactory", scan.sqlSessionFactoryRef());
        assertEquals(org.apache.ibatis.annotations.Mapper.class, scan.annotationClass());
    }

    @Test
    void actualMapperDeniesMissingIdentityBeforeDatabaseConnection() throws Exception {
        // DataSource 没有连接配置；检查在取得任何数据库连接前必须拒绝未授权调用。
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        String sql = factory.getConfiguration().getMappedStatement(TenantWatermarkMapper.class.getName() + ".lockCurrentTenant")
            .getBoundSql(null).getSql();
        assertNotNull(com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.parse(sql));
        try (var session = factory.openSession()) {
            var failure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                () -> session.getMapper(TenantWatermarkMapper.class).lockCurrentTenant());
            var denial = assertInstanceOf(TenantAccessException.class, failure.getCause());
            assertEquals(ErrorCode.ACCESS_DENIED, denial.code());
            assertNull(denial.getCause());
            var memberFailure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                () -> session.getMapper(TenantMemberMapper.class).currentMember(null, null));
            assertEquals(ErrorCode.ACCESS_DENIED, assertInstanceOf(TenantAccessException.class, memberFailure.getCause()).code());
            var actionFailure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                () -> session.getMapper(TenantMemberMapper.class).currentActions(null));
            assertEquals(ErrorCode.ACCESS_DENIED, assertInstanceOf(TenantAccessException.class, actionFailure.getCause()).code());
            var sourceMapper = session.getMapper(com.ruoyi.medical.persistence.mapper.SourceUploadMapper.class);
            for (var update : List.<org.junit.jupiter.api.function.Executable>of(
                    () -> sourceMapper.claimCompletionDelivery(null), () -> sourceMapper.renewCompletionDelivery(null))) {
                var leaseFailure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class, update);
                assertEquals(ErrorCode.ACCESS_DENIED, assertInstanceOf(TenantAccessException.class, leaseFailure.getCause()).code());
            }
            var rightsMapper = session.getMapper(com.ruoyi.medical.persistence.mapper.SourceRightsMapper.class);
            for (var action : List.<org.junit.jupiter.api.function.Executable>of(
                    () -> rightsMapper.state(null), () -> rightsMapper.operation(null), () -> rightsMapper.insertRights(null),
                    () -> rightsMapper.replaceSourceRights(null), () -> rightsMapper.revokeRights(null),
                    () -> rightsMapper.insertEvidenceLineage(null), () -> rightsMapper.saveOperation(null),
                    () -> rightsMapper.insertDecision(null), () -> rightsMapper.advanceState(null))) {
                var rightsFailure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class, action);
                assertEquals(ErrorCode.ACCESS_DENIED, assertInstanceOf(TenantAccessException.class, rightsFailure.getCause()).code());
            }
            var statusFailure=assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,() -> sourceMapper.completionState(null));
            assertEquals(ErrorCode.ACCESS_DENIED,assertInstanceOf(TenantAccessException.class,statusFailure.getCause()).code());
            var governanceMapper=session.getMapper(com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper.class);
            var reviewFailure=assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,() -> governanceMapper.uploadReviews(null));
            assertEquals(ErrorCode.ACCESS_DENIED,assertInstanceOf(TenantAccessException.class,reviewFailure.getCause()).code());
            var indexMapper=session.getMapper(com.ruoyi.medical.persistence.mapper.SourceIndexMapper.class);
            for(var action:List.<org.junit.jupiter.api.function.Executable>of(
                    () -> indexMapper.command(null), () -> indexMapper.build(null), () -> indexMapper.delivery(null),
                    () -> indexMapper.initializeDelivery(null), () -> indexMapper.claim(null), () -> indexMapper.renew(null),
                    () -> indexMapper.saveVersion(null), () -> indexMapper.saveLineage(null), () -> indexMapper.saveIndex(null),
                    () -> indexMapper.saveBuild(null), () -> indexMapper.saveItem(null), () -> indexMapper.readyIndex(null),
                    () -> indexMapper.completeBuild(null), () -> indexMapper.completeDelivery(null), () -> indexMapper.failIndex(null),
                    () -> indexMapper.failDelivery(null), () -> indexMapper.saveReceipt(null), () -> indexMapper.receipt(null),
                    () -> indexMapper.rawChunkCount(null), () -> indexMapper.itemCount(null), () -> indexMapper.chunks(null),
                    () -> indexMapper.items(null), () -> indexMapper.index(null), () -> indexMapper.views(null))) {
                var indexFailure=assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,action);
                assertEquals(ErrorCode.ACCESS_DENIED,assertInstanceOf(TenantAccessException.class,indexFailure.getCause()).code());
            }
        }
    }
}
