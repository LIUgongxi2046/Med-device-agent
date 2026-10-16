package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceGovernanceSchemaTest {
    @Test
    void governanceSchemaAndMapperStatementsRemainLoadable() throws Exception {
        var schema = new SourceGovernanceSchema(new PermissionSchema());
        assertEquals(64, schema.manifestSha256().length());
        var configuration = new MedicalPersistenceConfiguration()
                .medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var parameters = Map.<String,Object>of(
                "sourceVersions", List.of("11111111-1111-4111-8111-111111111111"),
                "holds", List.of(Map.of("holdId", "22222222-2222-4222-8222-222222222222",
                        "upstreamId", "11111111-1111-4111-8111-111111111111")));
        for (var method : SourceGovernanceMapper.class.getDeclaredMethods()) {
            String name = SourceGovernanceMapper.class.getName() + "." + method.getName();
            String sql = configuration.getMappedStatement(name).getBoundSql(parameters).getSql();
            assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql));
        }
        var mapping = configuration.getResultMap(SourceGovernanceMapper.class.getName() + ".review");
        Class<?>[] reviewParameters = mapping.getConstructorResultMappings().stream()
                .map(org.apache.ibatis.mapping.ResultMapping::getJavaType).toArray(Class<?>[]::new);
        assertNotNull(SourceGovernanceProtocol.ReviewRow.class.getDeclaredConstructor(reviewParameters));
        var catalogue = configuration.getResultMap(SourceGovernanceMapper.class.getName() + ".catalogue");
        Class<?>[] catalogueParameters = catalogue.getConstructorResultMappings().stream()
                .map(org.apache.ibatis.mapping.ResultMapping::getJavaType).toArray(Class<?>[]::new);
        assertNotNull(SourceGovernanceProtocol.CatalogueRow.class.getDeclaredConstructor(catalogueParameters));
        assertConstructor(configuration, "processingQueue", SourceGovernanceProtocol.ProcessingQueueRow.class);
        assertConstructor(configuration, "artifactCatalogue", SourceGovernanceProtocol.ArtifactCatalogueRow.class);
        assertConstructor(configuration, "sourceEvidence", SourceGovernanceProtocol.SourceEvidenceRow.class);
        assertConstructor(configuration, "artifactSubmission", SourceGovernanceProtocol.ArtifactSubmissionRow.class);
        assertConstructor(configuration, "sourceVersion", SourceGovernanceProtocol.SourceVersionRow.class);
        var binding = configuration.getResultMap(SourceGovernanceMapper.class.getName() + ".binding");
        Class<?>[] bindingParameters = binding.getConstructorResultMappings().stream()
                .map(org.apache.ibatis.mapping.ResultMapping::getJavaType).toArray(Class<?>[]::new);
        assertNotNull(SourceGovernanceProtocol.BindingRow.class.getDeclaredConstructor(bindingParameters));
        assertConstructor(configuration, "contentHead", SourceGovernanceProtocol.ContentHeadRow.class);
        assertConstructor(configuration, "contentChunk", SourceGovernanceProtocol.ContentChunkRow.class);
        assertConstructor(configuration, "readHistory", SourceGovernanceProtocol.ReadHistoryRow.class);
        assertConstructor(configuration, "processingConfig", SourceGovernanceProtocol.ProcessingConfigRow.class);
        assertConstructor(configuration, "processingAttempt", SourceGovernanceProtocol.ProcessingAttemptRow.class);
        assertConstructor(configuration, "usage", SourceGovernanceProtocol.UsageRow.class);
        assertConstructor(configuration, "lifecycleState", SourceGovernanceProtocol.LifecycleStateRow.class);
        assertConstructor(configuration, "retentionPolicy", SourceGovernanceProtocol.RetentionPolicyRow.class);
        assertConstructor(configuration, "lifecycleDecision", SourceGovernanceProtocol.LifecycleDecisionRow.class);
        assertConstructor(configuration, "deletionJob", SourceGovernanceProtocol.DeletionJobRow.class);
        assertConstructor(configuration, "deletionItem", SourceGovernanceProtocol.DeletionItemRow.class);
        assertConstructor(configuration, "folder", SourceGovernanceProtocol.FolderRow.class);
        assertConstructor(configuration, "organization", SourceGovernanceProtocol.OrganizationRow.class);
    }

    @Test
    void everyGovernanceStatementRequiresCurrentSubject() {
        for (var method : SourceGovernanceMapper.class.getDeclaredMethods()) {
            assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(
                    SourceGovernanceMapper.class.getName() + "." + method.getName(), Map.of()));
        }
    }

    @Test
    void catalogueCommonViewsRemainValidSql() throws Exception {
        var configuration = new MedicalPersistenceConfiguration()
                .medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        String name = SourceGovernanceMapper.class.getName() + ".catalogue";
        String sql = configuration.getMappedStatement(name).getBoundSql(Map.of(
                "approvalStatus", "DISABLED", "lifecycle", "RECYCLED", "bookmarked", true)).getSql();
        assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql));
        String compact = sql.replaceAll("\\s+", "");
        assertTrue(compact.contains("HAVINGapproval_status=?"));
        assertTrue(compact.contains("ANDlifecycle=?"));
        assertTrue(compact.contains("ANDbookmarked=?"));
    }

    @Test
    void processingQueuePageRemainsClosedAndVersioned() throws Exception {
        var values = new PermissionSchema(); var schema = new SourceGovernanceSchema(values);
        String id = "11111111-1111-4111-8111-111111111111";
        var item = values.object(); item.put("attempt_id", id); item.put("project_id", id);
        item.put("source_id", id); item.put("source_version", id); item.put("title", "法规资料");
        item.put("parse_version", id); item.put("job_type", "PARSE"); item.put("config_revision", id);
        item.put("status", "QUEUED"); item.put("parse_status", "QUEUED"); item.putObject("failure_manifest");
        item.putObject("request_manifest").put("stage", "PARSE_AND_INDEX"); item.put("run_id", id);
        item.put("created_by", id); item.put("created_at", "2026-10-11T12:00:00Z"); item.put("revision", "1");
        item.put("updated_at", "2026-10-11T12:00:00Z");
        var page = values.object(); page.put("schema_version", "1.0.0"); page.putArray("items").add(item);
        page.put("total", "1"); page.put("limit", 50); page.putNull("next_after");
        assertEquals("1", schema.envelope("ProcessingQueuePage", page, id, null).required("data").required("total").textValue());
        item.put("unregistered", true);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> schema.envelope("ProcessingQueuePage", page, id, null));
    }

    @Test
    void artifactCataloguePageRemainsClosedAndVersioned() throws Exception {
        var values = new PermissionSchema(); var schema = new SourceGovernanceSchema(values);
        String id = "11111111-1111-4111-8111-111111111111", hash = "a".repeat(64);
        var item = values.object(); item.put("artifact_id", id); item.put("project_id", id);
        item.put("run_id", id); item.put("document_revision", id); item.put("artifact_kind", "DOCUMENT_EXPORT");
        item.put("sha256", hash); item.put("size_bytes", "12"); item.put("media_type", "application/pdf");
        item.put("created_at", "2026-10-11T12:00:00Z");
        var page = values.object(); page.put("schema_version", "1.0.0"); page.putArray("items").add(item);
        page.put("total", "1"); page.put("limit", 50); page.putNull("next_after");
        assertEquals("1", schema.envelope("ArtifactCataloguePage", page, id, null)
                .required("data").required("total").textValue());
        item.put("unregistered", true);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> schema.envelope("ArtifactCataloguePage", page, id, null));
    }

    @Test
    void artifactSubmissionRemainsClosedAndVersioned() throws Exception {
        var values = new PermissionSchema(); var schema = new SourceGovernanceSchema(values);
        String id = "11111111-1111-4111-8111-111111111111";
        var input = values.object(); input.put("schema_version", "1.0.0"); input.put("ownership", "PROJECT");
        input.put("target_project_id", id); input.put("category", "监管资料"); input.put("title", "成果转存资料");
        var evidence = input.putArray("original_evidence").addObject(); evidence.put("source_version", id);
        evidence.put("rights_revision", id);
        var rights = input.putObject("rights"); rights.put("purpose", "SOURCE_REVIEW"); rights.put("license", "企业内部授权");
        rights.putArray("allowed_actions").add("READ").add("STORE"); rights.putArray("providers");
        rights.putArray("regions").add("CN"); rights.putNull("valid_from"); rights.putNull("valid_to");
        input.put("maintainer_id", id); input.putArray("applicable_project_ids").add(id); input.put("reviewer_id", id);
        assertEquals("PROJECT", schema.artifactSubmission(input.toString().getBytes(StandardCharsets.UTF_8)).ownership());
        var context = values.object(); context.put("tenant_id", id); context.put("context_revision", "2");
        context.put("auth_epoch", "1"); context.put("governance_epoch", "2");
        var receipt = values.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", id);
        receipt.put("submission_id", id); receipt.put("artifact_id", id); receipt.put("source_id", id);
        receipt.put("source_version", id); receipt.put("parse_version", id); receipt.put("rights_revision", id);
        receipt.put("sharing_decision", id); receipt.put("status", "PENDING"); receipt.put("approval_status", "PENDING");
        receipt.put("lifecycle", "ACTIVE"); receipt.put("actor_id", id); receipt.put("created_at", "2026-10-11T12:00:00Z");
        receipt.set("current_context", context);
        assertEquals("1", schema.envelope("ArtifactSubmissionReceipt", receipt, id, "1")
                .required("resource_revision").textValue());
        input.put("unregistered", true);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> schema.artifactSubmission(input.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void contentResponsesRemainClosedAndVersioned() throws Exception {
        var values = new PermissionSchema(); var schema = new SourceGovernanceSchema(values);
        String id = "11111111-1111-4111-8111-111111111111", hash = "a".repeat(64);
        var original = values.object(); original.put("schema_version", "1.0.0"); original.put("source_id", id);
        original.put("source_version", id); original.put("parse_version", id); original.put("representation", "ORIGINAL");
        original.put("purpose", "SOURCE_REVIEW"); original.put("media_type", "text/plain");
        original.put("original_sha256", hash); original.put("content_sha256", hash); original.put("start_byte", "0");
        original.put("end_byte", "1"); original.put("size_bytes", "1"); original.put("encoding", "BASE64");
        original.put("content", "YQ=="); original.put("chunk_sha256", hash); original.put("full_sha256", hash);
        original.putNull("next_start_byte"); original.put("read_at", "2026-10-11T12:00:00Z");
        assertEquals("ORIGINAL", schema.envelope("OriginalContent", original, id, null).required("data").required("representation").textValue());
        original.put("unregistered", true);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> schema.envelope("OriginalContent", original, id, null));
    }

    @Test
    void processingRequestAndReceiptRemainClosedAndVersioned() throws Exception {
        var values = new PermissionSchema(); var schema = new SourceGovernanceSchema(values);
        String id = "11111111-1111-4111-8111-111111111111";
        var input = values.object(); input.put("schema_version", "1.0.0"); input.put("project_id", id);
        input.put("config_revision", id); input.put("stage", "PARSE_AND_INDEX"); input.put("reason", "配置更新");
        assertEquals("PARSE_AND_INDEX", schema.processing(input.toString().getBytes(StandardCharsets.UTF_8)).stage());
        var context = values.object(); context.put("tenant_id", id); context.put("context_revision", "2");
        context.put("auth_epoch", "1"); context.put("governance_epoch", "2");
        var receipt = values.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", id);
        receipt.put("attempt_id", id); receipt.put("run_id", id); receipt.put("project_id", id);
        receipt.put("source_version", id); receipt.put("parse_version", id); receipt.put("config_revision", id);
        receipt.put("job_type", "PARSE"); receipt.put("stage", "PARSE_AND_INDEX"); receipt.put("status", "QUEUED");
        receipt.put("actor_id", id); receipt.put("queued_at", "2026-10-11T12:00:00Z"); receipt.set("current_context", context);
        assertEquals("1", schema.envelope("ProcessingReceipt", receipt, id, "1").required("resource_revision").textValue());
        input.put("unregistered", true);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> schema.processing(input.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void usagePageKeepsHiddenTitlesAndFixedWatermark() throws Exception {
        var values = new PermissionSchema(); var schema = new SourceGovernanceSchema(values);
        String id = "11111111-1111-4111-8111-111111111111";
        var item = values.object(); item.put("usage_key", "01:" + id + ":" + id);
        item.put("usage_kind", "PROJECT_BINDING"); item.put("relation_kind", "PROJECT_APPLICABILITY");
        item.put("project_id", id); item.put("resource_kind", "PROJECT"); item.put("resource_id", id);
        item.put("version_id", id); item.putNull("title"); item.put("status", "APPLICABLE");
        item.put("impact_status", "BLOCKING"); item.putNull("created_at");
        var page = values.object(); page.put("schema_version", "1.0.0"); page.put("source_version", id);
        page.putArray("items").add(item); page.put("total", "1"); page.put("limit", 20);
        page.putNull("next_after"); page.put("dataset_watermark", "7");
        assertEquals("7", schema.envelope("UsagePage", page, id, null).required("data").required("dataset_watermark").textValue());
        item.put("unregistered", true);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> schema.envelope("UsagePage", page, id, null));
    }

    @Test
    void lifecycleRequestAndReceiptRemainClosedAndVersioned() throws Exception {
        var values = new PermissionSchema(); var schema = new SourceGovernanceSchema(values);
        String id = "11111111-1111-4111-8111-111111111111";
        var input = values.object(); input.put("schema_version", "1.0.0"); input.put("decision", "PURGE");
        input.put("reason", "保存期限已经结束"); input.put("policy_revision", id); input.put("expected_revision", "2");
        assertEquals("PURGE", schema.lifecycle(input.toString().getBytes(StandardCharsets.UTF_8)).decision());
        var context = values.object(); context.put("tenant_id", id); context.put("context_revision", "3");
        context.put("auth_epoch", "1"); context.put("governance_epoch", "3");
        var receipt = values.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", id);
        receipt.put("decision_id", id); receipt.put("source_version", id); receipt.put("decision", "PURGE");
        receipt.put("previous_status", "RECYCLED"); receipt.put("current_status", "PURGE_PENDING");
        receipt.put("lifecycle_revision", "3"); receipt.put("policy_revision", id); receipt.put("deletion_id", id);
        receipt.put("deletion_status", "QUEUED"); receipt.put("deletion_item_count", "6"); receipt.put("actor_id", id);
        receipt.put("decided_at", "2026-10-11T12:00:00Z"); receipt.set("current_context", context);
        assertEquals("3", schema.envelope("LifecycleReceipt", receipt, id, "3").required("resource_revision").textValue());
        input.put("unregistered", true);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> schema.lifecycle(input.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void folderAndOrganizationRequestsRemainClosedAndVersioned() throws Exception {
        var values = new PermissionSchema(); var schema = new SourceGovernanceSchema(values);
        String id = "11111111-1111-4111-8111-111111111111";
        var folder = values.object(); folder.put("schema_version", "1.0.0"); folder.put("ownership", "PROJECT");
        folder.put("owner_project_id", id); folder.putNull("parent_id"); folder.put("category", "法规"); folder.put("name", "法规资料");
        assertEquals("法规资料", schema.folderCreate(folder.toString().getBytes(StandardCharsets.UTF_8)).name());
        var decision = values.object(); decision.put("schema_version", "1.0.0"); decision.put("decision", "MOVE");
        decision.putNull("name"); decision.putNull("target_parent_id"); decision.put("expected_revision", "1");
        assertEquals("MOVE", schema.folderDecision(decision.toString().getBytes(StandardCharsets.UTF_8)).decision());
        var organization = values.object(); organization.put("schema_version", "1.0.0");
        var item = organization.putArray("items").addObject(); item.put("item_id", id); item.put("source_version", id);
        item.put("decision", "LABEL"); item.putNull("folder_id"); item.putArray("labels").add("法规"); item.putNull("expected_revision");
        assertEquals("LABEL", schema.organization(organization.toString().getBytes(StandardCharsets.UTF_8)).items().get(0).decision());
        organization.put("unregistered", true);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> schema.organization(organization.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void deletionReceiptRemainsClosedAndVersioned() throws Exception {
        var values = new PermissionSchema(); var schema = new SourceGovernanceSchema(values);
        String id = "11111111-1111-4111-8111-111111111111", hash = "a".repeat(64);
        var input = values.object(); input.put("schema_version", "1.0.0"); input.put("deletion_id", id);
        input.put("item_id", id); input.put("store_kind", "ORIGINAL"); input.put("target_sha256", hash);
        input.put("expected_revision", "1"); input.put("expected_lifecycle_revision", "2");
        var receipt = input.putObject("receipt_ref"); receipt.put("deleted_at", "2026-10-11T12:00:00Z");
        receipt.put("query_confirmed_at", "2026-10-11T12:01:00Z");
        receipt.put("backup_confirmed_at", "2026-10-11T12:02:00Z"); receipt.put("evidence_sha256", hash);
        assertEquals("ORIGINAL", schema.deletionReceipt(input.toString().getBytes(StandardCharsets.UTF_8)).store_kind());
        input.put("unregistered", true);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> schema.deletionReceipt(input.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void deletionJobViewRemainsClosedAndVersioned() throws Exception {
        var values = new PermissionSchema(); var schema = new SourceGovernanceSchema(values);
        String id = "11111111-1111-4111-8111-111111111111", hash = "a".repeat(64);
        var view = values.object(); view.put("schema_version", "1.0.0"); view.put("deletion_id", id);
        view.put("source_version", id); view.put("policy_revision", id); view.put("status", "QUEUED");
        view.put("revision", "1"); view.put("updated_at", "2026-10-11T12:00:00Z");
        view.put("lifecycle_status", "PURGE_PENDING"); view.put("lifecycle_revision", "2");
        var item = view.putArray("items").addObject(); item.put("item_id", id); item.put("store_kind", "ORIGINAL");
        item.putObject("target_ref").put("object_key", "sources/example"); item.put("target_sha256", hash);
        item.put("status", "QUEUED"); item.putNull("receipt_ref"); item.putNull("backup_expiry"); item.put("revision", "1");
        assertEquals("1", schema.envelope("DeletionJobView", view, id, "1").required("resource_revision").textValue());
        item.put("unregistered", true);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> schema.envelope("DeletionJobView", view, id, "1"));
    }

    private static void assertConstructor(org.apache.ibatis.session.Configuration configuration,
            String mappingId, Class<?> type) throws Exception {
        var mapping = configuration.getResultMap(SourceGovernanceMapper.class.getName() + "." + mappingId);
        Class<?>[] parameters = mapping.getConstructorResultMappings().stream()
                .map(org.apache.ibatis.mapping.ResultMapping::getJavaType).toArray(Class<?>[]::new);
        assertNotNull(type.getDeclaredConstructor(parameters));
    }
}
