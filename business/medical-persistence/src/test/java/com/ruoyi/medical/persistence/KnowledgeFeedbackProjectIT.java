package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeFeedbackProjectIT {
    @Test void readsClassificationFromActualProjectRecord() throws Exception {
        var root=Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var json=JsonMapper.builder().build();
        var secrets=json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var workspace=json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/acceptance-workspace-seed.json")));
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC","medical_runtime",secrets.required("database_password").textValue());
        var data=new JdbcTemplate(source);
        String tenant=workspace.required("tenant_id").textValue(),project=workspace.required("project_id").textValue();
        var classification=KnowledgeFeedbackProofs.projectMetadata(data,tenant,project).get("classification");
        assertTrue(List.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH").contains(classification));
        String otherTenant=data.queryForObject("SELECT tenant_id FROM tenant WHERE tenant_id<>? ORDER BY tenant_id LIMIT 1",String.class,tenant);
        assertThrows(TenantAccessException.class,()->KnowledgeFeedbackProofs.projectMetadata(data,otherTenant,project));
    }
    @Test void classificationBindingPinsActualRuleAndContent() throws Exception {
        var root=Path.of(System.getProperty("medical.projectRoot")).toRealPath();var json=JsonMapper.builder().build();
        var secrets=json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var workflow=json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/acceptance-template-workflow.json")));
        var assets=json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/acceptance-template-assets.json")));
        var data=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC","medical_runtime",secrets.required("database_password").textValue()));
        String tenant=workflow.required("tenant_id").textValue(),rule=workflow.required("rule_version").textValue(),hash=workflow.required("rule_sha256").textValue();
        String metadata=data.queryForObject("SELECT classification FROM rule_version WHERE tenant_id=? AND rule_version=? AND content_sha256=?",String.class,tenant,rule,hash);
        assertEquals("LOCAL_AUTH_METADATA",metadata);
        var binding=new BusinessOperationsClassificationPolicy.Binding(tenant,"RULE",rule,hash,metadata,"INTERNAL","用户授权的公开素材编号字段规则，作为内部测试资料管理");
        var policy=new BusinessOperationsClassificationPolicy(List.of(binding));
        assertEquals("INTERNAL",policy.resolve(tenant,"RULE",rule,hash,metadata));
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,()->new BusinessOperationsClassificationPolicy(List.of()).resolve(tenant,"RULE",rule,hash,metadata)).code());
        String otherHash=data.queryForObject("SELECT sha256 FROM artifact WHERE tenant_id=? AND artifact_id=?",String.class,tenant,assets.required("assets").get(0).required("artifact_id").textValue());
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->policy.resolve(tenant,"RULE",rule,otherHash,metadata)).code());
    }
}
