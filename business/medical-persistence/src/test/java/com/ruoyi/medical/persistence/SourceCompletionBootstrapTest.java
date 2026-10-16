package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.SourceCompletionMapper;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.zip.ZipInputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class SourceCompletionBootstrapTest {
    @Test
    void applicationWithoutApprovedCompletionConfigurationDoesNotStartConsumer() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.register(SourceCompletionConfiguration.class,SourceCompletionRegistration.class,SourceCompletionExecution.class);
            context.refresh();
            assertEquals(0,context.getBeanNamesForType(SourceCompletionWorker.class).length);
            assertEquals(0,context.getBeanNamesForType(SourceCompletionRegistration.class).length);
            assertEquals(0,context.getBeanNamesForType(SourceCompletionExecution.class).length);
        }
    }

    @Test
    void completionStatementsRequireCurrentTenantTransaction() {
        for(var method:SourceCompletionMapper.class.getDeclaredMethods())
            assertThrows(TenantAccessException.class,() -> TenantScope.requireSqlAccess(SourceCompletionMapper.class.getName()+"."+method.getName()));
    }

    @Test
    void nativeMyBatisLoadsEveryCompletionStatementAndConstructor() throws Exception {
        var configuration=new Configuration(); String resource="medical-mappers/SourceCompletionMapper.xml";
        try(var input=getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input); new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
        }
        for(var method:SourceCompletionMapper.class.getDeclaredMethods())
            assertTrue(configuration.hasStatement(SourceCompletionMapper.class.getName()+"."+method.getName()));
    }

    @Test
    void currentClosedSchemasInitializeTogetherWithOriginalPublishedRightsSchema() throws Exception {
        var json=new PermissionSchema(); var uploads=new SourceUploadSchema(json); var rights=new SourceRightsSchema();
        assertEquals(64,uploads.schemaSha256().length()); assertEquals(64,uploads.completionResultManifest().length());
        assertNotEquals(rights.manifestSha256(),rights.pendingManifestSha256());
        new SourceCompletionScanResult(); new SourceCompletionObjects();
    }

    @Test
    void actualApprovedRightsDefinitionPreservesPreviouslyDeployedBytes() throws Exception {
        Path path=Path.of(Objects.requireNonNull(System.getProperty("medical.previous.artifact"),"实际原部署制品缺少"));
        try(var jar=new JarFile(path.toFile());
                var module=jar.getInputStream(Objects.requireNonNull(jar.getEntry("BOOT-INF/lib/medical-persistence-3.9.2.jar")));
                var entries=new ZipInputStream(new ByteArrayInputStream(module.readAllBytes()));
                var current=getClass().getClassLoader().getResourceAsStream("source-rights.schema.json")) {
            assertNotNull(current); boolean verified=false;
            for(var entry=entries.getNextEntry();entry!=null;entry=entries.getNextEntry())
                if(entry.getName().equals("source-rights.schema.json")) {
                    assertArrayEquals(current.readAllBytes(),entries.readAllBytes()); verified=true; break;
                }
            assertTrue(verified);
        }
    }
}
