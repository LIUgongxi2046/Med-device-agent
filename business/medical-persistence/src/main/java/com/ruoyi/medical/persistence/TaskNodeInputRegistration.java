package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.TaskNodeInputLedger.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** TASK_NODE_INPUT registration requires actual original-attempt transport readback and current
 * dedicated producer authority. Immutable receipts are rechecked against every owning journal. */
public final class TaskNodeInputRegistration {
    public static final String KIND="TASK_NODE_INPUT";
    private final JdbcTemplate db;private final TaskNodeInputLedger ledger;private final TaskNodeInputSchema schema;
    private final PermissionSchema json;private final DependencyManifestSchema dependencies;
    public TaskNodeInputRegistration(DataSource data,TaskNodeInputLedger ledger,TaskNodeInputSchema schema,PermissionSchema json,DependencyManifestSchema dependencies){this(new JdbcTemplate(data),ledger,schema,json,dependencies);}
    TaskNodeInputRegistration(JdbcTemplate db,TaskNodeInputLedger ledger,TaskNodeInputSchema schema,PermissionSchema json,DependencyManifestSchema dependencies){this.db=db;db.setQueryTimeout(5);this.ledger=ledger;this.schema=schema;this.json=json;this.dependencies=dependencies;}
    public Registered register(TaskNodeInputAuthority.Prepared p,TaskNodeInputLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception {
        var row=ledger.require(p,attempt);requireTransfer(attempt,physical);var a=p.bootstrap().admission();
        var existing=readStored(a,attempt);if(existing.isPresent()){if(!existing.get().object().equals(physical.reference()))bad();p.requireUnchanged();return existing.get();}
        var c=row.columns();var input=p.input();var object=physical.reference();String set=id(),audit=id(),event=id();var now=ledger.now();
        var manifest=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",input.tenantId(),input.projectId(),set,p.content().members());
        String rawManifest=schema.canonical(manifest),manifestHash=schema.hash(manifest);
        version(input.tenantId(),input.projectId(),set,"DEPENDENCY_ACCESS_SET",manifestHash);version(input.tenantId(),input.projectId(),attempt.artifactId(),"ARTIFACT",object.sha256());
        var access=meta(row,now,false);access.put("access_set_id",set);access.put("manifest_sha256",manifestHash);access.put("content_sha256",manifestHash);ledger.insert("dependency_access_set",access);
        var m=meta(row,now,true);m.put("schema_manifest_sha256",dependencies.manifestSha256());m.put("access_set_id",set);m.put("manifest_json",rawManifest);m.put("manifest_sha256",manifestHash);m.put("content_sha256",manifestHash);m.put("member_count",manifest.members().size());ledger.insert("dependency_access_manifest",m);
        for(var member:manifest.members()){var values=meta(row,now,false);values.remove("project_id");values.put("access_set_id",set);values.put("upstream_version_id",member.upstreamVersionId());values.put("rights_revision",member.rightsRevision());ledger.insert("dependency_access_member",values);}
        var artifact=meta(row,now,true);artifact.put("artifact_id",attempt.artifactId());artifact.put("run_id",input.runId());artifact.put("document_revision",null);artifact.put("artifact_kind",KIND);artifact.put("object_ref",StoredObjectReferences.encode(object));artifact.put("object_key",object.objectKey());artifact.put("object_version",object.objectVersion());artifact.put("sha256",object.sha256());artifact.put("size_bytes",object.sizeBytes());artifact.put("media_type",object.mediaType());artifact.put("dependency_access_set_id",set);artifact.put("content_sha256",object.sha256());ledger.insert("artifact",artifact);
        var binding=meta(row,now,false);binding.put("artifact_id",attempt.artifactId());binding.put("resource_kind","TASK");binding.put("resource_id",input.taskId());binding.put("schema_ref",TaskNodeInputSchema.REF);binding.put("schema_document_sha256",schema.manifest());binding.put("dependency_access_set_id",set);binding.put("dependency_manifest_sha256",manifestHash);ledger.insert("artifact_content_binding",binding);
        for(String parent:p.content().roots())ledger.insert("lineage_edge",Map.of("tenant_id",input.tenantId(),"consumer_version_id",attempt.artifactId(),"upstream_version_id",parent,"relation_kind","RUNTIME_CONTENT_INPUT","actor_id",a.actorId(),"created_at",now));
        var receipt=json.object().put("schema_version","1.0.0").put("kind",KIND).put("operation_id",attempt.operationId()).put("write_id",attempt.writeIntent().writeId()).put("attempt_id",attempt.attemptId()).put("artifact_id",attempt.artifactId()).put("intent_sha256",text(c,"intent_sha256")).put("created_at",instant(now)).put("created_by",a.actorId()).put("classification",text(c,"classification"));
        for(String field:List.of("tenant_id","project_id","task_id","run_id","step_id","graph_id","graph_sha256","producer_rule_id","producer_sha256"))receipt.put(field,text(c,field));
        receipt.set("object_ref",json.tree(object));receipt.set("dependency_manifest",json.tree(manifest));receipt.set("parents",json.tree(p.content().roots()));
        String receiptJson=schema.canonical(receipt),receiptHash=schema.hash(receipt);
        var registered=meta(row,now,true);for(String field:List.of("write_id","operation_id","artifact_id","task_id","run_id","step_id"))registered.put(field,c.get(field));
        registered.put("attempt_id",attempt.attemptId());registered.put("object_version",object.objectVersion());registered.put("payload_sha256",object.sha256());registered.put("receipt_json",receiptJson);registered.put("receipt_sha256",receiptHash);registered.put("audit_id",audit);registered.put("event_id",event);
        ledger.journal(row,"task.node.input.registered",audit,event,attempt.writeIntent().writeId(),3,receiptJson,now);ledger.insert("task_node_input_registered",registered);
        changed(db.update("UPDATE operation_record SET state='COMPLETED',http_status=201,response_ref=?,revision=3,updated_at=? WHERE tenant_id=? AND operation_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? AND request_sha256=? AND state='PROCESSING' AND revision=2",receiptJson,now,a.context().tenant_id(),attempt.operationId(),a.actorId(),TaskNodeInputLedger.OPERATION,c.get("idempotency_key"),c.get("request_sha256")));
        p.requireUnchanged();return readStored(a,attempt).orElseThrow(()->new IllegalStateException("REGISTRATION_MISSING"));
    }
    public Registered readVerified(TaskNodeInputAuthority.Prepared p,TaskNodeInputLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception {
        ledger.require(p,attempt);requireTransfer(attempt,physical);var result=readStored(p.bootstrap().admission(),attempt).orElseThrow(()->new IllegalStateException("REGISTRATION_MISSING"));if(!result.object().equals(physical.reference()))bad();p.requireUnchanged();return result;
    }
    static void requireTransfer(TaskNodeInputLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical){
        if(attempt==null||physical==null||physical.originalAttempt()!=attempt)bad();var wanted=attempt.writeIntent();var actual=physical.reference();
        if(actual==null||!wanted.objectKey().equals(actual.objectKey())||!wanted.sha256().equals(actual.sha256())||!wanted.sizeBytes().equals(actual.sizeBytes())||!wanted.mediaType().equals(actual.mediaType())||!wanted.sha256().equals(physical.rawSha256())||actual.objectVersion()==null||actual.objectVersion().isBlank())bad();
    }
    Optional<Registered> readStored(EnterpriseAccess.Admission a,TaskNodeInputLedger.Attempt attempt)throws Exception {
        var row=ledger.requireStored(a,attempt);var c=row.columns();var input=schema.read(attempt.bytes());var rows=db.queryForList("SELECT * FROM task_node_input_registered WHERE tenant_id=? AND write_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),attempt.writeIntent().writeId());
        var op=one(db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),attempt.operationId()));
        if(rows.isEmpty()){if("COMPLETED".equals(op.get("state")))bad();return Optional.empty();}
        var registered=one(rows);ledger.verifyMetadata(row,registered);var now=time(registered,"created_at");
        if(now.isBefore(time(c,"created_at"))||now.isAfter(ledger.now()))bad();
        for(String f:List.of("write_id","operation_id","artifact_id","task_id","run_id","step_id","payload_sha256"))if(!c.get(f).equals(registered.get(f)))bad();
        if(!attempt.attemptId().equals(registered.get("attempt_id")))bad();var receipt=schema.readTree(text(registered,"receipt_json"));
        if(receipt.size()!=22||!schema.canonical(receipt).equals(registered.get("receipt_json"))||!schema.hash(receipt).equals(registered.get("receipt_sha256")))bad();
        var o=receipt.required("object_ref");var object=new ObjectRef(text(o,"object_key"),text(o,"object_version"),text(o,"sha256"),text(o,"size_bytes"),text(o,"media_type"));var intent=attempt.writeIntent();
        if(!intent.objectKey().equals(object.objectKey())||!intent.sha256().equals(object.sha256())||!intent.sizeBytes().equals(object.sizeBytes())||!intent.mediaType().equals(object.mediaType())||!registered.get("object_version").equals(object.objectVersion()))bad();
        for(String f:List.of("tenant_id","project_id","task_id","run_id","step_id","graph_id","graph_sha256","producer_rule_id","producer_sha256","write_id","operation_id","artifact_id","intent_sha256","created_by","classification"))if(!text(c,f).equals(text(receipt,f)))bad();
        if(!"1.0.0".equals(text(receipt,"schema_version"))||!KIND.equals(text(receipt,"kind"))||!attempt.attemptId().equals(text(receipt,"attempt_id"))||!instant(now).equals(text(receipt,"created_at")))bad();
        var members=new ArrayList<ArtifactRegistryProtocol.MemberRow>();for(var member:row.intent().required("content_members"))members.add(new ArtifactRegistryProtocol.MemberRow(text(member,"upstream_version_id"),text(member,"rights_revision")));
        var manifest=receipt.required("dependency_manifest");String set=text(manifest,"access_set_id"),hash=schema.hash(manifest);
        if(!json.tree(new ArtifactRegistryProtocol.DependencyManifest("1.0.0",input.tenantId(),input.projectId(),set,members)).equals(manifest)||!row.intent().required("content_roots").equals(receipt.required("parents")))bad();
        var artifact=one(db.queryForList("SELECT * FROM artifact WHERE tenant_id=? AND artifact_id=? LIMIT 2",input.tenantId(),attempt.artifactId()));var expected=meta(row,now,true);expected.put("artifact_id",attempt.artifactId());expected.put("run_id",input.runId());expected.put("document_revision",null);expected.put("artifact_kind",KIND);expected.put("object_key",object.objectKey());expected.put("object_version",object.objectVersion());expected.put("sha256",object.sha256());expected.put("size_bytes",object.sizeBytes());expected.put("media_type",object.mediaType());expected.put("dependency_access_set_id",set);expected.put("content_sha256",object.sha256());sameColumns(expected,artifact);StoredObjectReferences.decode(text(artifact,"object_ref"),object);
        var binding=meta(row,now,false);binding.put("artifact_id",attempt.artifactId());binding.put("resource_kind","TASK");binding.put("resource_id",input.taskId());binding.put("schema_ref",TaskNodeInputSchema.REF);binding.put("schema_document_sha256",schema.manifest());binding.put("dependency_access_set_id",set);binding.put("dependency_manifest_sha256",hash);sameColumns(binding,one(db.queryForList("SELECT * FROM artifact_content_binding WHERE tenant_id=? AND artifact_id=? LIMIT 2",input.tenantId(),attempt.artifactId())));
        var access=meta(row,now,false);access.put("access_set_id",set);access.put("manifest_sha256",hash);access.put("content_sha256",hash);sameColumns(access,one(db.queryForList("SELECT * FROM dependency_access_set WHERE tenant_id=? AND access_set_id=? LIMIT 2",input.tenantId(),set)));
        var declared=meta(row,now,true);declared.put("schema_manifest_sha256",dependencies.manifestSha256());declared.put("access_set_id",set);declared.put("manifest_sha256",hash);declared.put("content_sha256",hash);declared.put("member_count",members.size());var actualManifest=one(db.queryForList("SELECT * FROM dependency_access_manifest WHERE tenant_id=? AND access_set_id=? LIMIT 2",input.tenantId(),set));sameColumns(declared,actualManifest);if(!manifest.equals(schema.readTree(text(actualManifest,"manifest_json"))))bad();
        var actualMembers=db.queryForList("SELECT * FROM dependency_access_member WHERE tenant_id=? AND access_set_id=? ORDER BY upstream_version_id LIMIT 513",input.tenantId(),set);if(members.size()!=actualMembers.size())bad();
        for(int i=0;i<members.size();i++){var wanted=meta(row,now,false);wanted.remove("project_id");wanted.put("access_set_id",set);wanted.put("upstream_version_id",members.get(i).upstreamVersionId());wanted.put("rights_revision",members.get(i).rightsRevision());sameColumns(wanted,actualMembers.get(i));}
        var parents=db.queryForList("SELECT * FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? ORDER BY upstream_version_id LIMIT 513",input.tenantId(),attempt.artifactId());var roots=receipt.required("parents");if(parents.size()!=roots.size())bad();
        for(int i=0;i<parents.size();i++){var wanted=new LinkedHashMap<String,Object>();wanted.put("tenant_id",input.tenantId());wanted.put("consumer_version_id",attempt.artifactId());wanted.put("upstream_version_id",roots.get(i).asText());wanted.put("relation_kind","RUNTIME_CONTENT_INPUT");wanted.put("actor_id",a.actorId());wanted.put("decision_ref",null);wanted.put("created_at",now);sameColumns(wanted,parents.get(i));}
        for(var v:List.of(Map.entry(attempt.artifactId(),object.sha256()),Map.entry(set,hash)))sameColumns(Map.of("tenant_id",input.tenantId(),"version_id",v.getKey(),"owner_project_id",input.projectId(),"entity_kind",v.getKey().equals(set)?"DEPENDENCY_ACCESS_SET":"ARTIFACT","content_sha256",v.getValue()),one(db.queryForList("SELECT * FROM version_registry WHERE tenant_id=? AND version_id=? LIMIT 2",input.tenantId(),v.getKey())));
        ledger.requireJournal(row,"task.node.input.registered",text(registered,"audit_id"),text(registered,"event_id"),attempt.writeIntent().writeId(),3,text(registered,"receipt_json"),now);
        if(!"COMPLETED".equals(op.get("state"))||number(op,"revision")!=3||number(op,"http_status")!=201||!now.equals(time(op,"updated_at"))||!text(registered,"receipt_json").equals(schema.canonical(schema.readTree(text(op,"response_ref")))))bad();
        return Optional.of(new Registered(attempt.artifactId(),object,text(registered,"receipt_sha256"),receipt,schema.tree(input)));
    }
    public static final class Registered {
        private final String artifact,receiptHash;private final ObjectRef object;private final JsonNode receipt,input;
        private Registered(String artifact,ObjectRef object,String receiptHash,JsonNode receipt,JsonNode input){this.artifact=artifact;this.object=object;this.receiptHash=receiptHash;this.receipt=receipt.deepCopy();this.input=input.deepCopy();}
        public String artifactId(){return artifact;}public ObjectRef object(){return object;}public String receiptSha256(){return receiptHash;}public JsonNode receipt(){return receipt.deepCopy();}public JsonNode input(){return input.deepCopy();}
    }
    private Map<String,Object> meta(TaskNodeInputLedger.Row row,LocalDateTime at,boolean schemaFields){var c=row.columns();return ledger.metadata(text(c,"tenant_id"),text(c,"project_id"),text(c,"created_by"),text(c,"classification"),at,schemaFields);}
    private void version(String tenant,String project,String id,String kind,String hash){ledger.insert("version_registry",Map.of("tenant_id",tenant,"owner_project_id",project,"version_id",id,"entity_kind",kind,"content_sha256",hash));}
    static void sameColumns(Map<String,Object> expected,Map<String,Object> actual){
        for(var e:expected.entrySet()){
            Object found=actual.get(e.getKey()),wanted=e.getValue();
            if(wanted instanceof LocalDateTime at){if(!at.equals(time(actual,e.getKey())))bad();}
            else if(wanted instanceof Number||"size_bytes".equals(e.getKey())){
                if(found==null||new java.math.BigDecimal(wanted.toString()).compareTo(new java.math.BigDecimal(found.toString()))!=0)bad();
            }else if(!actual.containsKey(e.getKey())||!Objects.equals(wanted,found))bad();
        }
    }
}
