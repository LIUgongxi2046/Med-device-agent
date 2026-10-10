package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.math.BigInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class TemplateApplicabilityTest {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.TemplateMapper.";
    private static final List<String> READS = List.of("approvedVersions", "applicableVersions", "ruleDefinition", "ruleParents");
    private static Connection database;
    private static String owner;
    private static String status;
    private static BigInteger migrationCount;

    @BeforeAll static void actualDatabase() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials);
        database.setAutoCommit(false);
        try (var query = database.createStatement(); var rows = query.executeQuery(
                "SELECT user_id,status FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
            assertTrue(rows.next()); owner = rows.getString(1); status = rows.getString(2); assertFalse(rows.next());
        }
        try (var query = database.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG")) {
            assertTrue(rows.next()); migrationCount = new BigInteger(rows.getString(1)); assertTrue(migrationCount.signum() > 0);
        }
    }

    @Test void frameworkExpressionsUseActualAccountAndDatabaseValues() throws Exception {
        var engine = new RuleExpression(); var json = JsonMapper.builder().build();
        // 原数据库值只验证表达式组件，没有创建项目属性或批准业务依据。
        var attributes = List.of(new ProjectProtocol.Attribute("user_id", "KNOWN", json.valueToTree(owner), List.of(), "实际平台账户"),
                new ProjectProtocol.Attribute("status", "KNOWN", json.valueToTree(status), List.of(), "实际账户状态"),
                new ProjectProtocol.Attribute("migration_count", "KNOWN", json.valueToTree(migrationCount), List.of(), "实际迁移数量"));
        var positive = engine.compile(new TemplateApplicabilityProtocol.Expression("1.0.0", RuleExpression.ENGINE,
                "#attributes['user_id'] == #attributes['user_id'] and #attributes['status'] == 'ACTIVE' and #attributes['migration_count'] >= #attributes['migration_count']",
                List.of("user_id", "status", "migration_count"), List.of()));
        assertEquals(new TemplateApplicabilityProtocol.Result("APPLICABLE", List.of()), engine.evaluate(positive, attributes));
        var negative = engine.compile(new TemplateApplicabilityProtocol.Expression("1.0.0", RuleExpression.ENGINE,
                "#attributes['migration_count'] < #attributes['migration_count']", List.of("migration_count"), List.of()));
        assertEquals(new TemplateApplicabilityProtocol.Result("NOT_APPLICABLE", List.of()), engine.evaluate(negative, attributes));
    }

    @Test void missingActualProjectAttributesRemainUnknown() throws Exception {
        var engine = new RuleExpression();
        try (var query = database.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM project_profile_revision")) {
            assertTrue(rows.next()); assertEquals(BigInteger.ZERO, new BigInteger(rows.getString(1)));
        }
        var expression = engine.compile(new TemplateApplicabilityProtocol.Expression("1.0.0", RuleExpression.ENGINE,
                "#attributes['application_path'] == 'FIRST_REGISTRATION'", List.of("application_path"), List.of()));
        assertEquals(new TemplateApplicabilityProtocol.Result("UNKNOWN", List.of("application_path")), engine.evaluate(expression, List.of()));
    }

    @Test void frameworkSyntaxCannotEnableMethodsTypesAssignmentsOrUnlistedInputs() throws Exception {
        var engine = new RuleExpression();
        for (String expression : List.of("T(java.lang.System).getProperties()", "new java.lang.String('ACTIVE')",
                "#attributes['status'].toString() == 'ACTIVE'", "#attributes['status'] = 'ACTIVE'", "@environment.getProperty('HOME')",
                "#attributes['status'] matches '.*'", "#attributes['status'] == 'ACTIVE'")) {
            assertThrows(SchemaRejection.class, () -> engine.compile(new TemplateApplicabilityProtocol.Expression("1.0.0", RuleExpression.ENGINE,
                    expression, List.of(), List.of())));
        }
    }

    @Test void queryUsesClosedSchemaAndBindsLaterPagesToDatasetAndFilter() throws Exception {
        var schema = new TemplateApplicabilitySchema(new PermissionSchema());
        var query = schema.query(java.util.Map.of("document_type", new String[]{"REGISTRATION_DIRECTORY"}, "profile_revision", new String[]{owner}));
        assertEquals(1, query.page()); assertEquals(20, query.page_size());
        assertThrows(SchemaRejection.class, () -> schema.query(java.util.Map.of("document_type", new String[]{"REGISTRATION_DIRECTORY"},
                "profile_revision", new String[]{owner}, "page", new String[]{"2"})));
        assertThrows(SchemaRejection.class, () -> schema.query(java.util.Map.of("document_type", new String[]{"REGISTRATION_DIRECTORY"},
                "profile_revision", new String[]{owner}, "page_size", new String[]{"101"})));
        assertThrows(SchemaRejection.class, () -> schema.query(java.util.Map.of("document_type", new String[]{"REGISTRATION_DIRECTORY"},
                "profile_revision", new String[]{owner}, "tenant_id", new String[]{owner})));
    }

    @Test void applicabilityAndRuleDefinitionsArePublishedAndClosed() throws Exception {
        var catalogue = SchemaCatalogue.fromClasspath(); var empty = JsonMapper.builder().build().createObjectNode();
        for (String definition : List.of("Constant", "Expression", "Content", "Result")) {
            assertEquals("urn:medical:rule-expressions:1.0.0#/$defs/" + definition,
                    assertDoesNotThrow(() -> catalogue.schemaRef("rule-expressions", definition), "规则需要固定发布定义"));
            assertThrows(SchemaRejection.class, () -> catalogue.validate("rule-expressions", empty, definition));
        }
        for (String definition : List.of("Query", "ConditionResult", "TemplateView", "Page")) {
            assertEquals("urn:medical:template-applicability:1.0.0#/$defs/" + definition,
                    assertDoesNotThrow(() -> catalogue.schemaRef("template-applicability", definition), "模板适用需要完整固定定义"));
            assertThrows(SchemaRejection.class, () -> catalogue.validate("template-applicability", empty, definition));
        }
    }

    @Test void fixedRuleAndTemplateQueriesExecuteOnActualDatabase() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        var values = new HashMap<String,Object>(); values.put("userId", owner); values.put("upstreamId", owner);
        values.put("documentType", "REGISTRATION_DIRECTORY"); values.put("versionIds", List.of(owner));
        for (String name : READS) {
            assertTrue(factory.getConfiguration().hasStatement(PREFIX + name), "适用检查需要原企业查询：" + name);
            var statement = factory.getConfiguration().getMappedStatement(PREFIX + name);
            assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
            var bound = statement.getBoundSql(values);
            try (var query = database.prepareStatement(bound.getSql())) {
                int index = 1;
                for (var mapping : bound.getParameterMappings()) {
                    String key = mapping.getProperty();
                    query.setObject(index++, bound.hasAdditionalParameter(key) ? bound.getAdditionalParameter(key) : values.get(key));
                }
                try (var rows = query.executeQuery()) {
                    assertFalse(rows.next()); var result = statement.getResultMaps().get(0);
                    if (result.getType().isRecord()) {
                        assertEquals(result.getType().getRecordComponents().length, rows.getMetaData().getColumnCount());
                        var constructor = result.getConstructorResultMappings();
                        assertEquals(constructor.size(), rows.getMetaData().getColumnCount());
                        for (int ordinal = 0; ordinal < constructor.size(); ordinal++)
                            assertEquals(constructor.get(ordinal).getColumn(), rows.getMetaData().getColumnLabel(ordinal + 1));
                    }
                }
            }
            try (var session = factory.openSession()) {
                var failure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                        () -> session.selectList(PREFIX + name, values));
                assertInstanceOf(TenantAccessException.class, failure.getCause());
            }
        }
    }

    @AfterAll static void closeDatabase() throws Exception { if (database != null && !database.isClosed()) database.close(); }
}
