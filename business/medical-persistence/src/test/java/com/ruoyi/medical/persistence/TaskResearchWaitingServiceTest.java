package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Stateful JDBC row workflow test, not MySQL/live grants/runtime/CSRF deployment evidence.
 * Sealed evaluator/session/source/owner authorities are explicit test doubles; the waiting writer,
 * journal verification, request schemas, CAS and read/decision/recovery services are real. */
class TaskResearchWaitingServiceTest {
    final PermissionSchema json=new PermissionSchema();final JdbcTemplate db=mock(JdbcTemplate.class);
    final EnterpriseAccess access=mock(EnterpriseAccess.class);final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);
    final TaskResearchCompletionEvaluation.WaitingCause proof=mock(TaskResearchCompletionEvaluation.WaitingCause.class);
    final TaskResearchExecutionService.Candidate candidate=mock(TaskResearchExecutionService.Candidate.class);
    final TaskProfessionalRuntimeService.Session session=mock(TaskProfessionalRuntimeService.Session.class);
    final TaskProfessionalSupervisor.Closed closure=mock(TaskProfessionalSupervisor.Closed.class);
    final TaskNodeInputAuthority.Verified input=mock(TaskNodeInputAuthority.Verified.class);
    final TaskResearchRuleInputs.Verified fixed=mock(TaskResearchRuleInputs.Verified.class);
    final TaskResearchSourceReader.VerifiedReceipts sources=mock(TaskResearchSourceReader.VerifiedReceipts.class);
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
    final EnterpriseAccess.Admission worker=mock(EnterpriseAccess.Admission.class),human=mock(EnterpriseAccess.Admission.class);
    final PermissionProtocol.GrantRow read=mock(PermissionProtocol.GrantRow.class),resolve=mock(PermissionProtocol.GrantRow.class);
    boolean rejectCas;
    final Map<String,List<Map<String,Object>>> tables=new HashMap<>();final List<String> statements=new ArrayList<>();
    final String tenant=id(1),project=id(2),task=id(3),run=id(4),step=id(5),action=id(6),target=id(7),owner=id(8),actor=id(9);
    final LocalDateTime now=LocalDateTime.of(2026,10,20,2,0);final TaskResearchWaitingService service;
    TaskResearchWaitingServiceTest()throws Exception {
        when(db.query(eq("SELECT UTC_TIMESTAMP(6)"),any(RowMapper.class))).thenReturn(List.of(now));
        when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(c->select(c.getArgument(0),args(c.getArguments())));
        when(db.update(anyString(),any(Object[].class))).thenAnswer(c->write(c.getArgument(0),args(c.getArguments())));
        service=new TaskResearchWaitingService(access,db,resources,json);
        for(var a:List.of(worker,human)){when(a.context()).thenReturn(new BusinessSessionProtocol.Context(tenant,"1","1","1"));when(a.watermarks()).thenReturn(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));when(a.bindingView()).thenReturn(new AuthenticatedIdentityBinding.View("local",a==worker?actor:owner,null,null));}
        when(worker.actorId()).thenReturn(actor);when(worker.formalWorker()).thenReturn(true);when(human.actorId()).thenReturn(owner);
        when(access.execute(eq(subject),any())).thenAnswer(c->tx(human,()->c.<Function<EnterpriseAccess.Admission,Object>>getArgument(1).apply(human)));
        when(resources.require(eq(human),any(),eq(TaskResearchWaitingService.READ),eq(TaskResearchWaitingService.READ_PURPOSE))).thenReturn(read);
        when(resources.require(eq(human),any(),eq(TaskResearchWaitingService.RESOLVE),eq(TaskResearchWaitingService.RESOLVE_PURPOSE))).thenReturn(resolve);
        when(resources.find(eq(human),any(),eq(TaskResearchWaitingService.RESOLVE),eq(TaskResearchWaitingService.RESOLVE_PURPOSE))).thenReturn(Optional.of(resolve));
        when(read.grantId()).thenReturn(id(60));when(read.revision()).thenReturn(BigInteger.ONE);when(resolve.grantId()).thenReturn(id(61));when(resolve.revision()).thenReturn(BigInteger.ONE);
        when(proof.candidate()).thenReturn(candidate);when(proof.inputs()).thenReturn(fixed);when(proof.reasons()).thenReturn(List.of("APPROVED_COMPLETION_RULE_REQUIRED","FROZEN_PROFILE_INPUT_UNKNOWN"));when(proof.receiptPins()).thenReturn(json.object().arrayNode());
        when(candidate.supervision()).thenReturn(closure);when(closure.receiptId()).thenReturn(id(18));when(closure.deploymentSha256()).thenReturn(hash('d'));var closed=json.object().put("named_authority_double","closed supervisor receipt");when(closure.sha256()).thenReturn(sha(closed));
        rows("professional_runtime_supervision").add(row("tenant_id",tenant,"project_id",project,"run_id",run,"step_id",step,"lease_generation",1,"session_id",id(10),"phase","CLOSED","created_by",actor,"input_sha256",hash('a'),"deployment_sha256",hash('d'),"receipt_id",id(18),"evidence_sha256",sha(closed),"canonical_evidence",json.canonical(closed)));
        when(candidate.session()).thenReturn(session);when(candidate.candidateSha256()).thenReturn(hash('c'));when(candidate.sourceReceipts()).thenReturn(sources);when(candidate.invocationPins()).thenReturn(json.object().arrayNode());when(sources.canonicalPins()).thenReturn(json.object().arrayNode());
        when(session.admission()).thenReturn(worker);when(session.input()).thenReturn(input);when(session.actionId()).thenReturn(action);when(session.sessionId()).thenReturn(id(10));when(session.generation()).thenReturn("1");
        when(input.tenantId()).thenReturn(tenant);when(input.projectId()).thenReturn(project);when(input.taskId()).thenReturn(task);when(input.runId()).thenReturn(run);when(input.stepId()).thenReturn(step);when(input.inputId()).thenReturn(target);when(input.inputSha256()).thenReturn(hash('a'));
        when(fixed.ownerId()).thenReturn(owner);when(fixed.profileId()).thenReturn(id(11));when(fixed.profileSha256()).thenReturn(hash('b'));when(fixed.requirementIds()).thenReturn(List.of(id(12)));when(fixed.checks()).thenReturn(List.of());when(fixed.witness()).thenReturn(json.object().put("named_authority_double","current frozen profile/rule authority"));
        var plan=json.object();plan.putArray("sections").addObject().put("section_id",id(13)).put("owner_id",owner);plan.putArray("questions").addObject().put("question_key","q1").put("section_id",id(13));
        var replay=json.object().put("plan_revision",id(14));replay.putObject("plan").set("content",plan);replay.putObject("project_profile").put("profile_revision",id(11)).put("content_sha256",hash('b'));
        var manifest=json.object().put("plan_revision",id(14)).put("plan_sha256",sha(plan)).put("graph_id",id(15)).put("graph_sha256",hash('e')).put("replay_sha256",sha(replay)).put("snapshot_id",id(16)).put("snapshot_sha256",hash('f')).put("question_id",id(17)).put("question_key","q1").put("section_id",id(13));when(input.manifest()).thenReturn(manifest);
        rows("task_run").add(row("tenant_id",tenant,"project_id",project,"run_id",run,"task_id",task,"execution_kind","TASK","created_by",actor,"classification","RESTRICTED","replay_manifest",json.canonical(replay)));
        var registration=json.object().put("artifact_id",target);String receiptHash=sha(registration);when(input.receiptSha256()).thenReturn(receiptHash);
        rows("task_node_input_registered").add(row("tenant_id",tenant,"project_id",project,"run_id",run,"step_id",step,"task_id",task,"artifact_id",target,"payload_sha256",hash('a'),"receipt_sha256",receiptHash,"receipt_json",json.canonical(registration)));
        rows("version_registry").add(row("tenant_id",tenant,"owner_project_id",project,"version_id",target,"entity_kind","ARTIFACT","content_sha256",hash('a')));
        rows("artifact").add(row("tenant_id",tenant,"project_id",project,"run_id",run,"artifact_id",target,"artifact_kind","TASK_NODE_INPUT","sha256",hash('a')));
        rows("artifact_content_binding").add(row("tenant_id",tenant,"project_id",project,"artifact_id",target,"resource_kind","TASK","resource_id",task,"schema_ref",TaskNodeInputSchema.REF));
        rows("step_action_binding").add(row("tenant_id",tenant,"project_id",project,"run_id",run,"step_id",step,"action_id",action,"kind","HOST_CONTROL","input_sha256",hash('a'),"lease_generation",1,"arguments_sha256",sha(json.tree(Map.of("operation","N02_HOST_CONTROL","run_id",run,"step_id",step,"input_artifact_id",target,"input_sha256",hash('a'),"input_receipt_sha256",receiptHash)))));
        rows("tenant_member").add(row("tenant_id",tenant,"user_id",owner));
        installInvocationMetadata();
    }
    void installInvocationMetadata()throws Exception {
        String invocation=id(30);var result=json.object().put("tenant_id",tenant).put("invocation_id",invocation).put("kind","MODEL_RESULT");
        var capture=json.object().put("tenant_id",tenant).put("invocation_id",invocation).put("kind","PROVIDER_CAPTURE");String resultHash=sha(result),captureHash=sha(capture);
        var account=json.object().put("tenant_id",tenant).put("project_id",project).put("invocation_id",invocation).put("created_by",actor).put("status","SUCCEEDED").put("settlement_status","SETTLED").put("provider_terminal",true).put("result_registration_sha256",resultHash).put("capture_registration_sha256",captureHash);
        rows("invocation").add(row("tenant_id",tenant,"invocation_id",invocation,"project_id",project,"run_id",run,"step_id",step,"lease_generation",1,"created_by",actor,"status","SUCCEEDED","settlement_status","SETTLED"));
        rows("formal_invocation_accounting").add(row("tenant_id",tenant,"invocation_id",invocation,"receipt_json",json.canonical(account),"receipt_sha256",sha(account)));
        for(var value:List.of(result,capture))rows("formal_invocation_registered_object").add(row("tenant_id",tenant,"invocation_id",invocation,"kind",value.required("kind").asText(),"receipt_json",json.canonical(value),"receipt_sha256",sha(value)));
        var pins=json.object().arrayNode();pins.addObject().put("invocation_id",invocation).put("accounting_sha256",sha(account)).put("result_registration_sha256",resultHash).put("capture_registration_sha256",captureHash);when(candidate.invocationPins()).thenReturn(pins);
    }
    @Test void sourcePointersAndSettledInvocationPinsCannotBeInventedOrGrafted()throws Exception {
        rows("invocation").get(0).put("run_id",id(99));assertThrows(TenantAccessException.class,this::create);assertTrue(rows("waiting_item").isEmpty());rows("invocation").get(0).put("run_id",run);
        var pins=json.object().arrayNode();pins.addObject().put("read_receipt_id",id(91)).put("receipt_sha256",hash('d'));when(sources.canonicalPins()).thenReturn(pins);
        assertThrows(TenantAccessException.class,this::create);assertTrue(rows("waiting_item").isEmpty());
    }
    @Test void pinnedAccountingAndModelRegistrationMutationFailsClosed()throws Exception {
        String id=create();assertEquals(1,service.read(subject,"1",id).required("model_invocations").size());
        rows("formal_invocation_accounting").get(0).put("receipt_sha256",hash('0'));assertThrows(TenantAccessException.class,()->service.read(subject,"1",id));
    }
    TaskResearchCompletionAuthority.Input historical()throws Exception {
        var historical=mock(TaskResearchCompletionAuthority.Input.class);when(historical.admission()).thenReturn(worker);when(worker.formalWorker()).thenReturn(false);when(worker.controlWorker()).thenReturn(true);
        when(historical.tenantId()).thenReturn(tenant);when(historical.projectId()).thenReturn(project);when(historical.runId()).thenReturn(run);when(historical.stepId()).thenReturn(step);when(historical.actionId()).thenReturn(action);when(historical.sessionId()).thenReturn(id(10));when(historical.generation()).thenReturn(1L);
        String inputHash=input.inputSha256(),receiptHash=input.receiptSha256();when(historical.inputId()).thenReturn(target);when(historical.inputSha256()).thenReturn(inputHash);when(historical.receiptSha256()).thenReturn(receiptHash);when(historical.questionId()).thenReturn(id(17));when(historical.actorId()).thenReturn(actor);
        var manifest=(ObjectNode)input.manifest().deepCopy();manifest.put("tenant_id",tenant).put("project_id",project).put("task_id",task).put("run_id",run).put("step_id",step);when(historical.manifest()).thenReturn(manifest);return historical;
    }
    @Test void freshCommitRequiresPrivateOriginalControlInputAndExactProducedPins()throws Exception {
        String id=create();var historical=historical();var cause=tx(worker,()->service.requireProduced(historical,id,candidate.candidateSha256(),sources.canonicalPins(),candidate.invocationPins(),List.of()));
        assertEquals(owner,cause.required("assignee_id").asText());verify(historical,atLeast(2)).requireUnchanged();
        assertThrows(TenantAccessException.class,()->tx(worker,()->service.requireProduced(historical,id,hash('0'),sources.canonicalPins(),candidate.invocationPins(),List.of())));
        assertThrows(TenantAccessException.class,()->tx(worker,()->service.requireProduced(historical,id,candidate.candidateSha256(),sources.canonicalPins(),json.object().arrayNode(),List.of())));
        when(historical.actionId()).thenReturn(TaskResearchWaitingServiceTest.id(99));assertThrows(TenantAccessException.class,()->tx(worker,()->service.requireProduced(historical,id,candidate.candidateSha256(),sources.canonicalPins(),candidate.invocationPins(),List.of())));assertNoRunMutation();
    }
    @Test void ownerStaleDecisionBlocksFreshProducedCommitButNotOriginalHistoricalVerification()throws Exception {
        String id=create();var request=decision(id);service.decide(subject,"1",id,"owner-stale-before-commit-0001",ContentReadAuthorization.canonicalBytes(request));var historical=historical();
        var failure=assertThrows(TenantAccessException.class,()->tx(worker,()->service.requireProduced(historical,id,candidate.candidateSha256(),sources.canonicalPins(),candidate.invocationPins(),List.of())));assertEquals(ErrorCode.STEP_NOT_READY,failure.code());
        tx(worker,()->{service.requireOriginal(proof,id);return null;});assertEquals("STALE",rows("waiting_item").get(0).get("status"));assertNoRunMutation();
    }
    @Test void originalCauseCreatesOnly009WaitingAndJournalsAndDuplicateReturnsOriginal()throws Exception {
        String id=create();assertEquals(id,create());tx(worker,()->{service.requireOriginal(proof,id);return null;});
        assertEquals(1,rows("waiting_item").size());assertEquals(1,rows("operation_record").size());assertEquals(1,rows("audit_event").size());assertEquals(1,rows("outbox_event").size());
        var waiting=rows("waiting_item").get(0);assertEquals(target,waiting.get("target_revision"));assertEquals(owner,waiting.get("assignee_id"));assertEquals("WAITING_INPUT",waiting.get("status"));assertEquals(1,rows("version_registry").size());
        verify(proof,atLeast(2)).requireUnchanged();assertNoRunMutation();
    }
    @Test void ownerMetadataReadDoesNotContainRawSourceProfileQuestionOrCandidateBody()throws Exception {
        String id=create();var result=service.read(subject,"1",id);assertEquals("REQUIRE_NEW_RUN",result.required("allowed_decisions").get(0).asText());assertFalse(result.required("can_resume_frozen_run").asBoolean());
        for(String prohibited:List.of("question","attributes","candidate","source_contents","rule_body","access_token"))assertFalse(result.has(prohibited));
        verify(resources,atLeastOnce()).require(eq(human),eq(new PermissionProtocol.Scope(tenant,project,"TASK",task)),eq("work_item.read"),eq("WORK_ITEM_READING"));
    }
    @Test void requireNewRunDecisionIsCasJournaledReplayableAndNeverResumesFrozenRun()throws Exception {
        String id=create();var request=decision(id);String key="original-owner-decision-0001";byte[] bytes=ContentReadAuthorization.canonicalBytes(request);
        var receipt=service.decide(subject,"1",id,key,bytes);assertEquals("STALE",receipt.required("status").asText());assertFalse(receipt.required("human_checkpoint_approved").asBoolean());assertEquals("2",receipt.required("revision").asText());
        assertEquals(receipt,service.decide(subject,"1",id,key,bytes));var recovery=json.object().put("schema_version","1.0.0").put("original_idempotency_key",key);recovery.set("original_request",request);assertEquals(receipt,service.recover(subject,"1",id,ContentReadAuthorization.canonicalBytes(recovery)));
        assertEquals(2,rows("operation_record").size());assertEquals(2,rows("outbox_event").size());assertEquals(2,rows("version_registry").size());assertTrue(service.read(subject,"1",id).required("allowed_decisions").isEmpty());assertNoRunMutation();
    }
    @Test void assignmentIsNotReadAuthorityAndResolveRequiresBothExplicitGrants()throws Exception {
        String id=create();when(resources.require(eq(human),any(),eq("work_item.read"),anyString())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class,()->service.read(subject,"1",id));assertEquals(1,rows("operation_record").size());
        when(resources.require(eq(human),any(),eq("work_item.read"),anyString())).thenReturn(read);var request=decision(id);
        when(resources.require(eq(human),any(),eq("work_item.resolve"),anyString())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class,()->service.decide(subject,"1",id,"owner-decide-no-grant-0001",ContentReadAuthorization.canonicalBytes(request)));assertEquals("WAITING_INPUT",rows("waiting_item").get(0).get("status"));
    }
    @Test void differentActorAndMissingConfirmedOwnerNeverFallbackToRunCreator()throws Exception {
        when(fixed.ownerId()).thenReturn(null);assertThrows(TenantAccessException.class,this::create);assertTrue(rows("waiting_item").isEmpty());
        when(fixed.ownerId()).thenReturn(owner);String id=create();when(human.actorId()).thenReturn(actor);assertThrows(TenantAccessException.class,()->service.read(subject,"1",id));
    }
    @Test void wrongExpectedRevisionChangedOriginalAndApproveAreRejectedWithoutAdditionalDecision()throws Exception {
        String id=create();var request=decision(id);request.put("expected_revision","2");assertThrows(TenantAccessException.class,()->service.decide(subject,"1",id,"bad-revision-request-0001",ContentReadAuthorization.canonicalBytes(request)));
        request.put("expected_revision","1");String key="immutable-owner-request-0001";service.decide(subject,"1",id,key,ContentReadAuthorization.canonicalBytes(request));request.put("reason","different original content");
        var failure=assertThrows(TenantAccessException.class,()->service.decide(subject,"1",id,key,ContentReadAuthorization.canonicalBytes(request)));assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,failure.code());
        request.put("decision","APPROVE");assertThrows(SchemaRejection.class,()->service.decide(subject,"1",id,key,ContentReadAuthorization.canonicalBytes(request)));assertEquals(2,rows("operation_record").size());assertNoRunMutation();
    }
    @Test void losingCasOrChangingAuthorityRollsBackAcknowledgementAndAllJournals()throws Exception {
        String id=create();var request=decision(id);rejectCas=true;
        assertThrows(TenantAccessException.class,()->service.decide(subject,"1",id,"losing-cas-original-0001",ContentReadAuthorization.canonicalBytes(request)));
        assertEquals("WAITING_INPUT",rows("waiting_item").get(0).get("status"));assertEquals(1,rows("version_registry").size());assertEquals(1,rows("operation_record").size());
        rejectCas=false;var revoked=mock(PermissionProtocol.GrantRow.class);
        when(resources.require(eq(human),any(),eq("work_item.resolve"),anyString())).thenReturn(resolve,revoked);
        assertThrows(TenantAccessException.class,()->service.decide(subject,"1",id,"authority-race-original-0001",ContentReadAuthorization.canonicalBytes(request)));
        assertEquals("WAITING_INPUT",rows("waiting_item").get(0).get("status"));assertEquals(1,rows("version_registry").size());assertEquals(1,rows("audit_event").size());assertEquals(1,rows("outbox_event").size());assertNoRunMutation();
    }
    @Test void unknownRecoveryIsReadOnlyAndRequiresExactOriginalRequest()throws Exception {
        String id=create();var recovery=json.object().put("schema_version","1.0.0").put("original_idempotency_key","never-submitted-key-0001");recovery.set("original_request",decision(id));
        var failure=assertThrows(TenantAccessException.class,()->service.recover(subject,"1",id,ContentReadAuthorization.canonicalBytes(recovery)));assertEquals(ErrorCode.SOURCE_MISSING,failure.code());assertEquals(1,rows("operation_record").size());
    }
    @Test void originalTargetActionOwnerOrJournalCorruptionCannotBeReadAsAValidWaitingItem()throws Exception {
        String id=create();rows("step_action_binding").get(0).put("kind","MODEL_CALL");assertThrows(TenantAccessException.class,()->service.read(subject,"1",id));rows("step_action_binding").get(0).put("kind","HOST_CONTROL");
        rows("outbox_event").get(0).put("aggregate_revision","9");assertThrows(TenantAccessException.class,()->service.read(subject,"1",id));rows("outbox_event").get(0).put("aggregate_revision","1");
        rows("audit_event").get(0).put("classification","PUBLIC");assertThrows(TenantAccessException.class,()->service.read(subject,"1",id));
    }
    @Test void transactionContextAndCurrentSessionAreRequired()throws Exception {
        assertThrows(TenantAccessException.class,()->service.create(proof));String id=create();assertThrows(TenantAccessException.class,()->service.read(subject,"2",id));
        when(human.formalWorker()).thenReturn(true);assertThrows(TenantAccessException.class,()->service.read(subject,"1",id));
    }
    @Test void producerHasNoJsonOverloadAndWaitingCauseFactoryIsPrivate() {
        assertTrue(Arrays.stream(TaskResearchCompletionEvaluation.WaitingCause.class.getDeclaredConstructors()).allMatch(c->java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
        assertEquals(List.of(TaskResearchCompletionEvaluation.WaitingCause.class),Arrays.stream(TaskResearchWaitingService.class.getMethods()).filter(m->m.getName().equals("create")).map(m->m.getParameterTypes()[0]).toList());
    }
    String create()throws Exception{return tx(worker,()->service.create(proof));}
    ObjectNode decision(String id)throws Exception {return json.object().put("schema_version","1.0.0").put("waiting_item_id",id).put("expected_revision","1").put("cause_sha256",service.read(subject,"1",id).required("cause_sha256").asText()).put("decision","REQUIRE_NEW_RUN").put("reason","I acknowledge that approved revisions and a new run are required.");}
    void assertNoRunMutation(){assertTrue(statements.stream().noneMatch(s->s.startsWith("UPDATE task_run")||s.startsWith("UPDATE run_step")||s.contains("workflow_command")||s.contains("RESOLVE_WAITING")));}
    interface Checked<T>{T get()throws Exception;}
    <T>T tx(EnterpriseAccess.Admission a,Checked<T> action)throws Exception {
        try(var scope=TenantScope.open(new TenantScope.Identity(tenant,a.actorId(),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.bindResource(db,new Object());scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
            var before=copyTables();try{return action.get();}catch(Exception|Error e){tables.clear();tables.putAll(before);throw e;}finally{scope.finishTransaction();TransactionSynchronizationManager.unbindResource(db);TransactionSynchronizationManager.clear();}}
    }
    Map<String,List<Map<String,Object>>> copyTables(){var copy=new HashMap<String,List<Map<String,Object>>>();tables.forEach((k,v)->copy.put(k,new ArrayList<>(v.stream().map(r->(Map<String,Object>)new LinkedHashMap<>(r)).toList())));return copy;}
    List<Map<String,Object>> rows(String table){return tables.computeIfAbsent(table,x->new ArrayList<>());}
    List<Map<String,Object>> select(String sql,Object[] args){statements.add(sql);var from=Pattern.compile("FROM ([a-z_]+)").matcher(sql);if(!from.find())throw new AssertionError(sql);var result=new ArrayList<Map<String,Object>>();
        for(var row:rows(from.group(1))){var filter=Pattern.compile("([a-z_]+)=\\?").matcher(sql);int index=0;boolean matches=true;while(filter.find()){if(!Objects.equals(String.valueOf(args[index++]),String.valueOf(row.get(filter.group(1)))))matches=false;}if(matches)result.add(new LinkedHashMap<>(row));}return result;
    }
    int write(String sql,Object[] args){statements.add(sql);
        if(sql.startsWith("UPDATE waiting_item")){if(rejectCas)return 0;var row=rows("waiting_item").get(0);if(!Objects.equals(args[2],row.get("tenant_id"))||!Objects.equals(args[3],row.get("waiting_item_id"))||!"WAITING_INPUT".equals(row.get("status"))||!Objects.equals(args[4],row.get("revision"))||row.get("resolution_id")!=null)return 0;row.put("status","STALE");row.put("resolution_id",args[0]);row.put("revision",2);row.put("updated_at",args[1]);return 1;}
        var insert=Pattern.compile("INSERT INTO ([a-z_]+)\\(([^)]+)\\) VALUES\\(([^)]+)\\)").matcher(sql);if(!insert.matches())throw new AssertionError(sql);String[] columns=insert.group(2).split(",");assertEquals(columns.length,args.length);var row=new LinkedHashMap<String,Object>();for(int i=0;i<columns.length;i++)row.put(columns[i],args[i]);rows(insert.group(1)).add(row);return 1;
    }
    static Object[] args(Object[] args){Object[] value=Arrays.copyOfRange(args,1,args.length);return value.length==1&&value[0] instanceof Object[] array?array:value;}
    static Map<String,Object> row(Object...args){var row=new LinkedHashMap<String,Object>();for(int i=0;i<args.length;i+=2)row.put((String)args[i],args[i+1]);return row;}
    static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}static String hash(char c){return String.valueOf(c).repeat(64);}static String sha(JsonNode node)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(node));}
}
