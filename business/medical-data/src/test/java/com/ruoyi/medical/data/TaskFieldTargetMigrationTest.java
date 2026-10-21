package com.ruoyi.medical.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;
import liquibase.Liquibase;
import liquibase.database.core.MySQLDatabase;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Local resource/parser checks only. Never connects to a database or executes a migration. */
class TaskFieldTargetMigrationTest {
    private static final String PATH="db/migration/096-task-field-target.sql";
    @Test void additiveMigrationParsesWithoutExecutingSql() throws Exception {
        try(var resources=new ClassLoaderResourceAccessor();var migration=new Liquibase(PATH,resources,new MySQLDatabase())) {
            var changes=migration.getDatabaseChangeLog().getChangeSets();assertEquals(12,changes.size());
            var ids=new HashSet<String>();
            for(var change:changes) {
                assertTrue(ids.add(change.getId()));assertFalse(change.isRunInTransaction());
                assertEquals(Set.of("mysql"),change.getDbmsSet());assertEquals(Boolean.TRUE,change.getFailOnError());
                assertNotNull(change.generateCheckSum(liquibase.ChecksumVersion.latest()));
                for(var operation:change.getChanges()) {
                    assertFalse(operation.validate(migration.getDatabase()).hasErrors());
                    assertTrue(operation.generateStatements(migration.getDatabase()).length>0);
                }
            }
        }
    }
    @Test void containsExactlyReservedTenTablesAndNoSeededAuthorityOrRuntime() throws Exception {
        String sql=read(PATH);var found=new HashSet<String>();var matcher=Pattern.compile("CREATE TABLE ([a-z_]+)").matcher(sql);
        while(matcher.find())assertTrue(found.add(matcher.group(1)));
        assertEquals(Set.of("task_field_mapping_revision","task_field_mapping_head","task_field_source_read_receipt",
            "task_field_target_intent","task_field_target_entry","task_field_target_attempt","task_field_target_registration",
            "task_field_target_input_capability","task_field_target_input_capability_state","task_field_target_input_capability_decision"),found);
        assertFalse(Pattern.compile("(?im)^\\s*(INSERT|UPDATE|DELETE|REPLACE|GRANT|TRUNCATE|DROP)\\s").matcher(sql).find());
        assertFalse(sql.contains("CREATE TABLE task_run"));assertFalse(sql.contains("artifact_content_receipt"));
        assertFalse(sql.contains("095-"));
    }
    @Test void everyNewTableHasExactTenantProjectActorSchemaAndClassification() throws Exception {
        String sql=read(PATH);var matcher=Pattern.compile("CREATE TABLE ([a-z_]+) \\((.*?)\\) ENGINE=",Pattern.DOTALL).matcher(sql);int count=0;
        while(matcher.find()) {
            count++;String body=matcher.group(2);
            for(String column:Set.of("tenant_id","project_id","task_id","schema_version","schema_manifest_sha256","created_by","created_at","classification"))
                assertTrue(body.contains(column+" "),matcher.group(1)+" missing "+column);
            assertTrue(body.contains("FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member"));
            assertTrue(body.contains("CHECK (classification IN"));assertTrue(body.contains("CHECK (schema_version='1.0.0'"));
        }
        assertEquals(10,count);
    }
    @Test void bindsRealInputsReceiptsSourcesAndOriginalArtifactInsteadOfSubstitutes() throws Exception {
        String sql=read(PATH);
        for(String relationship:Set.of(
            "REFERENCES task_input_revision (tenant_id,project_id,task_id,task_revision,request_sha256)",
            "REFERENCES plan_section_node (tenant_id,project_id,plan_revision,section_id,node_id)",
            "REFERENCES field_definition (tenant_id,template_version,node_id,field_id)",
            "REFERENCES scope_member (tenant_id,project_id,snapshot_id,source_version,parse_version,index_version,approval_revision,binding_revision)",
            "FOREIGN KEY (tenant_id,processing_project_id,processing_attempt_id,source_version,parse_version)",
            "REFERENCES source_exact_item (tenant_id,field_revision,ordinal)",
            "REFERENCES task_field_target_intent (tenant_id,project_id,task_id,write_id,planned_artifact_id,target_sha256)",
            "REFERENCES artifact (tenant_id,project_key,artifact_id,sha256,object_version)",
            "REFERENCES task_field_target_registration (tenant_id,project_id,task_id,registration_id,artifact_id,object_sha256,object_version,receipt_sha256,mapping_revision,mapping_sha256)",
            "REFERENCES task_field_target_input_capability_decision (tenant_id,project_id,task_id,capability_id,decision_id)"))assertTrue(sql.contains(relationship),relationship);
        assertTrue(sql.contains("status='PROPOSED'"));assertTrue(sql.contains("status IN ('DRAFT','APPROVED','DISABLED')"));
        assertTrue(sql.contains("purpose NOT IN ('STRICT_FIELD_PREPARATION','TASK_FIELD_TARGET_INPUT')"));
    }
    @Test void historicalPolicyAndDelegationMigrationBytesRemainFrozen() throws Exception {
        assertEquals("1cc3c0cc814a8c5534571dd46baf0a292fee8c0a620c1414e4ffafa56e9f0390",sha("db/migration/078-agent-execution-policy.sql"));
        assertEquals("287018fa4f609c3d90495275c49b5e94c8a98c6bc1714f7382f860c821a93b85",sha("db/migration/093-task-delegation-rule.sql"));
    }
    private String read(String path)throws Exception {try(var stream=getClass().getClassLoader().getResourceAsStream(path)){assertNotNull(stream);return new String(stream.readAllBytes(),StandardCharsets.UTF_8);}}
    private String sha(String path)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(read(path).getBytes(StandardCharsets.UTF_8)));}
}
