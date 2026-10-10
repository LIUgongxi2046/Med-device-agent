package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.system.domain.SysPost;
import com.ruoyi.system.domain.SysNotice;
import com.ruoyi.system.domain.SysOperLog;
import com.ruoyi.system.domain.SysLogininfor;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.entity.SysDictType;
import com.ruoyi.system.service.ISysPostService;
import com.ruoyi.system.service.ISysDictDataService;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysNoticeService;
import com.ruoyi.system.service.ISysNoticeReadService;
import com.ruoyi.system.service.ISysOperLogService;
import com.ruoyi.system.service.ISysLogininforService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;
import static org.junit.jupiter.api.Assertions.*;

public final class RuoYiManagementServicesTest {
    @Test void originalSystemServicesAreAvailableInTheActualMedicalApplication() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")); var json = JsonMapper.builder().build();
        var secrets = json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var identity = json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-identity.json")));
        var management = json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/local-management-secret.json")));
        var invitation = json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/invitation-response-secret.json")));
        var frameworkToken = json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/ruoyi-token-secret.json")));
        var application = assertInstanceOf(ServletWebServerApplicationContext.class,
            new SpringApplicationBuilder(MedicalApplication.class)
                .properties(Map.of("MEDICAL_LOCAL_DATABASE_PASSWORD", secrets.required("database_password").textValue(),
                    "MEDICAL_LOCAL_REDIS_PASSWORD", secrets.required("redis_password").textValue(),
                    "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY", management.required("key").textValue(),
                    "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY_ID", management.required("key_id").textValue(),
                    "MEDICAL_INVITATION_RESPONSE_KEY", invitation.required("key").textValue(),
                    "MEDICAL_INVITATION_RESPONSE_KEY_ID", invitation.required("key_id").textValue(),
                    "MEDICAL_RUOYI_TOKEN_SECRET", frameworkToken.required("key").textValue()))
                .run("--spring.config.name=application-medical-local", "--server.address=127.0.0.1", "--server.port=0",
                    "--server.shutdown=immediate", "--spring.main.banner-mode=off"));
        var proof = json.createObjectNode(); proof.put("scope", "ACTUAL_ORIGINAL_SYSTEM_SERVICES");
        proof.put("max_heap_bytes", Runtime.getRuntime().maxMemory());
        try (application) {
            // 原Service注解决定完整检查集合，检查应用不增加替代Service或客户样本。
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
            var implementations = scanner.findCandidateComponents("com.ruoyi.system.service.impl").stream()
                .map(definition -> ClassUtils.resolveClassName(definition.getBeanClassName(), application.getClassLoader())).toList();
            assertEquals(13, implementations.size());
            assertAll(implementations.stream().map(type -> (Executable) () ->
                assertEquals(1, application.getBeanNamesForType(type).length, type.getName() + "必须注册原Service")));
            var components = json.createArrayNode();
            for (var implementation : implementations) {
                assertEquals(implementation, AopUtils.getTargetClass(application.getBean(implementation)));
                components.add(implementation.getName());
            }
            var jdbc = new JdbcTemplate(application.getBean("masterDataSource", javax.sql.DataSource.class));
            assertEquals(identity.required("server_uuid").textValue(), jdbc.queryForObject("SELECT @@server_uuid", String.class));
            var before = counts(jdbc, json); var results = json.createObjectNode();
            results.put("sys_post", application.getBean(ISysPostService.class).selectPostList(new SysPost()).size());
            results.put("sys_dict_type", application.getBean(ISysDictTypeService.class).selectDictTypeList(new SysDictType()).size());
            results.put("sys_dict_data", application.getBean(ISysDictDataService.class).selectDictDataList(new SysDictData()).size());
            results.put("sys_notice", application.getBean(ISysNoticeService.class).selectNoticeList(new SysNotice()).size());
            results.put("sys_oper_log", application.getBean(ISysOperLogService.class).selectOperLogList(new SysOperLog()).size());
            results.put("sys_logininfor", application.getBean(ISysLogininforService.class).selectLogininforList(new SysLogininfor()).size());
            for (var entry : results.properties()) assertEquals(before.required(entry.getKey()).longValue(), entry.getValue().longValue());
            var users = jdbc.queryForList("SELECT user_id FROM sys_user WHERE del_flag='0' ORDER BY user_id", Long.class);
            assertFalse(users.isEmpty());
            for (Long user : users) assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM sys_notice n WHERE n.status='0' AND NOT EXISTS "
                + "(SELECT 1 FROM sys_notice_read r WHERE r.notice_id=n.notice_id AND r.user_id=?)", Integer.class, user),
                application.getBean(ISysNoticeReadService.class).selectUnreadCount(user));
            String origin = "http://127.0.0.1:" + application.getWebServer().getPort();
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(origin + "/auth/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode()); assertTrue(response.body().contains("</html>"));
            assertEquals(before, counts(jdbc, json));
            proof.set("original_service_classes", components); proof.set("original_query_counts", results);
            proof.set("row_counts", before); proof.put("actual_notice_user_count", users.size());
            proof.put("login_http_status", response.statusCode()); proof.put("actual_http_port", application.getWebServer().getPort());
        }
        assertFalse(application.isActive()); proof.put("temporary_application_closed", true);
        proof.put("customer_records_created", false); proof.put("customer_success_verified", false);
        Files.write(Path.of(System.getProperty("medical.managementServicesEvidence")), json.writeValueAsBytes(proof));
    }

    private static com.fasterxml.jackson.databind.JsonNode counts(JdbcTemplate jdbc, JsonMapper json) {
        var result = json.createObjectNode();
        for (String table : List.of("sys_user", "sys_role", "sys_menu", "sys_post", "sys_dict_type", "sys_dict_data", "sys_notice",
                "sys_notice_read", "sys_oper_log", "sys_logininfor", "tenant", "tenant_member", "project", "task_head", "operation_record"))
            result.put(table, Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)));
        return result;
    }
}
