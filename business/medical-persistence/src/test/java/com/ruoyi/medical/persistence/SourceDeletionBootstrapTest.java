package com.ruoyi.medical.persistence;

import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class SourceDeletionBootstrapTest {
    @Test
    void unconfiguredApplicationDoesNotStartPermanentDeletion() throws Exception {
        Class<?> configuration = Class.forName("com.ruoyi.medical.persistence.SourceDeletionConfiguration");
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(configuration);
            context.refresh();
            assertEquals(0, context.getBeanNamesForType(
                    Class.forName("com.ruoyi.medical.persistence.SourceDeletionWorker")).length);
        }
    }

    @Test
    void deletionSqlCannotExecuteWithoutTenantAdmission() throws Exception {
        Class<?> mapper = Class.forName("com.ruoyi.medical.persistence.mapper.SourceDeletionMapper");
        for (var method : mapper.getDeclaredMethods()) {
            assertThrows(TenantAccessException.class,
                    () -> TenantScope.requireSqlAccess(mapper.getName() + "." + method.getName()));
        }
    }

    @Test
    void actualDeletionMapperCanBeLoadedByMyBatis() throws Exception {
        var configuration = new Configuration();
        String resource = "medical-mappers/SourceDeletionMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        for (var method : com.ruoyi.medical.persistence.mapper.SourceDeletionMapper.class.getDeclaredMethods()) {
            assertTrue(configuration.hasStatement("com.ruoyi.medical.persistence.mapper.SourceDeletionMapper." + method.getName()));
        }
    }
}
