package com.ruoyi.medical.persistence;

import java.nio.file.Path;
import java.util.zip.ZipFile;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ByteArrayResource;
import static org.junit.jupiter.api.Assertions.*;

class SourceUploadStatusBootstrapTest {
    @Test
    void currentStatusDefinitionsUseTheOriginalSourceReview() throws Exception {
        new SourceUploadStatusSchema(new PermissionSchema());
    }

    @Test
    void actualLocalReadCapacityIsExplicit() throws Exception {
        var root=Path.of(System.getProperty("medical.projectRoot")).toRealPath(); var environment=new StandardEnvironment();
        for(var source:new YamlPropertySourceLoader().load("actual-medical-local",new FileSystemResource(
                root.resolve("business/ruoyi-admin/src/main/resources/application-medical-local.yml"))))
            environment.getPropertySources().addLast(source);
        var policy=Binder.get(environment).bindOrCreate("medical.source-upload-status",SourceUploadStatusPolicy.class);
        assertEquals(1000,policy.maximumReviews()); policy.requireConfigured();
        var previous=new StandardEnvironment();
        try(var jar=new ZipFile(Path.of(System.getProperty("medical.previous.artifact")).toRealPath().toFile())) {
            byte[] bytes=jar.getInputStream(jar.getEntry("BOOT-INF/classes/application-medical-local.yml")).readAllBytes();
            for(var source:new YamlPropertySourceLoader().load("original-medical-local",new ByteArrayResource(bytes)))
                previous.getPropertySources().addLast(source);
        }
        assertEquals(Binder.get(previous).bindOrCreate("medical.source-uploads",SourceUploadPolicy.class),
            Binder.get(environment).bindOrCreate("medical.source-uploads",SourceUploadPolicy.class));
    }

    @Test
    void originalUploadAndCompletionDefinitionsKeepTheirPublishedBytes() throws Exception {
        var artifact=Path.of(System.getProperty("medical.previous.artifact")).toRealPath();
        var names=new java.util.HashSet<>(java.util.Set.of("source-uploads.schema.json","source-completion-command.schema.json",
            "source-completion-result.schema.json"));
        try(var jar=new ZipFile(artifact.toFile()); var module=new ZipInputStream(new ByteArrayInputStream(
                jar.getInputStream(jar.getEntry("BOOT-INF/lib/medical-persistence-3.9.2.jar")).readAllBytes()))) {
            java.util.zip.ZipEntry entry;
            while((entry=module.getNextEntry())!=null) {
                if(names.contains(entry.getName())) {
                    try(var input=getClass().getResourceAsStream("/"+entry.getName())) {
                        assertNotNull(input); assertArrayEquals(input.readAllBytes(),module.readAllBytes());
                    }
                    names.remove(entry.getName());
                }
            }
        }
        assertTrue(names.isEmpty());
    }
}
