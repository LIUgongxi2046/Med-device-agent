package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real pre-COMMIT/read-after-COMMIT orchestration with explicit original075/097/076,
 * current-source/rule/closure/publication authority doubles and JDBC journals. No live services or DB. */
class TaskResearchCompletionAuthorityTest extends TaskNodeInputFixture {
    final TaskResearchSourceReader sourceReader=mock(TaskResearchSourceReader.class);
    final TaskResearchSourceReader.VerifiedReceipts reads=mock(TaskResearchSourceReader.VerifiedReceipts.class);
    final TaskProfessionalSupervisor supervisor=mock(TaskProfessionalSupervisor.class);
    final TaskProfessionalSupervisor.OriginalClosure closure=mock(TaskProfessionalSupervisor.OriginalClosure.class);
    final TaskProfessionalRuntimeService.Deployment deployment=mock(TaskProfessionalRuntimeService.Deployment.class);
    final TaskResearchRuleInputs ruleInputs=mock(TaskResearchRuleInputs.class);
    final TaskResearchRuleInputs.Verified fixed=mock(TaskResearchRuleInputs.Verified.class);
    final TaskInvocationResults invocations=mock(TaskInvocationResults.class);
    final TaskInvocationResults.HistoricalTerminal terminal=mock(TaskInvocationResults.HistoricalTerminal.class);
    final TaskNodeInputLedger originalInputs=mock(TaskNodeInputLedger.class);
    final TaskNodeInputLedger.Attempt inputAttempt=mock(TaskNodeInputLedger.Attempt.class);
    final TaskNodeInputRegistration originalInputRegistrations=mock(TaskNodeInputRegistration.class);
    final TaskNodeInputRegistration.Registered inputRegistered=mock(TaskNodeInputRegistration.Registered.class);
    final TaskResearchRetrievalAuthority retrievals=mock(TaskResearchRetrievalAuthority.class);
    final TaskResearchRetrievalAuthority.Verified retrieved=mock(TaskResearchRetrievalAuthority.Verified.class);
    final TaskResearchWaitingService waiting=mock(TaskResearchWaitingService.class);
    final TaskProfessionalObjectRegistration originalObjects=mock(TaskProfessionalObjectRegistration.class);
    final TaskResearchEvidenceContract evidenceContracts=mock(TaskResearchEvidenceContract.class);
    final SchemaCatalogue catalogue=SchemaCatalogue.fromClasspath();
    final SchemaCatalogue.PublishedSchema evidencePublication=catalogue.requirePublished(catalogue.schemaRef("artifacts","EvidenceBundle"),catalogue.schemaDigest("artifacts"));
    final RuleExpression engine=new RuleExpression();
    final TaskProfessionalSchema outputsSchema=new TaskProfessionalSchema();
    final TaskResearchCompletionAuthority service=new TaskResearchCompletionAuthority(db,json,sourceReader,supervisor,deployment,ruleInputs,engine,invocations,originalInputs,originalInputRegistrations,retrievals,waiting,originalObjects);
    final String session=id(80),action=id(81),inputId=id(82),resultId=id(83),evidenceId=id(84),exportId=id(85),invocationId=id(86),readId=id(87),ruleId=id(88),profileId=id(89),owner=id(90),closedId=id(91),waitingId=id(92),commandId=id(93);
    final Map<String,TaskProfessionalObjectRegistration.OriginalProduced> objects=new HashMap<>();
    final Map<String,Object> sessionRow=new LinkedHashMap<>(),runRow=new LinkedHashMap<>(),actionRow=new LinkedHashMap<>();
    final JsonNode sourcePins=json.object().arrayNode().add(json.object().put("read_receipt_id",readId).put("receipt_sha256",hash('a')));
    final JsonNode invocationPins=json.object().arrayNode().add(json.object().put("invocation_id",invocationId).put("accounting_sha256",hash('a')).put("result_registration_sha256",hash('b')).put("capture_registration_sha256",hash('c')));
    final JsonNode retrievalPins=json.object().arrayNode();
    final JsonNode ruleWitness=json.object().put("explicit_current_authority","approved-rule-profile");
    ObjectNode manifest,binding,projection,exportPayload,closureEvidence;
    List<ProjectProtocol.Attribute> attributes=List.of();
    String inputHash,inputReceiptHash,closeHash,checkId;
    final TemplateApplicabilityProtocol.RuleContent rule=new TemplateApplicabilityProtocol.RuleContent("1.0.0",RuleExpression.ENGINE,new TemplateApplicabilityProtocol.Expression("1.0.0",RuleExpression.ENGINE,"#attributes['ready'] == true",List.of("ready"),List.of()),List.of());

