package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** A predecessor is complete only through original registered professional outputs. Run-step
 * status, an SDK answer, host FINISH, a UUID, and a caller boolean are never completion proofs. */
public final class TaskResearchCompletionAuthority {
    private TaskResearchEvidenceContract evidenceContracts;
    @org.springframework.beans.factory.annotation.Autowired void evidenceContracts(TaskResearchEvidenceContract value){evidenceContracts=value;}
    private org.springframework.beans.factory.ObjectProvider<TaskRunSuccessReuse> reuse;
    @org.springframework.beans.factory.annotation.Autowired void reuse(org.springframework.beans.factory.ObjectProvider<TaskRunSuccessReuse> value){reuse=value;}
    public static final String RESULT_REF="urn:medical:task-research-step:1.0.0#/$defs/StepResult";
    public static final String EXPORT_REF="urn:medical:task-research-step:1.0.0#/$defs/SessionExport";
    private final JdbcTemplate db;private final PermissionSchema json;private final TaskResearchSourceReader sources;private final TaskProfessionalSupervisor supervisor;private final TaskProfessionalRuntimeService.Deployment deployment;private final TaskResearchRuleInputs ruleInputs;private final RuleExpression ruleEngine;private final TaskInvocationResults invocations;
    private final TaskProfessionalObjectRegistration originalObjects;private final TaskNodeInputLedger inputLedger;private final TaskNodeInputRegistration inputRegistrations;private final TaskResearchRetrievalAuthority retrievals;private final TaskResearchWaitingService waiting;
    private final JsonMapper reader=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS,com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    public TaskResearchCompletionAuthority(DataSource data,PermissionSchema json,TaskResearchSourceReader sources,TaskProfessionalSupervisor supervisor,
            TaskProfessionalRuntimeService.Deployment deployment,TaskResearchRuleInputs ruleInputs,RuleExpression ruleEngine,TaskInvocationResults invocations,
            TaskNodeInputLedger inputLedger,TaskNodeInputRegistration inputRegistrations,TaskResearchRetrievalAuthority retrievals,TaskResearchWaitingService waiting,TaskProfessionalObjectRegistration originalObjects){
        this(new JdbcTemplate(data),json,sources,supervisor,deployment,ruleInputs,ruleEngine,invocations,inputLedger,inputRegistrations,retrievals,waiting,originalObjects);
    }
    TaskResearchCompletionAuthority(JdbcTemplate db,PermissionSchema json,TaskResearchSourceReader sources,TaskProfessionalSupervisor supervisor,
            TaskProfessionalRuntimeService.Deployment deployment,TaskResearchRuleInputs ruleInputs,RuleExpression ruleEngine,TaskInvocationResults invocations,
            TaskNodeInputLedger inputLedger,TaskNodeInputRegistration inputRegistrations,TaskResearchRetrievalAuthority retrievals,TaskResearchWaitingService waiting,TaskProfessionalObjectRegistration originalObjects){
        this.db=Objects.requireNonNull(db);db.setQueryTimeout(5);this.json=Objects.requireNonNull(json);this.sources=Objects.requireNonNull(sources);
        this.supervisor=Objects.requireNonNull(supervisor);this.deployment=Objects.requireNonNull(deployment);this.ruleInputs=Objects.requireNonNull(ruleInputs);
        this.ruleEngine=Objects.requireNonNull(ruleEngine);this.invocations=Objects.requireNonNull(invocations);this.inputLedger=Objects.requireNonNull(inputLedger);
        this.inputRegistrations=Objects.requireNonNull(inputRegistrations);this.retrievals=Objects.requireNonNull(retrievals);this.waiting=Objects.requireNonNull(waiting);this.originalObjects=Objects.requireNonNull(originalObjects);
    }

    /** Predecessor completion is historical: a real SUCCEEDED step and its original atomic COMMIT
     * are mandatory. It is deliberately not usable to authorize a first commit. */
    public Verified require(EnterpriseAccess.Admission a,String run,String step)throws Exception {
        active(a);WireValues.uuid(run);WireValues.uuid(step);
        if(reuse!=null){var imported=reuse.getObject().completion(a,run,step);if(imported.isPresent())return imported.get();}
        var originals=db.queryForList("SELECT i.planned_artifact_id FROM runtime_object_intent i JOIN runtime_registered_object r ON r.tenant_id=i.tenant_id AND r.project_id=i.project_id AND r.run_id=i.run_id AND r.write_id=i.write_id WHERE i.tenant_id=? AND i.run_id=? AND i.step_id=? AND i.write_kind='STEP_RESULT' AND i.schema_version='2.0.0' AND i.schema_ref=? AND i.created_by=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),run,step,RESULT_REF,a.actorId());
        var verified=verify(a,run,step,text(one(originals),"planned_artifact_id"),Mode.COMMITTED);
        var commit=requireCommit(verified);
        String fingerprint=fingerprint(Map.of("verified",verified.fingerprint(),"commit",commit));
        return new Verified(a,run,step,verified.result(),verified.evidence(),text(commit,"receipt_sha256"),fingerprint);
    }

    /** Original result verification for one private RN-05 source. No execution principal is substituted. */
    public Verified requireOriginal(TaskRunResumeOriginalAuthority.Source source)throws Exception {
        source.requireUnchanged();same("N02",source.stageKind());var a=source.admission();String run=source.originalRunId(),step=source.originalStepId();
        var originals=db.queryForList("SELECT i.planned_artifact_id FROM runtime_object_intent i JOIN runtime_registered_object r ON r.tenant_id=i.tenant_id AND r.project_id=i.project_id AND r.run_id=i.run_id AND r.write_id=i.write_id WHERE i.tenant_id=? AND i.run_id=? AND i.step_id=? AND i.write_kind='STEP_RESULT' AND i.schema_version='2.0.0' AND i.schema_ref=? AND i.created_by=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),run,step,RESULT_REF,a.actorId());
        var verified=verify(a,run,step,text(one(originals),"planned_artifact_id"),Mode.COMMITTED,source);var commit=requireCommit(verified);
        var next=inputRegistrations.readStored(a,inputLedger.stored(a,source.successorRunId(),source.successorStepId())).orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH)).input();
        var prior=verified.input().manifest();
        for(String field:List.of("question_id","question_key","question_text","section_id","plan_revision","plan_sha256","snapshot_id","snapshot_sha256","role_version","execution_policy_version","fixed_sources","node_permissions"))same(prior.required(field),next.required(field));
        // Step IDs differ between runs. The actual predecessor objects/receipts may not.
        var oldPins=prior.required("predecessors").deepCopy();var newPins=next.required("predecessors").deepCopy();for(var pin:oldPins)((com.fasterxml.jackson.databind.node.ObjectNode)pin).remove("step_id");for(var pin:newPins)((com.fasterxml.jackson.databind.node.ObjectNode)pin).remove("step_id");same(oldPins,newPins);
        source.requireUnchanged();return new Verified(a,run,step,verified.result(),verified.evidence(),text(commit,"receipt_sha256"),fingerprint(Map.of("verified",verified.fingerprint(),"commit",commit)),source);
    }

