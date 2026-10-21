package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Immutable proposed assignments. Saving does not approve fields or produce an artifact. */
public final class TaskFieldMappingService {
    private static final String OPERATION="TASK-FIELD-MAPPING";
    private static final String ACTION="task.field_mapping.saved";
    private final EnterpriseAccess access;
    private final TaskFieldTargetBasis basis;
    private final TaskPlanRelationStore relations;
    private final TaskFieldTargetSchema schema;
    private final TaskFieldTargetPolicy policy;
    private final PermissionSchema json;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority resources;
    private final TaskService tasks;
    private final com.ruoyi.medical.identity.IdentityDirectoryPolicy identity;
    private final JdbcTemplate db;
    private final TaskFieldInventory inventories;
    public TaskFieldMappingService(EnterpriseAccess access,TaskFieldTargetBasis basis,TaskPlanRelationStore relations,
            TaskFieldTargetSchema schema,TaskFieldTargetPolicy policy,PermissionSchema json,PermissionMapper permissions,
            ResourceReadAuthority resources,TaskService tasks,DataSource data,com.ruoyi.medical.identity.IdentityDirectoryPolicy identity){
        this.access=access;this.basis=basis;this.relations=relations;this.schema=schema;this.policy=policy;this.json=json;
        this.permissions=permissions;this.resources=resources;this.tasks=tasks;this.identity=identity;db=new JdbcTemplate(data);db.setQueryTimeout(5);
        inventories=new TaskFieldInventory(json);
    }
    public JsonNode author(ApplicationSessionIdentity.Verified subject,String context,String taskId,String key,byte[] bytes)throws Exception{
        key(key);WireValues.uuid(taskId);var request=schema.request("MappingAuthor",bytes);
        return access.execute(subject,a->checked(()->{
            context(a,context);var task=tasks.fixedTask(a,taskId);requireProject(task.head().projectId(),request);
            resources.require(a,scope(a,task.head().projectId(),taskId),TaskFieldTargetProtocol.MAPPING_WRITE,TaskFieldTargetProtocol.PREPARATION_PURPOSE);
            var previous=recovery(a,taskId,key);if(previous!=null){
                if(!schema.hash(request).equals(text(previous,"request_sha256")))conflict();return previous;
            }
            policy.requireConfigured();var current=basis.require(a,taskId,TaskFieldTargetProtocol.MAPPING_WRITE);current.requirePins(request);
            if(request.required("mappings").size()>policy.maximumMappings())limit();
            var head=head(a,taskId);String revision=head==null?"0":string(head,"revision");
            if(!revision.equals(text(request,"expected_revision")))staleRevision();
            String base=request.required("base_mapping_id").isNull()?null:text(request,"base_mapping_id");
            if(!Objects.equals(base,head==null?null:string(head,"mapping_revision")))staleRevision();
            String id=id(),operation=id(),audit=id(),event=id();var now=Objects.requireNonNull(permissions.databaseTime());
            var manifest=manifest(current,id,request.required("mappings"));String digest=schema.hash(manifest),next=RunCreateSchema.next(revision);
            var receipt=common(a,taskId,task.head().projectId(),operation,audit,event,schema.hash(request),now,current.classification());
            receipt.put("assignment_id",id);receipt.put("assignment_sha256",digest);receipt.put("task_revision",task.head().taskRevision());
            receipt.put("plan_revision",current.plan().planRevision());receipt.put("revision",next);receipt.put("status","PROPOSED");schema.validate("MappingReceipt",receipt);
            var witness=json.object();witness.set("request",request);witness.set("manifest",manifest);witness.set("authorizations",current.authorizations());
            current.requireUnchanged();
            one(db.update("INSERT INTO version_registry(tenant_id,owner_project_id,version_id,entity_kind,content_sha256) VALUES(?,?,?,'TASK_FIELD_MAPPING_REVISION',?)",a.context().tenant_id(),task.head().projectId(),id,digest));
            journal(a,task.head().projectId(),operation,audit,event,key,id,request,receipt,witness,current.classification(),now);
            one(db.update("""
                INSERT INTO task_field_mapping_revision(tenant_id,project_id,task_id,task_revision,task_input_sha256,plan_revision,plan_sha256,
                template_version,template_sha256,profile_revision,scope_snapshot,mapping_revision,base_mapping_id,content_json,content_sha256,
                request_sha256,idempotency_key,operation_id,audit_id,event_id,receipt_json,expected_revision,authority_grant,authority_revision,
                auth_epoch,governance_epoch,reason,status,schema_version,schema_manifest_sha256,created_by,created_at,classification)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PROPOSED','1.0.0',?,?,?,?)
                """,a.context().tenant_id(),task.head().projectId(),taskId,task.head().taskRevision(),text(request,"task_input_sha256"),
                current.plan().planRevision(),text(request,"plan_sha256"),text(request,"template_version"),text(request,"template_sha256"),
                text(request,"profile_revision"),text(request,"scope_snapshot"),id,base,schema.canonical(manifest),digest,schema.hash(request),key,
                operation,audit,event,schema.canonical(receipt),revision,current.grant().grantId(),current.grant().revision().toString(),
                a.watermarks().authEpoch(),a.watermarks().governanceEpoch(),text(request,"reason"),schema.manifest(),a.actorId(),now,current.classification()));
            if(head==null)one(db.update("""
                INSERT INTO task_field_mapping_head(tenant_id,project_id,task_id,task_revision,plan_revision,mapping_revision,revision,
                schema_version,schema_manifest_sha256,created_by,created_at,updated_at,classification) VALUES(?,?,?,?,?,?,?,'1.0.0',?,?,?,?,?)
                """,a.context().tenant_id(),task.head().projectId(),taskId,task.head().taskRevision(),current.plan().planRevision(),id,next,schema.manifest(),a.actorId(),now,now,current.classification()));
            else one(db.update("""
                UPDATE task_field_mapping_head SET task_revision=?,plan_revision=?,mapping_revision=?,revision=?,updated_at=?
                WHERE tenant_id=? AND project_id=? AND task_id=? AND revision=? AND mapping_revision=?
                """,task.head().taskRevision(),current.plan().planRevision(),id,next,now,a.context().tenant_id(),task.head().projectId(),taskId,revision,base));
            var saved=readRow(a,taskId,id);if(!receipt.equals(saved.receipt()))bad();current.requireUnchanged();advance(a);return receipt;
        }));
    }
    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String taskId,String mappingId)throws Exception{
        WireValues.uuid(taskId);WireValues.uuid(mappingId);
        return access.execute(subject,a->checked(()->{context(a,context);tasks.fixedTask(a,taskId);return view(a,readRow(a,taskId,mappingId));}));
    }
    public JsonNode recover(ApplicationSessionIdentity.Verified subject,String context,String taskId,byte[] bytes)throws Exception{
        var request=schema.request("Recovery",bytes);return access.execute(subject,a->checked(()->{
            context(a,context);var task=tasks.fixedTask(a,taskId);requireProject(task.head().projectId(),request);
            return recovery(a,taskId,text(request,"original_idempotency_key"));
        }));
    }
    JsonNode currentView(EnterpriseAccess.Admission a,String taskId)throws Exception{
        tasks.fixedTask(a,taskId);var current=head(a,taskId);
        return current==null?null:view(a,readRow(a,taskId,string(current,"mapping_revision")));
    }
    Verified requireCurrent(TaskFieldTargetBasis.Verified proof,String mappingId,String digest)throws Exception{
        var row=readRow(proof.admission(),proof.task().head().taskId(),mappingId);var head=head(proof.admission(),row.task());
        if(head==null||!mappingId.equals(string(head,"mapping_revision"))||!digest.equals(row.hash()))bad();
        proof.requirePins(row.manifest());
        var recomputed=manifest(proof,mappingId,row.manifest().required("mappings"));
        if(!recomputed.equals(row.manifest()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        return new Verified(proof,row,string(head,"revision"));
    }
    ObjectNode manifest(TaskFieldTargetBasis.Verified proof,String id,JsonNode mappings)throws Exception{
        var fixed=proof.task().head();var template=proof.execution().template().view().template().content();
        var projection=relations.read(proof.admission(),fixed.projectId(),fixed.taskId(),proof.plan().planRevision(),proof.execution().validation());
        var manifest=proof.pins();manifest.put("schema_version","1.0.0");manifest.put("tenant_id",fixed.tenantId());manifest.put("task_id",fixed.taskId());
        manifest.put("assignment_id",id);manifest.put("status","PROPOSED");manifest.set("mappings",mappings.deepCopy());
        manifest.set("inventory",inventories.derive(template,projection,proof.execution().template().view().conditions(),mappings,policy.maximumInventoryFields()));
        manifest.set("template_nodes",json.tree(template.nodes()));manifest.set("repeat_definitions",json.tree(template.repeat_definitions()));
        manifest.set("attachment_requirements",json.tree(template.attachment_requirements()));manifest.set("condition_results",json.tree(proof.execution().template().view().conditions()));
        schema.validate("MappingManifest",manifest);return manifest;
    }
    final class Verified {
        private final TaskFieldTargetBasis.Verified proof;private final Row row;private final String revision;
        private Verified(TaskFieldTargetBasis.Verified proof,Row row,String revision){this.proof=proof;this.row=row;this.revision=revision;}
        void requireUnchanged()throws Exception{proof.requireUnchanged();var fresh=requireCurrent(proof,row.id(),row.hash());if(!row.receipt().equals(fresh.row.receipt()))bad();}
        JsonNode manifest(){proof.admission();return row.manifest().deepCopy();}
        String revision(){proof.admission();return revision;}String mappingRevision(){proof.admission();return row.id();}
        String mappingSha256(){proof.admission();return row.hash();}String actorId(){proof.admission();return row.actor();}
    }
    private record Row(String task,String project,String id,String hash,JsonNode manifest,JsonNode receipt,String actor){}
    private Row readRow(EnterpriseAccess.Admission a,String task,String id)throws Exception{
        var rows=db.queryForList("""
            SELECT m.*,v.content_sha256 AS registered_sha256,v.owner_project_id AS registered_project,v.entity_kind
            FROM task_field_mapping_revision m JOIN version_registry v ON v.tenant_id=m.tenant_id AND v.version_id=m.mapping_revision
            WHERE m.tenant_id=? AND m.task_id=? AND m.mapping_revision=?
            """,a.context().tenant_id(),task,id);if(rows.size()!=1)bad();var row=rows.get(0);
        if(!a.context().tenant_id().equals(string(row,"tenant_id"))||!task.equals(string(row,"task_id"))||!id.equals(string(row,"mapping_revision")))bad();
        var manifest=schema.validate("MappingManifest",schema.read(string(row,"content_json")));
        var receipt=schema.validate("MappingReceipt",schema.read(string(row,"receipt_json")));String hash=string(row,"content_sha256");
        if(!hash.equals(schema.hash(manifest))||!hash.equals(string(row,"registered_sha256"))||!"TASK_FIELD_MAPPING_REVISION".equals(string(row,"entity_kind"))
                ||!string(row,"project_id").equals(string(row,"registered_project"))||!schema.manifest().equals(string(row,"schema_manifest_sha256"))
                ||!"1.0.0".equals(string(row,"schema_version"))||!"PROPOSED".equals(string(row,"status"))||!identity.classification().equals(string(row,"classification")))bad();
        for(String key:List.of("tenant_id","project_id","task_id","task_revision","task_input_sha256","plan_revision","plan_sha256","template_version","template_sha256","profile_revision","scope_snapshot"))
            if(!string(row,key).equals(text(manifest,key)))bad();
        if(!id.equals(text(manifest,"assignment_id"))||!hash.equals(text(receipt,"assignment_sha256"))||!id.equals(text(receipt,"assignment_id")))bad();
        for(String key:List.of("tenant_id","project_id","task_id","task_revision","plan_revision","operation_id","audit_id","event_id","request_sha256","schema_manifest_sha256","created_by","classification"))
            if(!string(row,key).equals(text(receipt,key)))bad();
        if(!utc(time(row,"created_at")).equals(text(receipt,"created_at"))||!RunCreateSchema.next(string(row,"expected_revision")).equals(text(receipt,"revision")))bad();
        var request=json.object();for(String key:List.of("schema_version","project_id","task_revision","task_input_sha256","plan_revision","plan_sha256","template_version","template_sha256","profile_revision","scope_snapshot","mappings"))request.set(key,manifest.required(key).deepCopy());
        request.put("expected_revision",string(row,"expected_revision"));request.put("reason",string(row,"reason"));
        if(row.get("base_mapping_id")==null)request.putNull("base_mapping_id");else request.put("base_mapping_id",string(row,"base_mapping_id"));
        schema.validate("MappingAuthor",request);if(!schema.hash(request).equals(string(row,"request_sha256")))bad();
        verifyJournal(a,row,request,manifest,receipt);
        return new Row(task,string(row,"project_id"),id,hash,manifest,receipt,string(row,"created_by"));
    }
    private JsonNode recovery(EnterpriseAccess.Admission a,String task,String key)throws Exception{
        key(key);var rows=db.queryForList("SELECT target_id,request_sha256,response_ref,state,http_status FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_name=? AND idempotency_key=?",a.context().tenant_id(),a.actorId(),OPERATION,key);
        if(rows.size()>1)bad();if(rows.isEmpty())return null;var operation=rows.get(0);
        if(!"COMPLETED".equals(string(operation,"state"))||!"201".equals(string(operation,"http_status")))bad();
        var row=readRow(a,task,string(operation,"target_id"));if(!row.actor().equals(a.actorId())||!row.receipt().equals(schema.read(string(operation,"response_ref"))))bad();return row.receipt().deepCopy();
    }
    private JsonNode view(EnterpriseAccess.Admission a,Row row)throws Exception{
        var head=head(a,row.task());var current=tasks.fixedTask(a,row.task()).head();var value=json.object().put("schema_version","1.0.0").put("assignment_sha256",row.hash()).put("revision",text(row.receipt(),"revision"));
        value.set("manifest",row.manifest());value.put("is_current",head!=null&&row.id().equals(string(head,"mapping_revision"))&&current.taskRevision().equals(text(row.manifest(),"task_revision"))&&Objects.equals(current.planRevision(),text(row.manifest(),"plan_revision")));return schema.validate("MappingView",value);
    }
    private Map<String,Object> head(EnterpriseAccess.Admission a,String task){
        var rows=db.queryForList("SELECT * FROM task_field_mapping_head WHERE tenant_id=? AND task_id=? FOR UPDATE",a.context().tenant_id(),task);
        if(rows.size()>1)bad();if(rows.isEmpty())return null;var row=rows.get(0);
        if(!a.context().tenant_id().equals(string(row,"tenant_id"))||!task.equals(string(row,"task_id"))
                ||!"1.0.0".equals(string(row,"schema_version"))||!schema.manifest().equals(string(row,"schema_manifest_sha256"))
                ||!identity.classification().equals(string(row,"classification"))||new java.math.BigInteger(string(row,"revision")).signum()<=0
                ||time(row,"updated_at").isBefore(time(row,"created_at")))bad();
        for(String key:List.of("project_id","task_revision","plan_revision","mapping_revision","created_by"))WireValues.uuid(string(row,key));return row;
    }
    private void journal(EnterpriseAccess.Admission a,String project,String op,String audit,String event,String key,String id,JsonNode request,JsonNode receipt,JsonNode witness,String classification,LocalDateTime now)throws Exception{
        one(db.update("INSERT INTO operation_record(tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,http_status,response_ref,target_id,error_code,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,'COMPLETED',201,?,?,NULL,'1.0.0',?,?,?,?,1,?)",a.context().tenant_id(),op,a.actorId(),OPERATION,key,schema.hash(request),schema.canonical(receipt),id,schema.manifest(),a.actorId(),now,classification,now));
        one(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,reason_code,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',NULL,?,?,'1.0.0',?,?,?,?)",a.context().tenant_id(),project,audit,a.actorId(),ACTION,schema.canonical(witness),op,op,schema.manifest(),a.actorId(),now,classification));
        one(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at,published_at) VALUES(?,?,?,?,?,?,?,NULL)",a.context().tenant_id(),event,id,text(receipt,"revision"),ACTION,schema.canonical(witness),now));
    }
    private void verifyJournal(EnterpriseAccess.Admission a,Map<String,Object> row,JsonNode request,JsonNode manifest,JsonNode receipt)throws Exception{
        var operations=db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=?",a.context().tenant_id(),string(row,"operation_id"));if(operations.size()!=1)bad();var op=operations.get(0);
        for(String key:List.of("request_sha256","schema_manifest_sha256","created_by","classification"))if(!string(row,key).equals(string(op,key)))bad();
        if(!OPERATION.equals(string(op,"operation_name"))||!string(row,"created_by").equals(string(op,"subject_id"))||!string(row,"idempotency_key").equals(string(op,"idempotency_key"))||!"COMPLETED".equals(string(op,"state"))||!"201".equals(string(op,"http_status"))||!receipt.equals(schema.read(string(op,"response_ref"))))bad();
        var audits=db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=?",a.context().tenant_id(),string(row,"audit_id"));
        var events=db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=?",a.context().tenant_id(),string(row,"event_id"));if(audits.size()!=1||events.size()!=1)bad();
        var audit=audits.get(0);var event=events.get(0);var witness=schema.read(string(audit,"resource_ref"));
        if(!witness.equals(schema.read(string(event,"payload")))||!request.equals(witness.required("request"))||!manifest.equals(witness.required("manifest")))bad();
        if(!string(row,"project_id").equals(string(audit,"project_id"))||!string(row,"created_by").equals(string(audit,"subject_id"))||!string(row,"operation_id").equals(string(audit,"request_id"))||!ACTION.equals(string(audit,"action"))||!"SUCCESS".equals(string(audit,"result"))||!ACTION.equals(string(event,"event_type"))||!string(row,"mapping_revision").equals(string(event,"aggregate_id")))bad();
        if(!string(row,"tenant_id").equals(string(op,"tenant_id"))||!string(row,"operation_id").equals(string(op,"operation_id"))
                ||!string(row,"mapping_revision").equals(string(op,"target_id"))||!"1.0.0".equals(string(op,"schema_version"))
                ||!"1".equals(string(op,"revision"))||op.get("error_code")!=null||!time(row,"created_at").equals(time(op,"created_at"))
                ||!time(row,"created_at").equals(time(op,"updated_at")))bad();
        for(String key:List.of("tenant_id","created_by","classification","schema_version","schema_manifest_sha256"))
            if(!string(row,key).equals(string(audit,key)))bad();
        if(!string(row,"audit_id").equals(string(audit,"audit_id"))||audit.get("reason_code")!=null
                ||!string(row,"operation_id").equals(string(audit,"trace_id"))||!time(row,"created_at").equals(time(audit,"created_at")))bad();
        if(!string(row,"tenant_id").equals(string(event,"tenant_id"))||!string(row,"event_id").equals(string(event,"event_id"))
                ||!text(receipt,"revision").equals(string(event,"aggregate_revision"))||!time(row,"created_at").equals(time(event,"occurred_at")))bad();
        var auth=witness.required("authorizations");
        if(!string(row,"tenant_id").equals(text(auth,"tenant_id"))||!string(row,"task_id").equals(text(auth,"task_id"))
                ||!TaskFieldTargetProtocol.MAPPING_WRITE.equals(text(auth,"action"))||!TaskFieldTargetProtocol.PREPARATION_PURPOSE.equals(text(auth,"purpose"))
                ||!string(row,"classification").equals(text(auth,"classification")))bad();
        for(String key:List.of("project_id","task_revision","task_input_sha256","plan_revision","plan_sha256","template_version","template_sha256","profile_revision","scope_snapshot"))
            if(!string(row,key).equals(text(auth.required("pins"),key)))bad();
        if(!string(row,"authority_grant").equals(auth.at("/grant/grant_id").asText()))bad();
        if(!string(row,"authority_revision").equals(auth.at("/grant/revision").asText())||!string(row,"auth_epoch").equals(text(auth,"auth_epoch"))||!string(row,"governance_epoch").equals(text(auth,"governance_epoch"))||!string(row,"created_by").equals(text(auth,"actor_id")))bad();
    }
    private ObjectNode common(EnterpriseAccess.Admission a,String task,String project,String op,String audit,String event,String hash,LocalDateTime now,String classification){return json.object().put("schema_version","1.0.0").put("tenant_id",a.context().tenant_id()).put("project_id",project).put("task_id",task).put("operation_id",op).put("audit_id",audit).put("event_id",event).put("request_sha256",hash).put("schema_manifest_sha256",schema.manifest()).put("created_by",a.actorId()).put("created_at",utc(now)).put("classification",classification);}
    private void advance(EnterpriseAccess.Admission a){one(db.update("UPDATE tenant SET governance_epoch=governance_epoch+1 WHERE tenant_id=? AND auth_epoch=? AND governance_epoch=?",a.context().tenant_id(),a.watermarks().authEpoch(),a.watermarks().governanceEpoch()));}
    static void key(String key){if(key==null||!key.matches("[!-~]{16,128}"))throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);}
    private static PermissionProtocol.Scope scope(EnterpriseAccess.Admission a,String project,String task){return new PermissionProtocol.Scope(a.context().tenant_id(),project,"TASK",task);}
    private static String string(Map<String,Object> row,String key){var value=row.get(key);if(value==null)bad();return value.toString();}
    private static LocalDateTime time(Map<String,Object> row,String key){var value=row.get(key);if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime();if(value instanceof LocalDateTime t)return t;bad();return null;}
    private static String utc(LocalDateTime time){return time.toInstant(ZoneOffset.UTC).toString();}
    private static String text(JsonNode node,String key){var value=node.required(key);if(!value.isTextual())bad();return value.textValue();}
    private static void context(EnterpriseAccess.Admission a,String value){if(!a.context().context_revision().equals(value))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
    private static void requireProject(String project,JsonNode request){if(!project.equals(text(request,"project_id")))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
    private static String id(){return UUID.randomUUID().toString();}
    private static <T>T checked(ThrowingSupplier<T> action){return action.get();}
    private static void one(int count){if(count!=1)bad();}
    private static void conflict(){throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);}
    private static void staleRevision(){throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
    private static void limit(){throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
