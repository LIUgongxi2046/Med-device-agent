package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RunTokenAuthoritySchemaIT {
    @Test
    void currentDatabaseProvidesPersistentTokenAndStepLeaseBindings() throws Exception {
        var root=Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets=JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var properties=new Properties(); properties.setProperty("user","medical_runtime");
        properties.setProperty("password",secrets.required("database_password").textValue());
        try(var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",properties);
                var statement=connection.prepareStatement("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('principal_token','run_step')")) {
            connection.setReadOnly(true);
            var columns=new HashSet<String>();
            try(var rows=statement.executeQuery()) {
                while(rows.next()) columns.add(rows.getString(1)+"."+rows.getString(2));
            }
            for(var column:java.util.List.of("principal_token.token_sha256","principal_token.service_uri",
                    "principal_token.policy_revision","principal_token.policy_sha256","principal_token.lease_generation",
                    "run_step.lease_owner","run_step.lease_expires_at")) assertTrue(columns.contains(column),column);
        }
    }

    @Test
    void currentAuthorityUsesClosedScopeAndPrincipalDefinitions() throws Exception {
        var resource=getClass().getClassLoader().getResourceAsStream("run-token-authority.schema.json");
        assertNotNull(resource,"持久运行身份必须具有完整封闭定义");
        try(var input=resource) {
            var document=JsonMapper.builder().build().readTree(input);
            for(var name:java.util.List.of("ScopeManifest","PrincipalManifest","Permission","Service")) {
                var definition=document.required("$defs").required(name);
                assertFalse(definition.required("additionalProperties").booleanValue(),name);
                assertFalse(definition.required("required").isEmpty(),name);
            }
        }
    }

    @Test
    void actualDatasetCannotBeUsedAsRunPermissionOrPrincipal() throws Exception {
        var root=Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var dataset=Files.readString(root.resolve("data-design/sample_dataset.json"));
        var schema=new RunTokenAuthoritySchema();
        for(var name:java.util.List.of("ScopeManifest","PrincipalManifest","Permission","Service","RoleScope"))
            assertThrows(SecurityException.class,() -> schema.read(name,dataset),name);
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,() -> schema.read("ScopeManifest",dataset+dataset));
    }

    @Test
    void actualMysqlPreparesEveryPersistentRunAuthorityStatement() throws Exception {
        var root=Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets=JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var properties=new Properties(); properties.setProperty("user","medical_runtime"); properties.setProperty("useServerPrepStmts","true");
        properties.setProperty("password",secrets.required("database_password").textValue());
        try(var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",properties)) {
            connection.setReadOnly(true);
            for(var sql:RunTokenAuthorityStore.STATEMENTS) {
                assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql));
                try(var statement=connection.prepareStatement(sql)) { assertTrue(statement.getParameterMetaData().getParameterCount()>0); }
            }
        }
    }

    @Test
    void persistentStoreRequiresCurrentTenantTransactionBeforeReadingOrWriting() {
        var store=new RunTokenAuthorityStore(new org.springframework.jdbc.datasource.DriverManagerDataSource());
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED,
            assertThrows(TenantAccessException.class,() -> store.run(null,null)).code());
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED,
            assertThrows(TenantAccessException.class,() -> store.token(null,null)).code());
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED,
            assertThrows(TenantAccessException.class,store::now).code());
    }
}