    /** Sole pre-COMMIT capability. No caller Completion, copied state, fabricated terminal step,
     * existing COMMIT, provider call or database write participates in this factory. */
    public ProducedCompletion requireProduced(EnterpriseAccess.Admission a,String run,String step,String resultId)throws Exception {
        active(a);if(!a.controlWorker()||a.formalWorker()||a.simulationWorker()||a.recoveryWorker())deny();
        WireValues.uuid(run);WireValues.uuid(step);WireValues.uuid(resultId);
        return new ProducedCompletion(a,run,step,resultId,verify(a,run,step,resultId,Mode.PRODUCED));
    }
    private enum Mode { PRODUCED,COMMITTED }
    private record Checked(Input input,ObjectRecord result,ObjectRecord evidence,ObjectRecord export,JsonNode sourcePins,JsonNode invocationPins,
            JsonNode retrievalPins,JsonNode checkPins,JsonNode waitingCause,String outcome,String waitingId,String fingerprint) { }
    private Checked verify(EnterpriseAccess.Admission a,String run,String step,String resultId,Mode mode)throws Exception {return verify(a,run,step,resultId,mode,null);}
    private Checked verify(EnterpriseAccess.Admission a,String run,String step,String resultId,Mode mode,TaskRunResumeOriginalAuthority.Source source)throws Exception {
        var result=object(a,run,step,resultId,"STEP_RESULT",RESULT_REF);var value=result.payload();
        closed(value,Set.of("schema_version","execution_kind","node_kind","binding","question_id","session_id","outcome","evidence_bundle","session_export","invocation_ids","source_read_receipt_ids"));
        same("1.0.0",text(value,"schema_version"));same("TASK",text(value,"execution_kind"));same("N02",text(value,"node_kind"));
        String claimed=text(value,"outcome");if(!Set.of("SUCCEEDED","WAITING_INPUT").contains(claimed))bad();
        if(mode==Mode.COMMITTED&&!"SUCCEEDED".equals(claimed))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        var binding=ProfessionalRuntimeObservations.jsonBinding(value.required("binding"));same(a.context().tenant_id(),binding.tenant_id());same(run,binding.run_id());same(step,binding.step_id());
        String session=text(value,"session_id");WireValues.uuid(session);
        var input=readInput(a,run,step,session,binding,text(value,"question_id"),mode,source);
        var evidence=pin(a,run,step,value.required("evidence_bundle"),"EVIDENCE_BUNDLE",null);
        var export=pin(a,run,step,value.required("session_export"),"SESSION_EXPORT",EXPORT_REF);
        verifyProvenance(a,input,result);verifyProvenance(a,input,evidence);verifyProvenance(a,input,export);verifyExport(input,export);
        if(evidenceContracts==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var publication=evidenceContracts.require(input.tenantId(),input.actorId(),input.projectId(),input.manifest());
        same(publication.schemaRef(),text(evidence.receipt(),"schema_ref"));same(publication.schemaSha256(),text(evidence.receipt(),"payload_schema_sha256"));
        var bundle=ArtifactEvidenceBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.EVIDENCE_BUNDLE,evidence.payload(),publication.schemaRef(),publication.schemaSha256()).read();
        same(bundle.readReceiptIds(),ids(value.required("source_read_receipt_ids")));
        if(bundle.questionCoverage().size()!=1||!input.questionId().equals(bundle.questionCoverage().get(0).questionId()))bad();
        var allRefs=new LinkedHashSet<>(bundle.selectedRefs());allRefs.addAll(bundle.conflictRefs());
        for(var link:bundle.questionCoverage().get(0).evidenceLinks())if(!allRefs.contains(link.evidenceRef()))bad();
        var terminal=invocations.historicalTerminal(input,ids(value.required("invocation_ids")));
        var projection=TaskResearchCompletionChecks.candidateProjection(evidence.payload());
        same(projection,TaskResearchExecutionService.candidateProjection(terminal.output(),publication));
        String candidate=hash(projection);var invocationPins=terminal.canonicalPins();
        var receipts=sources.requireReceipts(a,input,bundle.readReceiptIds(),List.copyOf(allRefs));var sourcePins=receipts.canonicalPins();
        var retrieved=retrievals.requireOriginal(input,bundle.retrievalIds(),"artifacts-v2".equals(publication.name()));var retrievalPins=retrieved.canonicalPins();
        var checks=verifyQuestionChecks(input,evidence,bundle,sourcePins,invocationPins);
        same(checks.outcome(),claimed);
        var expected=json.object().put("candidate_sha256",candidate);expected.set("source_read_receipts",sourcePins);expected.set("invocations",invocationPins);
        expected.set("retrievals",retrievalPins);expected.set("completion_check_ids",json.tree(bundle.questionCoverage().get(0).completionCheckIds()));
        var producing=result.receipt().required("provenance").required("producing_receipts");
        var waitingValue=producing.required("waiting_item_id");String waitingId=waitingValue.isNull()?null:text(producing,"waiting_item_id");
        JsonNode waitingCause=json.tree(null);
        if(checks.reasons().isEmpty()){if(waitingId!=null)bad();expected.putNull("waiting_item_id");}
        else {
            if(mode!=Mode.PRODUCED||waitingId==null)throw new TenantAccessException(ErrorCode.EVIDENCE_INSUFFICIENT);
            WireValues.uuid(waitingId);expected.put("waiting_item_id",waitingId);
            waitingCause=waiting.requireProduced(input,waitingId,candidate,sourcePins,invocationPins,bundle.questionCoverage().get(0).completionCheckIds());
            verifyWaitingCause(input,checks,waitingCause,result.receipt().required("provenance"));
        }
        same(expected,evidence.receipt().required("provenance").required("producing_receipts"));
        expected.set("evidence_bundle",value.required("evidence_bundle"));expected.set("session_export",value.required("session_export"));same(expected,producing);
        same(json.object(),export.receipt().required("provenance").required("producing_receipts"));
        receipts.requireUnchanged();retrieved.requireUnchanged();terminal.requireUnchanged();checks.fixed().requireUnchanged();result.original().requireUnchanged();evidence.original().requireUnchanged();export.original().requireUnchanged();input.requireUnchanged();
        String fingerprint=fingerprint(Map.of("input",input.fingerprint,"result",result.receipt(),"evidence",evidence.receipt(),"export",export.receipt(),
                "reads",sourcePins,"invocations",invocationPins,"retrievals",retrievalPins,"checks",checks.receiptPins(),"waiting",waitingCause));
        return new Checked(input,result,evidence,export,sourcePins,invocationPins,retrievalPins,checks.receiptPins(),waitingCause,claimed,waitingId,fingerprint);
    }

