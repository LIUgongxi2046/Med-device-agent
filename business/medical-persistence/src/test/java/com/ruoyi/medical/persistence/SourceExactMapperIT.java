package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.SourceExactMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceExactMapperIT {
    private java.sql.Connection database() throws Exception {
        var root=Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets=JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var properties=new Properties(); properties.setProperty("user","medical_runtime");
        properties.setProperty("password",secrets.required("database_password").textValue()); properties.setProperty("useServerPrepStmts","true");
        var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",properties);
        connection.setReadOnly(true); connection.setAutoCommit(false); return connection;
    }
    @Test
    void actualMysqlPreparesEveryScopedExactStatement() throws Exception {
        var configuration=new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        try(var connection=database()) {
            for(var method:SourceExactMapper.class.getDeclaredMethods()) {
                var bound=configuration.getMappedStatement(SourceExactMapper.class.getName()+"."+method.getName()).getBoundSql(Map.of());
                assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(bound.getSql()));
                try(var statement=connection.prepareStatement(bound.getSql())) {
                    assertEquals(bound.getParameterMappings().size(),statement.getParameterMetaData().getParameterCount(),method.getName());
                }
            }
        }
    }
    @Test
    void exactMysqlEqualityPreservesActualPublishedIdentifierBytes() throws Exception {
        var root=Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var dataset=JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve("data-design/sample_dataset.json")));
        String original=dataset.required("sources").get(0).required("publisher_version").textValue();
        assertFalse(original.isBlank());
        try(var connection=database(); var statement=connection.prepareStatement("SELECT CAST(? AS BINARY)=CAST(? AS BINARY)")) {
            for(String query:java.util.List.of(original,original+" ","%"+original+"%",original.substring(0,original.length()-1))) {
                statement.setString(1,original); statement.setString(2,query);
                try(var row=statement.executeQuery()) { assertTrue(row.next()); assertEquals(original.equals(query),row.getBoolean(1)); assertFalse(row.next()); }
            }
        }
    }
    @Test
    void everyExactStatementRejectsMissingSubjectAndTenantBeforeConnection() throws Exception {
        var factory=new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        try(var session=factory.openSession()) {
            for(var method:SourceExactMapper.class.getDeclaredMethods()) {
                String id=SourceExactMapper.class.getName()+"."+method.getName();
                assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,() -> TenantScope.requireSubjectParameter(id,Map.of())).code());
                boolean query=java.util.List.class.isAssignableFrom(method.getReturnType());
                var failure=assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,() -> {
                    if(query) session.selectList(id,Map.of()); else session.update(id,Map.of());
                });
                assertEquals(ErrorCode.ACCESS_DENIED,assertInstanceOf(TenantAccessException.class,failure.getCause()).code());
            }
        }
    }
}
