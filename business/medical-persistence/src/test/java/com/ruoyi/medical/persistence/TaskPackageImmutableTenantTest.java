package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskPackageImmutableTenantTest {
    private static final String TENANT="00000000-0000-4000-8000-000000000001";
    private static final String ACTOR="00000000-0000-4000-8000-000000000002";
    private static final List<String> TABLES=List.of("task_package_authoring_intent",
        "task_package_authoring_registration","task_package_authoring_rejection");
    @Test void packageOriginalsCannotBeUpdatedOrDeletedThroughTenantMapper() {
        try(var scope=TenantScope.open(new TenantScope.Identity(TENANT,ACTOR,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))) {
            var interceptor=new ProtectedTenantInterceptor();
            for(String table:TABLES) {
                assertThrows(TenantAccessException.class,()->interceptor.parserSingle("UPDATE "+table+" SET project_id='"+ACTOR+"'",null));
                assertThrows(TenantAccessException.class,()->interceptor.parserSingle("DELETE FROM "+table,null));
            }
        }
    }
    @Test void packageReadsKeepTenantScopeAndCallerCannotSupplyInsertTenant() {
        try(var scope=TenantScope.open(new TenantScope.Identity(TENANT,ACTOR,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))) {
            var interceptor=new ProtectedTenantInterceptor();
            for(String table:TABLES) {
                String sql=interceptor.parserSingle("SELECT project_id FROM "+table+" WHERE created_by='"+ACTOR+"'",null);
                assertTrue(sql.contains("tenant_id = '"+TENANT+"'"),sql);
                assertTrue(sql.contains("created_by = '"+ACTOR+"'"),sql);
                assertThrows(TenantAccessException.class,()->interceptor.parserSingle("INSERT INTO "+table+"(tenant_id,created_by) VALUES ('"+TENANT+"','"+ACTOR+"')",null));
            }
        }
    }
}
