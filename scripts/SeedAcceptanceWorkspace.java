package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.runtime.MedicalApplication;
import com.ruoyi.medical.persistence.*;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class SeedAcceptanceWorkspace {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static ObjectNode ledger = JSON.createObjectNode().put("data_kind", "SIMULATION").put("scope", "LOCAL_ACCEPTANCE_WORKSPACE");
    static Path receipt;
    static HttpClient http;
    static String origin;
    static JsonNode session;

    static String id() { return UUID.randomUUID().toString(); }
    static void save() throws Exception {
        Files.writeString(receipt, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(ledger));
        Files.setPosixFilePermissions(receipt, PosixFilePermissions.fromString("rw-------"));
    }
    static JsonNode read(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(origin + path)).header("X-Schema-Version", "1.0.0");
        if (session != null && !session.required("current_context").isNull()) request.header("X-Context-Revision", session.required("current_context").required("context_revision").asText());
        var response = http.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IllegalStateException("ACCEPTANCE_READ_FAILED " + path + " " + response.statusCode() + " " + new String(response.body(), StandardCharsets.UTF_8));
        return JSON.readTree(response.body());
    }
    static JsonNode post(String path, JsonNode body, int expected) throws Exception {
        var csrf = read("/auth/csrf");
        var request = HttpRequest.newBuilder(URI.create(origin + path)).header("Origin", "http://127.0.0.1:18080")
            .header("X-Schema-Version", "1.0.0").header(csrf.required("header_name").asText(), csrf.required("token").asText())
            .header("Idempotency-Key", id()).header("Content-Type", "application/json");
        request.header("X-Context-Revision", session.required("current_context").isNull() ? "0" : session.required("current_context").required("context_revision").asText());
        var response = http.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != expected) throw new IllegalStateException("ACCEPTANCE_WRITE_FAILED " + path + " " + response.statusCode() + " " + new String(response.body(), StandardCharsets.UTF_8));
        var result = JSON.readTree(response.body()).required("data"); session = read("/api/session").required("data"); return result;
    }
    static void select(String tenant) throws Exception {
        if (tenant.equals(session.path("current_context").path("tenant_id").asText())) return;
        post("/api/session/tenant-selections", JSON.createObjectNode().put("tenant_id", tenant)
            .put("expected_context_revision", session.required("current_context").isNull() ? "0" : session.required("current_context").required("context_revision").asText()), 200);
    }
    static void grant(JdbcTemplate db, PermissionSchema json, String tenant, String actor, String subject, String basis, String action) throws Exception {
        var condition = new PermissionProtocol.Condition("1.0.0", List.of("local"), null,
            new PermissionProtocol.Delegation(List.of(), List.of(), List.of(), List.of(), List.of()));
        String encoded = json.canonicalObject(condition); json.condition(encoded);
        db.update("""
            INSERT INTO permission_grant(tenant_id,grant_id,subject_id,resource_kind,resource_id,action,`condition`,status,valid_from,basis_version,
                schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at)
            VALUES(?,?,?,'ENTERPRISE',?,?,?,'ACTIVE',UTC_TIMESTAMP(6),?,'1.0.0',?,?,UTC_TIMESTAMP(6),'SIMULATION',1,UTC_TIMESTAMP(6))
            """, tenant, id(), subject, tenant, action, encoded, basis, json.manifestSha256(), actor);
    }
    static void configurationInWorkspace(JdbcTemplate db, PermissionSchema json, String actor) throws Exception {
        String tenant = ledger.required("tenant_id").asText(), originalTenant = ledger.required("configuration_tenant_id").asText();
        String originalArtifact = ledger.required("rule_input").required("content").required("basis_manifest").get(0).required("version_id").asText();
        String artifact = id(), accessSet = id();
        var model = (ObjectNode) ledger.required("model_input").deepCopy();
        var rule = (ObjectNode) ledger.required("rule_input").deepCopy();
        var definition = (ObjectNode) model.required("definition");
        var price = (ObjectNode) definition.required("price_basis");
        var policy = (ObjectNode) definition.required("data_policy").required("policy_basis");
        String priceId = id(), policyId = id();
        var manifest = new com.ruoyi.medical.persistence.ArtifactRegistryProtocol.DependencyManifest("1.0.0", tenant, null, accessSet, List.of());
        String manifestJson = json.canonicalObject(manifest), manifestHash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(manifestJson);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(Objects.requireNonNull(db.getDataSource())));
        transaction.executeWithoutResult(status -> {
            for (var pair : List.of(Map.entry(originalArtifact, artifact), Map.entry(price.required("version_id").asText(), priceId), Map.entry(policy.required("version_id").asText(), policyId))) {
                if (db.update("INSERT INTO version_registry(tenant_id,version_id,entity_kind,content_sha256) SELECT ?,?,entity_kind,content_sha256 FROM version_registry WHERE tenant_id=? AND version_id=?", tenant, pair.getValue(), originalTenant, pair.getKey()) != 1) throw new IllegalStateException("ACCEPTANCE_BASIS_REQUIRED");
            }
            db.update("INSERT INTO version_registry(tenant_id,version_id,entity_kind,content_sha256) VALUES(?,?,'DEPENDENCY_ACCESS_SET',?)", tenant, accessSet, manifestHash);
            db.update("INSERT INTO dependency_access_set(tenant_id,access_set_id,manifest_sha256,content_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6),'SIMULATION')", tenant, accessSet, manifestHash, manifestHash, actor);
            db.update("INSERT INTO dependency_access_manifest(tenant_id,access_set_id,manifest_json,manifest_sha256,content_sha256,member_count,schema_version,schema_manifest_sha256,created_by,created_at,classification) SELECT ?,?,?,?,?,0,'1.0.0',m.schema_manifest_sha256,?,UTC_TIMESTAMP(6),'SIMULATION' FROM dependency_access_manifest m JOIN artifact a ON a.tenant_id=m.tenant_id AND a.dependency_access_set_id=m.access_set_id WHERE a.tenant_id=? AND a.artifact_id=?", tenant, accessSet, manifestJson, manifestHash, manifestHash, actor, originalTenant, originalArtifact);
            db.update("INSERT INTO artifact(tenant_id,artifact_id,artifact_kind,object_ref,object_key,object_version,sha256,size_bytes,media_type,dependency_access_set_id,content_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) SELECT ?,?,artifact_kind,object_ref,object_key,object_version,sha256,size_bytes,media_type,?,content_sha256,schema_version,schema_manifest_sha256,?,UTC_TIMESTAMP(6),'SIMULATION' FROM artifact WHERE tenant_id=? AND artifact_id=?", tenant, artifact, accessSet, actor, originalTenant, originalArtifact);
            db.update("INSERT INTO artifact_content_binding(tenant_id,artifact_id,resource_kind,resource_id,schema_ref,schema_document_sha256,dependency_access_set_id,dependency_manifest_sha256,created_by,created_at,classification) SELECT ?,?,'ARTIFACT',?,schema_ref,schema_document_sha256,?,?,?,UTC_TIMESTAMP(6),'SIMULATION' FROM artifact_content_binding WHERE tenant_id=? AND artifact_id=?", tenant, artifact, artifact, accessSet, manifestHash, actor, originalTenant, originalArtifact);
        });
        price.put("version_id", priceId); policy.put("version_id", policyId);
        ((ObjectNode) rule.required("content").required("basis_manifest").get(0)).put("version_id", artifact);
        ledger.set("workspace_model_input", model); ledger.set("workspace_rule_input", rule); save();
    }
    static void taskInputInWorkspace(JdbcTemplate db, PermissionSchema json, TaskInputSchema inputs, String actor) throws Exception {
        String tenant = ledger.required("tenant_id").asText(), task = ledger.required("task_id").asText();
        var row = db.queryForMap("SELECT r.* FROM task_head h JOIN task_revision r ON r.tenant_id=h.tenant_id AND r.task_revision=h.current_revision WHERE h.tenant_id=? AND h.task_id=?", tenant, task);
        String revision = (String) row.get("task_revision");
        db.update("UPDATE plan_content_revision SET schema_manifest_sha256=? WHERE tenant_id=? AND task_id=? AND plan_revision=? AND reason='SIMULATION 开发计划'", inputs.manifestSha256(), tenant, task, row.get("plan_revision"));
        if (db.queryForObject("SELECT COUNT(*) FROM task_input_revision WHERE tenant_id=? AND task_revision=?", Integer.class, tenant, revision) != 0) return;
        var request = (ObjectNode) inputs.read(db.queryForObject("SELECT request_json FROM task_input_revision WHERE tenant_id=? AND task_revision=?", String.class, tenant, row.get("parent_revision")));
        var snapshot = inputs.read(db.queryForObject("SELECT manifest FROM scope_snapshot WHERE tenant_id=? AND snapshot_id=?", String.class, tenant, row.get("snapshot_id")));
        request.put("template_version", (String) row.get("template_version")); request.set("scope_selection", snapshot.required("scope_selection"));
        var validated = inputs.create(json.canonical(request).getBytes(StandardCharsets.UTF_8));
        String operation = id(), missing = "[]", missingHash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(missing);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(Objects.requireNonNull(db.getDataSource())));
        transaction.executeWithoutResult(status -> {
            db.update("INSERT INTO operation_record(tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,http_status,target_id,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,'SIMULATION_TASK_INPUT',?,?,'COMPLETED',201,?,'1.0.0',?,?,UTC_TIMESTAMP(6),'SIMULATION',1,UTC_TIMESTAMP(6))", tenant, operation, actor, operation, validated.request_sha256(), task, json.manifestSha256(), actor);
            if (db.update("INSERT INTO task_input_revision(tenant_id,project_id,task_id,task_revision,operation_id,document_type,owner_id,goal,request_json,request_sha256,scope_json,scope_sha256,authority_grant,authority_revision,auth_epoch,governance_epoch,schema_version,schema_manifest_sha256,missing_requirements,requirements_sha256,created_by,created_at,classification) SELECT tenant_id,project_id,task_id,?,?,document_type,owner_id,goal,?,?,?,?,authority_grant,authority_revision,auth_epoch,governance_epoch,schema_version,schema_manifest_sha256,?,?,?,UTC_TIMESTAMP(6),'SIMULATION' FROM task_input_revision WHERE tenant_id=? AND task_revision=?", revision, operation, validated.canonical_request(), validated.request_sha256(), snapshot.required("scope_selection").toString(), validated.scope_sha256(), missing, missingHash, actor, tenant, row.get("parent_revision")) != 1) throw new IllegalStateException("ACCEPTANCE_TASK_INPUT_REQUIRED");
        });
        ledger.put("task_input_revision", revision); save();
    }
    public static void main(String[] args) throws Exception {
        var root = Path.of(args[0]); receipt = root.resolve(".implementation-work/runtime/acceptance-workspace-seed.json");
        if (Files.exists(receipt)) {
            ledger = (ObjectNode) JSON.readTree(Files.readAllBytes(receipt));
            if (!"SIMULATION".equals(ledger.required("data_kind").asText()) || !List.of("CREATING", "CREATED").contains(ledger.required("state").asText()))
                throw new IllegalStateException("ACCEPTANCE_WORKSPACE_NOT_RESUMABLE");
        }
        var owner = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/owner-credentials.json")));
        var member = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/acceptance-framework-data.json"))).required("member_user_id").asText();
        var application = (ServletWebServerApplicationContext) new SpringApplicationBuilder(MedicalApplication.class).run(
            "--spring.config.name=application-medical-local", "--server.address=127.0.0.1", "--server.port=0", "--server.shutdown=immediate",
            "--spring.main.banner-mode=off", "--spring.session.redis.namespace=medical:acceptance-seed:" + id(),
            "--medical.templates.environment=LOCAL", "--medical.templates.region=LOCAL", "--medical.templates.maximum-versions=100", "--medical.templates.maximum-relations=200",
            "--medical.templates.artifact-profiles[0].role=DEPENDENCY", "--medical.templates.artifact-profiles[0].resource-kind=ARTIFACT",
            "--medical.templates.artifact-profiles[0].action=artifact.read", "--medical.templates.artifact-profiles[0].schema-ref=" + ConfigurationWorkflowSimulationData.textSchemaRef(),
            "--medical.templates.artifact-profiles[0].schema-document-sha256=" + ConfigurationWorkflowSimulationData.interfacesHash(),
            "--medical.templates.artifact-profiles[0].artifact-schema-manifest=" + ConfigurationWorkflowSimulationData.interfacesHash());
        try (application) {
            var source = application.getBean("masterDataSource", javax.sql.DataSource.class); var db = new JdbcTemplate(source);
            String actor = db.queryForObject("SELECT user_id FROM platform_local_account WHERE username=? AND platform_role='PLATFORM_ADMIN' AND status='ACTIVE'", String.class, owner.required("username").asText());
            var permission = application.getBean(PermissionSchema.class); var inputs = application.getBean(TaskInputSchema.class);
            if (!ledger.has("member_user_id")) {
            var tasks = new SimulationTaskData(source, root, actor, permission, inputs);
            ledger.put("state", "CREATING").put("tenant_id", tasks.tenant).put("other_tenant_id", tasks.otherTenant).put("project_id", tasks.project).put("task_id", tasks.first); save();
            tasks.enableProjectAdministration(); tasks.enableSourceUploading(); tasks.enableTaskCreation(); tasks.enableRoleAdministration();
            String basis = tasks.projectBasis().required("version_id").asText();
            db.update("UPDATE project_record SET name='验收测试 · 注册资料示例项目',creation_reason='SIMULATION 本地功能验收' WHERE tenant_id=? AND project_id=?", tasks.tenant, tasks.project);
            var documents = new SimulationDocumentData(source, root, actor, tasks, permission, inputs, application.getBean(TemplateDefinitionSchema.class), application.getBean(SourceRightsSchema.class));
            documents.initialize();
            ledger.put("document_id", documents.document).put("document_revision", documents.initialRevision).put("source_version", documents.sourceVersion).put("template_version", documents.template); save();
            var runs = new SimulationRunData(source, tasks, actor, permission, application.getBean(BudgetSchema.class));
            ledger.put("run_id", runs.run).put("budget_id", runs.budget); save();
            for (String action : List.of("project.read", "project.task.read", "task.read", "task.create", "run.read", "work_item.read", "source.read", "source.write", "source.approve",
                    "source.manage", "collection.read", "collection.write", "template.read", "template.write", "template.approve", "rule.read", "rule.write", "rule.approve",
                    "artifact.read", "configuration.read", "model.configure", "budget.read", "budget.approve", "document.read", "document.write", "document.check", "document.review", "document.sign", "audit.read",
                    "role.read", "role.manage", "member.read", "member.manage", "management.discover", "management.configure", "management.approve", "evaluation.read", "evaluation.write")) {
                grant(db, permission, tasks.tenant, actor, actor, basis, action);
            }
            String memberId = id();
            db.update("INSERT INTO tenant_member(tenant_id,member_id,user_id,status,valid_from,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,'ACTIVE',UTC_TIMESTAMP(6),?,UTC_TIMESTAMP(6),'SIMULATION',1,UTC_TIMESTAMP(6))", tasks.tenant, memberId, member, actor);
            db.update("INSERT INTO identity_binding(tenant_id,identity_id,user_id,mode,local_user_id,status,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,'local',?,'ACTIVE',?,UTC_TIMESTAMP(6),'SIMULATION',1,UTC_TIMESTAMP(6))", tasks.tenant, id(), member, member, actor);
            db.update("INSERT INTO platform_member_route(user_id,tenant_id,member_id,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6),'SIMULATION',1,UTC_TIMESTAMP(6))", member, tasks.tenant, memberId, actor);
            grant(db, permission, tasks.tenant, actor, member, basis, "project.read");
            grant(db, permission, tasks.tenant, actor, member, basis, "project.task.read");
            grant(db, permission, tasks.tenant, actor, member, basis, "task.read");
            ledger.put("member_user_id", member); save();
            }
            taskInputInWorkspace(db, permission, inputs, actor);
            if (!ledger.has("configuration_tenant_id")) {
            var configurations = new ConfigurationWorkflowSimulationData(source, root, actor, member, permission);
            ledger.put("configuration_tenant_id", configurations.tenant).put("configuration_other_tenant_id", configurations.otherTenant); save();
            var modelInput = configurations.modelCreate("acceptance-unconfigured");
            modelInput.put("reason", "SIMULATION 验收测试模型草稿，尚未配置真实服务");
            ledger.set("model_input", modelInput); ledger.set("rule_input", configurations.ruleCreate()); save();
            }
            origin = "http://127.0.0.1:" + application.getWebServer().getPort();
            http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)).followRedirects(HttpClient.Redirect.NEVER).build();
            var csrf = read("/auth/csrf");
            String form = "username=" + URLEncoder.encode(owner.required("username").asText(), StandardCharsets.UTF_8) + "&password=" + URLEncoder.encode(owner.required("password").asText(), StandardCharsets.UTF_8);
            var login = http.send(HttpRequest.newBuilder(URI.create(origin + "/auth/login")).header("Origin", "http://127.0.0.1:18080")
                .header(csrf.required("header_name").asText(), csrf.required("token").asText()).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.discarding());
            if (login.statusCode() != 302) throw new IllegalStateException("ACCEPTANCE_LOGIN_FAILED");
            session = read("/api/session").required("data"); select(ledger.required("configuration_tenant_id").asText());
            if (!ledger.has("model_config_revision")) {
            var model = post("/api/model-config-revisions", ledger.required("model_input"), 201);
            ledger.put("model_config_revision", model.required("model_config_revision").asText()); save();
            }
            if (!ledger.has("rule_version")) {
            var rule = post("/api/rule-versions", ledger.required("rule_input"), 201);
            ledger.put("rule_version", rule.required("rule").required("rule_version").asText()); save();
            }
            select(ledger.required("tenant_id").asText());
            if (!ledger.has("workspace_model_input")) configurationInWorkspace(db, permission, actor);
            if (!ledger.has("workspace_model_config_revision")) {
                var model = post("/api/model-config-revisions", ledger.required("workspace_model_input"), 201);
                ledger.put("workspace_model_config_revision", model.required("model_config_revision").asText()); save();
            }
            if (!ledger.has("workspace_rule_version")) {
                var rule = post("/api/rule-versions", ledger.required("workspace_rule_input"), 201);
                ledger.put("workspace_rule_version", rule.required("rule").required("rule_version").asText()); save();
            }
            ledger.put("state", "CREATED").put("verified_at", java.time.Instant.now().toString()).put("existing_data_preserved", true); save();
            System.out.println("ACCEPTANCE_WORKSPACE_CREATED " + ledger.required("tenant_id").asText());
        }
    }
}
