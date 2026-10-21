package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.SourceExactMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Authenticated pre-run source authority. Caller pins are requests, never proofs. */
@Component
public final class TaskFieldSourceAuthority {
    static final String PURPOSE = "STRICT_FIELD_PREPARATION";
    private static final Set<String> KINDS = Set.of("MODEL", "PTR_NUMBER", "DOCUMENT_NUMBER", "STANDARD_NUMBER", "STANDARD_YEAR");
    private final TaskService tasks;
    private final TaskPlanService plans;
    private final ResourceReadAuthority resources;
    private final SourceReadAuthority sources;
    private final SourceExactMapper exacts;
    private final SourceExactSchema exact;
    private final SourceExactPolicy exactPolicy;
    private final TaskFieldTargetSchema schema;
    private final TaskFieldTargetPolicy policy;
    private final PermissionSchema json;
    private final JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired
    public TaskFieldSourceAuthority(TaskService tasks, TaskPlanService plans, ResourceReadAuthority resources,
            SourceReadAuthority sources, SourceExactMapper exacts, SourceExactSchema exact, SourceExactPolicy exactPolicy,
            TaskFieldTargetSchema schema, TaskFieldTargetPolicy policy, PermissionSchema json,
            @Qualifier("masterDataSource") DataSource data) {
        this(tasks,plans,resources,sources,exacts,exact,exactPolicy,schema,policy,json,new JdbcTemplate(data));
    }
    TaskFieldSourceAuthority(TaskService tasks, TaskPlanService plans, ResourceReadAuthority resources,
            SourceReadAuthority sources, SourceExactMapper exacts, SourceExactSchema exact, SourceExactPolicy exactPolicy,
            TaskFieldTargetSchema schema, TaskFieldTargetPolicy policy, PermissionSchema json, JdbcTemplate jdbc) {
        this.tasks=tasks; this.plans=plans; this.resources=resources; this.sources=sources; this.exacts=exacts;
        this.exact=exact; this.exactPolicy=exactPolicy; this.schema=schema; this.policy=policy; this.json=json;this.jdbc=jdbc;
    }

    public record Request(String taskId, String mappingRevision, String mappingSha256, String entryKey) {
        public Request { WireValues.uuid(taskId); WireValues.uuid(mappingRevision); WireValues.hash(mappingSha256); WireValues.uuid(entryKey); }
    }

    Proof require(EnterpriseAccess.Admission admission, String context, Request request) throws Exception {
        return require(admission,context,request,"task.field_target.produce");
    }