    private Input readInput(EnterpriseAccess.Admission a,String run,String step,String session,
            ProfessionalRuntimeObservations.Binding binding,String question,Mode mode,TaskRunResumeOriginalAuthority.Source source)throws Exception {
        active(a);var attempt=inputLedger.stored(a,run,step);
        var registration=inputRegistrations.readStored(a,attempt).orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
        var manifest=registration.input();new TaskNodeInputSchema().read(ContentReadAuthorization.canonicalBytes(manifest));var receipt=registration.receipt();
        same(hash(manifest),registration.object().sha256());same(binding.input_sha256(),registration.object().sha256());same(hash(receipt),registration.receiptSha256());
        var row=one(db.queryForList("SELECT s.*,t.status AS step_status,t.input_sha256 AS step_input_sha256,t.lease_generation AS step_generation,t.lease_owner,t.lease_expires_at,t.revision AS step_revision,t.output_ref AS step_output_ref FROM professional_runtime_session s JOIN run_step t ON t.tenant_id=s.tenant_id AND t.project_id=s.project_id AND t.run_id=s.run_id AND t.step_id=s.step_id WHERE s.tenant_id=? AND s.run_id=? AND s.step_id=? AND s.session_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),run,step,session));
        var start=parse(text(row,"canonical_start"));same(hash(start),text(row,"start_sha256"));same("CLOSED",text(row,"admission_state"));
        same(mode==Mode.PRODUCED?"RUNNING":"SUCCEEDED",text(row,"step_status"));same(a.actorId(),text(row,"created_by"));
        same(binding.input_sha256(),text(row,"input_sha256"));same(binding.input_sha256(),text(row,"step_input_sha256"));
        same(binding.step_envelope_sha256(),text(row,"envelope_sha256"));same(binding.action_id(),text(row,"action_id"));
        same(binding.lease_generation(),number(row,"lease_generation"));same(binding.lease_generation(),number(row,"step_generation"));
        same("TASK",text(start,"purpose"));same("N02",text(start,"node_kind"));same(session,text(start,"session_id"));same(json.tree(binding),start.required("binding"));
        same(deployment.sha256(),text(start,"deployment_sha256"));same(deployment.workerService(),text(row,"service_uri"));
        for(var e:Map.of("tenant_id",a.context().tenant_id(),"project_id",text(row,"project_id"),"run_id",run,"step_id",step,"question_id",question).entrySet())same(e.getValue(),text(manifest,e.getKey()));
        var runRow=one(db.queryForList("SELECT * FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),text(row,"project_id"),run));
        same("TASK",text(runRow,"execution_kind"));same(a.actorId(),text(runRow,"created_by"));same(text(manifest,"task_id"),text(runRow,"task_id"));same(hash(parse(text(runRow,"replay_manifest"))),text(manifest,"replay_sha256"));
        if(source!=null){if(mode!=Mode.COMMITTED)deny();source.requireOriginal(a,run,step);}else if(a.controlWorker())a.controlProof().requireBootstrap(text(manifest,"task_id"),run);
        if(mode==Mode.PRODUCED){
            RunContinuationService.requireSuccessor(db,a,run);
            if(!a.controlWorker()||a.formalWorker())deny();same("RUNNING",text(runRow,"status"));
            if(runRow.get("started_at")==null||runRow.get("ended_at")!=null||runRow.get("dispatch_closed_at")!=null||runRow.get("superseded_by_run_id")!=null)deny();
            same(text(row,"service_uri"),text(row,"lease_owner"));var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
            if(!now.isBefore(time(row,"lease_expires_at")))throw new TenantAccessException(ErrorCode.ACTION_STALE);
        }
        var action=one(db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),text(row,"project_id"),run,step,binding.action_id()));
        for(var e:Map.of("tenant_id",a.context().tenant_id(),"project_id",text(row,"project_id"),"run_id",run,"step_id",step,"action_id",binding.action_id(),"kind","HOST_CONTROL","state","BOUND","input_sha256",binding.input_sha256(),"tool_version",text(deployment.value(),"host_tool_version")).entrySet())same(e.getValue(),text(action,e.getKey()));
        same(binding.lease_generation(),number(action,"lease_generation"));if(action.get("invocation_id")!=null)bad();
        same(hash(Map.of("operation","N02_HOST_CONTROL","run_id",run,"step_id",step,"input_artifact_id",registration.artifactId(),"input_sha256",binding.input_sha256(),"input_receipt_sha256",registration.receiptSha256())),text(action,"arguments_sha256"));
        var facts=new LinkedHashMap<String,Object>(row);facts.put("artifact_id",registration.artifactId());facts.put("receipt_sha256",registration.receiptSha256());
        String fingerprint=fingerprint(Map.of("session",AgentPublicationWitness.tree(row),"run",AgentPublicationWitness.tree(runRow),"action",AgentPublicationWitness.tree(action),"input",manifest,"receipt",receipt));
        return new Input(a,run,step,session,binding,question,facts,manifest,receipt,start,fingerprint,mode,source);
    }

    /** Private original-input capability used only for factual/current-rights verification. */
    public final class Input {
        private final EnterpriseAccess.Admission a;private final String run,step,session,question,fingerprint;private final ProfessionalRuntimeObservations.Binding binding;
        private final Map<String,Object> row;private final JsonNode manifest,receipt,start;private final Lifetime lifetime;private final Mode mode;private final TaskRunResumeOriginalAuthority.Source source;
        private Input(EnterpriseAccess.Admission a,String run,String step,String session,ProfessionalRuntimeObservations.Binding binding,String question,
                Map<String,Object> row,JsonNode manifest,JsonNode receipt,JsonNode start,String fingerprint,Mode mode,TaskRunResumeOriginalAuthority.Source source){this.source=source;this.a=a;this.run=run;this.step=step;this.session=session;this.binding=binding;this.question=question;this.row=new LinkedHashMap<>(row);this.manifest=manifest.deepCopy();this.receipt=receipt.deepCopy();this.start=start.deepCopy();this.fingerprint=fingerprint;this.mode=mode;lifetime=new Lifetime(a);}
        public JsonNode manifest(){lifetime.require();return manifest.deepCopy();}public String inputId(){lifetime.require();return text(row,"artifact_id");}
        public String inputSha256(){lifetime.require();return binding.input_sha256();}public String receiptSha256(){lifetime.require();return text(row,"receipt_sha256");}
        public String tenantId(){lifetime.require();return a.context().tenant_id();}public String projectId(){lifetime.require();return text(row,"project_id");}
        public String runId(){lifetime.require();return run;}public String stepId(){lifetime.require();return step;}public String questionId(){lifetime.require();return question;}
        public EnterpriseAccess.Admission admission(){lifetime.require();return a;}public String actionId(){lifetime.require();return binding.action_id();}
        public String actorId(){lifetime.require();return a.actorId();}public String sessionId(){lifetime.require();return session;}
        public long generation(){lifetime.require();return binding.lease_generation();}public String workerService(){lifetime.require();return text(row,"service_uri");}
        public void requireUnchanged()throws Exception {lifetime.require();if(!fingerprint.equals(readInput(a,run,step,session,binding,question,mode,source).fingerprint))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
    }

    private record ObjectRecord(String artifact,String kind,String sha256,String version,String receiptSha256,JsonNode payload,JsonNode receipt,Map<String,Object> row,TaskProfessionalObjectRegistration.OriginalProduced original){}
    private ObjectRecord pin(EnterpriseAccess.Admission a,String run,String step,JsonNode pin,String kind,String ref)throws Exception {
        closed(pin,Set.of("artifact_id","sha256","object_version","receipt_sha256"));var result=object(a,run,step,text(pin,"artifact_id"),kind,ref);
        same(result.sha256(),text(pin,"sha256"));same(result.version(),text(pin,"object_version"));same(result.receiptSha256(),text(pin,"receipt_sha256"));return result;
    }
    private ObjectRecord object(EnterpriseAccess.Admission a,String run,String step,String artifact,String kind,String ref)throws Exception {
        WireValues.uuid(artifact);var original=originalObjects.readOriginalProduced(a,artifact,kind,supervisor,deployment);original.requireUnchanged();
        var receipt=original.receipt();var reference=original.reference();byte[] bytes=original.bytes();var payload=parse(new String(bytes,StandardCharsets.UTF_8));
        if(!Arrays.equals(bytes,ContentReadAuthorization.canonicalBytes(payload)))bad();same(hash(payload),reference.sha256());same(hash(receipt),original.receiptSha256());
        for(var pin:Map.of("tenant_id",a.context().tenant_id(),"run_id",run,"step_id",step,"artifact_id",artifact,"kind",kind,"created_by",a.actorId(),"schema_version","2.0.0").entrySet())same(pin.getValue(),text(receipt,pin.getKey()));
        same(artifact,original.artifactId());same(kind,original.kind());same(json.tree(reference),receipt.required("object_ref"));if(ref!=null)same(ref,text(receipt,"schema_ref"));
        return new ObjectRecord(artifact,kind,reference.sha256(),reference.objectVersion(),original.receiptSha256(),payload,receipt,Map.of("write_id",text(receipt,"write_id"),"schema_ref",text(receipt,"schema_ref")),original);
    }
    private void verifyProvenance(EnterpriseAccess.Admission a,Input input,ObjectRecord object)throws Exception {
        var row=one(db.queryForList("SELECT * FROM runtime_professional_object_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),input.runId(),text(object.row(),"write_id")));
        var proof=parse(text(row,"canonical_provenance"));same(hash(proof),text(row,"provenance_sha256"));same(json.canonical(proof),text(row,"canonical_provenance"));same(proof,object.receipt().required("provenance"));
        closed(proof,Set.of("schema_version","phase","kind","tenant_id","project_id","run_id","step_id","action_id","lease_generation","worker_service_uri","session_id","input_artifact_id","input_sha256","input_receipt_sha256","payload_sha256","supervision_receipt_id","supervision_sha256","deployment_sha256","producing_receipts"));
        for(var pin:Map.ofEntries(Map.entry("schema_version","1.0.0"),Map.entry("phase","POST_CLOSE"),Map.entry("kind",object.kind()),Map.entry("tenant_id",input.tenantId()),Map.entry("project_id",input.projectId()),Map.entry("run_id",input.runId()),Map.entry("step_id",input.stepId()),Map.entry("action_id",input.actionId()),Map.entry("worker_service_uri",input.workerService()),Map.entry("session_id",input.sessionId()),Map.entry("input_artifact_id",input.inputId()),Map.entry("input_sha256",input.inputSha256()),Map.entry("input_receipt_sha256",input.receiptSha256()),Map.entry("payload_sha256",object.sha256()),Map.entry("deployment_sha256",deployment.sha256())).entrySet())same(pin.getValue(),text(proof,pin.getKey()));
        if(!proof.required("lease_generation").isIntegralNumber()||!proof.required("lease_generation").bigIntegerValue().equals(java.math.BigInteger.valueOf(input.generation())))bad();
        for(String field:List.of("tenant_id","project_id","run_id","step_id","session_id","input_artifact_id","input_receipt_sha256","supervision_receipt_id","supervision_sha256"))same(text(proof,field),text(row,field));
        same(input.generation(),number(row,"lease_generation"));same(input.actorId(),text(row,"created_by"));
        for(String field:List.of("tenant_id","project_id","run_id","step_id","action_id","worker_service_uri","session_id","input_artifact_id","input_sha256","input_receipt_sha256"))same(text(proof,field),text(object.receipt(),field));
        same(Long.toString(input.generation()),text(object.receipt(),"lease_generation"));
        var closure=supervisor.requireClosedOriginal(a,json.tree(input.binding),input.sessionId(),text(row,"supervision_receipt_id"),text(proof,"supervision_sha256"),deployment);closure.requireSuccessfulExit();closure.requireUnchanged();
    }
    private void verifyExport(Input input,ObjectRecord original)throws Exception {
        var export=original.payload();closed(export,Set.of("schema_version","kind","binding","session_id","runtime_manifest_sha256","files","observation","supervision_sha256"));same("SESSION_EXPORT",text(export,"kind"));same("1.0.0",text(export,"schema_version"));same(input.sessionId(),text(export,"session_id"));same(json.tree(input.binding),export.required("binding"));same(input.start.required("runtime_manifest_sha256"),export.required("runtime_manifest_sha256"));
        same(original.receipt().required("provenance").required("supervision_sha256"),export.required("supervision_sha256"));
        same(input.sessionId(),text(export.required("observation"),"session_id"));same(json.tree(input.binding),export.required("observation").required("binding"));
        var paths=new HashSet<String>();var files=export.required("files");if(!files.isArray()||files.isEmpty()||files.size()>10000)bad();
        for(var file:files){closed(file,Set.of("path","sha256","size_bytes","content_base64"));String path=text(file,"path");if(path.startsWith("/")||path.contains("..")||path.contains("\\")||!paths.add(path))bad();byte[] content;try{content=Base64.getDecoder().decode(text(file,"content_base64"));}catch(IllegalArgumentException e){bad();return;}if(content.length>16*1024*1024||!Integer.toString(content.length).equals(text(file,"size_bytes"))||!WireValues.sha256(content).equals(text(file,"sha256")))bad();}
        if(!paths.contains("runtime-inventory.json"))bad();
        var closure=one(db.queryForList("SELECT canonical_evidence,evidence_sha256 FROM professional_runtime_supervision WHERE tenant_id=? AND session_id=? AND phase='CLOSED' AND receipt_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.sessionId(),text(original.receipt().required("provenance"),"supervision_receipt_id")));
        var closed=parse(text(closure,"canonical_evidence"));same(hash(closed),text(closure,"evidence_sha256"));same(text(export,"supervision_sha256"),text(closure,"evidence_sha256"));
        var archive=json.object();archive.set("files",files);archive.set("observation",export.required("observation"));byte[] bytes=ContentReadAuthorization.canonicalBytes(archive);
        var pin=closed.required("session_export");same(WireValues.sha256(bytes),text(pin,"sha256"));if(!pin.required("size_bytes").isIntegralNumber()||!pin.required("size_bytes").bigIntegerValue().equals(java.math.BigInteger.valueOf(bytes.length)))bad();
    }
    private JsonNode commitRequest(Checked checked)throws Exception {
        var input=checked.input();var request=json.object().put("schema_version","1.0.0").put("kind","TASK_RESEARCH_COMMIT")
                .put("run_id",input.runId()).put("step_id",input.stepId()).put("session_id",input.sessionId()).put("lease_generation",input.generation())
                .put("input_sha256",input.inputSha256()).put("outcome",checked.outcome());
        if(checked.waitingId()==null)request.putNull("waiting_item_id");else request.put("waiting_item_id",checked.waitingId());
        request.set("result",artifactPin(checked.result()));request.set("evidence_bundle",artifactPin(checked.evidence()));request.set("session_export",artifactPin(checked.export()));
        request.set("source_read_receipts",checked.sourcePins());request.set("invocations",checked.invocationPins());request.set("retrievals",checked.retrievalPins());request.set("completion_checks",checked.checkPins());
        TaskResearchCompletionSchema.require("CommitRequest",request);return request;
    }
    private JsonNode artifactPin(ObjectRecord original){return json.object().put("artifact_id",original.artifact()).put("sha256",original.sha256()).put("object_version",original.version()).put("receipt_sha256",original.receiptSha256());}
    private JsonNode requireCommit(Checked checked)throws Exception {
        var input=checked.input();var result=checked.result();
        var row=one(db.queryForList("SELECT * FROM run_runtime_receipt WHERE tenant_id=? AND project_id=? AND run_id=? AND command_kind='COMMIT' AND canonical_request->>'$.step_id'=? AND canonical_request->>'$.session_id'=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),input.runId(),input.stepId(),input.sessionId()));
        var request=parse(text(row,"canonical_request"));var receipt=parse(text(row,"canonical_receipt"));TaskResearchCompletionSchema.require("CommitRequest",request);TaskResearchCompletionSchema.require("CommitReceipt",receipt);
        same(commitRequest(checked),request);same(hash(request),text(row,"request_sha256"));same(hash(receipt),text(row,"receipt_sha256"));same(json.canonical(request),text(row,"canonical_request"));same(json.canonical(receipt),text(row,"canonical_receipt"));
        for(var pin:Map.of("tenant_id",input.tenantId(),"project_id",input.projectId(),"run_id",input.runId(),"command_kind","COMMIT","created_by",input.actorId(),"schema_version","1.0.0","schema_manifest_sha256",TaskResearchCompletionSchema.MANIFEST).entrySet())same(pin.getValue(),row.get(pin.getKey()));
        String receiptId=text(row,"receipt_id"),command=text(row,"command_id");WireValues.uuid(receiptId);WireValues.uuid(command);same(derived(command,"commit"),receiptId);
        var expected=json.object().put("stepId",input.stepId()).put("inputSha256",input.inputSha256()).put("leaseGeneration",input.generation()).put("status","SUCCEEDED").put("receiptId",receiptId);expected.putNull("waitingItemId");
        var outputs=expected.putArray("outputs");for(var object:List.of(checked.result(),checked.evidence(),checked.export()))outputs.addObject().put("artifactId",object.artifact()).put("sha256",object.sha256()).put("schemaRef",text(object.row(),"schema_ref"));same(expected,receipt);same(outputs,parse(text(input.row,"step_output_ref")));
        var dispatch=one(db.queryForList("SELECT command_id,canonical_dispatch,dispatch_sha256 FROM step_runtime_attempt WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),input.runId(),input.stepId(),input.generation()));
        same(command,text(dispatch,"command_id"));var dispatchValue=parse(text(dispatch,"canonical_dispatch"));same(hash(dispatchValue),text(dispatch,"dispatch_sha256"));same(command,text(dispatchValue,"command_id"));same(input.binding.step_envelope_sha256(),text(dispatchValue,"envelope_sha256"));if(!dispatchValue.required("lease_generation").isIntegralNumber()||!dispatchValue.required("lease_generation").bigIntegerValue().equals(java.math.BigInteger.valueOf(input.generation())))bad();same(input.actionId(),text(dispatchValue,"action_id"));same(input.inputSha256(),text(dispatchValue,"input_sha256"));
        String classification=one(db.query("SELECT classification FROM task_run WHERE tenant_id=? AND run_id=?",(r,i)->r.getString(1),input.tenantId(),input.runId()));same(classification,row.get("classification"));
        var witness=json.object();witness.set("request",request);witness.set("receipt",receipt);
        String auditId=derived(receiptId,"audit"),eventId=derived(receiptId,"event");
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",input.tenantId(),auditId));
        for(var pin:Map.ofEntries(Map.entry("tenant_id",input.tenantId()),Map.entry("project_id",input.projectId()),Map.entry("audit_id",auditId),Map.entry("subject_id",input.actorId()),Map.entry("created_by",input.actorId()),Map.entry("action","task.research.committed"),Map.entry("result","SUCCESS"),Map.entry("request_id",command),Map.entry("trace_id",input.runId()),Map.entry("schema_version","1.0.0"),Map.entry("schema_manifest_sha256",TaskResearchCompletionSchema.MANIFEST),Map.entry("classification",classification)).entrySet())same(pin.getValue(),audit.get(pin.getKey()));
        if(audit.get("reason_code")!=null)bad();same(time(row,"created_at"),time(audit,"created_at"));same(witness,parse(text(audit,"resource_ref")));
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",input.tenantId(),eventId));
        same(input.tenantId(),event.get("tenant_id"));same(eventId,event.get("event_id"));same(receiptId,event.get("aggregate_id"));same("task.research.committed",event.get("event_type"));same(1L,number(event,"aggregate_revision"));same(time(row,"created_at"),time(event,"occurred_at"));same(witness,parse(text(event,"payload")));
        return json.object().put("receipt_id",receiptId).put("receipt_sha256",text(row,"receipt_sha256")).set("completion",receipt);
    }
    private record CheckState(TaskResearchRuleInputs.Verified fixed,List<String> reasons,JsonNode receiptPins,String outcome) { }
    private CheckState verifyQuestionChecks(Input input,ObjectRecord evidence,ArtifactProtocol.EvidenceBundle bundle,JsonNode sourcePins,JsonNode invocationPins)throws Exception {
        var fixed=ruleInputs.requireOriginal(input.a,input);var checks=fixed.checks();
        var ids=checks.stream().map(TaskResearchRuleInputs.Check::ruleId).toList();if(new HashSet<>(ids).size()!=ids.size())bad();
        var projection=TaskResearchCompletionChecks.candidateProjection(evidence.payload());
        var reasons=new TreeSet<>(TaskResearchCompletionChecks.blockingCoverage(projection,fixed.requirementIds()));if(ids.isEmpty())reasons.add("APPROVED_COMPLETION_RULE_REQUIRED");
        String candidateHash=hash(projection);var seen=new ArrayList<String>();var pins=json.object().arrayNode();
        for(String checkId:bundle.questionCoverage().get(0).completionCheckIds()) {
            var row=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",input.tenantId(),checkId));
            var proof=parse(text(row,"resource_ref"));TaskResearchCompletionSchema.require(proof);
            for(var pin:Map.ofEntries(Map.entry("tenant_id",input.tenantId()),Map.entry("project_id",input.projectId()),Map.entry("audit_id",checkId),Map.entry("subject_id",input.actorId()),Map.entry("created_by",input.actorId()),Map.entry("action","task.research.rule.checked"),Map.entry("result","SUCCESS"),Map.entry("trace_id",input.runId()),Map.entry("schema_version","1.0.0"),Map.entry("schema_manifest_sha256",TaskResearchCompletionSchema.MANIFEST)).entrySet())same(pin.getValue(),row.get(pin.getKey()));
            if(row.get("reason_code")!=null)bad();same(json.canonical(proof),text(row,"resource_ref"));
            for(var pin:Map.ofEntries(Map.entry("check_id",checkId),Map.entry("tenant_id",input.tenantId()),Map.entry("project_id",input.projectId()),Map.entry("run_id",input.runId()),Map.entry("step_id",input.stepId()),Map.entry("question_id",input.questionId()),Map.entry("session_id",input.sessionId()),Map.entry("input_artifact_id",input.inputId()),Map.entry("input_sha256",input.inputSha256()),Map.entry("input_receipt_sha256",input.receiptSha256()),Map.entry("candidate_sha256",candidateHash),Map.entry("profile_revision",fixed.profileId()),Map.entry("profile_sha256",fixed.profileSha256())).entrySet())same(pin.getValue(),text(proof,pin.getKey()));
            same(input.actionId(),text(proof,"action_id"));same(input.actionId(),row.get("request_id"));
            same(UUID.nameUUIDFromBytes(("TASK_RESEARCH_RULE_CHECK|"+input.tenantId()+"|"+input.sessionId()+"|"+candidateHash+"|"+text(proof,"rule_version")).getBytes(StandardCharsets.UTF_8)).toString(),checkId);same(Long.toString(input.generation()),text(proof,"lease_generation"));
            String ruleId=text(proof,"rule_version");if(seen.contains(ruleId))bad();seen.add(ruleId);var rule=checks.stream().filter(c->c.ruleId().equals(ruleId)).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
            same(rule.ruleSha256(),text(proof,"rule_sha256"));same(rule.rule().engine_revision(),text(proof,"engine_revision"));
            same(rule.authorizations(),proof.required("rule_authorizations"));same(json.tree(fixed.attributes()),proof.required("attributes"));same(sourcePins,proof.required("source_read_receipts"));same(invocationPins,proof.required("invocations"));
            var outcome=TaskResearchCompletionChecks.evaluate(ruleEngine,ruleId,ids,rule.rule(),fixed.attributes());
            same(outcome.outcome().name(),text(proof,"outcome"));same(json.tree(outcome.missingInputKeys()),proof.required("unknown_input_keys"));
            if(outcome.outcome()==TaskResearchCompletionChecks.Outcome.UNKNOWN)reasons.add("FROZEN_PROFILE_INPUT_UNKNOWN");
            if(outcome.outcome()==TaskResearchCompletionChecks.Outcome.NOT_SATISFIED)reasons.add("DECLARED_COMPLETION_CHECK_NOT_SATISFIED");
            String eventId=derived(checkId,"outbox");var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",input.tenantId(),eventId));
            same(input.tenantId(),event.get("tenant_id"));same(eventId,event.get("event_id"));same(checkId,event.get("aggregate_id"));same("task.research.rule.checked",event.get("event_type"));same(text(row,"resource_ref"),event.get("payload"));same(time(row,"created_at"),time(event,"occurred_at"));same(1L,number(event,"aggregate_revision"));
            same(one(db.query("SELECT classification FROM task_run WHERE tenant_id=? AND run_id=?",(r,i)->r.getString(1),input.tenantId(),input.runId())),row.get("classification"));
            pins.addObject().put("check_id",checkId).put("receipt_sha256",hash(proof)).put("rule_version",ruleId).put("outcome",outcome.outcome().name());
        }
        same(ids,seen);
        String status=reasons.contains("UNRESOLVED_EVIDENCE_CONFLICT")?"CONFLICT":reasons.isEmpty()?"VERIFIED":"INSUFFICIENT";
        same(status,bundle.questionCoverage().get(0).status().name());
        if(!reasons.isEmpty()&&fixed.ownerId()==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        fixed.requireUnchanged();input.requireUnchanged();return new CheckState(fixed,List.copyOf(reasons),pins,reasons.isEmpty()?"SUCCEEDED":"WAITING_INPUT");
    }
    private void verifyWaitingCause(Input input,CheckState checks,JsonNode cause,JsonNode provenance)throws Exception {
        var fixed=checks.fixed();same(json.tree(checks.reasons()),cause.required("reason_codes"));same(json.tree(fixed.requirementIds()),cause.required("requirement_ids"));
        same(fixed.ownerId(),text(cause,"assignee_id"));same(fixed.profileId(),text(cause,"profile_revision"));same(fixed.profileSha256(),text(cause,"profile_sha256"));
        same(checks.receiptPins(),cause.required("completion_checks"));same(hash(fixed.witness()),text(cause,"rule_authority_sha256"));
        var rules=json.object().arrayNode();for(var rule:fixed.checks())rules.addObject().put("rule_version",rule.ruleId()).put("rule_sha256",rule.ruleSha256());same(rules,cause.required("rule_versions"));
        for(String field:List.of("supervision_receipt_id","supervision_sha256","deployment_sha256"))same(provenance.required(field),cause.required(field));
    }

    public final class ProducedCompletion {
        private final EnterpriseAccess.Admission admission;private final String run,step,resultId;private final Checked checked;private final Lifetime lifetime;
        private ProducedCompletion(EnterpriseAccess.Admission a,String run,String step,String resultId,Checked checked){this.admission=a;this.run=run;this.step=step;this.resultId=resultId;this.checked=checked;this.lifetime=new Lifetime(a);}
        public String tenantId(){lifetime.require();return checked.input().tenantId();}public String projectId(){lifetime.require();return checked.input().projectId();}public String actorId(){lifetime.require();return checked.input().actorId();}
        public List<com.ruoyi.medical.workflow.WorkflowProtocol.Output> outputs(){lifetime.require();return List.of(checked.result(),checked.evidence(),checked.export()).stream().map(o->new com.ruoyi.medical.workflow.WorkflowProtocol.Output(o.artifact(),o.sha256(),text(o.row(),"schema_ref"))).toList();}
        public String runId(){lifetime.require();return run;}public String stepId(){lifetime.require();return step;}
        public String sessionId(){lifetime.require();return checked.input().sessionId();}public String actionId(){lifetime.require();return checked.input().actionId();}
        public long leaseGeneration(){lifetime.require();return checked.input().generation();}public long stepRevision(){lifetime.require();return number(checked.input().row,"step_revision");}
        public String inputSha256(){lifetime.require();return checked.input().inputSha256();}public String outcome(){lifetime.require();return checked.outcome();}public String waitingItemId(){lifetime.require();return checked.waitingId();}
        public String resultArtifactId(){lifetime.require();return checked.result().artifact();}public String resultSha256(){lifetime.require();return checked.result().sha256();}public String resultObjectVersion(){lifetime.require();return checked.result().version();}public String resultReceiptSha256(){lifetime.require();return checked.result().receiptSha256();}
        public String evidenceArtifactId(){lifetime.require();return checked.evidence().artifact();}public String evidenceSha256(){lifetime.require();return checked.evidence().sha256();}public String evidenceObjectVersion(){lifetime.require();return checked.evidence().version();}public String evidenceReceiptSha256(){lifetime.require();return checked.evidence().receiptSha256();}
        public String exportArtifactId(){lifetime.require();return checked.export().artifact();}public String exportSha256(){lifetime.require();return checked.export().sha256();}public String exportObjectVersion(){lifetime.require();return checked.export().version();}public String exportReceiptSha256(){lifetime.require();return checked.export().receiptSha256();}
        public JsonNode sourceReceiptPins(){lifetime.require();return checked.sourcePins().deepCopy();}public JsonNode invocationPins(){lifetime.require();return checked.invocationPins().deepCopy();}public JsonNode retrievalPins(){lifetime.require();return checked.retrievalPins().deepCopy();}public JsonNode checkReceiptPins(){lifetime.require();return checked.checkPins().deepCopy();}public JsonNode waitingCause(){lifetime.require();return checked.waitingCause().deepCopy();}
        public JsonNode canonicalCommitRequest()throws Exception{lifetime.require();return commitRequest(checked);}
        public void requireUnchanged()throws Exception{lifetime.require();if(!checked.fingerprint().equals(verify(admission,run,step,resultId,Mode.PRODUCED).fingerprint()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
    }
    public final class Verified {
        private final EnterpriseAccess.Admission a;private final String run,step,commitHash,fingerprint;private final ObjectRecord result,evidence;private final Lifetime lifetime;private final TaskRunResumeOriginalAuthority.Source source;
        private Verified(EnterpriseAccess.Admission a,String run,String step,ObjectRecord result,ObjectRecord evidence,String commitHash,String fingerprint){this(a,run,step,result,evidence,commitHash,fingerprint,null);}
        private Verified(EnterpriseAccess.Admission a,String run,String step,ObjectRecord result,ObjectRecord evidence,String commitHash,String fingerprint,TaskRunResumeOriginalAuthority.Source source){this.source=source;this.a=a;this.run=run;this.step=step;this.result=result;this.evidence=evidence;this.commitHash=commitHash;this.fingerprint=fingerprint;lifetime=new Lifetime(a);}
        public String resultArtifactId(){lifetime.require();return result.artifact();}public String resultSha256(){lifetime.require();return result.sha256();}
        public String evidenceArtifactId(){lifetime.require();return evidence.artifact();}public String evidenceSha256(){lifetime.require();return evidence.sha256();}
        public String resultObjectVersion(){lifetime.require();return result.version();}
        public String evidenceObjectVersion(){lifetime.require();return evidence.version();}
        public String resultReceiptSha256(){lifetime.require();return result.receiptSha256();}
        public String evidenceReceiptSha256(){lifetime.require();return evidence.receiptSha256();}
        /** Defensive view of the independently verified original evidence output. */
        public JsonNode evidencePayload(){lifetime.require();return evidence.payload().deepCopy();}
        public String evidenceSchemaRef(){lifetime.require();return text(evidence.receipt(),"schema_ref");}
        public String evidenceSchemaSha256(){lifetime.require();return text(evidence.receipt(),"payload_schema_sha256");}
        public String completionReceiptSha256(){lifetime.require();return commitHash;}
        public String receiptSha256(){return completionReceiptSha256();}
        public void requireUnchanged()throws Exception {lifetime.require();if(!fingerprint.equals((source==null?TaskResearchCompletionAuthority.this.require(a,run,step):TaskResearchCompletionAuthority.this.requireOriginal(source)).fingerprint))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
    }
    private static final class Lifetime {
        private final EnterpriseAccess.Admission a;private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();private final Map<Object,Object> tx=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private Lifetime(EnterpriseAccess.Admission a){this.a=a;if(tx.isEmpty())deny();}
        private void require(){active(a);if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent())deny();var current=TransactionSynchronizationManager.getResourceMap();if(current.size()!=tx.size()||tx.entrySet().stream().anyMatch(e->current.get(e.getKey())!=e.getValue()))deny();}
    }
    private static void active(EnterpriseAccess.Admission a){if(!TransactionSynchronizationManager.isActualTransactionActive()||!TenantScope.requireCurrent().tenantId().equals(a.context().tenant_id())||!TenantScope.requireCurrent().subjectId().equals(a.actorId()))deny();if(a.formalWorker())a.formalProof().requireActive();else if(a.controlWorker())a.controlProof().requireActive();else deny();}
    private JsonNode parse(String raw)throws Exception {var value=reader.readTree(raw);if(value==null)bad();return value;}
    private String fingerprint(Object value)throws Exception{return hash(exactNumbers(json.tree(value)));}
    private static JsonNode exactNumbers(JsonNode value){
        if(value.isNumber())return com.fasterxml.jackson.databind.node.TextNode.valueOf(value.isIntegralNumber()?value.bigIntegerValue().toString():value.decimalValue().toPlainString());
        if(value.isObject()){var copy=((com.fasterxml.jackson.databind.node.ObjectNode)value).deepCopy();var fields=value.fields();while(fields.hasNext()){var field=fields.next();copy.set(field.getKey(),exactNumbers(field.getValue()));}return copy;}
        if(value.isArray()){var copy=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();for(var child:value)copy.add(exactNumbers(child));return copy;}return value.deepCopy();
    }
    private String hash(Object value)throws Exception {return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}
    private static List<String> ids(JsonNode values){if(!values.isArray()||values.size()>1000)bad();var ids=new ArrayList<String>();for(var v:values){if(!v.isTextual())bad();WireValues.uuid(v.textValue());if(ids.contains(v.textValue()))bad();ids.add(v.textValue());}return List.copyOf(ids);}
    private static void closed(JsonNode n,Set<String> keys){ProfessionalRuntimeObservations.closed(n,keys);}
    private static String text(JsonNode n,String k){if(!n.required(k).isTextual())bad();return n.required(k).textValue();}
    private static String text(Map<String,Object> n,String k){if(!(n.get(k) instanceof String))bad();return (String)n.get(k);}
    private static long number(Map<String,Object> n,String k){try{var v=n.get(k);if(!(v instanceof Number)&&!(v instanceof String))bad();return new java.math.BigDecimal(v.toString()).longValueExact();}catch(NumberFormatException|ArithmeticException e){bad();return 0;}}
    private static java.time.LocalDateTime time(Map<String,Object> row,String key){var value=row.get(key);if(value instanceof java.time.LocalDateTime t)return t;if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime();bad();return null;}
    private static String derived(String id,String purpose){return UUID.nameUUIDFromBytes((id+"|"+purpose).getBytes(StandardCharsets.UTF_8)).toString();}
    private static <T>T one(List<T> rows){if(rows.isEmpty())throw new TenantAccessException(ErrorCode.STEP_NOT_READY);if(rows.size()!=1)bad();return rows.get(0);}
    private static void same(Object a,Object b){if(a instanceof JsonNode x&&b instanceof JsonNode y){if(!FormalTaskNodeAuthority.sameEvidence(x,y))bad();}else if(!Objects.equals(a,b))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
