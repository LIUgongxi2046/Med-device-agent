package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Read-only original AT06 receipt recovery. No lease, model-send, adoption or content permission
 * is recreated. Only a private authenticated original dispatch can select the original result. */
public final class TaskSectionWriterCandidateRecovery {
    private final JdbcTemplate db;private final TaskSectionWriterSchema json;private final TaskSectionWriterInvocationService invocations;private final FormalCapabilityIdentityAuthority roles;
    public TaskSectionWriterCandidateRecovery(DataSource data,TaskSectionWriterSchema json,TaskSectionWriterInvocationService invocations,FormalCapabilityIdentityAuthority roles){this.roles=roles;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.invocations=invocations;}
    public Optional<JsonNode> original(TaskSectionWriterDispatchJournal.Original original)throws Exception {
        original.requireUnchanged();var d=original.dispatch();var reference=original.reference();String tenant=reference.tenantId(),project=reference.projectId(),actor=original.admission().actorId();
        var records=db.queryForList("SELECT * FROM task_section_writer_submission WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",tenant,project,d.runId(),d.stepId());
        if(records.isEmpty())return Optional.empty();var row=one(records);var receipt=json.read(str(row,"receipt_json"));
        same(json.hash(receipt),str(row,"receipt_sha256"));same(actor,str(row,"created_by"));same(tenant,text(receipt,"tenant_id"));same(d.runId(),text(receipt,"run_id"));same(d.stepId(),text(receipt,"step_id"));same(d.inputSha256(),text(receipt,"input_sha256"));same("READY",text(receipt,"status"));same("1",text(receipt,"revision"));same(str(row,"action_id"),text(receipt,"action_id"));same(str(row,"candidate_id"),text(receipt,"candidate_id"));same(str(row,"candidate_artifact_id"),text(receipt,"candidate_artifact_id"));
        var candidate=saved(original,text(receipt,"candidate_artifact_id"),"SECTION_CANDIDATE",text(receipt,"action_id"));
        var body=saved(original,text(receipt,"body_artifact_id"),"SECTION_BODY",text(receipt,"action_id"));
        same(candidate.receiptHash,text(receipt,"candidate_receipt_sha256"));same(body.object.sha256(),text(receipt,"body_sha256"));
        same(body.receiptHash,text(candidate.receipt.required("provenance"),"body_receipt_sha256"));
        same(candidate.receipt.required("provenance").required("invocations"),body.receipt.required("provenance").required("invocations"));
        same(candidate.receipt.required("provenance").required("supervision"),body.receipt.required("provenance").required("supervision"));
        var artifact=ArtifactEvidenceBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.ARTIFACT,candidate.payload,candidate.schemaRef,candidate.schemaSha).read();
        if(!(artifact.output() instanceof ArtifactProtocol.SectionOutput output))bad();
        var section=((ArtifactProtocol.SectionOutput)artifact.output()).content();
        same(d.inputSha256(),artifact.inputManifestSha256());same(d.runId(),artifact.runId());same(d.stepId(),artifact.stepId());same(tenant,artifact.tenantId());same(project,artifact.projectId());
        same(body.object.sha256(),section.bodyObjectRef().sha256());same(body.object.objectKey(),section.bodyObjectRef().objectKey());same(body.object.objectVersion(),section.bodyObjectRef().objectVersion());same(text(receipt,"base_document_revision"),section.baseDocumentRevision());
        var c=one(db.queryForList("SELECT * FROM candidate WHERE tenant_id=? AND project_id=? AND candidate_id=? LIMIT 2 FOR UPDATE",tenant,project,text(receipt,"candidate_id")));
        same(d.runId(),str(c,"run_id"));same(d.stepId(),str(c,"step_id"));same(d.inputSha256(),str(c,"input_sha256"));same(text(receipt,"action_id"),str(c,"action_id"));same(Long.toString(d.leaseGeneration()),str(c,"lease_generation"));same(text(receipt,"body_artifact_id"),str(c,"content_artifact_id"));same(text(receipt,"candidate_artifact_id"),str(c,"new_evidence_artifact_id"));same(body.object.sha256(),str(c,"content_sha256"));same(text(receipt,"base_document_revision"),str(c,"base_document_revision"));same(actor,str(c,"created_by"));
        // Candidate status can legitimately change after submission. Return its original READY receipt,
        // never reinterpret later adoption/rejection as the outcome of this generation action.
        audits(tenant,actor,receipt,"task.section_writer.candidate.submitted","candidate.ready");original.requireUnchanged();return Optional.of(receipt.deepCopy());
    }
    private Saved saved(TaskSectionWriterDispatchJournal.Original original,String id,String kind,String action)throws Exception {
        var d=original.dispatch();String tenant=original.reference().tenantId(),project=original.reference().projectId(),actor=original.admission().actorId();
        var r=one(db.queryForList("SELECT * FROM task_section_writer_object WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND artifact_id=? AND kind=? AND state='REGISTERED' LIMIT 2 FOR UPDATE",tenant,project,d.runId(),d.stepId(),action,id,kind));
        var receipt=json.read(str(r,"receipt_json"));var payload=json.read(str(r,"canonical_payload"));same(json.hash(receipt),str(r,"receipt_sha256"));same(json.hash(payload),str(r,"payload_sha256"));same(actor,str(r,"created_by"));same(id,text(receipt,"artifact_id"));same(d.inputSha256(),text(receipt,"input_sha256"));
        var intent=one(db.queryForList("SELECT * FROM task_section_writer_object_intent WHERE tenant_id=? AND write_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",tenant,str(r,"write_id"),id));same(str(r,"canonical_payload"),str(intent,"canonical_payload"));same(str(r,"payload_sha256"),str(intent,"payload_sha256"));same(json.hash(json.read(str(intent,"intent_json"))),str(intent,"intent_sha256"));same(str(intent,"intent_sha256"),text(receipt,"intent_sha256"));
        var attempt=one(db.queryForList("SELECT intent_sha256 FROM task_section_writer_object_attempt WHERE tenant_id=? AND write_id=? LIMIT 2 FOR UPDATE",tenant,str(r,"write_id")));same(str(intent,"intent_sha256"),str(attempt,"intent_sha256"));
        var o=receipt.required("object_ref");var object=new InterfaceProtocol.ObjectRef(text(o,"object_key"),text(o,"object_version"),text(o,"sha256"),o.required("size_bytes").asText(),text(o,"media_type"));
        var ar=one(db.queryForList("SELECT * FROM artifact WHERE tenant_id=? AND project_id=? AND artifact_id=? AND artifact_kind=? LIMIT 2 FOR UPDATE",tenant,project,id,kind));StoredObjectReferences.decode(str(ar,"object_ref"),object);same(object.sha256(),str(ar,"sha256"));same(object.objectVersion(),str(ar,"object_version"));same(object.sha256(),json.hash(payload));
        var provenance=receipt.required("provenance");same(action,text(provenance,"action_id"));same(kind,text(provenance,"kind"));same(object.sha256(),text(provenance,"payload_sha256"));invocations.requireOriginalProvenance(original,provenance);
        var declaration=declared(tenant,project,actor,d.runId(),d.stepId(),d.inputSha256(),id,kind,payload,r,intent);
        audits(tenant,actor,receipt,"task.section_writer.object.registered","task.section_writer.object.registered");return new Saved(payload,receipt,object,str(r,"receipt_sha256"),declaration.schemaRef(),declaration.schemaSha256());
    }
    public Optional<JsonNode> stageFacts(TaskSectionWriterDispatchJournal.StageFacts original)throws Exception {
        original.requireUnchanged();var d=original.dispatch();var reference=original.reference();String tenant=reference.tenantId(),project=reference.projectId(),actor=original.admission().actorId();
        var records=db.queryForList("SELECT * FROM task_section_writer_submission WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",tenant,project,d.runId(),d.stepId());
        if(records.isEmpty())return Optional.empty();var row=one(records);var receipt=json.read(str(row,"receipt_json"));
        same(json.hash(receipt),str(row,"receipt_sha256"));same(actor,str(row,"created_by"));same(tenant,text(receipt,"tenant_id"));same(d.runId(),text(receipt,"run_id"));same(d.stepId(),text(receipt,"step_id"));same(d.inputSha256(),text(receipt,"input_sha256"));same("READY",text(receipt,"status"));same("1",text(receipt,"revision"));same(str(row,"action_id"),text(receipt,"action_id"));same(str(row,"candidate_id"),text(receipt,"candidate_id"));same(str(row,"candidate_artifact_id"),text(receipt,"candidate_artifact_id"));
        var candidate=saved(original,text(receipt,"candidate_artifact_id"),"SECTION_CANDIDATE",text(receipt,"action_id"));
        var body=saved(original,text(receipt,"body_artifact_id"),"SECTION_BODY",text(receipt,"action_id"));
        same(candidate.receiptHash,text(receipt,"candidate_receipt_sha256"));same(body.object.sha256(),text(receipt,"body_sha256"));
        same(body.receiptHash,text(candidate.receipt.required("provenance"),"body_receipt_sha256"));
        same(candidate.receipt.required("provenance").required("invocations"),body.receipt.required("provenance").required("invocations"));
        same(candidate.receipt.required("provenance").required("supervision"),body.receipt.required("provenance").required("supervision"));
        var artifact=ArtifactEvidenceBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.ARTIFACT,candidate.payload,candidate.schemaRef,candidate.schemaSha).read();
        if(!(artifact.output() instanceof ArtifactProtocol.SectionOutput output))bad();
        var section=((ArtifactProtocol.SectionOutput)artifact.output()).content();
        same(d.inputSha256(),artifact.inputManifestSha256());same(d.runId(),artifact.runId());same(d.stepId(),artifact.stepId());same(tenant,artifact.tenantId());same(project,artifact.projectId());
        same(body.object.sha256(),section.bodyObjectRef().sha256());same(body.object.objectKey(),section.bodyObjectRef().objectKey());same(body.object.objectVersion(),section.bodyObjectRef().objectVersion());same(text(receipt,"base_document_revision"),section.baseDocumentRevision());
        var c=one(db.queryForList("SELECT * FROM candidate WHERE tenant_id=? AND project_id=? AND candidate_id=? LIMIT 2 FOR UPDATE",tenant,project,text(receipt,"candidate_id")));
        same(d.runId(),str(c,"run_id"));same(d.stepId(),str(c,"step_id"));same(d.inputSha256(),str(c,"input_sha256"));same(text(receipt,"action_id"),str(c,"action_id"));same(Long.toString(d.leaseGeneration()),str(c,"lease_generation"));same(text(receipt,"body_artifact_id"),str(c,"content_artifact_id"));same(text(receipt,"candidate_artifact_id"),str(c,"new_evidence_artifact_id"));same(body.object.sha256(),str(c,"content_sha256"));same(text(receipt,"base_document_revision"),str(c,"base_document_revision"));same(actor,str(c,"created_by"));
        // Candidate status can legitimately change after submission. Return its original READY receipt,
        // never reinterpret later adoption/rejection as the outcome of this generation action.
        audits(tenant,actor,receipt,"task.section_writer.candidate.submitted","candidate.ready");original.requireUnchanged();return Optional.of(receipt.deepCopy());
    }
    private Saved saved(TaskSectionWriterDispatchJournal.StageFacts original,String id,String kind,String action)throws Exception {
        var d=original.dispatch();String tenant=original.reference().tenantId(),project=original.reference().projectId(),actor=original.admission().actorId();
        var r=one(db.queryForList("SELECT * FROM task_section_writer_object WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND artifact_id=? AND kind=? AND state='REGISTERED' LIMIT 2 FOR UPDATE",tenant,project,d.runId(),d.stepId(),action,id,kind));
        var receipt=json.read(str(r,"receipt_json"));var payload=json.read(str(r,"canonical_payload"));same(json.hash(receipt),str(r,"receipt_sha256"));same(json.hash(payload),str(r,"payload_sha256"));same(actor,str(r,"created_by"));same(id,text(receipt,"artifact_id"));same(d.inputSha256(),text(receipt,"input_sha256"));
        var intent=one(db.queryForList("SELECT * FROM task_section_writer_object_intent WHERE tenant_id=? AND write_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",tenant,str(r,"write_id"),id));same(str(r,"canonical_payload"),str(intent,"canonical_payload"));same(str(r,"payload_sha256"),str(intent,"payload_sha256"));same(json.hash(json.read(str(intent,"intent_json"))),str(intent,"intent_sha256"));same(str(intent,"intent_sha256"),text(receipt,"intent_sha256"));
        var attempt=one(db.queryForList("SELECT intent_sha256 FROM task_section_writer_object_attempt WHERE tenant_id=? AND write_id=? LIMIT 2 FOR UPDATE",tenant,str(r,"write_id")));same(str(intent,"intent_sha256"),str(attempt,"intent_sha256"));
        var o=receipt.required("object_ref");var object=new InterfaceProtocol.ObjectRef(text(o,"object_key"),text(o,"object_version"),text(o,"sha256"),o.required("size_bytes").asText(),text(o,"media_type"));
        var ar=one(db.queryForList("SELECT * FROM artifact WHERE tenant_id=? AND project_id=? AND artifact_id=? AND artifact_kind=? LIMIT 2 FOR UPDATE",tenant,project,id,kind));StoredObjectReferences.decode(str(ar,"object_ref"),object);same(object.sha256(),str(ar,"sha256"));same(object.objectVersion(),str(ar,"object_version"));same(object.sha256(),json.hash(payload));
        var provenance=receipt.required("provenance");same(action,text(provenance,"action_id"));same(kind,text(provenance,"kind"));same(object.sha256(),text(provenance,"payload_sha256"));invocations.requireStageProvenance(original,provenance);
        var declaration=declared(tenant,project,actor,d.runId(),d.stepId(),d.inputSha256(),id,kind,payload,r,intent);
        audits(tenant,actor,receipt,"task.section_writer.object.registered","task.section_writer.object.registered");return new Saved(payload,receipt,object,str(r,"receipt_sha256"),declaration.schemaRef(),declaration.schemaSha256());
    }
    private SchemaCatalogue.PublishedSchema declared(String tenant,String project,String actor,String run,String step,String inputSha,String id,String kind,JsonNode payload,Map<String,Object> row,Map<String,Object> intent)throws Exception {
        var original=one(db.queryForList("SELECT canonical_input,input_sha256 FROM task_section_writer_input WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",tenant,project,run,step));
        var input=json.read(str(original,"canonical_input"));same(inputSha,json.hash(input));same(inputSha,str(original,"input_sha256"));
        var role=roles.requireRegisteredIdentity(tenant,actor,new PermissionSchema().basis(input.required("role_version")),"ROLE",project);var catalogue=SchemaCatalogue.fromClasspath();var contract=TaskWriterEvidenceContract.require(role.canonicalDefinition().required("request"),catalogue);
        var binding=one(db.queryForList("SELECT schema_ref,schema_document_sha256 FROM artifact_content_binding WHERE tenant_id=? AND project_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",tenant,project,id));
        String ref=str(binding,"schema_ref"),sha=str(binding,"schema_document_sha256");same("SECTION_BODY".equals(kind)?contract.bodyRef():contract.artifactRef(null),ref);same("SECTION_BODY".equals(kind)?contract.bodySha():contract.artifactSha(),sha);same(sha,str(row,"schema_manifest_sha256"));same(sha,str(intent,"schema_manifest_sha256"));
        if("SECTION_BODY".equals(kind))DocumentEvidenceBindings.bind(catalogue,InterfaceBindings.DOCUMENT_BODY,payload,ref,sha);else ArtifactEvidenceBindings.bind(catalogue,ArtifactBindings.ARTIFACT,payload,ref,sha);
        return catalogue.requirePublished(ref,sha);
    }
    private void audits(String tenant,String actor,JsonNode receipt,String action,String eventType)throws Exception {
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",tenant,text(receipt,"audit_id")));same(receipt,json.read(str(audit,"resource_ref")));same("SUCCESS",str(audit,"result"));same(actor,str(audit,"subject_id"));if(action!=null)same(action,str(audit,"action"));
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",tenant,text(receipt,"event_id")));same(receipt,json.read(str(event,"payload")));if(eventType!=null)same(eventType,str(event,"event_type"));
    }
    private record Saved(JsonNode payload,JsonNode receipt,InterfaceProtocol.ObjectRef object,String receiptHash,String schemaRef,String schemaSha){}
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String str(Map<String,Object> row,String key){var v=row.get(key);if(v==null)bad();return v.toString();}private static String text(JsonNode n,String key){return n.required(key).textValue();}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
