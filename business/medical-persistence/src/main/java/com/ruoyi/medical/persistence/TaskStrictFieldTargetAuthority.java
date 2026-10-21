package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Running N03 read authority. Historical pre-run receipts are not fresh source reads. */
public final class TaskStrictFieldTargetAuthority {
    private final JdbcTemplate db;private final TaskFieldTargetSchema schema;private final ArtifactRegistryMapper registry;
    private final SourceReadAuthority sources;private final PermissionSchema json;private final ResourceReadAuthority resources;
    private final TaskStrictFieldTypedTargetService typedTargets;private final TaskStrictFieldTypedTargetLedger typedLedger;
    private final DependencyManifestSchema dependencies;private final DependencyLineage lineage;private final DependencyLineage.Limits limits;
    public TaskStrictFieldTargetAuthority(DataSource data,TaskFieldTargetSchema schema,ArtifactRegistryMapper registry,
            SourceReadAuthority sources,PermissionSchema json,ResourceReadAuthority resources,DependencyManifestSchema dependencies,
            DependencyLineage lineage,DependencyLineage.Limits limits,TaskStrictFieldTypedTargetService typedTargets,TaskStrictFieldTypedTargetLedger typedLedger){this.typedTargets=typedTargets;this.typedLedger=typedLedger;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.registry=registry;
        this.sources=sources;this.json=json;this.resources=resources;this.dependencies=dependencies;this.lineage=lineage;this.limits=limits;}
    public Verified require(TaskNodeProductionAuthority.Verified production,String artifact,String receiptSha256)throws Exception {
        WireValues.uuid(artifact);WireValues.hash(receiptSha256);return new Verified(production,artifact,receiptSha256,inspect(production,artifact,receiptSha256));
    }
    private record State(JsonNode manifest,JsonNode receipt,ObjectRef object,JsonNode pins,List<TaskNodeInputProtocol.SourceSelection> sources,String fingerprint){}
    private State inspect(TaskNodeProductionAuthority.Verified p,String artifact,String receiptHash)throws Exception {
        p.requireUnchanged();var a=p.admission();String tenant=p.tenantId(),project=p.projectId();
        resources.require(a,new PermissionProtocol.Scope(tenant,project,"TASK",p.taskId()),"task.field_target_input.read","TASK_FIELD_TARGET_INPUT");
        resources.require(a,new PermissionProtocol.Scope(tenant,project,"ARTIFACT",artifact),"artifact.read","TASK_FIELD_TARGET_INPUT");
        var originalRegistrations=db.queryForList("SELECT * FROM task_field_target_registration WHERE tenant_id=? AND project_id=? AND task_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",tenant,project,p.taskId(),artifact);
        if(originalRegistrations.isEmpty())return inspectTyped(p,artifact,receiptHash);
        var row=one(originalRegistrations);
        JsonNode receipt=schema.validate("ProduceReceipt",schema.read(str(row,"receipt_json")));
        if(!receiptHash.equals(str(row,"receipt_sha256"))||!receiptHash.equals(schema.hash(receipt)))bad();
        var intentRow=one(db.queryForList("SELECT * FROM task_field_target_intent WHERE tenant_id=? AND write_id=? LIMIT 2 FOR UPDATE",tenant,str(row,"write_id")));
        var intent=schema.validate("TargetIntent",schema.read(str(intentRow,"intent_json")));var manifest=schema.validate("TargetManifest",intent.required("target_manifest"));
        if(!schema.hash(intent).equals(str(intentRow,"intent_sha256"))||!schema.hash(manifest).equals(str(row,"object_sha256"))
                ||!manifest.equals(schema.read(str(intentRow,"canonical_payload"))))bad();
        var ar=one(registry.artifact(artifact));var binding=one(registry.binding(artifact));var object=ar.reference();StoredObjectReferences.decode(ar.storedObject(),object);
        if(!tenant.equals(ar.tenantId())||!project.equals(ar.projectId())||!"STRICT_FIELD_TARGET".equals(ar.artifactKind())
                ||!object.sha256().equals(ar.contentSha256())||!schema.manifest().equals(ar.schemaManifest())
                ||!json.tree(object).equals(receipt.required("object_ref"))||!object.objectVersion().equals(str(row,"object_version"))
                ||!"TASK".equals(binding.resourceKind())||!p.taskId().equals(binding.resourceId())
                ||!TaskFieldTargetProtocol.SCHEMA_REF.equals(binding.schemaRef())||!schema.manifest().equals(binding.schemaDocumentSha256())
                ||!ar.accessSetId().equals(binding.accessSetId()))bad();
        var version=one(registry.version(artifact));if(!"ARTIFACT".equals(version.entityKind())||!project.equals(version.projectId())||!object.sha256().equals(version.contentSha256()))bad();
        var dependency=one(registry.manifest(ar.accessSetId()));var members=registry.members(ar.accessSetId());dependencies.verify(dependency,members);
        if(!dependency.manifestSha256().equals(binding.dependencyManifestSha256()))bad();lineage.require(artifact,members,limits);
        for(String field:List.of("tenant_id","project_id","task_id","write_id","artifact_id","registration_id"))if(!str(row,field).equals(receipt.required(field).textValue()))bad();
        if(!artifact.equals(intent.required("planned_artifact_id").textValue())||!str(row,"write_id").equals(intent.required("write_id").textValue()))bad();
        var replay=p.replay().content();var identity=manifest.required("target").required("identity");
        for(var pair:Map.of("tenant_id",tenant,"project_id",project,"task_id",p.taskId(),"plan_revision",replay.required("plan_revision").textValue(),"template_version",replay.required("plan").required("template_version").textValue()).entrySet())
            if(!pair.getValue().equals(identity.required(pair.getKey()).textValue()))bad();
        if(!replay.required("task_revision").equals(identity.required("task_revision"))||!replay.required("template").required("content_sha256").equals(identity.required("template_sha256"))
                ||!replay.required("plan").required("profile_revision").equals(manifest.required("profile_revision"))
                ||!replay.required("plan").required("content_sha256").equals(manifest.required("plan_sha256"))
                ||!replay.required("snapshot_id").equals(manifest.required("scope_snapshot")))bad();
        var entries=db.queryForList("SELECT entry_id,entry_json,entry_sha256,source_read_receipt_id FROM task_field_target_entry WHERE tenant_id=? AND write_id=? FOR UPDATE",tenant,str(row,"write_id"));
        if(entries.size()!=manifest.required("target").required("entries").size())bad();
        var historical=json.object().arrayNode();
        for(var entry:entries){var value=schema.read(str(entry,"entry_json"));if(!schema.hash(value).equals(str(entry,"entry_sha256"))||!contains(manifest.required("target").required("entries"),value))bad();
            var original=one(db.queryForList("SELECT receipt_manifest,receipt_sha256 FROM task_field_source_read_receipt WHERE tenant_id=? AND receipt_id=? LIMIT 2 FOR UPDATE",tenant,str(entry,"source_read_receipt_id")));
            var read=schema.validate("SourceReadReceipt",schema.read(str(original,"receipt_manifest")));if(!schema.hash(read).equals(str(original,"receipt_sha256"))||!p.taskId().equals(read.required("task_id").textValue()))bad();historical.add(read);
        }
        var selected=new ArrayList<TaskNodeInputProtocol.SourceSelection>();var grants=json.object().arrayNode();var execution=replay.required("execution_policy");
        for(var member:replay.required("scope_snapshot").required("members")){
            var pin=new TaskNodeInputProtocol.SourceSelection(text(member,"source_version"),text(member,"parse_version"),text(member,"index_version"),text(member,"approval_revision"),text(member,"binding_revision"),text(member,"rights_revision"),text(execution,"business_purpose"));
            selected.add(pin);var source=sources.require(a,new SourceReadProtocol.ReadRequirement(pin.sourceVersion(),pin.rightsRevision(),project,text(replay,"snapshot_id"),pin.parseVersion(),null,SourceReadProtocol.Use.FORMAL,pin.purpose(),text(execution,"environment"),text(execution,"region"),null));
            if(source.selection()==null||!pin.indexVersion().equals(source.selection().indexVersion())||!pin.bindingRevision().equals(source.selection().bindingRevision())||!pin.approvalRevision().equals(source.selection().approvalRevision()))bad();grants.add(TaskFieldAuthorityWitness.tree(source));
        }
        for(var read:historical)if(selected.stream().noneMatch(s->s.sourceVersion().equals(text(read,"source_version"))&&s.parseVersion().equals(text(read,"parse_version"))&&s.rightsRevision().equals(text(read,"rights_revision"))&&s.approvalRevision().equals(text(read,"approval_revision"))))bad();
        var pins=json.object();for(String field:List.of("task_revision","plan_revision","template_version","template_sha256"))pins.set(field,identity.required(field));
        for(String field:List.of("task_input_sha256","plan_sha256","profile_revision","scope_snapshot"))pins.set(field,manifest.required(field));pins.put("project_id",project);
        // Actual producer audit and immutable receipt are checked, not inferred from an artifact UUID.
        var audit=one(db.queryForList("SELECT action,resource_ref,result,request_id,trace_id FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",tenant,text(receipt,"audit_id")));
        if(!"task.field_target.registered".equals(str(audit,"action"))||!"SUCCESS".equals(str(audit,"result"))||!receipt.equals(schema.read(str(audit,"resource_ref")))||!str(row,"write_id").equals(str(audit,"request_id"))||!p.taskId().equals(str(audit,"trace_id")))bad();
        p.requireUnchanged();return new State(manifest,receipt,object,pins,List.copyOf(selected),schema.hash(List.of(manifest,receipt,pins,grants,historical)));
    }
    private State inspectTyped(TaskNodeProductionAuthority.Verified p,String artifact,String hash)throws Exception{
        var row=one(db.queryForList("SELECT target_revision,target_digest,receipt_sha256 FROM task_strict_field_typed_registration WHERE tenant_id=? AND project_id=? AND task_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),p.taskId(),artifact));if(!hash.equals(str(row,"receipt_sha256")))bad();
        var target=typedTargets.requireFixedRun(p,str(row,"target_revision"),str(row,"target_digest"));var registered=typedLedger.registered(target,artifact);if(!hash.equals(registered.receiptSha256()))bad();
        var manifest=target.manifest();var pins=json.object();var identity=manifest.required("target").required("identity");for(String key:List.of("task_revision","plan_revision","template_version","template_sha256"))pins.set(key,identity.required(key));for(String key:List.of("task_input_sha256","plan_sha256","profile_revision","scope_snapshot"))pins.set(key,manifest.required(key));pins.put("project_id",p.projectId());
        var selected=new ArrayList<TaskNodeInputProtocol.SourceSelection>();var replay=p.replay().content();for(var member:replay.required("scope_snapshot").required("members"))selected.add(new TaskNodeInputProtocol.SourceSelection(text(member,"source_version"),text(member,"parse_version"),text(member,"index_version"),text(member,"approval_revision"),text(member,"binding_revision"),text(member,"rights_revision"),text(replay.required("execution_policy"),"business_purpose")));
        target.requireApproved();registered.requireUnchanged();p.requireUnchanged();return new State(manifest,registered.receipt(),registered.reference(),pins,List.copyOf(selected),schema.hash(List.of(manifest,registered.receipt(),target.witness(),target.decision())));
    }
    public final class Verified {
        private final TaskNodeProductionAuthority.Verified production;private final String artifact,receiptHash;private final State state;
        private Verified(TaskNodeProductionAuthority.Verified production,String artifact,String hash,State state){this.production=production;this.artifact=artifact;receiptHash=hash;this.state=state;}
        public void requireUnchanged()throws Exception{if(!state.fingerprint().equals(inspect(production,artifact,receiptHash).fingerprint()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
        public void requireFor(EnterpriseAccess.Admission a,String task)throws Exception{requireUnchanged();if(a!=production.admission()||!task.equals(production.taskId()))deny();}
        public String artifactId(){return artifact;}public String taskId(){return production.taskId();}public String projectId(){return production.projectId();}
        public String registrationId(){return typed()?text(state.receipt(),"write_id"):text(state.receipt(),"registration_id");}
        public boolean typed(){return TaskStrictFieldTypedProtocol.typed(state.manifest().path("schema_ref").asText());}
        TaskStrictFieldTypedTargetService.Verified typedTarget()throws Exception{if(!typed())deny();return typedTargets.requireFixedRun(production,text(state.receipt(),"target_revision"),text(state.receipt(),"target_digest"));}public String receiptSha256(){return receiptHash;}
        public String mappingRevision(){return typed()?text(state.receipt(),"target_revision"):text(state.receipt(),"assignment_id");}public String mappingSha256(){return typed()?text(state.receipt(),"target_digest"):text(state.receipt(),"assignment_sha256");}
        public ObjectRef reference(){return state.object();}public String targetSha256(){return state.object().sha256();}public String targetSchemaSha256()throws Exception{return typed()?typedLedger.targetDigest(typedTarget().request().schemaVersion()):schema.manifest();}
        public JsonNode manifest(){return state.manifest().deepCopy();}public JsonNode receipt(){return state.receipt().deepCopy();}public JsonNode pins(){return state.pins().deepCopy();}
        public List<String> occurrenceIds(){var ids=new ArrayList<String>();state.manifest().required("target").required("entries").forEach(e->ids.add(text(e,"field_id")));return List.copyOf(ids);}
        public List<TaskNodeInputProtocol.SourceSelection> fixedSources(){return state.sources();}
    }
    private static boolean contains(JsonNode values,JsonNode value){for(var item:values)if(item.equals(value))return true;return false;}
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String text(JsonNode n,String key){return n.required(key).textValue();}
    private static String str(Map<String,Object> n,String key){var v=n.get(key);if(v==null)bad();return v.toString();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
