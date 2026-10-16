package com.ruoyi.medical.persistence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceIndexViewBootstrapTest {
    @Test
    void indexViewsHaveAnIndependentClosedSchemaAndExplicitReadCapacity() throws Exception {
        new SourceIndexViewSchema(new PermissionSchema());
        var root=java.nio.file.Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var environment=new org.springframework.core.env.StandardEnvironment();
        for(var source:new org.springframework.boot.env.YamlPropertySourceLoader().load("actual-medical-local",
                new org.springframework.core.io.FileSystemResource(root.resolve("business/ruoyi-admin/src/main/resources/application-medical-local.yml"))))
            environment.getPropertySources().addLast(source);
        var policy=org.springframework.boot.context.properties.bind.Binder.get(environment)
            .bindOrCreate("medical.source-index-views",SourceIndexViewPolicy.class);
        assertEquals(1000,policy.maximumBuilds());
    }
}
