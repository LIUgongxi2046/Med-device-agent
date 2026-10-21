package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.TaskProfessionalObjectGovernance.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Replays immutable 075 facts, not current execution, source, object-read or model-send
 * authority. In particular this reader cannot mint an Attempt, transfer or Registered.
 * Immutable097/098 identity/receipt pins are checked through075/099. Complete original
 *097/098 journal traversal belongs to the enclosing context/commitment authority; this
 * reader deliberately does not invoke their current-approval/source-rights paths. */
final class TaskProfessionalOriginalExactInput {
    private final String KIND;private final EnterpriseAccess.Admission admission;private final TaskProfessionalSupervisor supervisor;private final TaskProfessionalRuntimeService.Deployment deployment;
    private final JdbcTemplate db;private final PermissionSchema json;private final TaskProfessionalSchema schema;
    private final DependencyManifestSchema manifests;private final TaskProfessionalObjectGovernance governance;
    TaskProfessionalOriginalExactInput(JdbcTemplate db,PermissionSchema json,TaskProfessionalSchema schema,
            DependencyManifestSchema manifests,TaskProfessionalObjectGovernance governance){this(db,json,schema,manifests,governance,"TASK_EXACT_INPUT",null,null,null);}
    TaskProfessionalOriginalExactInput(JdbcTemplate db,PermissionSchema json,TaskProfessionalSchema schema,
            DependencyManifestSchema manifests,TaskProfessionalObjectGovernance governance,String kind,EnterpriseAccess.Admission admission,
            TaskProfessionalSupervisor supervisor,TaskProfessionalRuntimeService.Deployment deployment){
        if(!Set.of("TASK_EXACT_INPUT","SESSION_EXPORT","EVIDENCE_BUNDLE","STEP_RESULT").contains(kind))throw new IllegalArgumentException("ORIGINAL_KIND_REQUIRED");
        this.db=db;this.json=json;this.schema=schema;this.manifests=manifests;this.governance=governance;this.KIND=kind;this.admission=admission;this.supervisor=supervisor;this.deployment=deployment;
        if(!"TASK_EXACT_INPUT".equals(kind)){Objects.requireNonNull(admission);Objects.requireNonNull(supervisor);Objects.requireNonNull(deployment);}
    }
    static void active(String tenant){
        WireValues.uuid(tenant);TenantScope.requireSqlAccess("TaskProfessionalOriginalExactInput.read");
        if(!tenant.equals(TenantScope.requireCurrent().tenantId())||!TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
    Snapshot read(String tenant,String artifact)throws Exception {
        active(tenant);WireValues.uuid(artifact);
        // No version/kind filters: ambiguous or relabelled ownership must be rejected.
        var intent=one(db.queryForList("SELECT * FROM runtime_object_intent WHERE tenant_id=? AND planned_artifact_id=? LIMIT 2 FOR UPDATE",tenant,artifact));
        require(intent,"tenant_id",tenant);require(intent,"planned_artifact_id",artifact);require(intent,"schema_version","2.0.0");require(intent,"write_kind",KIND);
        String document=str(intent,"payload_schema_sha256");schema.requireDeclaration(KIND,str(intent,"schema_ref"),document);require(intent,"schema_manifest_sha256",document);
        String project=str(intent,"project_id"),run=str(intent,"run_id"),step=str(intent,"step_id"),write=str(intent,"write_id"),actor=str(intent,"created_by"),classification=str(intent,"classification");
        for(String id:List.of(project,run,step,write,actor))WireValues.uuid(id);
        if(!actor.equals(TenantScope.requireCurrent().subjectId()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var payload=schema.read(KIND,str(intent,"canonical_payload").getBytes(StandardCharsets.UTF_8),str(intent,"schema_ref"),document);byte[] bytes=ContentReadAuthorization.canonicalBytes(payload);
        String digest=WireValues.sha256(bytes);require(intent,"object_sha256",digest);if(bytes.length!=number(intent,"size_bytes"))bad();
        String key="runtime/"+tenant+"/"+run+"/"+write+"/"+artifact;require(intent,"object_key",key);
        var stored=journal("runtime_registered_object",tenant,project,run,write);var receipt=parse(stored,"receipt_json");
        String receiptHash=hash(receipt);require(stored,"receipt_sha256",receiptHash);require(stored,"artifact_id",artifact);require(stored,"object_sha256",digest);
        var created=time(stored,"created_at");var prepared=time(intent,"created_at");order(prepared,created);
        var base=metadata(intent,created);same(base,stored);require(receipt,"schema_version","2.0.0");
        for(String field:List.of("tenant_id","project_id","run_id","step_id","write_id","created_by","classification","schema_ref","payload_schema_sha256","write_key"))require(receipt,field,str(intent,field));
        require(receipt,"artifact_id",artifact);require(receipt,"kind",KIND);require(receipt,"created_at",created.toString());
        for(String field:List.of("task_id","principal_id","action_id","session_id","input_artifact_id","producer_rule_id","producer_decision_id"))WireValues.uuid(text(receipt,field));
        for(String field:List.of("governance_sha256","input_sha256","input_receipt_sha256","producer_sha256","producer_decision_sha256"))WireValues.hash(text(receipt,field));
        WireValues.revision(text(receipt,"lease_generation"));
        var object=new ObjectRef(key,str(stored,"object_version"),digest,Integer.toString(bytes.length),"application/json");equal(json.tree(object),receipt.required("object_ref"));
        if(object.objectVersion().isBlank())bad();
        ProfessionalRuntimeObservations.closed(receipt,Set.of("schema_version","tenant_id","project_id","run_id","step_id","task_id","principal_id","action_id","lease_generation","worker_service_uri","session_id","write_id","write_key","artifact_id","kind","schema_ref","payload_schema_sha256","governance_sha256","input_artifact_id","input_sha256","input_receipt_sha256","producer_rule_id","producer_sha256","producer_decision_id","producer_decision_sha256","created_by","created_at","classification","object_ref","content_manifest","content_roots","provenance"));
        var provenance=receipt.required("provenance");require(provenance,"schema_version","1.0.0");require(provenance,"phase",KIND.equals("TASK_EXACT_INPUT")?"PRE_SEND":"POST_CLOSE");require(provenance,"session_id",text(receipt,"session_id"));require(provenance,"payload_sha256",digest);
        if(KIND.equals("TASK_EXACT_INPUT")){
            var request=provenance.required("model_request");String requestHash=hash(request);
            require(intent,"write_key",TaskProfessionalObjectLedger.exactKey(run,step,text(receipt,"lease_generation"),text(request,"request_id"),requestHash));
        }else require(intent,"write_key",TaskProfessionalObjectLedger.key(run,step,text(receipt,"lease_generation"),KIND));
        var attempt=journal("runtime_object_attempt",tenant,project,run,write);same(metadata(intent,time(attempt,"created_at")),attempt);order(prepared,time(attempt,"created_at"));order(time(attempt,"created_at"),created);
        var state=journal("runtime_object_state",tenant,project,run,write);same(metadata(intent,prepared),state);require(state,"stage","ATTEMPTED");if(number(state,"revision")!=2)bad();order(prepared,time(state,"updated_at"));order(time(state,"updated_at"),created);
        var writeIntent=new S3ObjectWriter.WriteIntent(write,key,digest,Integer.toString(bytes.length),"application/json");
        var witness=json.object().put("schema_version","2.0.0").put("tenant_id",tenant).put("project_id",project).put("run_id",run).put("step_id",step).put("created_by",actor).put("write_key",str(intent,"write_key")).put("artifact_id",artifact).put("session_id",text(receipt,"session_id")).put("kind",KIND).put("schema_ref",str(intent,"schema_ref")).put("payload_schema_sha256",document);witness.set("write_intent",json.tree(writeIntent));
        audit(intent,"runtime.professional.prepared",write,run,witness,prepared);event(tenant,write,run,"runtime.professional.prepared",witness,prepared);
        verifyGovernance(intent,receipt,writeIntent,prepared,created);
        verifyContent(intent,receipt,object,created);
        if(KIND.equals("TASK_EXACT_INPUT"))verifyAdmission(intent,receipt,prepared,created);else verifyPostClose(intent,receipt,prepared,created);
        audit(intent,"runtime.professional.registered",write,run,receipt,created);event(tenant,artifact,write,"runtime.professional.registered",receipt,created);
        active(tenant);return new Snapshot(bytes,object,receipt,receiptHash);
    }
    private void verifyGovernance(Map<String,Object> intent,JsonNode receipt,S3ObjectWriter.WriteIntent writeIntent,LocalDateTime prepared,LocalDateTime registered)throws Exception {
        var row=journal("runtime_governance_manifest",str(intent,"tenant_id"),str(intent,"project_id"),str(intent,"run_id"),str(intent,"write_id"));var at=time(row,"created_at");order(prepared,at);order(at,registered);same(metadata(intent,at),row);
        var manifest=parse(row,"canonical_manifest");require(row,"manifest_sha256",hash(manifest));require(receipt,"governance_sha256",hash(manifest));require(manifest,"schema_version","2.0.0");
        ProfessionalRuntimeObservations.closed(manifest,Set.of("schema_version","tenant_id","project_id","run_id","step_id","task_id","principal_id","action_id","lease_generation","worker_service_uri","session_id","write_id","write_key","artifact_id","kind","schema_ref","payload_schema_sha256","replay_sha256","input_artifact_id","input_sha256","input_receipt_sha256","producer_rule_id","producer_sha256","producer_decision_id","producer_decision_sha256","write_intent","producer_authorizations","provenance","versions","edges"));
        for(String field:List.of("tenant_id","project_id","run_id","step_id","task_id","principal_id","action_id","lease_generation","worker_service_uri","session_id","write_id","write_key","artifact_id","kind","schema_ref","payload_schema_sha256","input_artifact_id","input_sha256","input_receipt_sha256","producer_rule_id","producer_sha256","producer_decision_id","producer_decision_sha256"))require(manifest,field,text(receipt,field));
        require(row,"principal_id",text(receipt,"principal_id"));require(row,"replay_sha256",text(manifest,"replay_sha256"));equal(manifest.required("provenance"),receipt.required("provenance"));equal(manifest.required("write_intent"),json.tree(writeIntent));if(!manifest.required("producer_authorizations").isObject())bad();
        var run=one(db.queryForList("SELECT * FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? LIMIT 2 FOR UPDATE",str(intent,"tenant_id"),str(intent,"project_id"),str(intent,"run_id")));
        for(String field:List.of("tenant_id","project_id","run_id","created_by","classification"))require(run,field,str(intent,field));require(run,"task_id",text(receipt,"task_id"));require(run,"execution_kind","TASK");
        var replay=parse(run,"replay_manifest");require(manifest,"replay_sha256",hash(replay));equal(manifest.required("versions"),replay.required("dependency_versions"));equal(manifest.required("edges"),replay.required("dependency_edges"));
        // Immutable graph identity, not current rights or grant evaluation.
        governance.requireGraph(manifest.required("versions"),manifest.required("edges"));
        for(String part:List.of("member","edge")){
            var rows=db.queryForList("SELECT * FROM runtime_governance_"+part+" WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? ORDER BY "+part+"_ordinal FOR UPDATE",str(intent,"tenant_id"),str(intent,"project_id"),str(intent,"run_id"),str(intent,"write_id"));
            var values=manifest.required(part.equals("member")?"versions":"edges");if(rows.size()!=values.size())bad();
            for(int n=0;n<rows.size();n++){var expected=metadata(intent,at);expected.put(part+"_ordinal",n);var v=values.get(n);
                if(part.equals("member")){expected.put("upstream_version_id",text(v,"version_id"));expected.put("upstream_project_id",v.required("project_id").isNull()?null:text(v,"project_id"));expected.put("entity_kind",text(v,"entity_kind"));expected.put("content_sha256",text(v,"content_sha256"));}
                else for(String field:List.of("consumer_version_id","upstream_version_id","relation_kind"))expected.put(field,text(v,field));same(expected,rows.get(n));
            }
        }
    }
    private void verifyContent(Map<String,Object> intent,JsonNode receipt,ObjectRef object,LocalDateTime at)throws Exception {
        String tenant=str(intent,"tenant_id"),artifact=text(receipt,"artifact_id"),project=str(intent,"project_id");var manifest=receipt.required("content_manifest");String set=text(manifest,"access_set_id"),digest=hash(manifest);WireValues.uuid(set);
        var expectedManifest=json.object().put("schema_version","1.0.0").put("tenant_id",tenant).put("project_id",project).put("access_set_id",set);expectedManifest.set("members",manifest.required("members"));equal(expectedManifest,manifest);
        var ar=one(db.queryForList("SELECT * FROM artifact WHERE tenant_id=? AND artifact_id=? LIMIT 2",tenant,artifact));var expected=meta(intent,at,true);expected.put("artifact_id",artifact);expected.put("run_id",str(intent,"run_id"));expected.put("document_revision",null);expected.put("artifact_kind",KIND);expected.put("object_key",object.objectKey());expected.put("object_version",object.objectVersion());expected.put("sha256",object.sha256());expected.put("size_bytes",object.sizeBytes());expected.put("media_type",object.mediaType());expected.put("dependency_access_set_id",set);expected.put("content_sha256",object.sha256());same(expected,ar);StoredObjectReferences.decode(str(ar,"object_ref"),object);
        var binding=one(db.queryForList("SELECT * FROM artifact_content_binding WHERE tenant_id=? AND artifact_id=? LIMIT 2",tenant,artifact));expected=meta(intent,at,false);expected.put("artifact_id",artifact);expected.put("resource_kind","TASK");expected.put("resource_id",text(receipt,"task_id"));expected.put("schema_ref",str(intent,"schema_ref"));expected.put("schema_document_sha256",str(intent,"payload_schema_sha256"));expected.put("dependency_access_set_id",set);expected.put("dependency_manifest_sha256",digest);same(expected,binding);
        var access=one(db.queryForList("SELECT * FROM dependency_access_set WHERE tenant_id=? AND access_set_id=? LIMIT 2",tenant,set));expected=meta(intent,at,false);expected.put("access_set_id",set);expected.put("manifest_sha256",digest);expected.put("content_sha256",digest);same(expected,access);
        var m=one(db.queryForList("SELECT * FROM dependency_access_manifest WHERE tenant_id=? AND access_set_id=? LIMIT 2",tenant,set));expected=meta(intent,at,true);expected.put("schema_manifest_sha256",manifests.manifestSha256());expected.put("access_set_id",set);expected.put("manifest_sha256",digest);expected.put("content_sha256",digest);expected.put("member_count",manifest.required("members").size());same(expected,m);equal(manifest,parse(m,"manifest_json"));
        var members=db.queryForList("SELECT * FROM dependency_access_member WHERE tenant_id=? AND access_set_id=? ORDER BY upstream_version_id",tenant,set);var declared=manifest.required("members");if(!declared.isArray()||declared.isEmpty()||members.size()!=declared.size())bad();String previous="";
        for(int n=0;n<members.size();n++){var member=declared.get(n);String id=text(member,"upstream_version_id"),rights=text(member,"rights_revision");WireValues.uuid(id);WireValues.uuid(rights);if(previous.compareTo(id)>=0)bad();previous=id;expected=meta(intent,at,false);expected.remove("project_id");expected.put("access_set_id",set);expected.put("upstream_version_id",id);expected.put("rights_revision",rights);same(expected,members.get(n));}
        var edges=db.queryForList("SELECT * FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? ORDER BY upstream_version_id",tenant,artifact);var roots=receipt.required("content_roots");if(!roots.isArray()||roots.isEmpty()||edges.size()!=roots.size())bad();previous="";
        for(int n=0;n<edges.size();n++){if(!roots.get(n).isTextual())bad();String root=roots.get(n).textValue();WireValues.uuid(root);if(previous.compareTo(root)>=0)bad();previous=root;expected=new LinkedHashMap<>();expected.put("tenant_id",tenant);expected.put("consumer_version_id",artifact);expected.put("upstream_version_id",root);expected.put("relation_kind","RUNTIME_CONTENT_INPUT");expected.put("actor_id",str(intent,"created_by"));expected.put("created_at",at);expected.put("decision_ref",null);same(expected,edges.get(n));}
        for(var pin:List.of(Map.entry(artifact,object.sha256()),Map.entry(set,digest)))same(Map.of("tenant_id",tenant,"owner_project_id",project,"version_id",pin.getKey(),"entity_kind",pin.getKey().equals(set)?"DEPENDENCY_ACCESS_SET":"ARTIFACT","content_sha256",pin.getValue()),one(db.queryForList("SELECT * FROM version_registry WHERE tenant_id=? AND version_id=? LIMIT 2",tenant,pin.getKey())));
    }
    /** Shared original075 metadata with the actual independent099 CLOSED authority. This is
     * immutable evidence only: no execution/read/registration capability is created. */
    private void verifyPostClose(Map<String,Object> intent,JsonNode receipt,LocalDateTime prepared,LocalDateTime registered)throws Exception {
        String tenant=str(intent,"tenant_id"),session=text(receipt,"session_id");var provenance=receipt.required("provenance");
        if(!tenant.equals(admission.context().tenant_id())||!str(intent,"created_by").equals(admission.actorId()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        ProfessionalRuntimeObservations.closed(provenance,Set.of("schema_version","phase","kind","tenant_id","project_id","run_id","step_id","action_id","lease_generation","worker_service_uri","session_id","input_artifact_id","input_sha256","input_receipt_sha256","payload_sha256","supervision_receipt_id","supervision_sha256","deployment_sha256","producing_receipts"));
        require(provenance,"kind",KIND);require(provenance,"deployment_sha256",deployment.sha256());
        for(String field:List.of("tenant_id","project_id","run_id","step_id","action_id","worker_service_uri","session_id","input_artifact_id","input_sha256","input_receipt_sha256"))require(provenance,field,text(receipt,field));
        if(!provenance.required("lease_generation").isIntegralNumber()||!provenance.required("lease_generation").bigIntegerValue().equals(new java.math.BigInteger(text(receipt,"lease_generation"))))bad();
        var binding=journal("runtime_professional_object_binding",tenant,str(intent,"project_id"),str(intent,"run_id"),str(intent,"write_id"));same(metadata(intent,registered),binding);
        for(String field:List.of("step_id","session_id","producer_rule_id","producer_sha256","producer_decision_id","producer_decision_sha256","input_artifact_id","input_receipt_sha256"))require(binding,field,text(receipt,field));require(binding,"lease_generation",text(receipt,"lease_generation"));
        for(String field:List.of("supervision_receipt_id","supervision_sha256"))require(binding,field,text(provenance,field));require(binding,"provenance_sha256",hash(provenance));equal(provenance,parse(binding,"canonical_provenance"));
        var sessionRow=one(db.queryForList("SELECT * FROM professional_runtime_session WHERE tenant_id=? AND session_id=? LIMIT 2 FOR UPDATE",tenant,session));
        require(sessionRow,"admission_state","CLOSED");require(sessionRow,"session_id",session);require(sessionRow,"action_id",text(receipt,"action_id"));require(sessionRow,"lease_generation",text(receipt,"lease_generation"));require(sessionRow,"input_sha256",text(receipt,"input_sha256"));require(sessionRow,"service_uri",text(receipt,"worker_service_uri"));
        for(String field:List.of("tenant_id","project_id","run_id","step_id","created_by","classification"))require(sessionRow,field,str(intent,field));
        var start=parse(sessionRow,"canonical_start");require(sessionRow,"start_sha256",hash(start));var identity=start.required("binding");ProfessionalRuntimeObservations.jsonBinding(identity);
        for(String field:List.of("tenant_id","run_id","step_id","action_id","input_sha256"))require(identity,field,text(receipt,field));
        if(!identity.required("lease_generation").bigIntegerValue().equals(new java.math.BigInteger(text(receipt,"lease_generation"))))bad();require(identity,"step_envelope_sha256",str(sessionRow,"envelope_sha256"));
        var closure=supervisor.requireClosedOriginal(admission,identity,session,text(provenance,"supervision_receipt_id"),text(provenance,"supervision_sha256"),deployment);
        closure.requireUnchanged();if(!KIND.equals("SESSION_EXPORT"))closure.requireSuccessfulExit();
        var closed=one(db.queryForList("SELECT * FROM professional_runtime_supervision WHERE tenant_id=? AND session_id=? AND phase=? LIMIT 2 FOR UPDATE",tenant,session,"CLOSED"));
        require(closed,"receipt_id",text(provenance,"supervision_receipt_id"));require(closed,"evidence_sha256",text(provenance,"supervision_sha256"));order(time(closed,"created_at"),prepared);order(time(sessionRow,"created_at"),prepared);
        // The close authority validates all admitted/closed audits, outbox metadata, actual
        // process ownership/exit, deployment/trust, session and original binding, not a UUID.
        closure.requireUnchanged();
    }
    private void verifyAdmission(Map<String,Object> intent,JsonNode receipt,LocalDateTime prepared,LocalDateTime registered)throws Exception {
        var provenance=receipt.required("provenance");String tenant=str(intent,"tenant_id"),session=text(receipt,"session_id");
        var binding=journal("runtime_professional_object_binding",tenant,str(intent,"project_id"),str(intent,"run_id"),str(intent,"write_id"));same(metadata(intent,registered),binding);
        for(String field:List.of("step_id","session_id","producer_rule_id","producer_sha256","producer_decision_id","producer_decision_sha256","input_artifact_id","input_receipt_sha256"))require(binding,field,text(receipt,field));require(binding,"lease_generation",text(receipt,"lease_generation"));
        for(String field:List.of("supervision_receipt_id","supervision_sha256"))require(binding,field,text(provenance,field));require(binding,"provenance_sha256",hash(provenance));equal(provenance,parse(binding,"canonical_provenance"));
        var admitted=one(db.queryForList("SELECT * FROM professional_runtime_supervision WHERE tenant_id=? AND session_id=? AND phase=? LIMIT 2 FOR UPDATE",tenant,session,"ADMITTED"));
        var admissionAt=time(admitted,"created_at");order(admissionAt,prepared);var expected=meta(intent,admissionAt,true);expected.put("run_id",str(intent,"run_id"));expected.put("step_id",str(intent,"step_id"));expected.put("schema_manifest_sha256",json.manifestSha256());expected.put("session_id",session);expected.put("lease_generation",Long.parseLong(text(receipt,"lease_generation")));expected.put("phase","ADMITTED");expected.put("input_sha256",text(receipt,"input_sha256"));same(expected,admitted);
        require(admitted,"receipt_id",text(provenance,"supervision_receipt_id"));require(admitted,"evidence_sha256",text(provenance,"supervision_sha256"));require(admitted,"deployment_sha256",text(provenance,"deployment_sha256"));var evidence=parse(admitted,"canonical_evidence");require(admitted,"evidence_sha256",hash(evidence));
        require(evidence,"schema_version","1.0.0");require(evidence,"phase","ADMITTED");require(evidence,"gate","SDK_READY_LOCKED");require(evidence,"session_id",session);require(evidence,"receipt_id",str(admitted,"receipt_id"));require(evidence,"deployment_sha256",str(admitted,"deployment_sha256"));require(evidence,"deployment_revision",str(admitted,"deployment_revision"));TaskProfessionalSupervisor.validateStoredAdmission(evidence);
        var original=evidence.required("binding");ProfessionalRuntimeObservations.jsonBinding(original);for(String field:List.of("tenant_id","run_id","step_id","action_id","input_sha256"))require(original,field,text(receipt,field));if(original.required("lease_generation").longValue()!=Long.parseLong(text(receipt,"lease_generation")))bad();require(original,"step_envelope_sha256",str(admitted,"envelope_sha256"));
        var row=one(db.queryForList("SELECT * FROM professional_runtime_session WHERE tenant_id=? AND session_id=? LIMIT 2 FOR UPDATE",tenant,session));var start=parse(row,"canonical_start");require(row,"start_sha256",hash(start));equal(original,start.required("binding"));
        for(String field:List.of("tenant_id","project_id","run_id","step_id","created_by","classification"))require(row,field,str(intent,field));require(row,"session_id",session);require(row,"action_id",text(receipt,"action_id"));require(row,"lease_generation",text(receipt,"lease_generation"));require(row,"input_sha256",text(receipt,"input_sha256"));require(row,"envelope_sha256",str(admitted,"envelope_sha256"));require(row,"service_uri",text(receipt,"worker_service_uri"));require(row,"schema_version","1.0.0");require(row,"schema_manifest_sha256",json.manifestSha256());order(admissionAt,time(row,"created_at"));order(time(row,"created_at"),prepared);
        require(start,"session_id",session);require(start,"purpose","TASK");require(start,"node_kind","N02");require(start,"state","START");require(start,"deployment_sha256",text(provenance,"deployment_sha256"));require(start,"runtime_manifest_sha256",text(evidence,"runtime_manifest_sha256"));
        // Admission audit is immutable even if the session is now CLOSED or its lease expired.
        var a=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",tenant,str(admitted,"audit_id")));var e=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",tenant,str(admitted,"event_id")));
        for(String field:List.of("tenant_id","project_id","created_by","classification","schema_version","schema_manifest_sha256","created_at"))same(Collections.singletonMap(field,admitted.get(field)),a);
        require(a,"subject_id",str(intent,"created_by"));require(a,"action","task.professional.admitted");require(a,"result","SUCCESS");if(a.get("reason_code")!=null)bad();require(a,"request_id",session);require(a,"trace_id",str(admitted,"receipt_id"));equal(evidence,parse(a,"resource_ref"));same(Map.of("tenant_id",tenant,"aggregate_id",session,"aggregate_revision",1,"event_type","task.professional.admitted","occurred_at",admissionAt),e);equal(evidence,parse(e,"payload"));
        var request=provenance.required("model_request");ProfessionalRuntimeObservations.closed(request,Set.of("schema_version","receipt_id","request_id","ordinal","session_id","binding","deployment_sha256","supervision_receipt_id","supervision_sha256","method","route","raw_body_sha256","raw_size_bytes","projection_schema_ref","projection_schema_version","projection_schema_sha256","body_sha256","body_base64","peer_pid","peer_start_ticks","peer_exe_sha256"));require(request,"schema_version","1.0.0");require(request,"session_id",session);equal(original,request.required("binding"));require(request,"deployment_sha256",text(provenance,"deployment_sha256"));require(request,"supervision_receipt_id",str(admitted,"receipt_id"));require(request,"supervision_sha256",str(admitted,"evidence_sha256"));require(request,"method","POST");require(request,"route","/v1/model-contexts");WireValues.uuid(text(request,"receipt_id"));WireValues.uuid(text(request,"request_id"));if(!request.required("ordinal").isIntegralNumber()||request.required("ordinal").longValue()<1||request.required("ordinal").longValue()>10000)bad();
        byte[] observed=Base64.getDecoder().decode(text(request,"body_base64"));if(observed.length<1||observed.length>TaskInvocationContextSchema.MAXIMUM_BYTES)bad();require(request,"body_sha256",WireValues.sha256(observed));require(request,"raw_body_sha256",WireValues.sha256(observed));if(request.required("raw_size_bytes").longValue()!=observed.length)bad();require(request,"projection_schema_ref",TaskInvocationContextSchema.PROJECTION_REF);require(request,"projection_schema_version","1.0.0");require(request,"projection_schema_sha256",str(intent,"payload_schema_sha256"));TaskProfessionalSupervisor.validateProjection(observed);
        boolean peer=false;for(var process:evidence.required("process_unit").required("processes"))if(FormalTaskNodeAuthority.sameEvidence(process.required("pid"),request.required("peer_pid"))&&FormalTaskNodeAuthority.sameEvidence(process.required("start_ticks"),request.required("peer_start_ticks"))&&text(process,"exe_sha256").equals(text(request,"peer_exe_sha256")))peer=true;if(!peer)bad();
        require(provenance.required("tokenization"),"input_sha256",text(receipt.required("object_ref"),"sha256"));
    }
    private Map<String,Object> journal(String table,String tenant,String project,String run,String write){return one(db.queryForList("SELECT * FROM "+table+" WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? LIMIT 2 FOR UPDATE",tenant,project,run,write));}
    private void audit(Map<String,Object> intent,String action,String request,String trace,JsonNode value,LocalDateTime at)throws Exception {
        var row=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND request_id=? AND action=? LIMIT 2",str(intent,"tenant_id"),request,action));var expected=meta(intent,at,true);expected.put("subject_id",str(intent,"created_by"));expected.put("action",action);expected.put("result","SUCCESS");expected.put("reason_code",null);expected.put("request_id",request);expected.put("trace_id",trace);same(expected,row);WireValues.uuid(str(row,"audit_id"));equal(value,parse(row,"resource_ref"));
    }
    private void event(String tenant,String id,String aggregate,String type,JsonNode value,LocalDateTime at)throws Exception {
        var row=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",tenant,id));same(Map.of("tenant_id",tenant,"event_id",id,"aggregate_id",aggregate,"aggregate_revision",1,"event_type",type,"occurred_at",at),row);equal(value,parse(row,"payload"));
    }
    private Map<String,Object> metadata(Map<String,Object> intent,LocalDateTime at){var result=new LinkedHashMap<String,Object>();for(String key:List.of("tenant_id","project_id","run_id","write_id","schema_manifest_sha256","created_by","classification"))result.put(key,intent.get(key));result.put("schema_version","1.0.0");result.put("created_at",at);return result;}
    private Map<String,Object> meta(Map<String,Object> intent,LocalDateTime at,boolean schemaFields){var result=metadata(intent,at);result.remove("run_id");result.remove("write_id");if(!schemaFields){result.remove("schema_version");result.remove("schema_manifest_sha256");}return result;}
    private JsonNode parse(Map<String,Object> row,String field)throws Exception{return schema.parse(str(row,field));}
    private static void require(Map<String,Object> row,String field,String expected){if(!expected.equals(str(row,field)))bad();}
    private static void require(JsonNode row,String field,String expected){if(!expected.equals(text(row,field)))bad();}
    private static long number(Map<String,Object> row,String field){try{return Long.parseLong(str(row,field));}catch(NumberFormatException e){bad();return 0;}}
    private static void equal(JsonNode a,JsonNode b){if(!FormalTaskNodeAuthority.sameEvidence(a,b))bad();}
    private static void order(LocalDateTime earlier,LocalDateTime later){if(earlier.isAfter(later)||later.isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(5)))bad();}
    static final class Snapshot {
        private final byte[] bytes;private final ObjectRef reference;private final JsonNode receipt;private final String digest;
        private Snapshot(byte[] bytes,ObjectRef reference,JsonNode receipt,String digest){this.bytes=bytes.clone();this.reference=reference;this.receipt=receipt.deepCopy();this.digest=digest;}
        byte[] bytes(){return bytes.clone();}ObjectRef reference(){return reference;}JsonNode receipt(){return receipt.deepCopy();}String digest(){return digest;}
    }
}
