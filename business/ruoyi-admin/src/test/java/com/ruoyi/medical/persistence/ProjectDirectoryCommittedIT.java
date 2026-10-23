package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.persistence.mapper.ProjectMapper;
import com.ruoyi.medical.persistence.mapper.TenantWatermarkMapper;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.function.ThrowingSupplier;
import static org.junit.jupiter.api.Assertions.*;

class ProjectDirectoryCommittedIT {
    @Test void actualProjectTotalRemainsTheCompleteAuthorizedFilterAcrossPages() throws Exception {
        var root=Path.of(System.getProperty("medical.projectRoot")).toRealPath();var json=JsonMapper.builder().build();
        var secrets=json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var current=LocalWorkspaceSession.owner(root);var context=current.required("current_context");String tenant=context.required("tenant_id").asText(),actor=current.required("user").required("user_id").asText();
        var data=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC","medical_runtime",secrets.required("database_password").asText());
        var authority=new PermissionAuthority(new PermissionSchema());
        var directory=new ProjectWorkspaceDirectorySchema(new PermissionSchema());
        var session=new SqlSessionTemplate(new MedicalPersistenceConfiguration().medicalSqlSessionFactory(data));var mapper=session.getMapper(ProjectMapper.class);
        var values=new HashMap<String,Object>();values.put("userId",actor);
        try(var scope=TenantScope.open(new TenantScope.Identity(tenant,actor,new BigInteger(context.required("context_revision").asText()),new BigInteger(context.required("auth_epoch").asText()),new BigInteger(context.required("governance_epoch").asText())))) {
            new TenantTransaction(new DataSourceTransactionManager(data),session.getMapper(TenantWatermarkMapper.class)).execute(watermarks->ThrowingSupplier.of(()->{
                var permitted=authority.projectAccess(tenant,"local","project.read","PROJECT_READING",mapper.grants(values));assertTrue(permitted.available());values.put("enterprise",permitted.enterprise());values.put("allowedProjects",permitted.projects());
                var all=mapper.projects(values);assertFalse(all.isEmpty());var total=mapper.projectCount(values);assertEquals(BigInteger.valueOf(all.size()),total);
                for(String after:all){values.put("after",after);assertEquals(total,mapper.projectCount(values));assertEquals(all.stream().filter(value->value.compareTo(after)>0).toList(),mapper.projects(values));}
                values.put("after",null);values.put("projectId",all.get(0));var actual=mapper.project(values).get(0);
                var query=directory.query(Map.of("limit",new String[]{"1"},"q",new String[]{actual.name()},"status",new String[]{actual.status()}));assertEquals(actual.name(),directory.filters(query).required("q").asText());
                for(var invalid:List.of(Map.of("limit",new String[]{"0"}),Map.of("unexpected_parameter",new String[]{"INVALID"}),Map.of("status",new String[]{"INVALID"})))assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->directory.query(invalid));
                values.put("keyword",actual.name());assertTrue(mapper.projects(values).contains(actual.projectId()));
                values.put("keyword","未登记的产品目录搜索验证");assertEquals(BigInteger.ZERO,mapper.projectCount(values));assertTrue(mapper.projects(values).isEmpty());
                values.put("keyword",null);values.put("name","未登记的项目目录搜索验证");assertEquals(BigInteger.ZERO,mapper.projectCount(values));assertTrue(mapper.projects(values).isEmpty());return null;
            }).get());
        }
    }
}
