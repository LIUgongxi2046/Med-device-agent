package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;

/** Output factories consume private session and independently authenticated closure evidence.
 * Host FINISH, UUIDs, file-hash lists and model text alone never construct Produced. */
public final class TaskResearchExecutionService {
    private TaskResearchEvidenceContract evidenceContracts;
    @org.springframework.beans.factory.annotation.Autowired void evidenceContracts(TaskResearchEvidenceContract value){evidenceContracts=value;}
    private final PermissionSchema json;private final TaskProfessionalSchema schema;private final TaskResearchSourceReader sources;private final TaskResearchRetrievalAuthority retrievals;
    public TaskResearchExecutionService(PermissionSchema json,TaskProfessionalSchema schema){this(json,schema,null,null);}
    public TaskResearchExecutionService(PermissionSchema json,TaskProfessionalSchema schema,TaskResearchSourceReader sources,TaskResearchRetrievalAuthority retrievals){this.json=Objects.requireNonNull(json);this.schema=Objects.requireNonNull(schema);this.sources=sources;this.retrievals=retrievals;}

    /** Diagnostic export is truthful even for a failed process; it does not assert business success. */
    public Produced sessionExport(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Closed closure)throws Exception {
        requireClosure(session,closure);
        var archive=schema.parse(new String(closure.sessionExport(),java.nio.charset.StandardCharsets.UTF_8));
        ProfessionalRuntimeObservations.closed(archive,Set.of("files","observation"));
        var value=json.object().put("schema_version","1.0.0").put("kind","SESSION_EXPORT").put("session_id",session.sessionId()).put("runtime_manifest_sha256",session.launch().required("runtime_manifest_sha256").textValue()).put("supervision_sha256",closure.sha256());
        value.set("binding",session.binding());value.set("files",archive.required("files"));value.set("observation",archive.required("observation"));
        var observation=value.required("observation");if(!session.sessionId().equals(observation.path("session_id").asText())||!FormalTaskNodeAuthority.sameEvidence(session.binding(),observation.path("binding")))bad();
        byte[] payload=ContentReadAuthorization.canonicalBytes(value);schema.read("SESSION_EXPORT",payload);
        return new Produced(session,closure,"SESSION_EXPORT",payload,provenance(session,closure,"SESSION_EXPORT",payload,json.object()));
    }
    /** One strict final text projection from a genuinely settled, registered C07 model result.
     * Parsing the candidate grants no completion status; the separate evaluator supplies it. */
    public Candidate candidate(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Closed closure,TaskInvocationService.Terminal terminal)throws Exception {
        requireClosure(session,closure);closure.requireSuccessfulExit();terminal.requireFor(session,closure);
        if(sources==null||retrievals==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var publication=publication(session);var projection=candidateProjection(terminal.output(),publication);var bundle=projectionBundle(projection,publication);
        if(bundle.questionCoverage().size()!=1||!session.input().questionId().equals(bundle.questionCoverage().get(0).questionId()))bad();
        var refs=new LinkedHashSet<DatasetProtocol.EvidenceRef>(bundle.selectedRefs());refs.addAll(bundle.conflictRefs());
        for(var link:bundle.questionCoverage().get(0).evidenceLinks())if(!refs.contains(link.evidenceRef()))bad();
        var reads=sources.requireReceipts(session.admission(),session.input(),bundle.readReceiptIds(),List.copyOf(refs));
        var retrieved=retrievals.require(session,bundle.retrievalIds(),"artifacts-v2".equals(publication.name()));
        return new Candidate(session,closure,terminal,projection,bundle,reads,retrieved,publication);
    }
    private SchemaCatalogue.PublishedSchema publication(TaskProfessionalRuntimeService.Session session)throws Exception {
        if(evidenceContracts==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var input=session.input();input.requireUnchanged();return evidenceContracts.require(input.tenantId(),session.admission().actorId(),input.projectId(),input.manifest());
    }
    static JsonNode candidateProjection(JsonNode modelResult)throws Exception {return candidateProjection(modelResult,null);}
    static JsonNode candidateProjection(JsonNode modelResult,SchemaCatalogue.PublishedSchema publication)throws Exception {
        var blocks=modelResult.required("blocks");if(!blocks.isArray()||blocks.size()!=1||!"text".equals(blocks.get(0).path("type").asText())||!blocks.get(0).path("text").isTextual())bad();
        byte[] text=blocks.get(0).required("text").textValue().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(text.length==0||text.length>2*1024*1024)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var parser=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
        var value=parser.readTree(text);projectionBundle(value,publication);return value;
    }
    static ArtifactProtocol.EvidenceBundle projectionBundle(JsonNode projection)throws Exception {return projectionBundle(projection,null);}
    static ArtifactProtocol.EvidenceBundle projectionBundle(JsonNode projection,SchemaCatalogue.PublishedSchema publication)throws Exception {
        ProfessionalRuntimeObservations.closed(projection,Set.of("retrieval_ids","selected_refs","conflict_refs","missing_requirement_ids","read_receipt_ids","question_coverage"));
        var questions=projection.required("question_coverage");if(!questions.isArray()||questions.size()!=1)bad();
        ProfessionalRuntimeObservations.closed(questions.get(0),Set.of("question_id","evidence_links","missing_requirement_ids"));
        var checked=(com.fasterxml.jackson.databind.node.ObjectNode)projection.deepCopy();var question=(com.fasterxml.jackson.databind.node.ObjectNode)checked.required("question_coverage").get(0);
        // Schema-only adapter: no status is emitted or treated as a determination here.
        question.put("status","INSUFFICIENT");question.putArray("completion_check_ids");
        return publication==null?TypedSchemaBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.EVIDENCE_BUNDLE,checked).read():ArtifactEvidenceBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.EVIDENCE_BUNDLE,checked,publication.schemaRef(),publication.schemaSha256()).read();
    }
    public final class Candidate {
        private final TaskProfessionalRuntimeService.Session session;private final TaskProfessionalSupervisor.Closed closure;private final TaskInvocationService.Terminal terminal;
        private final SchemaCatalogue.PublishedSchema publication;private final JsonNode projection,invocations;private final ArtifactProtocol.EvidenceBundle bundle;private final String digest;
        private final TaskResearchSourceReader.VerifiedReceipts reads;private final TaskResearchRetrievalAuthority.Verified retrieved;
        private Candidate(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Closed closure,TaskInvocationService.Terminal terminal,JsonNode projection,ArtifactProtocol.EvidenceBundle bundle,TaskResearchSourceReader.VerifiedReceipts reads,TaskResearchRetrievalAuthority.Verified retrieved,SchemaCatalogue.PublishedSchema publication)throws Exception {
            this.publication=publication;this.session=session;this.closure=closure;this.terminal=terminal;this.projection=projection.deepCopy();this.bundle=bundle;this.reads=reads;this.retrieved=retrieved;this.invocations=terminal.canonicalPins();this.digest=WireValues.sha256(ContentReadAuthorization.canonicalBytes(projection));
        }
        public void requireUnchanged()throws Exception {requireClosure(session,closure);closure.requireSuccessfulExit();terminal.requireFor(session,closure);if(!publication.equals(publication(session)))bad();reads.requireUnchanged();retrieved.requireUnchanged();if(!FormalTaskNodeAuthority.sameEvidence(projection,candidateProjection(terminal.output(),publication))||!FormalTaskNodeAuthority.sameEvidence(invocations,terminal.canonicalPins())||!digest.equals(WireValues.sha256(ContentReadAuthorization.canonicalBytes(projection))))bad();}
        public TaskProfessionalRuntimeService.Session session(){TaskProfessionalRuntimeService.current(session.admission());return session;}
        public TaskProfessionalSupervisor.Closed closure(){TaskProfessionalRuntimeService.current(session.admission());return closure;}
        public TaskProfessionalSupervisor.Closed supervision(){return closure();}
        public String questionId(){return session.input().questionId();}public String candidateSha256(){TaskProfessionalRuntimeService.current(session.admission());return digest;}
        public JsonNode projection(){TaskProfessionalRuntimeService.current(session.admission());return projection.deepCopy();}
        public TaskResearchSourceReader.VerifiedReceipts sourceReceipts(){TaskProfessionalRuntimeService.current(session.admission());return reads;}
        public JsonNode invocationPins()throws Exception{requireUnchanged();return invocations.deepCopy();}
        public JsonNode retrievalPins()throws Exception{requireUnchanged();return retrieved.canonicalPins();}
    }
    public Produced evidenceBundle(Candidate candidate,TaskResearchCompletionEvaluation.Evaluated evaluated)throws Exception {
        requireEvaluation(candidate,evaluated);var value=(com.fasterxml.jackson.databind.node.ObjectNode)candidate.projection();var question=(com.fasterxml.jackson.databind.node.ObjectNode)value.required("question_coverage").get(0);
        question.put("status",evaluated.questionStatus().name());question.set("completion_check_ids",json.tree(evaluated.completionCheckIds()));
        byte[] bytes=ContentReadAuthorization.canonicalBytes(value);schema.read("EVIDENCE_BUNDLE",bytes,candidate.publication.schemaRef(),candidate.publication.schemaSha256());
        return new Produced(candidate.session,candidate.closure,"EVIDENCE_BUNDLE",bytes,provenance(candidate.session,candidate.closure,"EVIDENCE_BUNDLE",bytes,producing(candidate,evaluated)),candidate,evaluated,null,null);
    }
    public Produced stepResult(Candidate candidate,TaskResearchCompletionEvaluation.Evaluated evaluated,TaskProfessionalObjectRegistration.Registered evidence,TaskProfessionalObjectRegistration.Registered export)throws Exception {
        requireEvaluation(candidate,evaluated);var expected=evidenceBundle(candidate,evaluated);requireRegistered(candidate,evidence,"EVIDENCE_BUNDLE",expected.payloadSha256());requireRegistered(candidate,export,"SESSION_EXPORT",sessionExport(candidate.session,candidate.closure).payloadSha256());
        var value=json.object().put("schema_version","1.0.0").put("execution_kind","TASK").put("node_kind","N02").put("question_id",candidate.questionId()).put("session_id",candidate.session.sessionId()).put("outcome",evaluated.outcome());
        value.set("binding",candidate.session.binding());value.set("evidence_bundle",pin(evidence));value.set("session_export",pin(export));value.set("invocation_ids",json.tree(candidate.terminal.invocationIds()));value.set("source_read_receipt_ids",json.tree(candidate.bundle.readReceiptIds()));
        byte[] bytes=ContentReadAuthorization.canonicalBytes(value);schema.read("STEP_RESULT",bytes);var producing=producing(candidate,evaluated);producing.set("evidence_bundle",pin(evidence));producing.set("session_export",pin(export));
        return new Produced(candidate.session,candidate.closure,"STEP_RESULT",bytes,provenance(candidate.session,candidate.closure,"STEP_RESULT",bytes,producing),candidate,evaluated,evidence,export);
    }
    private void requireEvaluation(Candidate candidate,TaskResearchCompletionEvaluation.Evaluated evaluated)throws Exception {
        candidate.requireUnchanged();evaluated.requireUnchanged();if(!candidate.candidateSha256().equals(evaluated.candidateSha256()))bad();
        if("SUCCEEDED".equals(evaluated.outcome())&&(evaluated.questionStatus()!=ArtifactProtocol.QuestionStatus.VERIFIED||evaluated.waitingItemId()!=null||candidate.bundle.selectedRefs().isEmpty()||!candidate.bundle.conflictRefs().isEmpty()||!candidate.bundle.missingRequirementIds().isEmpty()||!candidate.bundle.questionCoverage().get(0).missingRequirementIds().isEmpty()||evaluated.completionCheckIds().isEmpty()))bad();
        if("WAITING_INPUT".equals(evaluated.outcome())&&evaluated.waitingItemId()==null)bad();
    }
    private com.fasterxml.jackson.databind.node.ObjectNode producing(Candidate candidate,TaskResearchCompletionEvaluation.Evaluated evaluated)throws Exception {
        var value=json.object().put("candidate_sha256",candidate.candidateSha256());value.set("source_read_receipts",candidate.sourceReceipts().canonicalPins());value.set("invocations",candidate.invocationPins());value.set("retrievals",candidate.retrievalPins());value.set("completion_check_ids",json.tree(evaluated.completionCheckIds()));if(evaluated.waitingItemId()==null)value.putNull("waiting_item_id");else value.put("waiting_item_id",evaluated.waitingItemId());return value;
    }
    private void requireRegistered(Candidate c,TaskProfessionalObjectRegistration.Registered registered,String kind,String hash)throws Exception {
        registered.requireUnchanged();if(!kind.equals(registered.kind())||!c.session.sessionId().equals(registered.sessionId())||!c.session.runId().equals(registered.runId())||!c.session.stepId().equals(registered.stepId())||!c.session.actionId().equals(registered.actionId())||!c.session.generation().equals(registered.leaseGeneration())||!c.session.workerService().equals(registered.workerService())||!c.session.input().inputId().equals(registered.inputId())||!c.session.input().receiptSha256().equals(registered.inputReceiptSha256())||!hash.equals(registered.reference().sha256()))bad();
    }
    private JsonNode pin(TaskProfessionalObjectRegistration.Registered registered){return json.object().put("artifact_id",registered.artifactId()).put("sha256",registered.reference().sha256()).put("object_version",registered.reference().objectVersion()).put("receipt_sha256",registered.receiptSha256());}
    private JsonNode provenance(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Closed closure,String kind,byte[] bytes,JsonNode producing)throws Exception {
        var input=session.input();var identity=session.admission().formalProof().identity();
        var proof=json.object().put("schema_version","1.0.0").put("phase","POST_CLOSE").put("kind",kind).put("tenant_id",identity.tenant()).put("project_id",identity.project()).put("run_id",identity.run()).put("step_id",identity.step()).put("action_id",identity.action()).put("lease_generation",Long.parseLong(identity.generation())).put("worker_service_uri",identity.workerService()).put("session_id",session.sessionId()).put("input_artifact_id",input.inputId()).put("input_sha256",input.inputSha256()).put("input_receipt_sha256",input.receiptSha256()).put("payload_sha256",WireValues.sha256(bytes)).put("supervision_receipt_id",closure.receiptId()).put("supervision_sha256",closure.sha256()).put("deployment_sha256",closure.deploymentSha256());
        proof.set("producing_receipts",producing);return proof;
    }
    private static void requireClosure(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Closed closure)throws Exception {
        Objects.requireNonNull(session);Objects.requireNonNull(closure);session.requireUnchanged();closure.requireUnchanged();
        if(!session.actionId().equals(session.admission().formalProof().identity().action())||!"CLOSED".equals(session.state())||!session.sessionId().equals(closure.sessionId())||!FormalTaskNodeAuthority.sameEvidence(session.binding(),closure.binding())||!session.deployment().sha256().equals(closure.deploymentSha256()))bad();
    }
    public final class Produced {
        private final TaskProfessionalRuntimeService.Session session;private final TaskProfessionalSupervisor.Closed closure;
        private final String kind,digest;private final byte[] payload;private final JsonNode provenance;private final Candidate candidate;private final TaskResearchCompletionEvaluation.Evaluated evaluated;private final TaskProfessionalObjectRegistration.Registered evidence,export;
        private Produced(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Closed closure,String kind,byte[] payload,JsonNode provenance)throws Exception {
            this(session,closure,kind,payload,provenance,null,null,null,null);
        }
        private Produced(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Closed closure,String kind,byte[] payload,JsonNode provenance,Candidate candidate,TaskResearchCompletionEvaluation.Evaluated evaluated,TaskProfessionalObjectRegistration.Registered evidence,TaskProfessionalObjectRegistration.Registered export)throws Exception {
            this.session=session;this.closure=closure;this.kind=kind;this.payload=payload.clone();this.provenance=provenance.deepCopy();this.digest=WireValues.sha256(payload);this.candidate=candidate;this.evaluated=evaluated;this.evidence=evidence;this.export=export;
        }
        public void requireUnchanged()throws Exception {requireClosure(session,closure);if(candidate!=null){requireEvaluation(candidate,evaluated);if(evidence!=null)evidence.requireUnchanged();if(export!=null)export.requireUnchanged();}schema.read(kind,payload,schemaRef(),schemaSha256());if(!digest.equals(WireValues.sha256(payload))||!digest.equals(provenance.required("payload_sha256").textValue())||!closure.sha256().equals(provenance.required("supervision_sha256").textValue()))bad();}
        public String schemaRef(){return "EVIDENCE_BUNDLE".equals(kind)?candidate.publication.schemaRef():schema.ref(kind);}
        public String schemaSha256(){return "EVIDENCE_BUNDLE".equals(kind)?candidate.publication.schemaSha256():schema.manifest(kind);}
        public String kind(){TaskProfessionalRuntimeService.current(session.admission());return kind;}
        public byte[] bytes()throws Exception{requireUnchanged();return payload.clone();}public String payloadSha256(){TaskProfessionalRuntimeService.current(session.admission());return digest;}
        public String runId(){return session.runId();}public String stepId(){return session.stepId();}public String actionId(){return session.actionId();}
        public String leaseGeneration(){return session.generation();}public String workerService(){return session.workerService();}
        public String inputId(){return session.input().inputId();}public String inputSha256(){return session.input().inputSha256();}public String sessionId(){return session.sessionId();}
        TaskProfessionalRuntimeService.Session session(){TaskProfessionalRuntimeService.current(session.admission());return session;}
        TaskProfessionalSupervisor.Closed supervision(){TaskProfessionalRuntimeService.current(session.admission());return closure;}
        JsonNode provenance(){TaskProfessionalRuntimeService.current(session.admission());return provenance.deepCopy();}
    }
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