    TaskResearchCompletionAuthorityTest()throws Exception {
        // Explicit synthetic role-publication authority; real catalogue/schema validation remains active.
        service.evidenceContracts(evidenceContracts);
        when(evidenceContracts.require(eq(tenant),eq(actor),eq(project),any())).thenReturn(evidencePublication);
        var replay=json.object().put("explicit_test_original","replay");String replayHash=sha(replay);
        manifest=(ObjectNode)schema.tree(input);manifest.put("replay_sha256",replayHash);((ObjectNode)manifest.required("replay_artifact")).put("sha256",replayHash);inputHash=sha(manifest);
        var inputReceipt=json.object().put("artifact_id",inputId);inputReceiptHash=sha(inputReceipt);
        when(originalInputs.stored(admission,run,step)).thenReturn(inputAttempt);when(originalInputRegistrations.readStored(admission,inputAttempt)).thenReturn(Optional.of(inputRegistered));
        when(inputRegistered.input()).thenAnswer(i->manifest.deepCopy());when(inputRegistered.receipt()).thenReturn(inputReceipt);when(inputRegistered.receiptSha256()).thenReturn(inputReceiptHash);when(inputRegistered.artifactId()).thenReturn(inputId);
        when(inputRegistered.object()).thenReturn(new InterfaceProtocol.ObjectRef("original/input","input-version",inputHash,Integer.toString(ContentReadAuthorization.canonicalBytes(manifest).length),"application/json"));
        binding=json.object().put("tenant_id",tenant).put("run_id",run).put("step_id",step).put("action_id",action).put("lease_generation",1).put("input_sha256",inputHash).put("step_envelope_sha256",hash('e'));
        var start=json.object().put("purpose","TASK").put("node_kind","N02").put("session_id",session).put("deployment_sha256",hash('d')).put("runtime_manifest_sha256",hash('f'));start.set("binding",binding);
        sessionRow.putAll(row("tenant_id",tenant,"project_id",project,"run_id",run,"step_id",step,"session_id",session,"action_id",action,"canonical_start",json.canonical(start),"start_sha256",sha(start),"admission_state","CLOSED","created_by",actor,"input_sha256",inputHash,"step_input_sha256",inputHash,"envelope_sha256",hash('e'),"lease_generation",1L,"step_generation",1L,"step_status","RUNNING","service_uri","spiffe://test.invalid/worker","lease_owner","spiffe://test.invalid/worker","lease_expires_at",now.plusMinutes(5),"step_revision",3L));
        runRow.putAll(row("tenant_id",tenant,"project_id",project,"task_id",task,"run_id",run,"execution_kind","TASK","created_by",actor,"status","RUNNING","classification","RESTRICTED","started_at",now,"replay_manifest",json.canonical(replay)));
        actionRow.putAll(row("tenant_id",tenant,"project_id",project,"run_id",run,"step_id",step,"action_id",action,"kind","HOST_CONTROL","state","BOUND","input_sha256",inputHash,"tool_version",id(95),"lease_generation",1L,"arguments_sha256",sha(Map.of("operation","N02_HOST_CONTROL","run_id",run,"step_id",step,"input_artifact_id",inputId,"input_sha256",inputHash,"input_receipt_sha256",inputReceiptHash))));
        when(deployment.sha256()).thenReturn(hash('d'));when(deployment.workerService()).thenReturn("spiffe://test.invalid/worker");when(deployment.value()).thenReturn(json.object().put("host_tool_version",id(95)));
        when(supervisor.requireClosedOriginal(eq(admission),any(),eq(session),eq(closedId),anyString(),eq(deployment))).thenReturn(closure);
        var ref=new DatasetProtocol.EvidenceRef("1.0.0",tenant,project,id(20),id(21),id(22),id(23),DatasetProtocol.LocatorKind.TXT,new DatasetProtocol.TextLocator("TXT","UTF-8","PRESERVE",0,1,1,1),hash('a'),id(24));
        projection=json.object();projection.putArray("retrieval_ids");projection.set("selected_refs",json.tree(List.of(ref)));projection.putArray("conflict_refs");projection.putArray("missing_requirement_ids");projection.putArray("read_receipt_ids").add(readId);
        var question=projection.putArray("question_coverage").addObject().put("question_id",input.questionId());question.set("evidence_links",json.tree(List.of(new ArtifactProtocol.EvidenceLink(ref,ArtifactProtocol.Relation.SUPPORT))));question.putArray("missing_requirement_ids");
        when(invocations.historicalTerminal(any(),eq(List.of(invocationId)))).thenReturn(terminal);when(terminal.canonicalPins()).thenReturn(invocationPins);when(terminal.output()).thenAnswer(i->{var value=json.object();value.putArray("blocks").addObject().put("type","text").put("text",json.canonical(projection));return value;});
        when(sourceReader.requireReceipts(eq(admission),any(TaskResearchCompletionAuthority.Input.class),eq(List.of(readId)),anyList())).thenReturn(reads);when(reads.canonicalPins()).thenReturn(sourcePins);
        when(retrievals.requireOriginal(any(),eq(List.of()),eq(false))).thenReturn(retrieved);when(retrieved.canonicalPins()).thenReturn(retrievalPins);
        when(ruleInputs.requireOriginal(eq(admission),any())).thenReturn(fixed);when(fixed.checks()).thenReturn(List.of(new TaskResearchRuleInputs.Check(ruleId,hash('c'),rule,ruleWitness)));when(fixed.requirementIds()).thenReturn(List.of());when(fixed.attributes()).thenAnswer(i->attributes);when(fixed.profileId()).thenReturn(profileId);when(fixed.profileSha256()).thenReturn(hash('d'));when(fixed.ownerId()).thenReturn(owner);when(fixed.witness()).thenReturn(ruleWitness);
        when(originalObjects.readOriginalProduced(eq(admission),anyString(),anyString(),eq(supervisor),eq(deployment))).thenAnswer(i->{var found=objects.get(i.getArgument(1));if(found==null)throw new TenantAccessException(ErrorCode.SOURCE_MISSING);return found;});
        exportPayload=json.object().put("schema_version","1.0.0").put("kind","SESSION_EXPORT").put("session_id",session).put("runtime_manifest_sha256",hash('f'));exportPayload.set("binding",binding);
        exportPayload.putArray("files").addObject().put("path","runtime-inventory.json").put("sha256",WireValues.sha256("{}".getBytes(StandardCharsets.UTF_8))).put("size_bytes","2").put("content_base64","e30=");exportPayload.set("observation",json.object().put("session_id",session).set("binding",binding));
        var archive=json.object();archive.set("files",exportPayload.required("files"));archive.set("observation",exportPayload.required("observation"));byte[] archiveBytes=ContentReadAuthorization.canonicalBytes(archive);
        closureEvidence=json.object();closureEvidence.putObject("session_export").put("sha256",WireValues.sha256(archiveBytes)).put("size_bytes",archiveBytes.length);closeHash=sha(closureEvidence);exportPayload.put("supervision_sha256",closeHash);
        when(db.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(i->{String sql=i.getArgument(0);if(sql.startsWith("SELECT classification"))return List.of("RESTRICTED");throw new AssertionError(sql);});
        when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(i->selectCompletion(i.getArgument(0),args(i.getArguments())));
        produce(true);
    }
    void produce(Boolean ready)throws Exception {
        attributes=ready==null?List.of():List.of(new ProjectProtocol.Attribute("ready","KNOWN",json.tree(ready),List.of(),null));
        var result=TaskResearchCompletionChecks.evaluate(engine,ruleId,List.of(ruleId),rule,attributes);var reasons=new ArrayList<String>();if(ready==null)reasons.add("FROZEN_PROFILE_INPUT_UNKNOWN");else if(!ready)reasons.add("DECLARED_COMPLETION_CHECK_NOT_SATISFIED");
        checkId=UUID.nameUUIDFromBytes(("TASK_RESEARCH_RULE_CHECK|"+tenant+"|"+session+"|"+sha(projection)+"|"+ruleId).getBytes(StandardCharsets.UTF_8)).toString();
        var check=json.object().put("schema_version","1.0.0").put("kind","TASK_RESEARCH_RULE_CHECK").put("check_id",checkId).put("tenant_id",tenant).put("project_id",project).put("run_id",run).put("step_id",step).put("action_id",action).put("lease_generation","1").put("question_id",input.questionId()).put("session_id",session).put("input_artifact_id",inputId).put("input_sha256",inputHash).put("input_receipt_sha256",inputReceiptHash).put("candidate_sha256",sha(projection)).put("rule_version",ruleId).put("rule_sha256",hash('c')).put("engine_revision",RuleExpression.ENGINE).put("profile_revision",profileId).put("profile_sha256",hash('d')).put("outcome",result.outcome().name());
        check.set("unknown_input_keys",json.tree(result.missingInputKeys()));check.set("attributes",json.tree(attributes));check.set("rule_authorizations",ruleWitness);check.set("source_read_receipts",sourcePins);check.set("invocations",invocationPins);TaskResearchCompletionSchema.require(check);
        rows("audit_event").clear();rows("outbox_event").clear();
        rows("audit_event").add(row("tenant_id",tenant,"project_id",project,"audit_id",checkId,"subject_id",actor,"created_by",actor,"action","task.research.rule.checked","result","SUCCESS","request_id",action,"trace_id",run,"schema_version","1.0.0","schema_manifest_sha256",TaskResearchCompletionSchema.MANIFEST,"classification","RESTRICTED","created_at",now,"resource_ref",json.canonical(check)));
        rows("outbox_event").add(row("tenant_id",tenant,"event_id",derived(checkId,"outbox"),"aggregate_id",checkId,"aggregate_revision",1L,"event_type","task.research.rule.checked","payload",json.canonical(check),"occurred_at",now));
        var checkPins=json.object().arrayNode().add(json.object().put("check_id",checkId).put("receipt_sha256",sha(check)).put("rule_version",ruleId).put("outcome",result.outcome().name()));
        var producing=json.object().put("candidate_sha256",sha(projection));producing.set("source_read_receipts",sourcePins);producing.set("invocations",invocationPins);producing.set("retrievals",retrievalPins);producing.set("completion_check_ids",json.tree(List.of(checkId)));if(reasons.isEmpty())producing.putNull("waiting_item_id");else producing.put("waiting_item_id",waitingId);
        var evidence=projection.deepCopy();((ObjectNode)evidence.required("question_coverage").get(0)).put("status",reasons.isEmpty()?"VERIFIED":"INSUFFICIENT").set("completion_check_ids",json.tree(List.of(checkId)));
        original(exportId,"SESSION_EXPORT",exportPayload,json.object());original(evidenceId,"EVIDENCE_BUNDLE",evidence,producing);
        var value=json.object().put("schema_version","1.0.0").put("execution_kind","TASK").put("node_kind","N02").put("question_id",input.questionId()).put("session_id",session).put("outcome",reasons.isEmpty()?"SUCCEEDED":"WAITING_INPUT");value.set("binding",binding);value.set("evidence_bundle",pin(evidenceId));value.set("session_export",pin(exportId));value.set("invocation_ids",json.tree(List.of(invocationId)));value.set("source_read_receipt_ids",json.tree(List.of(readId)));
        producing.set("evidence_bundle",pin(evidenceId));producing.set("session_export",pin(exportId));original(resultId,"STEP_RESULT",value,producing);
        var cause=json.object().put("assignee_id",owner).put("profile_revision",profileId).put("profile_sha256",hash('d')).put("rule_authority_sha256",sha(ruleWitness)).put("supervision_receipt_id",closedId).put("supervision_sha256",closeHash).put("deployment_sha256",hash('d'));cause.set("reason_codes",json.tree(reasons));cause.putArray("requirement_ids");cause.set("completion_checks",checkPins);cause.putArray("rule_versions").addObject().put("rule_version",ruleId).put("rule_sha256",hash('c'));
        when(waiting.requireProduced(any(),eq(waitingId),eq(sha(projection)),eq(sourcePins),eq(invocationPins),eq(List.of(checkId)))).thenReturn(cause);
    }
    void original(String id,String kind,JsonNode payload,JsonNode producing)throws Exception {
        byte[] bytes=ContentReadAuthorization.canonicalBytes(payload);outputsSchema.read(kind,bytes);String digest=WireValues.sha256(bytes);
        var provenance=json.object().put("schema_version","1.0.0").put("phase","POST_CLOSE").put("kind",kind).put("tenant_id",tenant).put("project_id",project).put("run_id",run).put("step_id",step).put("action_id",action).put("lease_generation",1).put("worker_service_uri","spiffe://test.invalid/worker").put("session_id",session).put("input_artifact_id",inputId).put("input_sha256",inputHash).put("input_receipt_sha256",inputReceiptHash).put("payload_sha256",digest).put("supervision_receipt_id",closedId).put("supervision_sha256",closeHash).put("deployment_sha256",hash('d'));provenance.set("producing_receipts",producing.deepCopy());
        var ref=new InterfaceProtocol.ObjectRef("runtime/"+id,"original-version",digest,Integer.toString(bytes.length),"application/json");
        var receipt=json.object().put("schema_version","2.0.0").put("tenant_id",tenant).put("project_id",project).put("run_id",run).put("step_id",step).put("artifact_id",id).put("write_id",id).put("kind",kind).put("schema_ref",outputsSchema.ref(kind)).put("payload_schema_sha256",outputsSchema.manifest(kind)).put("created_by",actor).put("action_id",action).put("lease_generation","1").put("worker_service_uri","spiffe://test.invalid/worker").put("session_id",session).put("input_artifact_id",inputId).put("input_sha256",inputHash).put("input_receipt_sha256",inputReceiptHash);receipt.set("object_ref",json.tree(ref));receipt.set("provenance",provenance);
        var proof=mock(TaskProfessionalObjectRegistration.OriginalProduced.class);when(proof.bytes()).thenReturn(bytes);when(proof.receipt()).thenReturn(receipt);when(proof.receiptSha256()).thenReturn(sha(receipt));when(proof.reference()).thenReturn(ref);when(proof.artifactId()).thenReturn(id);when(proof.kind()).thenReturn(kind);objects.put(id,proof);
        rows("runtime_professional_object_binding").removeIf(r->id.equals(r.get("write_id")));rows("runtime_professional_object_binding").add(row("write_id",id,"tenant_id",tenant,"project_id",project,"run_id",run,"step_id",step,"session_id",session,"lease_generation",1L,"created_by",actor,"input_artifact_id",inputId,"input_receipt_sha256",inputReceiptHash,"supervision_receipt_id",closedId,"supervision_sha256",closeHash,"canonical_provenance",json.canonical(provenance),"provenance_sha256",sha(provenance)));
    }
    JsonNode pin(String id)throws Exception{var object=objects.get(id);return json.object().put("artifact_id",id).put("sha256",object.reference().sha256()).put("object_version",object.reference().objectVersion()).put("receipt_sha256",object.receiptSha256());}
    List<Map<String,Object>> selectCompletion(String sql,Object[] args)throws Exception{
        executedSql.add(sql);
        if(sql.contains("FROM professional_runtime_session s JOIN run_step"))return List.of(new LinkedHashMap<>(sessionRow));
        if(sql.contains("FROM task_run"))return List.of(new LinkedHashMap<>(runRow));
        if(sql.contains("FROM step_action_binding"))return List.of(new LinkedHashMap<>(actionRow));
        if(sql.contains("FROM runtime_object_intent"))return List.of(Map.of("planned_artifact_id",resultId));
        if(sql.contains("FROM professional_runtime_supervision"))return List.of(Map.of("canonical_evidence",json.canonical(closureEvidence),"evidence_sha256",closeHash));
        String table=sql.contains("FROM runtime_professional_object_binding")?"runtime_professional_object_binding":sql.contains("FROM audit_event")?"audit_event":sql.contains("FROM outbox_event")?"outbox_event":sql.contains("FROM run_runtime_receipt")?"run_runtime_receipt":sql.contains("FROM step_runtime_attempt")?"step_runtime_attempt":null;
        if(table==null)throw new AssertionError(sql);String key=table.equals("audit_event")?"audit_id":table.equals("outbox_event")?"event_id":table.equals("runtime_professional_object_binding")?"write_id":null;
        return rows(table).stream().filter(r->key==null||Objects.equals(args[args.length-1],r.get(key))).map(LinkedHashMap::new).map(r->(Map<String,Object>)r).toList();
    }
    @Override <T>T tx(CheckedSupplier<T> work)throws Exception{return super.tx(()->{var key=new Object();org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(key,new Object());try{return work.get();}finally{org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(key);}});}
    TaskResearchCompletionAuthority.ProducedCompletion produced()throws Exception{return service.requireProduced(admission,run,step,resultId);}
    @Test void genuineRunningPrecommitNeedsNoPriorCommitAndCarriesExactPinsWithoutWrites()throws Exception {
        tx(()->{var proof=produced();assertEquals("SUCCEEDED",proof.outcome());assertNull(proof.waitingItemId());assertEquals(3,proof.outputs().size());assertEquals(3,proof.stepRevision());assertEquals(sourcePins,proof.sourceReceiptPins());TaskResearchCompletionSchema.require("CommitRequest",proof.canonicalCommitRequest());proof.requireUnchanged();return null;});
        assertEquals("RUNNING",sessionRow.get("step_status"));assertTrue(rows("run_runtime_receipt").isEmpty());verify(db,never()).update(anyString(),any(Object[].class));verify(closure,atLeastOnce()).requireSuccessfulExit();verify(terminal,atLeastOnce()).requireUnchanged();verify(inputRegistered,atLeastOnce()).input();verify(evidenceContracts,atLeastOnce()).require(tenant,actor,project,manifest);verify(retrievals,atLeastOnce()).requireOriginal(any(),eq(List.of()),eq(false));
    }
    @Test void missingPublicationAuthorityFailsClosed()throws Exception {
        service.evidenceContracts(null);
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,()->tx(this::produced)).code());
    }
    @Test void revokedPublicationAuthorityCannotAuthorizeCompletion()throws Exception {
        when(evidenceContracts.require(eq(tenant),eq(actor),eq(project),any())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->tx(this::produced)).code());
    }
    @Test void rolePublicationMustMatchOriginalEvidenceDeclaration()throws Exception {
        var other=catalogue.requirePublished(catalogue.schemaRef("artifacts-v2","EvidenceBundle"),catalogue.schemaDigest("artifacts-v2"));
        when(evidenceContracts.require(eq(tenant),eq(actor),eq(project),any())).thenReturn(other);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->tx(this::produced)).code());
    }
    @Test void predecessorCannotUseProducedOutputsOrStatusWithoutActualCommit()throws Exception {
        assertThrows(TenantAccessException.class,()->tx(()->service.require(admission,run,step)));sessionRow.put("step_status","SUCCEEDED");assertThrows(TenantAccessException.class,()->tx(()->service.require(admission,run,step)));assertThrows(TenantAccessException.class,()->tx(this::produced));
    }
    @Test void actualUnknownAndFalseRemainWaitingWithGenuineCause()throws Exception {
        for(Boolean ready:Arrays.asList(null,false)){produce(ready);tx(()->{var proof=produced();assertEquals("WAITING_INPUT",proof.outcome());assertEquals(waitingId,proof.waitingItemId());assertFalse(proof.waitingCause().required("reason_codes").isEmpty());TaskResearchCompletionSchema.require("CommitRequest",proof.canonicalCommitRequest());return null;});}
    }
    @Test void changedOwnerCauseReasonsCannotConvertWaitingToVerified()throws Exception {
        produce(false);when(waiting.requireProduced(any(),anyString(),anyString(),any(),any(),anyList())).thenReturn(json.object());assertThrows(Exception.class,()->tx(this::produced));
    }
    @Test void staleWaitingItemBlocksNewCommit()throws Exception {
        produce(false);doThrow(new TenantAccessException(ErrorCode.STEP_NOT_READY)).when(waiting).requireProduced(any(),anyString(),anyString(),any(),any(),anyList());assertThrows(TenantAccessException.class,()->tx(this::produced));
    }
    @Test void candidateMustEqualActualFinalSettled076Output()throws Exception {
        projection.putArray("selected_refs");assertThrows(TenantAccessException.class,()->tx(this::produced));
    }
    @Test void revokedOriginalsSourceRulesAndRetrievalsFailClosed()throws Exception {
        for(Object authority:List.of(objects.get(resultId),reads,fixed,retrieved,terminal)){
            if(authority instanceof TaskProfessionalObjectRegistration.OriginalProduced p){doThrow(new TenantAccessException(ErrorCode.CONTENT_MISMATCH)).when(p).requireUnchanged();assertThrows(TenantAccessException.class,()->tx(this::produced));doNothing().when(p).requireUnchanged();}
            if(authority instanceof TaskResearchSourceReader.VerifiedReceipts p){doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(p).requireUnchanged();assertThrows(TenantAccessException.class,()->tx(this::produced));doNothing().when(p).requireUnchanged();}
            if(authority instanceof TaskResearchRuleInputs.Verified p){doThrow(new TenantAccessException(ErrorCode.SCOPE_STALE)).when(p).requireUnchanged();assertThrows(TenantAccessException.class,()->tx(this::produced));doNothing().when(p).requireUnchanged();}
            if(authority instanceof TaskResearchRetrievalAuthority.Verified p){doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(p).requireUnchanged();assertThrows(TenantAccessException.class,()->tx(this::produced));doNothing().when(p).requireUnchanged();}
            if(authority instanceof TaskInvocationResults.HistoricalTerminal p){doThrow(new TenantAccessException(ErrorCode.CONTENT_MISMATCH)).when(p).requireUnchanged();assertThrows(TenantAccessException.class,()->tx(this::produced));doNothing().when(p).requireUnchanged();}
        }
    }
    @Test void currentLeaseActionRunAndExactIntegerMustRemainValid()throws Exception {
        sessionRow.put("lease_expires_at",now);assertThrows(TenantAccessException.class,()->tx(this::produced));sessionRow.put("lease_expires_at",now.plusMinutes(5));
        actionRow.put("action_id",id(999));assertThrows(TenantAccessException.class,()->tx(this::produced));actionRow.put("action_id",action);
        runRow.put("superseded_by_run_id",id(999));assertThrows(TenantAccessException.class,()->tx(this::produced));runRow.remove("superseded_by_run_id");
        sessionRow.put("step_generation",new java.math.BigInteger("18446744073709551617"));assertThrows(TenantAccessException.class,()->tx(this::produced));sessionRow.put("step_generation",1L);
        sessionRow.put("step_revision",new java.math.BigDecimal("3.01"));tx(()->{assertThrows(TenantAccessException.class,()->produced().stepRevision());return null;});
    }
    @Test void receiptOutcomeMetadataAndOutboxCannotBeSelfAsserted()throws Exception {
        attributes=List.of(new ProjectProtocol.Attribute("ready","KNOWN",json.tree(false),List.of(),null));assertThrows(TenantAccessException.class,()->tx(this::produced));produce(true);
        only("audit_event").put("classification","PUBLIC");assertThrows(TenantAccessException.class,()->tx(this::produced));produce(true);
        only("outbox_event").put("aggregate_revision",new java.math.BigInteger("18446744073709551617"));assertThrows(TenantAccessException.class,()->tx(this::produced));
    }
    @Test void capabilityCannotEscapeTransactionOrSurviveOriginalMutation()throws Exception {
        var proof=tx(this::produced);assertThrows(TenantAccessException.class,proof::outcome);
        tx(()->{var fresh=produced();sessionRow.put("step_revision",4L);assertThrows(TenantAccessException.class,fresh::requireUnchanged);return null;});
    }
    @Test void humanOrFormalStepAdmissionCannotMintSchedulerCommit()throws Exception {
        when(admission.controlWorker()).thenReturn(false);when(admission.formalWorker()).thenReturn(true);when(admission.formalProof()).thenReturn(mock(FormalWorkerAccess.Proof.class));assertThrows(TenantAccessException.class,()->tx(this::produced));
    }
    String installCommit()throws Exception {
        var request=tx(()->produced().canonicalCommitRequest());String receiptId=derived(commandId,"commit");
        var receipt=json.object().put("stepId",step).put("inputSha256",inputHash).put("leaseGeneration",1L).put("status","SUCCEEDED").put("receiptId",receiptId);receipt.putNull("waitingItemId");
        var outputs=receipt.putArray("outputs");for(String artifact:List.of(resultId,evidenceId,exportId)){var object=objects.get(artifact);outputs.addObject().put("artifactId",artifact).put("sha256",object.reference().sha256()).put("schemaRef",object.receipt().required("schema_ref").textValue());}
        rows("run_runtime_receipt").add(row("tenant_id",tenant,"project_id",project,"run_id",run,"command_id",commandId,"receipt_id",receiptId,"command_kind","COMMIT","canonical_request",json.canonical(request),"request_sha256",sha(request),"canonical_receipt",json.canonical(receipt),"receipt_sha256",sha(receipt),"created_by",actor,"schema_version","1.0.0","schema_manifest_sha256",TaskResearchCompletionSchema.MANIFEST,"classification","RESTRICTED","created_at",now));
        var witness=json.object();witness.set("request",request);witness.set("receipt",receipt);
        rows("audit_event").add(row("tenant_id",tenant,"project_id",project,"audit_id",derived(receiptId,"audit"),"subject_id",actor,"created_by",actor,"action","task.research.committed","result","SUCCESS","request_id",commandId,"trace_id",run,"schema_version","1.0.0","schema_manifest_sha256",TaskResearchCompletionSchema.MANIFEST,"classification","RESTRICTED","created_at",now,"resource_ref",json.canonical(witness)));
        rows("outbox_event").add(row("tenant_id",tenant,"event_id",derived(receiptId,"event"),"aggregate_id",receiptId,"aggregate_revision",1L,"event_type","task.research.committed","payload",json.canonical(witness),"occurred_at",now));
        var dispatch=json.object().put("command_id",commandId).put("session_id",id(501)).put("action_id",action).put("envelope_sha256",hash('e')).put("input_sha256",inputHash).put("lease_generation",1L);
        rows("step_runtime_attempt").add(row("command_id",commandId,"canonical_dispatch",json.canonical(dispatch),"dispatch_sha256",sha(dispatch)));
        sessionRow.put("step_status","SUCCEEDED");sessionRow.put("step_revision",4L);sessionRow.put("step_output_ref",json.canonical(outputs));return sha(receipt);
    }
    @Test void historicalPredecessorRequiresActualCommitAndPersistedOutputRefs()throws Exception {
        String commitHash=installCommit();tx(()->{var verified=service.require(admission,run,step);assertEquals(commitHash,verified.completionReceiptSha256());assertEquals(resultId,verified.resultArtifactId());verified.requireUnchanged();return null;});
        assertThrows(TenantAccessException.class,()->tx(this::produced));sessionRow.put("step_output_ref","[]");assertThrows(TenantAccessException.class,()->tx(()->service.require(admission,run,step)));
    }
    @Test void historicalCommitJournalAndDispatchIdentityCannotBeCopied()throws Exception {
        installCommit();var committed=rows("outbox_event").stream().filter(r->"task.research.committed".equals(r.get("event_type"))).findFirst().orElseThrow();committed.put("aggregate_revision",2L);assertThrows(TenantAccessException.class,()->tx(()->service.require(admission,run,step)));committed.put("aggregate_revision",1L);
        var dispatch=only("step_runtime_attempt");var changed=(ObjectNode)new com.fasterxml.jackson.databind.ObjectMapper().readTree(dispatch.get("canonical_dispatch").toString());changed.put("envelope_sha256",hash('0'));dispatch.put("canonical_dispatch",json.canonical(changed));dispatch.put("dispatch_sha256",sha(changed));assertThrows(TenantAccessException.class,()->tx(()->service.require(admission,run,step)));
    }
    static Map<String,Object> row(Object... pairs){var out=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
    static String sha(Object value)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}
    static String derived(String id,String suffix){return UUID.nameUUIDFromBytes((id+"|"+suffix).getBytes(StandardCharsets.UTF_8)).toString();}
    static Object[] args(Object[] args){var out=Arrays.copyOfRange(args,1,args.length);return out.length==1&&out[0] instanceof Object[] a?a:out;}
}
