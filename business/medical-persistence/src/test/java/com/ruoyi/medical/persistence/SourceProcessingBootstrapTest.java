package com.ruoyi.medical.persistence;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class SourceProcessingBootstrapTest {
    @Test
    void applicationWithoutProcessingConfigurationDoesNotStartWorker() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(Class.forName("com.ruoyi.medical.persistence.SourceProcessingConfiguration"));
            context.refresh();
            assertEquals(0, context.getBeanNamesForType(Class.forName("com.ruoyi.medical.persistence.SourceProcessingWorker")).length);
        }
    }

    @Test
    void processingStatementsRequireCurrentTenantTransaction() throws Exception {
        for (var method : Class.forName("com.ruoyi.medical.persistence.mapper.SourceProcessingMapper").getDeclaredMethods())
            assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(
                "com.ruoyi.medical.persistence.mapper.SourceProcessingMapper." + method.getName()));
    }

    @Test
    void nativeMyBatisLoadsAllProcessingMethods() throws Exception {
        var configuration = new Configuration();
        String resource = "medical-mappers/SourceProcessingMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        for (var method : Class.forName("com.ruoyi.medical.persistence.mapper.SourceProcessingMapper").getDeclaredMethods())
            assertTrue(configuration.hasStatement("com.ruoyi.medical.persistence.mapper.SourceProcessingMapper." + method.getName()));
    }
}
