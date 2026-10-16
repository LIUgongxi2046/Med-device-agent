package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.persistence.PermissionSchema;
import com.ruoyi.medical.persistence.SimulationDocumentData;
import com.ruoyi.medical.persistence.SimulationSourceRightsData;
import com.ruoyi.medical.persistence.SimulationTaskData;
import com.ruoyi.medical.persistence.SourceRightsSchema;
import com.ruoyi.medical.persistence.TaskInputSchema;
import com.ruoyi.medical.persistence.TemplateDefinitionSchema;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

public final class SourceRightsSimulationHttpTest {
    private static final JsonMapper JSON=JsonMapper.builder().build();

    @Test void reviewsAndDecidesRightsWithActualObjectsAndBrowser() throws Exception {
        var root=Path.of(System.getProperty("medical.projectRoot"));
        var secrets=readFile(root,"service-secrets.json"); var management=readFile(root,"local-management-secret.json");
        var invitation=readFile(root,"invitation-response-secret.json"); var token=readFile(root,"ruoyi-token-secret.json");
        var owner=readFile(root,"owner-credentials.json"); var catalogue=SchemaCatalogue.fromClasspath();
        String interfacesHash=catalogue.bind("interfaces",com.fasterxml.jackson.databind.node.NullNode.instance,"NullableId").schemaSha256();
        var arguments=new ArrayList<>(List.of("--spring.config.name=application-medical-local","--server.address=127.0.0.1","--server.port=0",
            "--server.shutdown=immediate","--spring.main.banner-mode=off","--medical.source-rights.enabled=true",
            "--medical.source-rights.revision="+UUID.randomUUID(),"--medical.source-rights.environment=LOCAL","--medical.source-rights.region=LOCAL",
            "--medical.source-rights.separate-reviewer=false","--medical.source-rights.maximum-evidence=64","--medical.source-rights.maximum-evidence-bytes=1048576",
            "--medical.source-rights.maximum-versions=1024","--medical.source-rights.maximum-relations=1024",
            "--medical.source-rights.artifact-profiles[0].role=AUTHORIZATION","--medical.source-rights.artifact-profiles[0].artifact-kind=RIGHTS_AUTHORIZATION",
            "--medical.source-rights.artifact-profiles[0].resource-kind=ARTIFACT","--medical.source-rights.artifact-profiles[0].action=artifact.read",
            "--medical.source-rights.artifact-profiles[0].schema-ref="+catalogue.schemaRef("interfaces","TextContent"),
            "--medical.source-rights.artifact-profiles[0].schema-document-sha256="+interfacesHash,
            "--medical.source-rights.artifact-profiles[0].artifact-schema-manifest="+interfacesHash));
        var application=assertInstanceOf(ServletWebServerApplicationContext.class,new SpringApplicationBuilder(MedicalApplication.class).properties(Map.of(
            "MEDICAL_LOCAL_DATABASE_PASSWORD",secrets.required("database_password").textValue(),"MEDICAL_LOCAL_REDIS_PASSWORD",secrets.required("redis_password").textValue(),
            "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY",management.required("key").textValue(),"MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY_ID",management.required("key_id").textValue(),
            "MEDICAL_INVITATION_RESPONSE_KEY",invitation.required("key").textValue(),"MEDICAL_INVITATION_RESPONSE_KEY_ID",invitation.required("key_id").textValue(),
            "MEDICAL_RUOYI_TOKEN_SECRET",token.required("key").textValue())).run(arguments.toArray(String[]::new)));
        var evidence=JSON.createObjectNode();
        try(application) {
            var source=application.getBean("masterDataSource",javax.sql.DataSource.class); var jdbc=new JdbcTemplate(source);
            long original=jdbc.queryForObject("SELECT COUNT(*) FROM project",Long.class);
            String origin="http://127.0.0.1:"+application.getWebServer().getPort();
            var http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER)).followRedirects(HttpClient.Redirect.NEVER).build();
            for(String entry:List.of("/library/collections","/library/search","/library/processing","/library/artifacts",
                    "/library/versions/"+id(),"/library/versions/"+id()+"/rights")) {
                var response=http.send(HttpRequest.newBuilder(URI.create(origin+entry)).header("Accept","text/html").GET().build(),HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(200,response.statusCode(),entry);
                assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
                assertTrue(new String(response.body(),StandardCharsets.UTF_8).contains("id=\"app\""));
            }
            evidence.put("spring_frontend_entries_verified",true);
            var client=RestClient.builder().baseUrl(origin).requestFactory(new JdkClientHttpRequestFactory(http)).build();
            var csrf=JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            var form=new LinkedMultiValueMap<String,String>(); form.add("username",owner.required("username").textValue()); form.add("password",owner.required("password").textValue());
            assertEquals(302,client.post().uri("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED).header("Origin","http://127.0.0.1:18080")
                .header(csrf.required("header_name").textValue(),csrf.required("token").textValue()).body(form).retrieve().toBodilessEntity().getStatusCode().value());
            String actor=JSON.readTree(client.get().uri("/auth/account").retrieve().body(byte[].class)).required("user_id").textValue();
            try(var tasks=new SimulationTaskData(source,root,actor,application.getBean(PermissionSchema.class),application.getBean(TaskInputSchema.class));
                var documents=new SimulationDocumentData(source,root,actor,tasks,application.getBean(PermissionSchema.class),application.getBean(TaskInputSchema.class),
                    application.getBean(TemplateDefinitionSchema.class),application.getBean(SourceRightsSchema.class));
                var rights=new SimulationSourceRightsData(source,root,actor,tasks,documents,application.getBean(PermissionSchema.class))) {
                documents.initialize(); tasks.enableProjectAdministration(); rights.initialize(); rights.initializeParsedContent();
                String context=success(post(http,origin,"/api/session/tenant-selections","0",id(),JSON.createObjectNode().put("tenant_id",tasks.tenant).put("expected_context_revision","0")),200)
                    .required("current_context").required("context_revision").textValue();
                var indexes=success(read(http,origin,"/api/source-versions/"+documents.sourceVersion+"/index-builds?project_id="+tasks.project,context),200);
                assertEquals(documents.source,indexes.required("source_id").textValue());
                assertEquals(documents.sourceVersion,indexes.required("source_version").textValue());
                assertEquals(tasks.project,indexes.required("project_id").textValue());
                assertEquals("0",indexes.required("total").textValue()); assertEquals(0,indexes.required("items").size());
                evidence.put("actual_empty_index_listing_verified",true);
                String exactRetrieval=null;
                for(String decision:List.of("RETURN","APPROVE","REVOKE")) {
                    String baseContext=context;
                    var input=JSON.createObjectNode().put("schema_version","1.0.0").put("decision",decision);
                    input.set("proposal","APPROVE".equals(decision)?rights.proposal():com.fasterxml.jackson.databind.node.NullNode.instance);
                    String path="/api/source-versions/"+documents.sourceVersion;
                    var reviewResponse=post(http,origin,path+"/rights-reviews",context,null,input);
                    assertEquals(200,reviewResponse.statusCode(),decision+" "+new String(reviewResponse.body(),StandardCharsets.UTF_8));
                    var reviewed=success(reviewResponse,200);
                    var body=input.deepCopy().put("expected_revision",reviewed.required("revision").textValue())
                        .put("reviewed_manifest_sha256",reviewed.required("reviewed_manifest_sha256").textValue()).put("reason","SIMULATION 完整权利决定检查");
                    body.set("base_rights",reviewed.required("base_rights")); String key=id();
                    var response=post(http,origin,path+"/rights-decisions",context,key,body); var result=success(response,200);
                    assertEquals(decision,result.required("decision").textValue());
                    assertEquals(JSON.readTree(response.body()),JSON.readTree(post(http,origin,path+"/rights-decisions",baseContext,key,body).body()));
                    assertEquals(409,post(http,origin,path+"/rights-decisions",baseContext,key,body.deepCopy().put("reason","SIMULATION 其他理由")).statusCode());
                    context=result.required("current_context").required("context_revision").textValue();
                    var sourceResponse=read(http,origin,path,context);
                    assertEquals(200,sourceResponse.statusCode(),decision+" "+new String(sourceResponse.body(),StandardCharsets.UTF_8));
                    var actual=success(sourceResponse,200);
                    assertEquals(result.required("rights").required("version_id"),actual.required("rights_revision"));
                    assertEquals(result.required("rights_status"),actual.required("rights_status"));
                    if("APPROVE".equals(decision)) {
                        var content=success(read(http,origin,path+"/content?parse_version="+documents.parse+"&representation=PARSED&purpose=DOCUMENT_BODY&limit=20",context),200);
                        assertEquals(1,content.required("items").size()); assertEquals(documents.chunk,content.required("items").get(0).required("chunk_id").textValue());
                        var repeated=success(read(http,origin,path+"/content?parse_version="+documents.parse+"&representation=PARSED&purpose=DOCUMENT_BODY&limit=20",context),200);
                        assertEquals(content.required("items"),repeated.required("items"));
                        assertEquals("2",jdbc.queryForObject("SELECT CAST(revision AS CHAR) FROM source_read_history WHERE tenant_id=? AND user_id=? AND source_version=?",
                            String.class,tasks.tenant,actor,documents.sourceVersion));
                        evidence.put("actual_native_txt_content_verified",true);
                        evidence.put("actual_read_history_insert_update_and_reopening_verified",true);
                        var project=success(read(http,origin,"/api/projects/"+tasks.project,context),200);
                        var bindingInput=JSON.createObjectNode().put("schema_version","1.0.0").put("source_version",documents.sourceVersion)
                            .put("profile_revision",project.required("profile").required("profile_revision").textValue())
                            .put("profile_sha256",project.required("profile").required("content_sha256").textValue())
                            .put("applicability","APPLICABLE").put("reason","SIMULATION 完整项目适用核查")
                            .put("expected_binding_revision",documents.binding).put("approval_attested",true);
                        var bound=success(post(http,origin,"/api/projects/"+tasks.project+"/source-bindings",context,id(),bindingInput),200);
                        context=bound.required("current_context").required("context_revision").textValue();
                        String bindingRevision=bound.required("binding_revision").textValue();
                        assertEquals("SOURCE_BINDING",jdbc.queryForObject("SELECT entity_kind FROM version_registry WHERE tenant_id=? AND version_id=?",
                            String.class,tasks.tenant,bindingRevision));
                        var bindingManifest=JSON.readTree(jdbc.queryForObject("SELECT decision_manifest FROM project_source_binding WHERE tenant_id=? AND binding_revision=?",
                            String.class,tasks.tenant,bindingRevision));
                        assertEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(application.getBean(PermissionSchema.class).canonical(bindingManifest)),
                            jdbc.queryForObject("SELECT content_sha256 FROM version_registry WHERE tenant_id=? AND version_id=?",String.class,tasks.tenant,bindingRevision));
                        var sourceReview=success(read(http,origin,path,context),200).required("reviews").get(0);
                        var approvalInput=JSON.createObjectNode().put("schema_version","1.0.0").put("parse_version",documents.parse)
                            .put("index_version",documents.index).put("rights_revision",result.required("rights").required("version_id").textValue())
                            .put("binding_revision",bindingRevision).put("reviewed_manifest_sha256",sourceReview.required("review_manifest_sha256").textValue())
                            .put("decision","APPROVE").put("reason","SIMULATION 完整原文与索引核查").put("expected_revision",sourceReview.required("gate_revision").textValue());
                        var returnedApproval=success(post(http,origin,path+"/approval-decisions",context,id(),approvalInput.deepCopy()
                            .put("decision","RETURN").put("reason","SIMULATION 重新核查完整原文")),200);
                        assertEquals("PENDING",returnedApproval.required("approval_status").textValue());
                        context=returnedApproval.required("current_context").required("context_revision").textValue();
                        var pendingReview=success(read(http,origin,path,context),200).required("reviews").get(0);
                        approvalInput.put("expected_revision",pendingReview.required("gate_revision").textValue())
                            .put("reviewed_manifest_sha256",pendingReview.required("review_manifest_sha256").textValue());
                        var approval=success(post(http,origin,path+"/approval-decisions",context,id(),approvalInput),200);
                        context=approval.required("current_context").required("context_revision").textValue();
                        String approvalRevision=approval.required("approval_revision").textValue();
                        assertEquals("APPROVAL_DECISION",jdbc.queryForObject("SELECT entity_kind FROM version_registry WHERE tenant_id=? AND version_id=?",
                            String.class,tasks.tenant,approvalRevision));
                        var decisionManifest=JSON.createObjectNode().put("schema_version","1.0.0").put("tenant_id",tasks.tenant)
                            .put("source_version",documents.sourceVersion).put("parse_version",documents.parse).put("approval_revision",approvalRevision)
                            .put("decision","APPROVE").put("reviewer_id",actor).put("reviewed_manifest_sha256",pendingReview.required("review_manifest_sha256").textValue())
                            .put("reason",approvalInput.required("reason").textValue()).put("decided_at",approval.required("decided_at").textValue());
                        assertEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(application.getBean(PermissionSchema.class).canonical(decisionManifest)),
                            jdbc.queryForObject("SELECT content_sha256 FROM version_registry WHERE tenant_id=? AND version_id=?",String.class,tasks.tenant,approvalRevision));
                        evidence.put("actual_source_approval_and_project_binding_versions_registered",true);
                        var fields=JSON.createArrayNode(); var chunk=content.required("items").get(0);
                        for(String kind:List.of("MODEL","MODEL","PTR_NUMBER","DOCUMENT_NUMBER","STANDARD_NUMBER","STANDARD_YEAR")) {
                            var field=fields.addObject().put("field_kind",kind).put("raw_value","SIMULATION")
                                .put("chunk_id",documents.chunk).put("quote_hash",chunk.required("quote_hash").textValue())
                                .put("locator_sha256",chunk.required("locator_sha256").textValue()).put("start_codepoint",0).put("end_codepoint",10);
                            if("MODEL".equals(kind)) field.put("alias_value","SIMULATION-A00"+fields.size()).put("alias_reason","SIMULATION 人工型号别名核查");
                            else field.putNull("alias_value").putNull("alias_reason");
                        }
                        var publication=JSON.createObjectNode().put("schema_version","1.0.0").put("project_id",tasks.project)
                            .put("parse_version",documents.parse).put("index_version",documents.index).put("approval_revision",approvalRevision)
                            .put("binding_revision",bindingRevision).put("rights_revision",result.required("rights").required("version_id").textValue())
                            .put("expected_revision","0").put("reason","SIMULATION 五类完整字段核准").set("fields",fields);
                        var published=success(post(http,origin,path+"/exact-fields",context,id(),publication),201);
                        assertEquals("1",published.required("revision").textValue()); assertEquals(6,published.required("field_count").intValue());
                        assertEquals(publication,published.required("manifest").required("request"));
                        context=published.required("current_context").required("context_revision").textValue();
                        String fixedPath=path+"/exact-fields/"+published.required("field_revision").textValue()+"?project_id="+tasks.project;
                        assertEquals(published.required("manifest"),success(read(http,origin,fixedPath,context),200));
                        assertEquals(published.required("manifest"),success(read(http,origin,path+"/exact-fields?project_id="+tasks.project
                            +"&parse_version="+documents.parse+"&approval_revision="+approvalRevision,context),200));
                        for(String kind:List.of("MODEL","PTR_NUMBER","DOCUMENT_NUMBER","STANDARD_NUMBER","STANDARD_YEAR")) {
                            var query=JSON.createObjectNode().put("schema_version","1.0.0").put("project_id",tasks.project)
                                .put("field_kind",kind).put("query","SIMULATION").put("purpose","VERIFICATION");
                            var retrieval=success(post(http,origin,"/api/retrievals/exact",context,id(),query),201);
                            assertEquals("COMPLETED",retrieval.required("status").textValue());
                            String retrievalId=retrieval.required("resource_id").textValue();
                            var page=success(read(http,origin,"/api/retrievals/exact/"+retrievalId+"?page=1&page_size=100",context),200);
                            assertEquals("FOUND",page.required("status").textValue()); assertEquals("1",page.required("total").textValue());
                            assertEquals("SIMULATION",page.required("query").textValue());
                            assertEquals(published.required("field_revision"),page.required("items").get(0).required("field_revision"));
                            assertEquals("MODEL".equals(kind)?2:1,page.required("items").get(0).required("matches").size());
                            if("MODEL".equals(kind)) exactRetrieval=retrievalId;
                            assertEquals(page,success(read(http,origin,"/api/retrievals/exact/"+retrievalId+"?page=1&page_size=100",context),200));
                        }
                        for(String literal:List.of("SIMULATION-A001","simulation"," SIMULATION")) {
                            var query=JSON.createObjectNode().put("schema_version","1.0.0").put("project_id",tasks.project)
                                .put("field_kind","MODEL").put("query",literal).put("purpose","VERIFICATION");
                            var retrieval=success(post(http,origin,"/api/retrievals/exact",context,id(),query),201);
                            var page=success(read(http,origin,"/api/retrievals/exact/"+retrieval.required("resource_id").textValue()+"?page=1&page_size=100",context),200);
                            assertEquals(literal,page.required("query").textValue());
                            assertEquals("SIMULATION-A001".equals(literal)?"FOUND":"EMPTY",page.required("status").textValue());
                        }
                        evidence.put("actual_exact_publication_fixed_manifest_and_five_kinds_verified",true);
                        evidence.put("actual_exact_alias_literal_empty_and_fixed_history_verified",true);
                    }
                    if("REVOKE".equals(decision)) {
                        assertNotNull(exactRetrieval);
                        var revoked=read(http,origin,"/api/retrievals/exact/"+exactRetrieval+"?page=1&page_size=100",context);
                        assertEquals(403,revoked.statusCode());
                        assertEquals("ACCESS_DENIED",JSON.readTree(revoked.body()).required("code").textValue());
                        evidence.put("actual_exact_history_revocation_verified",true);
                    }
                    var operation=success(read(http,origin,"/api/operations/"+result.required("operation_id").textValue(),context),200);
                    assertEquals("COMPLETED",operation.required("status").textValue()); assertEquals(documents.sourceVersion,operation.required("resource_id").textValue());
                    assertEquals(409,post(http,origin,path+"/rights-decisions",context,id(),body).statusCode());
                }
                assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM source_rights_decision WHERE tenant_id=? AND source_version=?",Integer.class,tasks.tenant,documents.sourceVersion));
                assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='KN-RIGHTS-01'",Integer.class,tasks.tenant));
                assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action='source.rights_decided'",Integer.class,tasks.tenant));
                assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND event_type='source.rights_decided'",Integer.class,tasks.tenant));
                var proof=JSON.readTree(jdbc.queryForObject("SELECT evidence_proof FROM source_rights_decision WHERE tenant_id=? AND decision='APPROVE'",String.class,tasks.tenant));
                assertEquals(1,proof.size()); assertEquals(rights.authorizationSha256,proof.get(0).required("full_sha256").textValue());
                rights.revokeApprovalPermission();
                assertEquals(403,post(http,origin,"/api/source-versions/"+documents.sourceVersion+"/rights-reviews",context,null,
                    JSON.createObjectNode().put("schema_version","1.0.0").put("decision","APPROVE").set("proposal",rights.proposal())).statusCode());
                evidence.put("native_decisions",3); evidence.put("fixed_object_bytes_verified",true); evidence.put("replay_conflict_and_reopening_verified",true);
                evidence.put("audit_outbox_and_revocation_verified",true);
            }
            if(Boolean.getBoolean("medical.sourceRightsBrowser")) {
                var output=Path.of(System.getProperty("medical.sourceRightsEvidence")).getParent();
                for(String mode:List.of("development","production")) {
                    try(var tasks=new SimulationTaskData(source,root,actor,application.getBean(PermissionSchema.class),application.getBean(TaskInputSchema.class));
                        var documents=new SimulationDocumentData(source,root,actor,tasks,application.getBean(PermissionSchema.class),application.getBean(TaskInputSchema.class),
                            application.getBean(TemplateDefinitionSchema.class),application.getBean(SourceRightsSchema.class));
                        var rights=new SimulationSourceRightsData(source,root,actor,tasks,documents,application.getBean(PermissionSchema.class))) {
                        documents.initialize(); tasks.enableSourceUploading(); rights.initialize(); rights.initializeParsedContent();
                        var builder=new ProcessBuilder("/usr/local/bin/node",root.resolve("tests/browser/source-rights-checks.js").toString(),output.toString(),origin,
                            tasks.tenant,documents.sourceVersion,rights.authorization,rights.authorizationSha256,mode,System.getProperty("medical.sourceRightsFrontend"),tasks.project);
                        builder.directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.resolve("browser-"+mode+".log").toFile());
                        var browser=builder.start();
                        boolean finished=browser.waitFor(900,java.util.concurrent.TimeUnit.SECONDS);
                        if(!finished) {
                            var children=browser.toHandle().descendants().toList();
                            children.forEach(ProcessHandle::destroyForcibly); browser.destroyForcibly();
                            assertTrue(browser.waitFor(30,java.util.concurrent.TimeUnit.SECONDS),"超时浏览器检查必须终止");
                        }
                        assertTrue(finished,"完整权利浏览器检查必须完成");
                        assertEquals(0,browser.exitValue(),Files.readString(output.resolve("browser-"+mode+".log")));
                        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM source_rights_decision WHERE tenant_id=?",Integer.class,tasks.tenant));
                        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM source_rights_decision WHERE tenant_id=? AND decision='APPROVE' AND JSON_LENGTH(evidence_proof)=1",Integer.class,tasks.tenant));
                        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM source_exact_revision WHERE tenant_id=?",Integer.class,tasks.tenant));
                        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='KN-EXACT-PUBLISH' AND state='COMPLETED'",Integer.class,tasks.tenant));
                    }
                }
                evidence.put("browser_development_and_production_verified",true);
            }
            assertEquals(original,jdbc.queryForObject("SELECT COUNT(*) FROM project",Long.class));
            evidence.put("project_counts_unchanged",true); evidence.put("actual_http_port",application.getWebServer().getPort());
        }
        assertFalse(application.isActive()); evidence.put("temporary_application_closed",true); evidence.put("data_kind","SIMULATION");
        evidence.put("customer_success_verified",false); evidence.put("professional_source_adoption_verified",false);
        Files.write(Path.of(System.getProperty("medical.sourceRightsEvidence")),JSON.writeValueAsBytes(evidence));
    }
    private static JsonNode readFile(Path root,String name) throws Exception { return JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/"+name))); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static HttpResponse<byte[]> read(HttpClient http,String origin,String path,String context) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(origin+path)).header("X-Schema-Version","1.0.0").header("X-Context-Revision",context).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    private static HttpResponse<byte[]> post(HttpClient http,String origin,String path,String context,String key,JsonNode body) throws Exception {
        var session=success(read(http,origin,"/api/session",context),200); var csrf=session.required("csrf");
        var builder=HttpRequest.newBuilder(URI.create(origin+path)).header("X-Schema-Version","1.0.0").header("X-Context-Revision",context)
            .header("Origin","http://127.0.0.1:18080").header("Content-Type","application/json")
            .header(csrf.required("header_name").textValue(),csrf.required("token").textValue());
        if(key!=null) builder.header("Idempotency-Key",key);
        return http.send(builder.POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    private static JsonNode success(HttpResponse<byte[]> response,int status) throws Exception {
        assertEquals(status,response.statusCode(),new String(response.body(),StandardCharsets.UTF_8));
        assertEquals("no-store",response.headers().firstValue("Cache-Control").orElseThrow()); return JSON.readTree(response.body()).required("data");
    }



}
