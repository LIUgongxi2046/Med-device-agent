package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

public final class SimulationSourceRightsData implements AutoCloseable {
    public final String authorization = id(), accessSet = id();
    public String authorizationSha256;
    private final SimulationTaskData tasks;
    private final SimulationDocumentData documents;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PermissionSchema json;
    private final String actor;
    private final Path root;

    public SimulationSourceRightsData(javax.sql.DataSource source, Path root, String actor, SimulationTaskData tasks,
            SimulationDocumentData documents, PermissionSchema json) {
        this.root=root; this.actor=actor; this.tasks=tasks; this.documents=documents; this.json=json;
        jdbc=new JdbcTemplate(source); transaction=new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    public void initialize() throws Exception {
        var body=json.object().put("kind","TEXT").put("text","SIMULATION 样例企业授权本轮功能开发读取、保存及使用模拟资料，环境和区域均为LOCAL，分类为INTERNAL。");
        body.putArray("marks");
        SchemaCatalogue.fromClasspath().validate("interfaces",body,"TextContent");
        var original=writeObject(json.canonical(body).getBytes(StandardCharsets.UTF_8)); authorizationSha256=original.sha256();
        transaction.executeWithoutResult(status -> org.springframework.util.function.ThrowingSupplier.of(() -> {
            jdbc.update("UPDATE rights_revision SET status='PENDING_REVIEW' WHERE tenant_id=? AND rights_revision=?",tasks.tenant,documents.rights);
            insert("source_lifecycle_state",metadata(row("source_version",documents.sourceVersion,"status","ACTIVE","last_decision_id",null,"revision",1,"updated_at",tasks.updated)));
            jdbc.update("INSERT INTO source_rights_state(tenant_id,source_version,rights_revision,last_decision_id,revision,updated_at,schema_version,schema_manifest_sha256,created_by,created_at,classification) "
                +"SELECT v.tenant_id,v.source_version,v.rights_revision,NULL,1,v.created_at,r.schema_version,r.schema_manifest_sha256,v.created_by,v.created_at,s.classification "
                +"FROM source_version v JOIN rights_revision r ON r.tenant_id=v.tenant_id AND r.source_version=v.source_version AND r.rights_revision=v.rights_revision "
                +"JOIN source s ON s.tenant_id=v.tenant_id AND s.source_id=v.source_id WHERE v.tenant_id=? AND v.source_version=?",tasks.tenant,documents.sourceVersion);
            var condition=row("schema_version","1.0.0","identity_modes",List.of("local"),
                "purposes",List.of("DOCUMENT_BODY","SOURCE_RIGHTS_REVIEW","PROJECT_READING","PROJECT_PROFILE_CONFIRMATION","SOURCE_REVIEW","SOURCE_ORGANIZATION","SOURCE_UPLOAD"),
                "delegation",row("actions",List.of(),"role_ids",List.of(),"resource_kinds",List.of(),"project_ids",List.of(),"resource_ids",List.of()));
            String encoded=json.canonical(json.tree(condition)); json.condition(encoded);
            jdbc.update("UPDATE permission_grant SET `condition`=? WHERE tenant_id=?",encoded,tasks.tenant);
            insert("permission_grant",schema(metadata(row("grant_id",id(),"subject_id",actor,"project_id",null,"resource_kind","ENTERPRISE","resource_id",tasks.tenant,
                "action","source.approve","condition",encoded,"status","ACTIVE","valid_from",tasks.updated.minusDays(1),"basis_version",documents.sourceVersion,
                "revision",1,"updated_at",tasks.updated)),json.manifestSha256()));
            var manifest=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",tasks.tenant,tasks.project,accessSet,List.of());
            String encodedManifest=json.canonical(json.tree(manifest)), hash=DigestUtils.sha256Hex(encodedManifest);
            version(accessSet,"DEPENDENCY_ACCESS_SET",hash);
            insert("dependency_access_set",metadata(row("project_id",tasks.project,"access_set_id",accessSet,"manifest_sha256",hash,"content_sha256",hash)));
            insert("dependency_access_manifest",schema(metadata(row("project_id",tasks.project,"access_set_id",accessSet,"manifest_json",encodedManifest,
                "manifest_sha256",hash,"content_sha256",hash,"member_count",0)),new DependencyManifestSchema().manifestSha256()));
            version(authorization,"ARTIFACT",original.sha256());
            insert("artifact",schema(metadata(row("project_id",tasks.project,"artifact_id",authorization,"document_revision",null,
                "artifact_kind","RIGHTS_AUTHORIZATION","object_ref",StoredObjectReferences.encode(original),"object_key",original.objectKey(),
                "object_version",original.objectVersion(),"sha256",original.sha256(),"size_bytes",original.sizeBytes(),"media_type",original.mediaType(),
                "dependency_access_set_id",accessSet,"content_sha256",original.sha256())),documents.interfacesHash));
            insert("artifact_content_binding",metadata(row("project_id",tasks.project,"artifact_id",authorization,"resource_kind","ARTIFACT","resource_id",authorization,
                "schema_ref",SchemaCatalogue.fromClasspath().schemaRef("interfaces","TextContent"),"schema_document_sha256",documents.interfacesHash,
                "dependency_access_set_id",accessSet,"dependency_manifest_sha256",hash)));
            return null;
        }).get());
    }

    public void initializeParsedContent() throws Exception {
        var chunk=jdbc.queryForMap("SELECT raw_text,quote_hash FROM source_chunk WHERE tenant_id=? AND source_version=? AND parse_version=? AND chunk_id=?",
            tasks.tenant,documents.sourceVersion,documents.parse,documents.chunk);
        byte[] bytes=((String)chunk.get("raw_text")).getBytes(StandardCharsets.UTF_8);
        String hash=DigestUtils.sha256Hex(bytes);
        if(!hash.equals(chunk.get("quote_hash")) || !hash.equals(jdbc.queryForObject("SELECT original_sha256 FROM source_version WHERE tenant_id=? AND source_version=?",
                String.class,tasks.tenant,documents.sourceVersion))) throw new IllegalStateException("SIMULATION_SOURCE_BYTES_MISMATCH");
        var directory=Files.createDirectories(root.resolve(".implementation-work/source-exact-native-fixtures/"+tasks.tenant),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        var input=directory.resolve("original.txt"); var output=directory.resolve("parsed.json");
        Files.write(input,bytes,java.nio.file.StandardOpenOption.CREATE_NEW); Files.setPosixFilePermissions(input,PosixFilePermissions.fromString("rw-------"));
        var builder=new ProcessBuilder(System.getProperty("medical.test.python", root.resolve(".implementation-work/document-venv/bin/python").toString()),root.resolve("scripts/prepare_source_exact_simulation.py").toString(),
            "--root",root.toString(),"--source",input.toString(),"--source-version",documents.sourceVersion,"--parse-version",documents.parse,"--sha256",hash,"--output",output.toString());
        builder.directory(root.toFile()); builder.environment().put("TMPDIR",directory.toString()); builder.environment().put("PYTHONPATH",
            root.resolve("document/src")+java.io.File.pathSeparator+root.resolve("contracts/python/src"));
        builder.redirectErrorStream(true).redirectOutput(directory.resolve("parser.log").toFile());
        var process=builder.start();
        if(!process.waitFor(120,java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("SIMULATION_NATIVE_PARSE_TIMEOUT"); }
        if(process.exitValue()!=0) throw new IllegalStateException(Files.readString(directory.resolve("parser.log")));
        var parsed=JsonMapper.builder().build().readTree(Files.readAllBytes(output));
        if(!"SIMULATION".equals(parsed.required("data_kind").textValue()) || !documents.sourceVersion.equals(parsed.required("source_version").textValue())
                || !documents.parse.equals(parsed.required("parse_version").textValue()) || !hash.equals(parsed.required("original_sha256").textValue())
                || !hash.equals(parsed.required("quote_hash").textValue()) || !java.util.Arrays.equals(bytes,parsed.required("raw_text").textValue().getBytes(StandardCharsets.UTF_8)))
            throw new IllegalStateException("SIMULATION_NATIVE_PARSE_IDENTITY_MISMATCH");
        SchemaCatalogue.fromClasspath().validate("dataset",parsed.required("locator"),"locator");
        String node=parsed.required("node_id").textValue();
        var content=json.object().put("kind","TEXT").put("text",parsed.required("raw_text").textValue()); content.putArray("marks");
        SchemaCatalogue.fromClasspath().validate("interfaces",content,"TextContent");
        String locatorJson=json.canonical(parsed.required("locator")),contentJson=json.canonical(content),processorJson=json.canonical(parsed.required("processor_manifest"));
        String datasetHash=SchemaCatalogue.fromClasspath().bind("dataset",parsed.required("locator"),"locator").schemaSha256();
        transaction.executeWithoutResult(status -> {
            insert("content_node",schema(metadata(row("source_version",documents.sourceVersion,"parse_version",documents.parse,"node_id",node,
                "parent_id",null,"ordinal",0,"node_kind","TEXT","locator",locatorJson,"content_ref",contentJson,
                "node_hash",parsed.required("node_hash").textValue())),datasetHash));
            if(jdbc.update("UPDATE source_chunk SET node_id=? WHERE tenant_id=? AND source_version=? AND parse_version=? AND chunk_id=?",
                    node,tasks.tenant,documents.sourceVersion,documents.parse,documents.chunk)!=1) throw new IllegalStateException("SIMULATION_SOURCE_NODE_SAVE_MISMATCH");
            if(jdbc.update("UPDATE parse_version SET processor_manifest=? WHERE tenant_id=? AND source_version=? AND parse_version=?",
                    processorJson,tasks.tenant,documents.sourceVersion,documents.parse)!=1) throw new IllegalStateException("SIMULATION_PROCESSOR_SAVE_MISMATCH");
        });
    }

    public com.fasterxml.jackson.databind.JsonNode proposal() {
        var value=json.object().put("purpose","DOCUMENT_BODY"); value.set("allowed_actions",json.tree(List.of("READ","STORE","ENTERPRISE_USE")));
        value.putArray("providers"); value.set("regions",json.tree(List.of("LOCAL"))); value.putNull("valid_from"); value.putNull("valid_to");
        var evidence=value.putObject("evidence_manifest").put("owner","SIMULATION 样例企业").put("license","SIMULATION 资料许可，仅用于本轮开发");
        evidence.set("environments",json.tree(List.of("LOCAL"))); evidence.set("classifications",json.tree(List.of("INTERNAL")));
        evidence.putArray("authorization_evidence").addObject().put("version_id",authorization).put("content_sha256",authorizationSha256); return value;
    }
    public void revokeApprovalPermission() { jdbc.update("UPDATE permission_grant SET status='REVOKED',revision=revision+1 WHERE tenant_id=? AND action='source.approve'",tasks.tenant); }

    private InterfaceProtocol.ObjectRef writeObject(byte[] bytes) throws Exception {
        var storage=root.resolve(".implementation-work/runtime/object-storage"); var reader=JsonMapper.builder().build();
        var deployment=reader.readTree(Files.readAllBytes(storage.resolve("deployment.json")));
        var credentials=reader.readTree(Files.readAllBytes(storage.resolve("service-secret.json")));
        if(!deployment.required("service_id").equals(credentials.required("service_id"))) throw new IllegalStateException("SIMULATION_OBJECT_SERVICE_MISMATCH");
        var spool=Files.createDirectories(storage.resolve("source-rights-simulation-"+authorization),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        var file=Files.createTempFile(spool,"authorization-",".json",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.write(file,bytes);
        try(var writer=new S3ObjectWriter(URI.create(deployment.required("endpoint").textValue()),deployment.required("bucket").textValue(),"us-east-1",S3ObjectReader.Network.LOCAL_LOOPBACK,
                StaticCredentialsProvider.create(AwsBasicCredentials.create(credentials.required("access_key").textValue(),credentials.required("secret_key").textValue())),spool,1048576,1048576)) {
            return writer.writeVerified(new S3ObjectWriter.WriteIntent(id(),"simulation/source-rights/"+tasks.tenant+"/"+authorization,DigestUtils.sha256Hex(bytes),String.valueOf(bytes.length),"application/json"),file,
                () -> { if(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_member WHERE tenant_id=? AND user_id=? AND status='ACTIVE'",Integer.class,tasks.tenant,actor)!=1)
                    throw new IllegalStateException("SIMULATION_AUTHORITY_UNAVAILABLE"); });
        } finally { Files.delete(file); }
    }
    private void version(String value,String kind,String hash) { insert("version_registry",row("version_id",value,"owner_project_id",tasks.project,"entity_kind",kind,"content_sha256",hash)); }
    private Map<String,Object> metadata(Map<String,Object> value) { value.put("created_by",actor); value.put("created_at",tasks.updated); value.put("classification","INTERNAL"); return value; }
    private Map<String,Object> schema(Map<String,Object> value,String hash) { value.put("schema_version","1.0.0"); value.put("schema_manifest_sha256",hash); return value; }
    private void insert(String table,Map<String,Object> values) {
        values.put("tenant_id",tasks.tenant);
        jdbc.update("INSERT INTO "+table+" ("+String.join(",",values.keySet().stream().map(key -> "`"+key+"`").toList())+") VALUES ("+String.join(",",Collections.nCopies(values.size(),"?"))+")",values.values().toArray());
    }
    private static Map<String,Object> row(Object... pairs) { var result=new LinkedHashMap<String,Object>(); for(int index=0;index<pairs.length;index+=2) result.put((String)pairs[index],pairs[index+1]); return result; }
    private static String id() { return UUID.randomUUID().toString(); }
    @Override public void close() {
        transaction.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM source_read_history WHERE tenant_id=?",tasks.tenant);
            jdbc.update("DELETE FROM applicability_decision WHERE tenant_id=? AND target_version=?",tasks.tenant,documents.sourceVersion);
            jdbc.update("DELETE h FROM reference_hold h JOIN source_exact_query q ON q.tenant_id=h.tenant_id AND q.retrieval_id=h.consumer_version_id WHERE h.tenant_id=?",tasks.tenant);
            for(String table:List.of("source_exact_hit","source_exact_query","source_exact_item","source_exact_head","source_exact_revision"))
                jdbc.update("DELETE FROM "+table+" WHERE tenant_id=?",tasks.tenant);
            jdbc.update("DELETE FROM content_node WHERE tenant_id=?",tasks.tenant);
            jdbc.update("DELETE FROM source_rights_state WHERE tenant_id=?",tasks.tenant);
            jdbc.update("DELETE FROM source_rights_decision WHERE tenant_id=?",tasks.tenant);
            jdbc.update("DELETE FROM source_lifecycle_state WHERE tenant_id=?",tasks.tenant);
        });
    }
}