    Proof require(EnterpriseAccess.Admission admission, String context, Request request, String action) throws Exception {
        boolean producing="task.field_target.produce".equals(action);
        if(!producing && !Set.of("task.field_target_input.read","task.field_target_input.write","task.field_target_input.approve").contains(action)) fail(ErrorCode.ACCESS_DENIED);
        Objects.requireNonNull(request); policy.requireConfigured(); exactPolicy.requireConfigured();
        if(admission.simulationWorker() || admission.formalWorker() || admission.controlWorker() || admission.recoveryWorker()) fail(ErrorCode.ACCESS_DENIED);
        if(!context.equals(admission.context().context_revision())) fail(ErrorCode.CONTEXT_CHANGED);
        if(!TenantScope.requireCurrent().tenantId().equals(admission.context().tenant_id())) fail(ErrorCode.ACCESS_DENIED);
        var fixed=tasks.fixedTask(admission,request.taskId()); var head=fixed.head();
        if(!head.tenantId().equals(admission.context().tenant_id()) || !head.taskId().equals(request.taskId())) fail(ErrorCode.ACCESS_DENIED);
        var producer=resources.require(admission,new PermissionProtocol.Scope(head.tenantId(),head.projectId(),"TASK",head.taskId()),
                action,producing?PURPOSE:"TASK_FIELD_TARGET_INPUT");
        if(head.planRevision()==null) fail(ErrorCode.PLAN_STALE);
        var row=one(jdbc.queryForList("""
            SELECT m.*,v.content_sha256 AS registered_sha256,v.owner_project_id AS registry_project,v.entity_kind
            FROM task_field_mapping_revision m
            JOIN version_registry v ON v.tenant_id=m.tenant_id AND v.version_id=m.mapping_revision
            JOIN task_field_mapping_head h ON h.tenant_id=m.tenant_id AND h.project_id=m.project_id AND h.task_id=m.task_id
              AND h.task_revision=m.task_revision AND h.plan_revision=m.plan_revision
            WHERE m.tenant_id=? AND m.project_id=? AND m.task_id=? AND m.mapping_revision=?
              AND (?=FALSE OR h.mapping_revision=m.mapping_revision) FOR UPDATE
            """,head.tenantId(),head.projectId(),head.taskId(),request.mappingRevision(),producing));
        var mapping=schema.read(text(row,"content_json")); schema.validate("MappingManifest",mapping);
        String mappingHash=schema.hash(mapping);
        if(!mappingHash.equals(request.mappingSha256()) || !mappingHash.equals(text(row,"content_sha256"))
                || !mappingHash.equals(text(row,"registered_sha256")) || !head.projectId().equals(text(row,"registry_project"))
                || !"TASK_FIELD_MAPPING_REVISION".equals(text(row,"entity_kind"))
                || !"1.0.0".equals(text(row,"schema_version")) || !schema.manifest().equals(text(row,"schema_manifest_sha256"))) mismatch();
        for(var pin:Map.of("tenant_id",head.tenantId(),"project_id",head.projectId(),"task_id",head.taskId(),
                "task_revision",head.taskRevision(),"plan_revision",Objects.requireNonNull(head.planRevision()),
                "template_version",head.templateVersion(),"profile_revision",head.profileRevision(),"scope_snapshot",head.snapshotId(),
                "assignment_id",request.mappingRevision()).entrySet()) if(!pin.getValue().equals(mapping.required(pin.getKey()).textValue())) mismatch();
        if(!head.taskRevision().equals(text(row,"task_revision")) || !head.planRevision().equals(text(row,"plan_revision"))
                || !head.snapshotId().equals(text(row,"scope_snapshot")) || !"PROPOSED".equals(mapping.required("status").textValue())
                || !fixed.input().requestHash().equals(mapping.required("task_input_sha256").textValue())) mismatch();
        for(String name:List.of("tenant_id","project_id","task_id","task_revision","task_input_sha256","plan_revision","plan_sha256",
                "template_version","template_sha256","profile_revision","scope_snapshot","status"))
            if(!mapping.required(name).textValue().equals(text(row,name))) mismatch();
        if(!request.mappingRevision().equals(text(row,"mapping_revision"))) mismatch();
        var plan=plans.readFixed(admission,fixed,head.planRevision(),true);
        var execution=producing?plans.execution(admission,fixed,plan):null;
        if(!"CONFIRMED".equals(plan.status()) || !head.taskRevision().equals(plan.taskRevision())
                || !head.planRevision().equals(plan.planRevision()) || !head.snapshotId().equals(plan.snapshotId())
                || !plan.contentSha256().equals(mapping.required("plan_sha256").textValue())) mismatch();
        if(execution!=null && !execution.template().view().template().content_sha256().equals(mapping.required("template_sha256").textValue())) mismatch();
        var sourceProof=execution==null?plans.sourceProof(admission,head.projectId(),head.taskId(),head.taskRevision(),head.snapshotId(),head.profileRevision(),true):execution.sources();
        var entries=mapping.required("mappings"); JsonNode selected=null;
        for(var item:entries) if(request.entryKey().equals(item.required("entry_key").textValue())) {
            if(selected!=null) mismatch(); selected=item;
        }
        if(selected==null || selected.required("source_exact").isNull()) fail(ErrorCode.SOURCE_NOT_READY);
        String definitionId=selected.required("field_definition_id").textValue();
        var definition=one(jdbc.queryForList("""
            SELECT field_id,node_id,value_type FROM field_definition
            WHERE tenant_id=? AND template_version=? AND field_id=? FOR UPDATE
            """,head.tenantId(),head.templateVersion(),definitionId));
        if(!definitionId.equals(text(definition,"field_id")) || !selected.required("node_id").textValue().equals(text(definition,"node_id"))) mismatch();
        if(!"TEXT".equals(text(definition,"value_type"))) fail(ErrorCode.SCHEMA_UNSUPPORTED);
        if(!"APPLICABLE".equals(selected.required("applicability").textValue())
                || "UNRESOLVED".equals(selected.required("model_context").textValue())
                || "UNRESOLVED".equals(selected.required("condition_context").textValue())) fail(ErrorCode.CONDITION_UNKNOWN);
        var reference=selected.required("source_exact"); String sourceVersion=reference.required("source_version").textValue();
        var source=one(sourceProof.stream().filter(item -> sourceVersion.equals(item.source().sourceVersion())).toList());
        var selection=Objects.requireNonNull(source.selection());
        // The plan's source proof resolves the pinned business purpose. Never use the UI purpose as data-use rights.
        var requirement=new SourceReadProtocol.ReadRequirement(sourceVersion,source.rights().rightsRevision(),head.projectId(),
                head.snapshotId(),selection.parseVersion(),null,SourceReadProtocol.Use.FORMAL,source.rights().purpose(),policy.environment(),policy.region(),null);
        var actual=sources.require(admission,requirement);
        if(!actual.equals(source)) fail(ErrorCode.SCOPE_STALE);
        var field=verifyExact(admission,reference,source,head.projectId());
        var processing=processing(head.tenantId(),sourceVersion,selection.parseVersion(),source.source().originalSha256());
        if(new BigInteger(processing.original().sizeBytes()).compareTo(BigInteger.valueOf(policy.maximumOriginalBytes()))>0
                || new BigInteger(processing.parsed().sizeBytes()).compareTo(BigInteger.valueOf(policy.maximumParsedBytes()))>0) fail(ErrorCode.CONTEXT_LIMIT);
        var witness=json.object(); witness.put("schema_version","1.0.0"); witness.set("request",json.tree(request));
        witness.put("tenant_id",head.tenantId()); witness.put("project_id",head.projectId()); witness.put("actor_id",admission.actorId());
        witness.put("action",action); witness.put("session_id",admission.subject().sessionId()); witness.set("context",json.tree(admission.context()));
        witness.put("task_input_sha256",fixed.input().requestHash()); witness.put("task_revision",head.taskRevision());
        witness.put("plan_revision",head.planRevision()); witness.put("plan_sha256",plan.contentSha256());
        witness.put("snapshot_id",head.snapshotId()); witness.put("template_version",head.templateVersion());
        witness.put("template_sha256",mapping.required("template_sha256").textValue()); witness.put("profile_revision",head.profileRevision());
        witness.set("source",TaskFieldAuthorityWitness.tree(source)); witness.set("producer_grant",TaskFieldAuthorityWitness.tree(producer));
        witness.set("source_exact",reference.deepCopy()); witness.put("field_sha256",schema.hash(json.tree(field)));
        witness.set("original_object",json.tree(processing.original())); witness.set("parsed_object",json.tree(processing.parsed()));
        witness.put("processing_project_id",processing.projectId()); witness.put("processing_attempt_id",processing.attempt()); witness.put("processing_command_sha256",schema.hash(processing.command()));
        witness.put("processing_result_sha256",schema.hash(processing.result())); witness.put("processor_manifest_sha256",schema.hash(processing.processorManifest())); witness.put("purpose",source.rights().purpose());
        witness.put("environment",policy.environment()); witness.put("region",policy.region());
        return new Proof(request,head,source,field,reference,processing,text(row,"classification"),witness,schema.hash(witness));
    }

