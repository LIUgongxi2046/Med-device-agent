package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import static org.junit.jupiter.api.Assertions.*;

class SourceRightsBootstrapTest {
    @Test
    void actualPublishedDefinitionsInitializeTogether() throws Exception {
        var schema=new SourceRightsDecisionSchema(new PermissionSchema());
        assertEquals(64,schema.manifestSha256().length()); new SourceRightsSchema();
    }

    @Test
    void actualLocalConfigurationKeepsRightsApprovalDisabled() throws Exception {
        var root=Path.of(System.getProperty("medical.projectRoot")).toRealPath(); var environment=new StandardEnvironment();
        for(var source:new YamlPropertySourceLoader().load("actual-medical-local",new FileSystemResource(
                root.resolve("business/ruoyi-admin/src/main/resources/application-medical-local.yml"))))
            environment.getPropertySources().addLast(source);
        var policy=Binder.get(environment).bindOrCreate("medical.source-rights",SourceRightsPolicy.class);
        assertFalse(policy.enabled()); assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,
            assertThrows(TenantAccessException.class,policy::requireConfigured).code());
    }

    @Test
    void actualParserResultCannotDeclareAnApprovalDecision() throws Exception {
        var result=Path.of(System.getProperty("medical.processing.result")).toRealPath();
        var schema=new SourceRightsDecisionSchema(new PermissionSchema()); byte[] bytes=Files.readAllBytes(result);
        assertEquals(ErrorCode.SCHEMA_INVALID,assertThrows(SchemaRejection.class,() -> schema.decision(bytes)).code());
        assertEquals(ErrorCode.SCHEMA_INVALID,assertThrows(SchemaRejection.class,() -> schema.preview(bytes)).code());
    }
}
