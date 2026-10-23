package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.persistence.mapper.ConfigurationDiscoveryMapper;
import com.ruoyi.medical.persistence.mapper.TenantWatermarkMapper;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationDirectoryUnicodeIT {
    @Test void unicodeSearchReadsActualDirectoriesWithoutAsciiConversion()throws Exception{
        var root=Path.of(System.getProperty("medical.projectRoot")).toRealPath();var json=JsonMapper.builder().build();
        var secrets=json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));var workspace=json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/acceptance-workspace-seed.json")));
        var receipt=json.readTree(Files.readAllBytes(root.resolve(".implementation-work/template-profile-controls-checks.json")));
        var ruleReceipt=json.readTree(Files.readAllBytes(root.resolve(".implementation-work/rule-business-ui-checks.json")));
        String rule=null;
        for(var check:ruleReceipt.required("checks"))if(check.required("id").asText().equals("PR02-UI-APPROVAL-READ"))rule=check.required("rule_version").asText();
        assertNotNull(rule);final String ruleId=rule;
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC","medical_runtime",secrets.required("database_password").asText());var db=new JdbcTemplate(source);
        var current=LocalWorkspaceSession.owner(root);var context=current.required("current_context");String tenant=workspace.required("tenant_id").asText(),actor=current.required("user").required("user_id").asText(),template=receipt.required("template_version").asText();assertEquals(tenant,context.required("tenant_id").asText());
        String reason=db.queryForObject("SELECT reason FROM template_definition WHERE tenant_id=? AND template_version=?",String.class,tenant,template);assertNotNull(reason);assertTrue(reason.codePoints().anyMatch(value->value>127));
        var business=json.readTree(db.queryForObject("SELECT definition_json FROM rule_business_definition WHERE tenant_id=? AND rule_version=?",String.class,tenant,ruleId));
        String status=db.queryForObject("SELECT status FROM rule_version WHERE tenant_id=? AND rule_version=?",String.class,tenant,ruleId);
        var directory=new RuleBusinessDirectorySchema(new PermissionSchema());
        var filters=directory.query(java.util.Map.of("limit",new String[]{"1"},"q",new String[]{business.required("name").asText()},"dimension",new String[]{business.required("dimension").asText()},"handling",new String[]{business.required("handling").asText()},"status",new String[]{status}));
        assertEquals(status,directory.filters(filters).required("status").asText());
        for(String key:java.util.List.of("dimension","handling","status","unexpected_parameter"))assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->directory.query(java.util.Map.of(key,new String[]{"INVALID"})));
        var factory=new MedicalPersistenceConfiguration().medicalSqlSessionFactory(source);var session=new SqlSessionTemplate(factory);var mapper=session.getMapper(ConfigurationDiscoveryMapper.class);var query=new HashMap<String,Object>();query.put("userId",actor);query.put("mode","EXTERNAL");
        try(var scope=TenantScope.open(new TenantScope.Identity(tenant,actor,new BigInteger(context.required("context_revision").asText()),new BigInteger(context.required("auth_epoch").asText()),new BigInteger(context.required("governance_epoch").asText())))){
            new TenantTransaction(new DataSourceTransactionManager(source),session.getMapper(TenantWatermarkMapper.class)).execute(watermarks->{
                query.put("q",reason);assertTrue(mapper.templates(query).stream().anyMatch(value->value.id().equals(template)));
                query.put("q","未登记的中文目录搜索验证");assertTrue(mapper.templates(query).isEmpty());assertTrue(mapper.models(query).isEmpty());assertTrue(mapper.rules(query).isEmpty());
                query.put("q",filters.q());query.put("dimension",filters.dimension());query.put("handling",filters.handling());query.put("status",filters.status());
                assertTrue(mapper.rules(query).stream().anyMatch(value->value.id().equals(ruleId)));
                query.put("status",status.equals("DISABLED")?"DRAFT":"DISABLED");assertTrue(mapper.rules(query).stream().noneMatch(value->value.id().equals(ruleId)));
                query.put("status",status);query.put("dimension",filters.dimension().equals("RULE_COMPLIANCE")?"HUMAN_MODIFICATION":"RULE_COMPLIANCE");assertTrue(mapper.rules(query).stream().noneMatch(value->value.id().equals(ruleId)));
                query.put("dimension",filters.dimension());query.put("handling",filters.handling().equals("NOTICE")?"BLOCK_SIGNING":"NOTICE");assertTrue(mapper.rules(query).stream().noneMatch(value->value.id().equals(ruleId)));
                return null;
            });
        }
    }
}
