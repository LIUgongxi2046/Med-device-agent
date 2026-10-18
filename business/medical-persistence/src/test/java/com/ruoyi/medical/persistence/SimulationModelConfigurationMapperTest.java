package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** Generates SQL through actual mapper and tenant interceptor; no database connection is made. */
class SimulationModelConfigurationMapperTest {
    @Test void simulationSqlRetainsTenantModeAndExplicitNullExternalFields() throws Exception {
        var configuration=new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        String prefix="com.ruoyi.medical.persistence.mapper.ModelConfigurationMapper.",id=SimulationModelConfigurationFixtures.CASE;
        var values=new HashMap<String,Object>();values.put("userId",id);values.put("mode","SIMULATION");values.put("definition",SimulationModelConfigurationFixtures.definition());
        values.put("configId",id);values.put("versionId",id);values.put("projectId",null);values.put("simulationDefinition","{}");
        try(var scope=TenantScope.open(new TenantScope.Identity(id,id,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
                for(String method:List.of("head","stored","insertHead","insertModel","initializeHead","advanceHead")) {
                    TenantScope.requireSqlAccess(prefix+method);TenantScope.requireSubjectParameter(prefix+method,values);
                    var bound=configuration.getMappedStatement(prefix+method).getBoundSql(values);String sql=bound.getSql();
                    String scoped=new ProtectedTenantInterceptor().parserSingle(sql,null);assertTrue(scoped.contains("tenant_id"));
                    assertDoesNotThrow(()->net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(scoped));
                    var parameters=bound.getParameterMappings().stream().map(org.apache.ibatis.mapping.ParameterMapping::getProperty).toList();
                    assertTrue(parameters.contains("mode"),method);
                    if(method.equals("insertModel")) {
                        assertTrue(sql.contains("'SIMULATION',NULL,'LOCAL'"));assertTrue(parameters.contains("simulationDefinition"));
                        for(String forbidden:List.of("definition.auth_ref","definition.credential_reference_id","definition.credential_revision","definition.price_basis.version_id","dataPolicy"))assertFalse(parameters.contains(forbidden),forbidden);
                        assertFalse(sql.contains("connection_verification"));assertFalse(sql.contains("APPROVED"));
                    }
                    if(method.equals("stored")) {
                        assertTrue(sql.contains("CASE WHEN m.execution_mode='SIMULATION'"));assertTrue(sql.contains("m.simulation_definition"));
                        assertTrue(sql.contains("m.execution_mode=?"));
                    }
                }
                scope.finishTransaction();
            } finally {TransactionSynchronizationManager.setActualTransactionActive(false);}
        }
    }
}
