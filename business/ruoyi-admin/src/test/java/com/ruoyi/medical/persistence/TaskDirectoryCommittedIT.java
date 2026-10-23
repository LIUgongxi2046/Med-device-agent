package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.persistence.mapper.TaskMapper;
import com.ruoyi.medical.persistence.mapper.TenantWatermarkMapper;
import java.math.BigInteger;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class TaskDirectoryCommittedIT {
    @Test void actualCreationDraftsDoNotEnterCommittedTaskDirectory()throws Exception {
        var root=Path.of(System.getProperty("medical.projectRoot")).toRealPath();var json=JsonMapper.builder().build();
        var secrets=json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var workspace=json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/acceptance-workspace-seed.json")));
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC","medical_runtime",secrets.required("database_password").asText());
        var db=new JdbcTemplate(source);String tenant=workspace.required("tenant_id").asText(),project=workspace.required("project_id").asText();
        var credentials=json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/owner-credentials.json")));
        var http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build();String origin="http://127.0.0.1:18083";
        var csrfReply=http.send(HttpRequest.newBuilder(URI.create(origin+"/auth/csrf")).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,csrfReply.statusCode());var csrf=json.readTree(csrfReply.body());
        String form="username="+URLEncoder.encode(credentials.required("username").asText(),StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(credentials.required("password").asText(),StandardCharsets.UTF_8)+"&_csrf="+URLEncoder.encode(csrf.required("token").asText(),StandardCharsets.UTF_8);
        var login=http.send(HttpRequest.newBuilder(URI.create(origin+"/auth/login")).timeout(Duration.ofSeconds(60)).header("Origin",origin).header("Content-Type","application/x-www-form-urlencoded").header(csrf.required("header_name").asText(),csrf.required("token").asText()).POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.discarding());assertEquals(302,login.statusCode());
        var sessionReply=http.send(HttpRequest.newBuilder(URI.create(origin+"/api/session")).timeout(Duration.ofSeconds(60)).header("X-Schema-Version","1.0.0").GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,sessionReply.statusCode());var current=json.readTree(sessionReply.body()).required("data");var context=current.required("current_context");String actor=current.required("user").required("user_id").asText();assertEquals(tenant,context.required("tenant_id").asText());
        var drafts=db.queryForList("SELECT task_id FROM task_head WHERE tenant_id=? AND project_id=? AND current_revision IS NULL",String.class,tenant,project);assertFalse(drafts.isEmpty());
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM permission_grant WHERE tenant_id=? AND subject_id=? AND action='task.read' AND resource_kind='ENTERPRISE' AND resource_id=? AND status='ACTIVE'",Integer.class,tenant,actor,tenant)>0);
        var factory=new MedicalPersistenceConfiguration().medicalSqlSessionFactory(source);var session=new SqlSessionTemplate(factory);var tasks=session.getMapper(TaskMapper.class);
        var values=new HashMap<String,Object>();values.put("userId",actor);values.put("projectId",project);values.put("allResources",true);values.put("resourceIds",List.of());
        try(var scope=TenantScope.open(new TenantScope.Identity(tenant,actor,new BigInteger(context.required("context_revision").asText()),new BigInteger(context.required("auth_epoch").asText()),new BigInteger(context.required("governance_epoch").asText())))){
            var transaction=new TenantTransaction(new DataSourceTransactionManager(source),session.getMapper(TenantWatermarkMapper.class));
            transaction.execute(watermarks->{
                var ids=tasks.listed(values);assertTrue(ids.contains(workspace.required("task_id").asText()));assertTrue(ids.stream().noneMatch(drafts::contains));
                assertEquals(BigInteger.valueOf(ids.size()),tasks.visibleCount(values));
                var page=com.github.pagehelper.PageHelper.startPage(1,100).<String>doSelectPageInfo(()->tasks.listed(values));
                assertEquals(ids,page.getList());assertEquals(ids.size(),page.getTotal());assertNull(com.github.pagehelper.PageHelper.getLocalPage());return null;
            });
        }
    }
}
