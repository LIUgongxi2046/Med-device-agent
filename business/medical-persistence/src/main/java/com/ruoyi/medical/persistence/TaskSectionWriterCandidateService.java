package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real N04 candidate-only producer. Saving or submitting never adopts, approves, confirms,
 * edits a document head or declares independent N05/N07 checks successful. */
public final class TaskSectionWriterCandidateService {
    public static final String ACTION="task.candidate.submit",PURPOSE="SECTION_WRITING";
    private final JdbcTemplate db;private final TaskSectionWriterSchema json;private final TaskSectionWriterNormalizer normalizer;private final SchemaCatalogue schemas;
    public TaskSectionWriterCandidateService(DataSource data,TaskSectionWriterSchema json,TaskSectionWriterNormalizer normalizer)throws Exception{db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.normalizer=normalizer;schemas=SchemaCatalogue.fromClasspath();}
    /** The tool action must be genuinely bound before host ID normalization; no UUID is a receipt. */
    public Produced body(TaskSectionWriterInvocationService.Terminal terminal,RunTokenAuthority.ActionRequest action)throws Exception {
        terminal.requireUnchanged();var input=terminal.input();requireAction(input,action);
        var blocks=terminal.output().required("blocks");if(blocks.size()!=1||!"text".equals(blocks.get(0).path("type").asText())||!blocks.get(0).path("text").isTextual())bad();
        var projection=json.read(blocks.get(0).required("text").textValue());
        var fixed=new TaskSectionWriterNormalizer.Fixed(input.input(),input.baselineBody(),input.fieldCheck(),input.evidenceRefs(),input.sectionNodeOwners(),input.evidenceContract());
        var normalized=normalizer.normalize(fixed,action.actionId(),projection);
        return new Produced(terminal,action,normalized,"SECTION_BODY",normalized.body(),null);
    }
    public Produced candidate(Produced body,TaskSectionWriterObjectLedger.Registered physicalBody)throws Exception {
        body.requireUnchanged();physicalBody.requireUnchanged();if(!"SECTION_BODY".equals(body.kind)||!"SECTION_BODY".equals(physicalBody.kind())||!WireValues.sha256(body.bytes()).equals(physicalBody.reference().sha256())||!body.actionId().equals(physicalBody.actionId())||!body.input().runId().equals(physicalBody.runId())||!body.input().stepId().equals(physicalBody.stepId()))bad();
        var ref=physicalBody.reference();var d=body.normalized.draft();var i=body.input().input();
        var candidate=new ArtifactProtocol.SectionCandidate(i.baseDocumentRevision(),i.targetSections(),new ArtifactProtocol.ObjectRef(ref.objectKey(),ref.objectVersion(),ref.sha256(),Long.parseLong(ref.sizeBytes()),ref.mediaType()),i.bodySchemaRevision(),body.normalized.manifest(),d.claimRecords(),d.fieldAssignments(),d.attachmentBindings(),d.issueProposals());
        var output=new ArtifactProtocol.SectionOutput("SECTION_CANDIDATE",candidate);var artifact=new ArtifactProtocol.ProfessionalArtifact(body.input().evidenceContract().version(),ArtifactProtocol.ExecutionKind.TASK,i.tenantId(),i.projectId(),i.runId(),i.stepId(),body.input().inputSha256(),i.planRevision(),i.snapshotId(),i.targetSections(),TaskSectionWriterObjectLedger.plannedAccessSet(i,body.actionId(),"SECTION_CANDIDATE"),json.hash(output),output);
        ArtifactEvidenceBindings.bind(schemas,ArtifactBindings.ARTIFACT,json.tree(artifact),body.input().evidenceContract().artifactRef(null),body.input().evidenceContract().artifactSha()).read();return new Produced(body.terminal,body.action,body.normalized,"SECTION_CANDIDATE",json.bytes(artifact),physicalBody);
    }
    /** Invoked only inside the actual AT-06 FormalWorkerAccess transaction. It consumes an
     * already registered structured artifact and writes candidate, audit and receipt atomically. */
    public JsonNode submit(EnterpriseAccess.Admission admission,RunTokenAuthority.Admission admitted,RunTokenAuthority.ActionRequest action,
            JsonNode arguments,Produced candidate,TaskSectionWriterObjectLedger.Registered artifact)throws Exception {
        if(!admission.formalWorker()||!admitted.actionId().equals(action.actionId()))deny();admission.formalProof().requireActive();
        var input=admitted.currentWriterInput();input.requireUnchanged();candidate.requireUnchanged();artifact.requireUnchanged();
        if(!input.inputSha256().equals(candidate.input().inputSha256())||!input.receiptSha256().equals(candidate.input().receiptSha256())
                ||!"SECTION_CANDIDATE".equals(candidate.kind)||!"SECTION_CANDIDATE".equals(artifact.kind())||!candidate.action.equals(action)
                ||!WireValues.sha256(candidate.bytes()).equals(artifact.reference().sha256()))bad();
        schemas.validate("tool-inputs",arguments,"CandidateArgs");if(!artifact.artifactId().equals(arguments.required("candidate_artifact_id").textValue())||!json.hash(arguments).equals(action.argumentsSha256()))bad();
        requireAction(input,action);var i=input.input();var id=admission.formalProof().identity();
        if(!i.runId().equals(id.run())||!i.stepId().equals(id.step())||!action.actionId().equals(id.action())||!action.generation().toString().equals(id.generation())||!input.production().actorId().equals(admission.actorId()))deny();
        var prior=db.queryForList("SELECT * FROM task_section_writer_submission WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",i.tenantId(),i.runId(),i.stepId(),action.actionId());
        if(!prior.isEmpty())return verifySubmission(input,candidate,artifact,one(prior));
        String candidateId=UUID.randomUUID().toString(),audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();var at=now();var body=candidate.body;body.requireUnchanged();
        var original=one(db.queryForList("SELECT artifact_id FROM artifact WHERE tenant_id=? AND project_id=? AND document_revision=? AND artifact_kind='DOCUMENT_EVIDENCE' LIMIT 2 FOR UPDATE",i.tenantId(),i.projectId(),i.baseDocumentRevision()));
        String bodyHash=body.reference().sha256();insert("version_registry",Map.of("tenant_id",i.tenantId(),"version_id",candidateId,"owner_project_id",i.projectId(),"entity_kind","CANDIDATE","content_sha256",bodyHash));
        var row=meta(input,at);row.putAll(Map.of("candidate_id",candidateId,"candidate_kind","DOCUMENT","task_id",i.taskId(),"task_revision",input.production().replay().content().required("task_revision").textValue(),"template_version",i.templateVersion(),"run_id",i.runId(),"step_id",i.stepId(),"action_id",action.actionId(),"lease_generation",action.generation(),"base_document_revision",i.baseDocumentRevision()));
        row.putAll(Map.of("input_sha256",input.inputSha256(),"snapshot_id",i.snapshotId(),"plan_revision",i.planRevision(),"target_sections",json.canonical(i.targetSections()),"target_node_ids",json.canonical(candidate.normalized.manifest().stream().map(ArtifactProtocol.NodeManifestItem::nodeId).toList()),"content_ref",json.canonical(body.reference()),"content_artifact_id",body.artifactId(),"evidence_refs",json.canonical(candidate.normalized.evidence()),"original_evidence_artifact_id",str(original,"artifact_id"),"new_evidence_artifact_id",artifact.artifactId()));
        row.put("issue_proposals",json.canonical(candidate.normalized.draft().issueProposals()));row.put("dependency_access_set_id",artifact.accessSetId());row.put("content_sha256",bodyHash);row.put("status","READY");row.put("revision",1);row.put("updated_at",at);insert("candidate",row);
        var receipt=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("candidate_id",candidateId).put("status","READY").put("revision","1")
                .put("tenant_id",i.tenantId()).put("run_id",i.runId()).put("step_id",i.stepId()).put("action_id",action.actionId()).put("input_sha256",input.inputSha256()).put("candidate_artifact_id",artifact.artifactId())
                .put("candidate_receipt_sha256",artifact.receiptSha256()).put("body_artifact_id",body.artifactId()).put("body_sha256",bodyHash).put("base_document_revision",i.baseDocumentRevision()).put("audit_id",audit).put("event_id",event);
        var record=meta(input,at);record.putAll(Map.of("run_id",i.runId(),"step_id",i.stepId(),"action_id",action.actionId(),"candidate_id",candidateId,"candidate_artifact_id",artifact.artifactId(),"receipt_json",json.canonical(receipt),"receipt_sha256",json.hash(receipt)));record.put("audit_id",audit);record.put("event_id",event);
        var log=meta(input,at);log.putAll(Map.of("audit_id",audit,"subject_id",admission.actorId(),"action","task.section_writer.candidate.submitted","resource_ref",json.canonical(receipt),"result","SUCCESS","request_id",action.actionId(),"trace_id",i.runId()));insert("audit_event",log);
        insert("outbox_event",Map.of("tenant_id",i.tenantId(),"event_id",event,"aggregate_id",candidateId,"aggregate_revision",1,"event_type","candidate.ready","payload",json.canonical(receipt),"occurred_at",at));insert("task_section_writer_submission",record);
        return verifySubmission(input,candidate,artifact,one(db.queryForList("SELECT * FROM task_section_writer_submission WHERE tenant_id=? AND candidate_id=? LIMIT 2 FOR UPDATE",i.tenantId(),candidateId)));
    }
    private JsonNode verifySubmission(TaskSectionWriterInputAuthority.Verified input,Produced produced,TaskSectionWriterObjectLedger.Registered artifact,Map<String,Object> record)throws Exception {
        input.requireUnchanged();produced.requireUnchanged();artifact.requireUnchanged();var r=json.read(str(record,"receipt_json"));if(!json.hash(r).equals(str(record,"receipt_sha256"))||!artifact.artifactId().equals(str(record,"candidate_artifact_id"))||!artifact.receiptSha256().equals(r.required("candidate_receipt_sha256").textValue()))bad();
        var row=one(db.queryForList("SELECT * FROM candidate WHERE tenant_id=? AND project_id=? AND candidate_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),str(record,"candidate_id")));
        if(!produced.actionId().equals(str(row,"action_id"))||!produced.body.artifactId().equals(str(row,"content_artifact_id"))||!produced.body.reference().sha256().equals(str(row,"content_sha256"))||!input.input().baseDocumentRevision().equals(str(row,"base_document_revision"))||!input.inputSha256().equals(str(row,"input_sha256")))bad();
        var audit=one(db.queryForList("SELECT resource_ref,result,subject_id FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",input.tenantId(),r.required("audit_id").textValue()));if(!"SUCCESS".equals(str(audit,"result"))||!input.production().actorId().equals(str(audit,"subject_id"))||!r.equals(json.read(str(audit,"resource_ref"))))bad();
        var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",input.tenantId(),r.required("event_id").textValue()));if(!r.equals(json.read(str(event,"payload"))))bad();return r.deepCopy();
    }
    /** Current AT-06 consumes original immutable registrations. It never transports a live
     * Terminal/Produced proof from an earlier transaction into this one. */
    public JsonNode submitSaved(TaskSectionWriterRuntimeAuthority.Verified current,RunTokenAuthority.Admission admitted,
            RunTokenAuthority.ActionRequest action,JsonNode arguments,TaskSectionWriterInvocationService invocations)throws Exception {
        current.requireUnchanged();var admission=current.admission();var input=current.input();var i=input.input();
        if(!admission.formalWorker()||!admitted.actionId().equals(action.actionId())||!admitted.currentWriterInput().receiptSha256().equals(input.receiptSha256()))deny();
        schemas.validate("tool-inputs",arguments,"CandidateArgs");if(!json.hash(arguments).equals(action.argumentsSha256()))bad();requireAction(input,action);
        String artifactId=arguments.required("candidate_artifact_id").textValue();var saved=saved(input,artifactId,"SECTION_CANDIDATE",action.actionId());
        invocations.requireSavedProvenance(current,saved.receipt.required("provenance"));
        var full=ArtifactEvidenceBindings.bind(schemas,ArtifactBindings.ARTIFACT,saved.payload,saved.schemaRef,saved.schemaSha).read();
        if(!(full.output() instanceof ArtifactProtocol.SectionOutput))bad();
        var output=(ArtifactProtocol.SectionOutput)full.output();var candidate=output.content();
        if(!i.tenantId().equals(full.tenantId())||!i.projectId().equals(full.projectId())||!i.runId().equals(full.runId())||!i.stepId().equals(full.stepId())
                ||!input.inputSha256().equals(full.inputManifestSha256())||!i.planRevision().equals(full.planRevision())||!i.snapshotId().equals(full.snapshotId())
                ||!i.targetSections().equals(full.targetSectionIds())||!i.targetSections().equals(candidate.targetSections())||!i.baseDocumentRevision().equals(candidate.baseDocumentRevision())
                ||!json.hash(json.tree(full.output())).equals(full.contentSha256())||!saved.accessSet.equals(full.dependencyAccessSetId()))bad();
        var bodies=db.queryForList("SELECT artifact_id FROM task_section_writer_object WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? AND kind='SECTION_BODY' LIMIT 2 FOR UPDATE",i.tenantId(),i.runId(),i.stepId(),action.actionId());
        var body=saved(input,str(one(bodies),"artifact_id"),"SECTION_BODY",action.actionId());invocations.requireSavedProvenance(current,body.receipt.required("provenance"));
        var bodyRef=candidate.bodyObjectRef();if(!bodyRef.objectKey().equals(body.object.objectKey())||!bodyRef.objectVersion().equals(body.object.objectVersion())||!bodyRef.sha256().equals(body.object.sha256())||bodyRef.sizeBytes()!=Long.parseLong(body.object.sizeBytes()))bad();
        var refs=new LinkedHashSet<DatasetProtocol.EvidenceRef>();for(var claim:candidate.claimRecords())for(var link:claim.evidenceLinks())refs.add(link.evidenceRef());candidate.fieldAssignments().forEach(f->refs.addAll(f.evidenceRefs()));candidate.attachmentBindings().forEach(f->refs.addAll(f.evidenceRefs()));candidate.issueProposals().forEach(f->refs.addAll(f.evidenceRefs()));
        if(!new HashSet<>(input.evidenceRefs()).containsAll(refs))bad();
        var prior=db.queryForList("SELECT * FROM task_section_writer_submission WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",i.tenantId(),i.runId(),i.stepId(),action.actionId());
        if(!prior.isEmpty())return savedSubmission(input,action,saved,body,one(prior));
        String candidateId=UUID.randomUUID().toString(),audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();var at=now();
        var original=one(db.queryForList("SELECT artifact_id FROM artifact WHERE tenant_id=? AND project_id=? AND document_revision=? AND artifact_kind='DOCUMENT_EVIDENCE' LIMIT 2 FOR UPDATE",i.tenantId(),i.projectId(),i.baseDocumentRevision()));
        insert("version_registry",Map.of("tenant_id",i.tenantId(),"version_id",candidateId,"owner_project_id",i.projectId(),"entity_kind","CANDIDATE","content_sha256",body.object.sha256()));
        var row=meta(input,at);row.putAll(Map.of("candidate_id",candidateId,"candidate_kind","DOCUMENT","task_id",i.taskId(),"task_revision",input.production().replay().content().required("task_revision").textValue(),"template_version",i.templateVersion(),"run_id",i.runId(),"step_id",i.stepId(),"action_id",action.actionId(),"lease_generation",action.generation(),"base_document_revision",i.baseDocumentRevision()));
        row.putAll(Map.of("input_sha256",input.inputSha256(),"snapshot_id",i.snapshotId(),"plan_revision",i.planRevision(),"target_sections",json.canonical(i.targetSections()),"target_node_ids",json.canonical(candidate.nodeManifest().stream().map(ArtifactProtocol.NodeManifestItem::nodeId).toList()),"content_ref",json.canonical(body.object),"content_artifact_id",body.id,"evidence_refs",json.canonical(refs),"original_evidence_artifact_id",str(original,"artifact_id"),"new_evidence_artifact_id",saved.id));
        row.put("issue_proposals",json.canonical(candidate.issueProposals()));row.put("dependency_access_set_id",saved.accessSet);row.put("content_sha256",body.object.sha256());row.put("status","READY");row.put("revision",1);row.put("updated_at",at);insert("candidate",row);
        var receipt=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("candidate_id",candidateId).put("status","READY").put("revision","1")
                .put("tenant_id",i.tenantId()).put("run_id",i.runId()).put("step_id",i.stepId()).put("action_id",action.actionId()).put("input_sha256",input.inputSha256()).put("candidate_artifact_id",saved.id)
                .put("candidate_receipt_sha256",saved.receiptHash).put("body_artifact_id",body.id).put("body_sha256",body.object.sha256()).put("base_document_revision",i.baseDocumentRevision()).put("audit_id",audit).put("event_id",event);
        var record=meta(input,at);record.putAll(Map.of("run_id",i.runId(),"step_id",i.stepId(),"action_id",action.actionId(),"candidate_id",candidateId,"candidate_artifact_id",saved.id,"receipt_json",json.canonical(receipt),"receipt_sha256",json.hash(receipt)));record.put("audit_id",audit);record.put("event_id",event);
        var log=meta(input,at);log.putAll(Map.of("audit_id",audit,"subject_id",admission.actorId(),"action","task.section_writer.candidate.submitted","resource_ref",json.canonical(receipt),"result","SUCCESS","request_id",action.actionId(),"trace_id",i.runId()));insert("audit_event",log);
        insert("outbox_event",Map.of("tenant_id",i.tenantId(),"event_id",event,"aggregate_id",candidateId,"aggregate_revision",1,"event_type","candidate.ready","payload",json.canonical(receipt),"occurred_at",at));insert("task_section_writer_submission",record);current.requireUnchanged();
        return savedSubmission(input,action,saved,body,one(db.queryForList("SELECT * FROM task_section_writer_submission WHERE tenant_id=? AND candidate_id=? LIMIT 2 FOR UPDATE",i.tenantId(),candidateId)));
    }
    private Saved saved(TaskSectionWriterInputAuthority.Verified input,String artifact,String kind,String action)throws Exception {
        input.requireUnchanged();var i=input.input();var row=one(db.queryForList("SELECT * FROM task_section_writer_object WHERE tenant_id=? AND run_id=? AND step_id=? AND artifact_id=? AND action_id=? AND kind=? LIMIT 2 FOR UPDATE",i.tenantId(),i.runId(),i.stepId(),artifact,action,kind));
        var receipt=json.read(str(row,"receipt_json"));var payload=json.read(str(row,"canonical_payload"));
        if(!json.hash(receipt).equals(str(row,"receipt_sha256"))||!json.hash(payload).equals(str(row,"payload_sha256"))||!input.production().actorId().equals(str(row,"created_by"))||!input.inputSha256().equals(receipt.required("input_sha256").textValue()))bad();
        var intent=one(db.queryForList("SELECT * FROM task_section_writer_object_intent WHERE tenant_id=? AND write_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",i.tenantId(),str(row,"write_id"),artifact));
        if(!str(row,"payload_sha256").equals(str(intent,"payload_sha256"))||!str(row,"canonical_payload").equals(str(intent,"canonical_payload"))||!json.hash(json.read(str(intent,"intent_json"))).equals(str(intent,"intent_sha256"))||!str(intent,"intent_sha256").equals(receipt.required("intent_sha256").textValue()))bad();
        var attempted=one(db.queryForList("SELECT intent_sha256 FROM task_section_writer_object_attempt WHERE tenant_id=? AND write_id=? LIMIT 2 FOR UPDATE",i.tenantId(),str(row,"write_id")));if(!str(intent,"intent_sha256").equals(str(attempted,"intent_sha256")))bad();
        var ar=one(db.queryForList("SELECT * FROM artifact WHERE tenant_id=? AND project_id=? AND artifact_id=? AND artifact_kind=? LIMIT 2 FOR UPDATE",i.tenantId(),i.projectId(),artifact,kind));var o=receipt.required("object_ref");
        var object=new InterfaceProtocol.ObjectRef(o.required("object_key").textValue(),o.required("object_version").textValue(),o.required("sha256").textValue(),o.required("size_bytes").asText(),o.required("media_type").textValue());StoredObjectReferences.decode(str(ar,"object_ref"),object);
        if(!json.hash(payload).equals(object.sha256())||!object.sha256().equals(str(ar,"sha256"))||!object.objectVersion().equals(str(ar,"object_version")))bad();
        var binding=one(db.queryForList("SELECT schema_ref,schema_document_sha256 FROM artifact_content_binding WHERE tenant_id=? AND project_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",i.tenantId(),i.projectId(),artifact));
        String declaredRef=str(binding,"schema_ref"),declaredSha=str(binding,"schema_document_sha256");var contract=input.evidenceContract();String expectedRef="SECTION_BODY".equals(kind)?contract.bodyRef():contract.artifactRef(null),expectedSha="SECTION_BODY".equals(kind)?contract.bodySha():contract.artifactSha();
        if(!expectedRef.equals(declaredRef)||!expectedSha.equals(declaredSha)||!declaredSha.equals(str(row,"schema_manifest_sha256"))||!declaredSha.equals(str(intent,"schema_manifest_sha256")))bad();
        if("SECTION_BODY".equals(kind))DocumentEvidenceBindings.bind(schemas,InterfaceBindings.DOCUMENT_BODY,payload,declaredRef,declaredSha);else ArtifactEvidenceBindings.bind(schemas,ArtifactBindings.ARTIFACT,payload,declaredRef,declaredSha);
        var audit=one(db.queryForList("SELECT resource_ref,result,subject_id FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",i.tenantId(),receipt.required("audit_id").textValue()));if(!receipt.equals(json.read(str(audit,"resource_ref")))||!"SUCCESS".equals(str(audit,"result"))||!input.production().actorId().equals(str(audit,"subject_id")))bad();
        var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",i.tenantId(),receipt.required("event_id").textValue()));if(!receipt.equals(json.read(str(event,"payload"))))bad();return new Saved(artifact,object,payload,receipt,str(row,"receipt_sha256"),str(ar,"dependency_access_set_id"),declaredRef,declaredSha);
    }
    private JsonNode savedSubmission(TaskSectionWriterInputAuthority.Verified input,RunTokenAuthority.ActionRequest action,Saved saved,Saved body,Map<String,Object> record)throws Exception {
        var receipt=json.read(str(record,"receipt_json"));if(!json.hash(receipt).equals(str(record,"receipt_sha256"))||!saved.id.equals(str(record,"candidate_artifact_id"))||!saved.receiptHash.equals(receipt.required("candidate_receipt_sha256").textValue()))bad();
        var row=one(db.queryForList("SELECT * FROM candidate WHERE tenant_id=? AND project_id=? AND candidate_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),str(record,"candidate_id")));
        if(!body.id.equals(str(row,"content_artifact_id"))||!body.object.sha256().equals(str(row,"content_sha256"))||!action.actionId().equals(str(row,"action_id"))||!input.input().baseDocumentRevision().equals(str(row,"base_document_revision"))||!input.inputSha256().equals(str(row,"input_sha256")))bad();
        var audit=one(db.queryForList("SELECT resource_ref,result FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",input.tenantId(),receipt.required("audit_id").textValue()));if(!receipt.equals(json.read(str(audit,"resource_ref")))||!"SUCCESS".equals(str(audit,"result")))bad();
        var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",input.tenantId(),receipt.required("event_id").textValue()));if(!receipt.equals(json.read(str(event,"payload"))))bad();input.requireUnchanged();return receipt.deepCopy();
    }
    private record Saved(String id,InterfaceProtocol.ObjectRef object,JsonNode payload,JsonNode receipt,String receiptHash,String accessSet,String schemaRef,String schemaSha){}
    private void requireAction(TaskSectionWriterInputAuthority.Verified input,RunTokenAuthority.ActionRequest action)throws Exception {
        input.requireUnchanged();var i=input.input();var scope=new PermissionProtocol.Scope(i.tenantId(),i.projectId(),"TASK",i.taskId());
        if(!"TOOL".equals(action.kind())||!ACTION.equals(action.action())||!PURPOSE.equals(action.purpose())||!scope.equals(action.scope())||!i.submitToolVersion().version_id().equals(action.toolVersion())
                ||!i.runId().equals(action.runId())||!i.stepId().equals(action.stepId())||!input.inputSha256().equals(action.inputSha256())||!i.nodePermissions().contains(new RunTokenAuthoritySchema.Permission(scope,ACTION,PURPOSE)))deny();
        var rows=db.queryForList("SELECT b.action_id FROM step_action_binding b JOIN run_step s ON s.tenant_id=b.tenant_id AND s.project_id=b.project_id AND s.run_id=b.run_id AND s.step_id=b.step_id WHERE b.tenant_id=? AND b.project_id=? AND b.run_id=? AND b.step_id=? AND b.action_id=? AND b.kind='TOOL' AND b.tool_version=? AND b.arguments_sha256=? AND b.input_sha256=? AND b.lease_generation=? AND s.lease_generation=b.lease_generation AND s.status='RUNNING' AND s.lease_expires_at>UTC_TIMESTAMP(6) AND b.state IN ('BOUND','RESERVED','DISPATCHING','IN_FLIGHT') LIMIT 2 FOR UPDATE",i.tenantId(),i.projectId(),i.runId(),i.stepId(),action.actionId(),action.toolVersion(),action.argumentsSha256(),action.inputSha256(),action.generation());if(rows.size()!=1)deny();
    }
    public final class Produced {
        private final TaskSectionWriterInputAuthority.Verified fixedInput;private final TaskSectionWriterInvocationService.Terminal terminal;private final RunTokenAuthority.ActionRequest action;private final TaskSectionWriterNormalizer.Normalized normalized;
        private final String kind;private final byte[] bytes;private final TaskSectionWriterObjectLedger.Registered body;
        private Produced(TaskSectionWriterInvocationService.Terminal t,RunTokenAuthority.ActionRequest action,TaskSectionWriterNormalizer.Normalized n,String kind,byte[] bytes,TaskSectionWriterObjectLedger.Registered body)throws Exception{fixedInput=t.input();terminal=t;this.action=action;normalized=n;this.kind=kind;this.bytes=bytes.clone();this.body=body;}
        public void requireUnchanged()throws Exception{terminal.requireUnchanged();requireAction(terminal.input(),action);if(body!=null){body.requireUnchanged();if(!WireValues.sha256(normalized.body()).equals(body.reference().sha256()))bad();}}
        public TaskSectionWriterInputAuthority.Verified input(){return fixedInput;}public String actionId(){return action.actionId();}public String kind(){return kind;}public byte[] bytes()throws Exception{requireUnchanged();return bytes.clone();}
        public String schemaRef()throws Exception{return "SECTION_BODY".equals(kind)?input().evidenceContract().bodyRef():input().evidenceContract().artifactRef(null);}
        public String schemaSha()throws Exception{return "SECTION_BODY".equals(kind)?input().evidenceContract().bodySha():input().evidenceContract().artifactSha();}
        public JsonNode provenance()throws Exception{requireUnchanged();var p=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("node_kind","N04").put("action_id",actionId()).put("kind",kind).put("session_id",terminal.sessionId()).put("input_sha256",input().inputSha256()).put("input_receipt_sha256",input().receiptSha256()).put("payload_sha256",WireValues.sha256(bytes));
            if(input().evidenceContract().v2())p.put("schema_ref",schemaRef()).put("schema_sha256",schemaSha());
            p.set("invocations",terminal.canonicalPins());p.set("supervision",terminal.supervisionPins());p.set("normalization",normalized.pins());p.put("field_check_artifact_id",input().input().fieldCheckArtifactId()).put("field_check_receipt_sha256",input().input().fieldCheckReceiptSha256());if(body!=null)p.put("body_receipt_sha256",body.receiptSha256());return p;}
    }
    private Map<String,Object> meta(TaskSectionWriterInputAuthority.Verified input,LocalDateTime at)throws Exception{var m=new LinkedHashMap<String,Object>();m.put("tenant_id",input.tenantId());m.put("project_id",input.projectId());m.put("created_by",input.production().actorId());m.put("created_at",at);m.put("classification",input.production().classification());m.put("schema_version","1.0.0");m.put("schema_manifest_sha256",schemas.schemaDigest("interfaces"));return m;}
    private void insert(String table,Map<String,Object> values){var keys=new ArrayList<>(values.keySet());if(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(values::get).toArray())!=1)bad();}
    private LocalDateTime now(){return db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class);}private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String str(Map<String,Object> row,String key){var v=row.get(key);if(v==null)bad();return v.toString();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