    void requireSame(Proof expected, Proof actual) {
        if(!expected.authoritySha256.equals(actual.authoritySha256) || !expected.authority.equals(actual.authority)) fail(ErrorCode.SCOPE_STALE);
    }

    private SourceExactProtocol.Field verifyExact(EnterpriseAccess.Admission admission,JsonNode reference,
            SourceReadProtocol.VerifiedSource source,String project) throws Exception {
        String version=reference.required("source_version").textValue(), revision=reference.required("field_revision").textValue();
        var values=new HashMap<String,Object>(); values.put("sourceVersion",version); values.put("fieldRevision",revision);
        values.put("projectId",project); values.put("userId",admission.actorId()); values.put("fieldLimit",exactPolicy.maximumFields()+1);
        var row=one(exacts.revision(values)); var manifest=exact.parse(text(row,"manifest")); exact.require("Manifest",manifest);
        var input=exact.storedInput(manifest.required("request")); String hash=schema.hash(manifest);
        if(!hash.equals(reference.required("manifest_sha256").textValue()) || !hash.equals(text(row,"manifest_sha256"))
                || !hash.equals(text(row,"registered_sha256")) || !"SOURCE_EXACT_REVISION".equals(text(row,"entity_kind"))
                || !Objects.equals(row.get("owner_project_id"),row.get("registered_project_id"))
                || !version.equals(text(row,"source_version")) || !revision.equals(text(row,"field_revision"))
                || !admission.context().tenant_id().equals(text(row,"tenant_id"))
                || !version.equals(manifest.required("source_version").textValue()) || !revision.equals(manifest.required("field_revision").textValue())
                || !admission.context().tenant_id().equals(manifest.required("tenant_id").textValue())
                || !source.source().originalSha256().equals(manifest.required("original_sha256").textValue())
                || !text(row,"created_by").equals(manifest.required("actor_id").textValue())
                || !time(row,"created_at").equals(manifest.required("created_at").textValue())
                || !number(row,"publication_revision").toString().equals(manifest.required("revision").textValue())
                || !"1.0.0".equals(text(row,"schema_version")) || !exact.manifestSha256().equals(text(row,"schema_manifest_sha256"))) mismatch();
        var pins=source.selection();
        if(!project.equals(input.project_id()) || !pins.parseVersion().equals(input.parse_version()) || !pins.indexVersion().equals(input.index_version())
                || !pins.approvalRevision().equals(input.approval_revision()) || !pins.bindingRevision().equals(input.binding_revision())
                || !source.rights().rightsRevision().equals(input.rights_revision())) mismatch();
        for(var pin:Map.of("parse_version",input.parse_version(),"index_version",input.index_version(),"approval_revision",input.approval_revision(),
                "rights_revision",input.rights_revision()).entrySet()) if(!pin.getValue().equals(text(row,pin.getKey()))) mismatch();
        if(input.fields().size()>exactPolicy.maximumFields() || !number(row,"field_count").equals(BigInteger.valueOf(input.fields().size()))) mismatch();
        var fields=exacts.fields(values); if(fields.size()!=input.fields().size()) mismatch();
        for(int ordinal=0;ordinal<fields.size();ordinal++) {
            var field=input.fields().get(ordinal); var item=fields.get(ordinal);
            if(!number(item,"ordinal").equals(BigInteger.valueOf(ordinal)) || !json.tree(field).equals(exact.parse(text(item,"field_manifest")))
                    || !field.field_kind().equals(text(item,"field_kind")) || !field.raw_value().equals(text(item,"raw_value"))
                    || !WireValues.sha256(field.raw_value().getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(text(item,"raw_value_sha256"))
                    || !Objects.equals(field.alias_value(),item.get("alias_value"))
                    || !Objects.equals(field.alias_value()==null?null:WireValues.sha256(field.alias_value().getBytes(java.nio.charset.StandardCharsets.UTF_8)),item.get("alias_value_sha256"))
                    || !field.chunk_id().equals(text(item,"chunk_id"))) mismatch();
            for(String name:List.of("source_version","parse_version","approval_revision","field_revision","schema_version","schema_manifest_sha256","created_by","created_at","classification"))
                if(!Objects.equals(row.get(name),item.get(name))) mismatch();
            exact.checkField(field);
        }
        int ordinal=reference.required("ordinal").intValue();
        if(ordinal<0 || ordinal>=input.fields().size()) mismatch();
        var field=input.fields().get(ordinal);
        if(!KINDS.contains(field.field_kind()) || !field.field_kind().equals(reference.required("field_kind").textValue())) mismatch();
        return field;
    }

    Processing processing(String tenant,String source,String parse,String originalHash) throws Exception {
        var row=one(jdbc.queryForList("""
            SELECT a.project_id,a.attempt_id,a.run_id,a.config_revision,a.command_ref,a.result_ref,a.write_intent,a.lease_generation,
              sv.object_key,sv.object_version,sv.original_sha256,sv.media_type,sv.size_bytes,
              p.artifact_key,p.artifact_version,p.artifact_size_bytes,p.content_sha256,p.processor_manifest,
              v.content_sha256 AS registered_sha256
            FROM processing_attempt a
            JOIN source_version sv ON sv.tenant_id=a.tenant_id AND sv.source_version=a.source_version
            JOIN parse_version p ON p.tenant_id=a.tenant_id AND p.source_version=a.source_version AND p.parse_version=a.parse_version
            JOIN version_registry v ON v.tenant_id=p.tenant_id AND v.version_id=p.parse_version AND v.entity_kind='PARSE_VERSION'
            WHERE a.tenant_id=? AND a.source_version=? AND a.parse_version=? AND a.job_type='PARSE' AND a.status='SUCCEEDED'
              AND p.parse_status='COMPLETE' LIMIT 2 FOR UPDATE
            """,tenant,source,parse));
        var command=schema.read(text(row,"command_ref")); var result=schema.read(text(row,"result_ref"));
        var original=new ObjectRef(text(row,"object_key"),text(row,"object_version"),text(row,"original_sha256"),number(row,"size_bytes").toString(),text(row,"media_type"));
        var parsed=new ObjectRef(text(row,"artifact_key"),text(row,"artifact_version"),text(row,"content_sha256"),number(row,"artifact_size_bytes").toString(),"application/json");
        if(!originalHash.equals(original.sha256()) || !parsed.sha256().equals(text(row,"registered_sha256"))
                || !json.tree(parsed).equals(result.required("object_ref"))
                || !text(row,"attempt_id").equals(command.required("attempt_id").textValue())
                || !text(row,"run_id").equals(command.required("run_id").textValue())
                || !text(row,"config_revision").equals(command.required("config_revision").textValue())
                || !source.equals(command.required("source_version").textValue()) || !parse.equals(command.required("parse_version").textValue())
                || !original.sha256().equals(command.required("original_sha256").textValue())
                || !original.sizeBytes().equals(command.required("size_bytes").textValue())
                || !parsed.objectKey().equals("tenants/"+tenant+"/source-parsed/"+source+"/"+parse+"/parsed.json")
                || !original.objectKey().startsWith("tenants/"+tenant+"/source-uploads/")) mismatch();
        var intent=schema.read(text(row,"write_intent"));
        if(!parsed.objectKey().equals(intent.required("object_key").textValue()) || !parsed.sha256().equals(intent.required("sha256").textValue())
                || !parsed.sizeBytes().equals(intent.required("size_bytes").textValue()) || !parsed.mediaType().equals(intent.required("media_type").textValue())) mismatch();
        WireValues.uuid(result.required("confirmed_by_worker").textValue()); WireValues.revision(result.required("confirmed_lease_generation").textValue());
        if(!number(row,"lease_generation").toString().equals(result.required("confirmed_lease_generation").textValue())) mismatch();
        return new Processing(text(row,"project_id"),text(row,"attempt_id"),command.deepCopy(),result.deepCopy(),original,parsed,schema.read(text(row,"processor_manifest")));
    }

    static final class Proof {
        private final Request request; private final TaskProtocol.Head task; private final SourceReadProtocol.VerifiedSource source;
        private final SourceExactProtocol.Field field; private final JsonNode exactRef; private final Processing processing;
        private final String classification; private final JsonNode authority; private final String authoritySha256;
        private Proof(Request request,TaskProtocol.Head task,SourceReadProtocol.VerifiedSource source,SourceExactProtocol.Field field,
                JsonNode exactRef,Processing processing,String classification,JsonNode authority,String hash) {
            this.request=request;this.task=task;this.source=source;this.field=field;this.exactRef=exactRef.deepCopy();this.processing=processing;
            this.classification=classification;this.authority=authority.deepCopy();this.authoritySha256=hash;
        }
        Request request(){return request;} TaskProtocol.Head task(){return task;} SourceReadProtocol.VerifiedSource source(){return source;}
        SourceExactProtocol.Field field(){return field;} JsonNode exactRef(){return exactRef.deepCopy();} Processing processing(){return processing;}
        String classification(){return classification;} JsonNode authority(){return authority.deepCopy();} String authoritySha256(){return authoritySha256;}
    }
    record Processing(String projectId,String attempt,JsonNode command,JsonNode result,ObjectRef original,ObjectRef parsed,JsonNode processorManifest) {
        Processing { command=command.deepCopy();result=result.deepCopy();processorManifest=processorManifest.deepCopy(); }
        @Override public JsonNode command(){return command.deepCopy();} @Override public JsonNode result(){return result.deepCopy();}
        @Override public JsonNode processorManifest(){return processorManifest.deepCopy();}
    }
    static String text(Map<String,Object> row,String name){return Objects.requireNonNull((String)row.get(name));}
    static BigInteger number(Map<String,Object> row,String name){return new BigInteger(Objects.requireNonNull(row.get(name)).toString());}
    static String time(Map<String,Object> row,String name){var value=row.get(name);return (value instanceof java.sql.Timestamp timestamp?timestamp.toLocalDateTime():(LocalDateTime)value).toInstant(ZoneOffset.UTC).toString();}
    static <T>T one(List<T> rows){if(rows.size()!=1)mismatch();return rows.get(0);}
    static void mismatch(){fail(ErrorCode.CONTENT_MISMATCH);} static void fail(ErrorCode code){throw new TenantAccessException(code);}
}
