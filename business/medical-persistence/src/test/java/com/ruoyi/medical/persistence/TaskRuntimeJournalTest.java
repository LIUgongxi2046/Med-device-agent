package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real 075 producers and closed validation over explicit JDBC-row and private-authority
 * doubles. Not a live database, storage transfer, credential, or process-isolation claim. */
class TaskRuntimeJournalTest {
    final PermissionSchema json=new PermissionSchema();final TaskRunBootstrapSchema bootstrapSchema=new TaskRunBootstrapSchema();
    final JdbcTemplate db=mock(JdbcTemplate.class);final TaskRunGraphService graphs=mock(TaskRunGraphService.class);
    final TaskRunGraphService.Verified graph=mock(TaskRunGraphService.Verified.class);
    final TaskRunBootstrapAuthority.Verified authority=mock(TaskRunBootstrapAuthority.Verified.class);
    final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);final FormalWorkerAccess.Proof formal=mock(FormalWorkerAccess.Proof.class);
    final TaskProfessionalRuntimeService.Envelope envelope=mock(TaskProfessionalRuntimeService.Envelope.class);
    final TaskProfessionalObjectRegistration.Registered registered=mock(TaskProfessionalObjectRegistration.Registered.class);
    final TaskNodeInputAuthority.Verified input=mock(TaskNodeInputAuthority.Verified.class);
    final Map<String,List<Map<String,Object>>> tables=new HashMap<>();final List<String> writes=new ArrayList<>();
    final Object resourceKey=new Object(),resource=new Object();
    TaskRuntimeJournal journal;TenantScope scope;ObjectNode replay,command,replayIntent,replayReceipt;
    String tenant,project,run,task,principal,actor,replayHash,replaySchemaHash;
    final String step=id(901),action=id(902),session=id(903),dispatchCommand=id(904),inputId=id(905),envelopeId=id(906),graphId=id(907),writeId=id(908),replayArtifact=id(909),startEvent=id(910);
    final String service="spiffe://test/professional",inputHash=hash('c'),inputReceiptHash=hash('d'),graphHash=hash('e');
    LocalDateTime now=LocalDateTime.of(2026,10,20,2,30),preparedAt=now.minusSeconds(3),registeredAt=now.minusSeconds(1);
    TaskRuntimeJournalTest()throws Exception{}

    @BeforeEach void setup()throws Exception {
        replay=(ObjectNode)bootstrapSchema.read(new String(RuntimeReplaySchemaTest.fixture(),StandardCharsets.UTF_8));
        tenant=t(replay,"tenant_id");project=t(replay,"project_id");run=t(replay,"run_id");task=t(replay,"task_id");principal=t(replay,"principal_id");actor=t(replay,"initiator_id");
        replayHash=digest(replay);replaySchemaHash=SchemaCatalogue.fromClasspath().schemaDigest("replay-manifest");
        byte[] bytes=canonical(replay).getBytes(StandardCharsets.UTF_8);var verified=new RuntimeReplaySchema(json).read(bytes,replayHash,replaySchemaHash);
        when(admission.actorId()).thenReturn(actor);when(admission.context()).thenReturn(new com.ruoyi.medical.identity.BusinessSessionProtocol.Context(tenant,"1","1","1"));when(admission.controlWorker()).thenReturn(true);when(admission.controlProof()).thenReturn(mock(FormalRunControlAccess.Proof.class));when(admission.formalProof()).thenReturn(formal);
        when(authority.admission()).thenReturn(admission);when(authority.tenantId()).thenReturn(tenant);when(authority.projectId()).thenReturn(project);when(authority.runId()).thenReturn(run);when(authority.taskId()).thenReturn(task);when(authority.principalId()).thenReturn(principal);when(authority.actorId()).thenReturn(actor);when(authority.classification()).thenReturn("INTERNAL");when(authority.replay()).thenReturn(verified);
        when(graphs.require(admission,run)).thenReturn(graph);when(graph.tenantId()).thenReturn(tenant);when(graph.projectId()).thenReturn(project);when(graph.runId()).thenReturn(run);when(graph.taskId()).thenReturn(task);when(graph.principalId()).thenReturn(principal);when(graph.replaySha256()).thenReturn(replayHash);when(graph.graphId()).thenReturn(graphId);when(graph.graphSha256()).thenReturn(graphHash);when(graph.planRevision()).thenReturn(t(replay,"plan_revision"));when(graph.planSha256()).thenReturn(t(replay.required("plan"),"content_sha256"));
        command=json.object();var c=command.putObject("command").put("schema_version","1.0.0").put("kind","START_RUN").put("command_id",id(911)).put("workflow_id",tenant+"/"+run).put("replay_sha256",replayHash).put("replay_schema_sha256",replaySchemaHash).put("replay_schema_ref",RuntimeReplaySchema.REF);
        for(String f:List.of("tenant_id","project_id","run_id","task_id","principal_id"))c.set(f,replay.required(f));command.putObject("decision").put("actor_id",actor);c.put("command_sha256",digest(command));when(authority.command()).thenAnswer(x->command.deepCopy());
        replayIntent=json.object().put("schema_version","1.0.0");for(String f:List.of("tenant_id","project_id","run_id","task_id","principal_id","initiator_id"))replayIntent.set(f,replay.required(f));
        replayIntent.put("command_id",t(c,"command_id")).put("command_sha256",t(c,"command_sha256")).put("outbox_event_id",startEvent).put("write_id",writeId).put("write_key",TaskRunReplayLedger.key(run)).put("planned_artifact_id",replayArtifact)
            .put("replay_schema_ref",RuntimeReplaySchema.REF).put("replay_schema_sha256",replaySchemaHash).put("replay_sha256",replayHash).put("size_bytes",Integer.toString(bytes.length)).put("canonical_payload",canonical(replay))
            .put("object_key","runtime/"+tenant+"/"+run+"/"+writeId+"/"+replayArtifact).put("media_type","application/json").put("release_id",t(replay,"agent_release_id")).put("publication_approval_sha256",t(replay,"publication_approval_sha256"))
            .put("authority_sha256",hash('a')).put("classification","INTERNAL").put("created_at",iso(preparedAt));replayIntent.set("execution_policy",replay.required("execution_policy_version"));bootstrapSchema.requireReplayIntent(replayIntent,verified);
        replayReceipt=json.object().put("schema_version","1.0.0");for(String f:List.of("tenant_id","project_id","run_id","task_id","principal_id","initiator_id","command_id","command_sha256","write_id","replay_schema_ref","replay_schema_sha256","authority_sha256","classification","execution_policy"))replayReceipt.set(f,replayIntent.required(f));
        replayReceipt.put("artifact_id",replayArtifact).put("intent_sha256",digest(replayIntent)).put("governance_sha256",hash('b')).put("registered_at",iso(registeredAt));
        var object=new ObjectRef(t(replayIntent,"object_key"),"actual-replay-version",replayHash,Integer.toString(bytes.length),"application/json");replayReceipt.set("object_ref",json.tree(object));replayReceipt.set("content_manifest",json.tree(new ArtifactRegistryProtocol.DependencyManifest("1.0.0",tenant,project,id(912),List.of())));replayReceipt.putArray("content_roots");bootstrapSchema.requireRegistration(replayIntent,replayReceipt);
        var runRow=meta(now,bootstrapSchema.manifest());runRow.put("run_id",run);runRow.put("task_id",task);runRow.put("execution_kind","TASK");runRow.put("status","QUEUED");runRow.put("replay_manifest",canonical(replay));add("task_run",runRow);
        var ir=meta(preparedAt,bootstrapSchema.manifest());for(String f:List.of("run_id","principal_id","task_id","command_id","command_sha256","outbox_event_id","write_id","write_key","planned_artifact_id","object_key","replay_sha256","replay_schema_sha256","size_bytes","canonical_payload"))ir.put(f,t(replayIntent,f));ir.put("intent_json",canonical(replayIntent));ir.put("intent_sha256",digest(replayIntent));add("task_run_replay_intent",ir);
        var rr=meta(registeredAt,bootstrapSchema.manifest());rr.put("run_id",run);rr.put("write_id",writeId);rr.put("artifact_id",replayArtifact);rr.put("object_sha256",replayHash);rr.put("object_version",object.objectVersion());rr.put("receipt_json",canonical(replayReceipt));rr.put("receipt_sha256",digest(replayReceipt));add("task_run_replay_registration",rr);
        var attempt=meta(preparedAt.plusSeconds(1),bootstrapSchema.manifest());attempt.put("run_id",run);attempt.put("write_id",writeId);add("task_run_replay_attempt",attempt);
        var ar=meta(registeredAt,replaySchemaHash);ar.put("artifact_id",replayArtifact);ar.put("run_id",run);ar.put("artifact_kind","REPLAY_MANIFEST");ar.put("object_ref",StoredObjectReferences.encode(object));for(String f:List.of("object_key","object_version","sha256","size_bytes","media_type"))ar.put(f,t(replayReceipt.required("object_ref"),f));ar.put("content_sha256",replayHash);ar.put("dependency_access_set_id",id(912));add("artifact",ar);
        add("version_registry",Map.of("tenant_id",tenant,"version_id",replayArtifact,"owner_project_id",project,"entity_kind","ARTIFACT","content_sha256",replayHash));
        add("artifact_content_binding",Map.of("tenant_id",tenant,"project_id",project,"artifact_id",replayArtifact,"resource_kind","TASK","resource_id",task,"schema_ref",RuntimeReplaySchema.REF,"schema_document_sha256",replaySchemaHash,"dependency_access_set_id",id(912)));
        sourceJournal(rr,"task.run.replay.registered",writeId,replayArtifact,writeId,replayReceipt,replaySchemaHash);
        var prepared=json.object().put("schema_version","1.0.0").put("kind","RUN_REPLAY_PREPARED").put("intent_sha256",digest(replayIntent));for(String f:List.of("tenant_id","project_id","run_id","principal_id","task_id","initiator_id","command_id","command_sha256","write_id","planned_artifact_id","replay_sha256","replay_schema_sha256"))prepared.set(f,replayIntent.required(f));sourceJournal(ir,"task.run.replay.prepared",writeId,writeId,run,prepared,bootstrapSchema.manifest());
        add("outbox_event",Map.of("tenant_id",tenant,"event_id",startEvent,"aggregate_id",run,"event_type","run.start.requested","aggregate_revision",1,"payload",canonical(command),"occurred_at",preparedAt.minusSeconds(1)));
        when(db.query(eq("SELECT UTC_TIMESTAMP(6)"),any(RowMapper.class))).thenAnswer(x->List.of(now));when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(x->select(x.getArgument(0),args(x.getArguments())));when(db.update(anyString(),any(Object[].class))).thenAnswer(x->insert(x.getArgument(0),args(x.getArguments())));
        journal=new TaskRuntimeJournal(db,json,graphs);
    }
    void leased()throws Exception {
        when(admission.controlWorker()).thenReturn(false);when(admission.formalWorker()).thenReturn(true);
        when(formal.identity()).thenReturn(new FormalWorkerAccess.Identity(tenant,project,run,principal,actor,"TASK",step,action,"1",service,"{}","{}",List.of("task.run"),null));only("task_run").put("status","RUNNING");
        when(input.tenantId()).thenReturn(tenant);when(input.projectId()).thenReturn(project);when(input.runId()).thenReturn(run);when(input.taskId()).thenReturn(task);when(input.stepId()).thenReturn(step);when(input.graphId()).thenReturn(graphId);when(input.graphSha256()).thenReturn(graphHash);when(input.inputId()).thenReturn(inputId);when(input.inputSha256()).thenReturn(inputHash);when(input.receiptSha256()).thenReturn(inputReceiptHash);when(input.objectReference()).thenReturn(new ObjectRef("test/input","actual-input-version",inputHash,"20","application/json"));
        when(envelope.admission()).thenReturn(admission);when(envelope.input()).thenReturn(input);when(envelope.runId()).thenReturn(run);when(envelope.stepId()).thenReturn(step);when(envelope.actionId()).thenReturn(action);when(envelope.leaseGeneration()).thenReturn("1");when(envelope.workerService()).thenReturn(service);when(envelope.sessionId()).thenReturn(session);
        var payload=json.object().put("explicit_test_boundary","final_compiled_envelope");String payloadHash=digest(payload);when(envelope.bytes()).thenReturn(canonical(payload).getBytes(StandardCharsets.UTF_8));when(envelope.payloadSha256()).thenReturn(payloadHash);var deployment=mock(TaskProfessionalRuntimeService.Deployment.class);when(deployment.value()).thenReturn(json.object().put("host_tool_version","host-tool-v1"));when(envelope.deployment()).thenReturn(deployment);
        when(registered.kind()).thenReturn("STEP_ENVELOPE");when(registered.payloadSha256()).thenReturn(payloadHash);when(registered.sessionId()).thenReturn(session);when(registered.actionId()).thenReturn(action);when(registered.runId()).thenReturn(run);when(registered.stepId()).thenReturn(step);when(registered.leaseGeneration()).thenReturn("1");when(registered.workerService()).thenReturn(service);when(registered.inputId()).thenReturn(inputId);when(registered.inputSha256()).thenReturn(inputHash);when(registered.inputReceiptSha256()).thenReturn(inputReceiptHash);when(registered.artifactId()).thenReturn(envelopeId);when(registered.receiptSha256()).thenReturn(hash('f'));when(registered.reference()).thenReturn(new ObjectRef("test/envelope","actual-envelope-version",payloadHash,"20","application/json"));
        for(var e:Map.of("created_by",actor,"tenant_id",tenant,"project_id",project,"principal_id",principal,"task_id",task,"schema_version","2.0.0","schema_ref",TaskProfessionalRuntimeService.ENVELOPE_REF,"classification","INTERNAL").entrySet())when(registered.pin(e.getKey())).thenReturn(e.getValue());
        add("run_step",Map.of("tenant_id",tenant,"project_id",project,"run_id",run,"step_id",step,"input_sha256",inputHash,"status","RUNNING","lease_generation",1L,"lease_owner",service,"lease_expires_at",now.plusMinutes(2)));
        var binding=new LinkedHashMap<String,Object>();binding.put("tenant_id",tenant);binding.put("project_id",project);binding.put("run_id",run);binding.put("step_id",step);binding.put("action_id",action);binding.put("input_sha256",inputHash);binding.put("lease_generation",1L);binding.put("kind","HOST_CONTROL");binding.put("state","BOUND");binding.put("arguments_sha256",TaskProfessionalRuntimeService.hostArguments(input));binding.put("tool_version","host-tool-v1");add("step_action_binding",binding);
    }

    @Test void runIntentUsesRegisteredReplayAndImmutableGraphWithoutSyntheticStepsOrRunningState()throws Exception {
        tx(()->{var first=journal.prepareRun(authority,graph);var repeated=journal.prepareRun(authority,graph);assertEquals(first.intentId(),repeated.intentId());assertEquals(first.sha256(),repeated.sha256());assertEquals(replayArtifact,t(first.content(),"replay_manifest_id"));assertEquals("actual-replay-version",t(first.content(),"replay_object_version"));assertEquals(graphId,t(first.content(),"graph_id"));first.requireUnchanged();return null;});
        assertEquals(1,rows("run_runtime_intent").size());assertEquals(3,writes.size());assertTrue(rows("step_runtime_attempt").isEmpty());assertTrue(rows("run_step").isEmpty());assertEquals("QUEUED",only("task_run").get("status"));
    }
    @Test void missing092RegistrationCannotBeReplacedByCallerHash()throws Exception {rows("task_run_replay_registration").clear();assertThrows(TenantAccessException.class,()->tx(()->journal.prepareRun(authority,graph)));assertTrue(writes.isEmpty());}
    @Test void missingReplayAttemptCannotBeRebuiltDuringRuntimePreparation()throws Exception {rows("task_run_replay_attempt").clear();assertThrows(TenantAccessException.class,()->tx(()->journal.prepareRun(authority,graph)));assertTrue(writes.isEmpty());}
    @Test void alteredReplayPhysicalVersionFailsBefore075Write()throws Exception {only("artifact").put("object_version","substitution");assertThrows(TenantAccessException.class,()->tx(()->journal.prepareRun(authority,graph)));assertTrue(writes.isEmpty());}
    @Test void missingOriginalReplayAuditFailsClosed()throws Exception {rows("audit_event").removeIf(r->"task.run.replay.registered".equals(r.get("action")));assertThrows(TenantAccessException.class,()->tx(()->journal.prepareRun(authority,graph)));assertTrue(writes.isEmpty());}
    @Test void changedGraphOrCommandCannotReplacePersistedRunIntent()throws Exception {prepareRun();when(graph.graphSha256()).thenReturn(hash('f'));assertThrows(TenantAccessException.class,()->tx(()->journal.prepareRun(authority,graph)));assertEquals(3,writes.size());}
    @Test void recoveryOfAbsentRunIntentNeverWritesIt()throws Exception {assertThrows(TenantAccessException.class,()->tx(()->journal.requireRun(authority,graph)));assertTrue(writes.isEmpty());}
    @Test void stepNeedsOriginalRunIntentAndNeverCreatesOneAtDispatch()throws Exception {leased();assertThrows(TenantAccessException.class,()->tx(()->journal.prepareStep(envelope,registered,dispatchCommand)));assertTrue(writes.isEmpty());}
    @Test void actualCommandLeaseEnvelopeAndInputArePreservedAcrossExactRecovery()throws Exception {
        prepareRun();leased();tx(()->{var first=journal.prepareStep(envelope,registered,dispatchCommand);assertEquals(dispatchCommand,first.dispatch().commandId());assertEquals(envelopeId,first.dispatch().stepEnvelopeArtifactId());assertEquals(inputHash,first.dispatch().inputSha256());assertEquals(first.dispatch(),journal.prepareStep(envelope,registered,dispatchCommand).dispatch());assertEquals(first.dispatch(),journal.requireStep(envelope,registered).dispatch());first.requireUnchanged();return null;});
        assertEquals(6,writes.size());assertEquals(1,rows("step_runtime_attempt").size());assertEquals("RUNNING",only("run_step").get("status"));
    }
    @Test void replacementSchedulerCommandCannotRetargetExistingAttempt()throws Exception {prepareStep();var e=assertThrows(TenantAccessException.class,()->tx(()->journal.prepareStep(envelope,registered,id(999))));assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,e.code());assertEquals(dispatchCommand,only("step_runtime_attempt").get("command_id"));assertEquals(6,writes.size());}
    @Test void reusedCommandCannotCreateAnotherGeneration()throws Exception {prepareRun();leased();add("step_runtime_attempt",Map.of("tenant_id",tenant,"project_id",project,"run_id",run,"step_id",id(999),"lease_generation",2L,"command_id",dispatchCommand));assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,assertThrows(TenantAccessException.class,()->tx(()->journal.prepareStep(envelope,registered,dispatchCommand))).code());assertEquals(3,writes.size());}
    @Test void expiredLeaseCannotDispatchOrRecover()throws Exception {prepareStep();now=now.plusMinutes(3);assertThrows(TenantAccessException.class,()->tx(()->journal.requireStep(envelope,registered)));assertEquals(6,writes.size());}
    @Test void differentWorkerCannotDispatch()throws Exception {prepareRun();leased();only("run_step").put("lease_owner","spiffe://test/other");assertThrows(TenantAccessException.class,()->tx(()->journal.prepareStep(envelope,registered,dispatchCommand)));assertEquals(3,writes.size());}
    @Test void modelActionCannotMasqueradeAsHostControl()throws Exception {prepareRun();leased();only("step_action_binding").put("kind","MODEL_CALL");assertThrows(TenantAccessException.class,()->tx(()->journal.prepareStep(envelope,registered,dispatchCommand)));assertEquals(3,writes.size());}
    @Test void metadataOnlyEnvelopeCannotCreateAttempt()throws Exception {prepareRun();leased();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(envelope).requireProductionReady();assertThrows(TenantAccessException.class,()->tx(()->journal.prepareStep(envelope,registered,dispatchCommand)));assertEquals(3,writes.size());}
    @Test void changedRegisteredInputCannotCreateAttempt()throws Exception {prepareRun();leased();when(registered.inputReceiptSha256()).thenReturn(hash('a'));assertThrows(TenantAccessException.class,()->tx(()->journal.prepareStep(envelope,registered,dispatchCommand)));assertEquals(3,writes.size());}
    @Test void revokedOriginalRegistrationPreventsDispatch()throws Exception {prepareRun();leased();doThrow(new TenantAccessException(ErrorCode.SCOPE_STALE)).when(registered).requireUnchanged();assertThrows(TenantAccessException.class,()->tx(()->journal.prepareStep(envelope,registered,dispatchCommand)));assertEquals(3,writes.size());}
    @Test void missingDispatchWitnessCannotBeRegeneratedOnRetry()throws Exception {prepareStep();rows("outbox_event").removeIf(r->"task.step.runtime.dispatched".equals(r.get("event_type")));assertThrows(TenantAccessException.class,()->tx(()->journal.prepareStep(envelope,registered,dispatchCommand)));assertEquals(6,writes.size());}
    @Test void unknownCanonicalFieldCannotBecomeRecoveryOriginal()throws Exception {prepareRun();var row=only("run_runtime_intent");var value=(ObjectNode)bootstrapSchema.read((String)row.get("canonical_intent"));value.put("future_step_id",id(999));row.put("canonical_intent",canonical(value));row.put("intent_sha256",digest(value));assertThrows(TenantAccessException.class,()->tx(()->journal.requireRun(authority,graph)));assertEquals(3,writes.size());}
    @Test void proofCannotEscapeItsTransactionAndConstructorsArePrivate()throws Exception {var proof=tx(()->journal.prepareRun(authority,graph));assertThrows(TenantAccessException.class,proof::intentId);assertEquals(0,TaskRuntimeJournal.RunIntent.class.getConstructors().length);assertEquals(0,TaskRuntimeJournal.Dispatch.class.getConstructors().length);}
    @Test void currentAuthorityRevocationPreventsRunWrite()throws Exception {doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(authority).requireUnchanged();assertThrows(TenantAccessException.class,()->tx(()->journal.prepareRun(authority,graph)));assertTrue(writes.isEmpty());}
    @Test void timestampJdbcRepresentationDoesNotChangeCanonicalIdentity()throws Exception {prepareRun();for(var table:tables.values())for(var row:table)for(var key:List.of("created_at","occurred_at"))if(row.get(key) instanceof LocalDateTime t)row.put(key,java.sql.Timestamp.valueOf(t));assertDoesNotThrow(()->tx(()->journal.requireRun(authority,graph)));}

    @Test void readonlyWorkerOriginalCannotCreateMissingAttempt()throws Exception {
        prepareRun();leased();var current=mock(FormalTaskNodeAuthority.Verified.class);when(current.controlAdmission()).thenReturn(admission);when(current.currentInput()).thenReturn(input);
        var binding=new ProfessionalRuntimeObservations.Binding(tenant,run,step,TaskHostControlBootstrap.actionId(tenant,run,step,BigInteger.ONE),1,inputHash,envelope.payloadSha256());
        assertThrows(TenantAccessException.class,()->tx(()->journal.requireExistingStep(current,binding)));assertEquals(3,writes.size());assertTrue(rows("step_runtime_attempt").isEmpty());
    }
    @Test void readonlyWorkerOriginalCannotUseRevokedCurrentNodeAuthority()throws Exception {
        var current=mock(FormalTaskNodeAuthority.Verified.class);doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(current).requireUnchanged();
        var binding=new ProfessionalRuntimeObservations.Binding(tenant,run,step,TaskHostControlBootstrap.actionId(tenant,run,step,BigInteger.ONE),1,inputHash,hash('a'));
        assertThrows(TenantAccessException.class,()->tx(()->journal.requireExistingStep(current,binding)));assertTrue(writes.isEmpty());
    }
    @Test void readonlyWorkerOriginalRejectsSubstitutedInputBeforeReadingDispatch()throws Exception {
        prepareRun();leased();var current=mock(FormalTaskNodeAuthority.Verified.class);when(current.controlAdmission()).thenReturn(admission);when(current.currentInput()).thenReturn(input);
        var binding=new ProfessionalRuntimeObservations.Binding(tenant,run,step,TaskHostControlBootstrap.actionId(tenant,run,step,BigInteger.ONE),1,hash('f'),envelope.payloadSha256());
        assertThrows(TenantAccessException.class,()->tx(()->journal.requireExistingStep(current,binding)));assertEquals(3,writes.size());assertTrue(rows("step_runtime_attempt").isEmpty());
    }

    @Test void historicalReadNeedsNoLiveNodeLeaseOrFutureGraphAndCannotWrite()throws Exception {
        prepareStep();var expected=tx(()->journal.requireStep(envelope,registered).dispatch());var recovery=recovery();
        only("task_run").put("status","SUCCEEDED");only("task_run").put("ended_at",now);rows("run_step").clear();rows("step_action_binding").clear();now=now.plusDays(1);reset(graphs,graph,input,envelope,registered);
        assertEquals(expected,tx(()->journal.requireHistoricalStep(recovery,expected)));assertEquals(expected,tx(()->journal.readHistoricalStep(recovery,run,step,1)));assertEquals(6,writes.size());verifyNoInteractions(graphs,graph,input,envelope,registered);
    }
    @Test void historicalReadRejectsExecutionAdmissionInsteadOfWideningAuthority()throws Exception {
        prepareStep();assertThrows(TenantAccessException.class,()->tx(()->journal.readHistoricalStep(admission,run,step,1)));assertEquals(6,writes.size());
    }
    @Test void historicalReadRejectsDifferentRunBeforeSelectingOriginal()throws Exception {
        prepareStep();var recovery=recovery();assertThrows(TenantAccessException.class,()->tx(()->journal.readHistoricalStep(recovery,id(999),step,1)));assertEquals(6,writes.size());
    }
    @Test void historicalReadRequiresOriginalRunAndDispatchWitnesses()throws Exception {
        prepareStep();var recovery=recovery();rows("outbox_event").removeIf(r->"task.step.runtime.dispatched".equals(r.get("event_type")));assertThrows(TenantAccessException.class,()->tx(()->journal.readHistoricalStep(recovery,run,step,1)));assertEquals(6,writes.size());
    }
    @Test void historicalReadRejectsChangedOriginalReplayAndMissingAttempt()throws Exception {
        prepareStep();var recovery=recovery();var row=only("run_runtime_intent");row.put("replay_sha256",hash('f'));assertThrows(TenantAccessException.class,()->tx(()->journal.readHistoricalStep(recovery,run,step,1)));row.put("replay_sha256",replayHash);rows("step_runtime_attempt").clear();assertThrows(TenantAccessException.class,()->tx(()->journal.readHistoricalStep(recovery,run,step,1)));assertEquals(6,writes.size());
    }
    @Test void historicalReadRejectsSubstitutedDispatchAndRevokedRecovery()throws Exception {
        prepareStep();var recovery=recovery();var expected=tx(()->journal.requireStep(envelope,registered).dispatch());var changed=new com.ruoyi.medical.workflow.WorkflowProtocol.Dispatch(run,step,action,id(999),inputHash,1,envelopeId,expected.stepEnvelopeSha256());
        assertThrows(TenantAccessException.class,()->tx(()->journal.requireHistoricalStep(recovery,changed)));var recoveryProof=recovery.recoveryProof();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(recoveryProof).requireActive();assertThrows(TenantAccessException.class,()->tx(()->journal.readHistoricalStep(recovery,run,step,1)));assertEquals(6,writes.size());
    }
    EnterpriseAccess.Admission recovery(){
        var context=admission.context();
        var result=mock(EnterpriseAccess.Admission.class);var proof=mock(FormalRunRecoveryAccess.Proof.class);when(result.recoveryWorker()).thenReturn(true);when(result.recoveryProof()).thenReturn(proof);when(result.actorId()).thenReturn(actor);when(result.context()).thenReturn(context);
        when(proof.identity()).thenReturn(new FormalRunRecoveryAccess.RecoveryIdentity("FORMAL_RUN_LAUNCH_RECOVERY",tenant,project,run,task,principal,actor,id(991),new com.ruoyi.medical.identity.AuthenticatedIdentityBinding.View("local",actor,null,null),service,hash('a'),"trust-v1",replayHash,t(command.required("command"),"command_id"),t(command.required("command"),"command_sha256")));return result;
    }

    void prepareRun()throws Exception {tx(()->journal.prepareRun(authority,graph));}
    void prepareStep()throws Exception {prepareRun();leased();tx(()->journal.prepareStep(envelope,registered,dispatchCommand));}
    <T>T tx(Checked<T> work)throws Exception {scope=TenantScope.open(new TenantScope.Identity(tenant,actor,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE));TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.bindResource(resourceKey,resource);try{return work.get();}finally{TransactionSynchronizationManager.unbindResource(resourceKey);TransactionSynchronizationManager.clear();scope.close();scope=null;}}
    interface Checked<T>{T get()throws Exception;}
    Map<String,Object> meta(LocalDateTime time,String manifest){return new LinkedHashMap<>(Map.of("tenant_id",tenant,"project_id",project,"created_by",actor,"created_at",time,"classification","INTERNAL","schema_version","1.0.0","schema_manifest_sha256",manifest));}
    void sourceJournal(Map<String,Object> original,String action,String request,String event,String aggregate,JsonNode value,String manifest)throws Exception {var a=meta((LocalDateTime)original.get("created_at"),manifest);a.put("audit_id",UUID.randomUUID().toString());a.put("subject_id",actor);a.put("action",action);a.put("request_id",request);a.put("trace_id",run);a.put("result","SUCCESS");a.put("resource_ref",canonical(value));add("audit_event",a);add("outbox_event",Map.of("tenant_id",tenant,"event_id",event,"aggregate_id",aggregate,"aggregate_revision",1,"event_type",action,"payload",canonical(value),"occurred_at",original.get("created_at")));}
    void add(String table,Map<String,Object> row){rows(table).add(new LinkedHashMap<>(row));}List<Map<String,Object>> rows(String table){return tables.computeIfAbsent(table,k->new ArrayList<>());}Map<String,Object> only(String table){assertEquals(1,rows(table).size(),table);return rows(table).get(0);}
    List<Map<String,Object>> select(String sql,Object[] args){var matcher=Pattern.compile("FROM ([a-z_]+)").matcher(sql);if(!matcher.find())throw new AssertionError(sql);var found=new ArrayList<Map<String,Object>>();for(var row:rows(matcher.group(1))){var condition=Pattern.compile("([a-z_]+)=\\?").matcher(sql);int index=0;boolean equal=true;while(condition.find()){Object expected=args[index++],actual=row.get(condition.group(1));if(!Objects.equals(String.valueOf(expected),String.valueOf(actual))){equal=false;break;}}if(equal)found.add(new LinkedHashMap<>(row));}return found;}
    int insert(String sql,Object[] values){writes.add(sql);var matcher=Pattern.compile("INSERT INTO ([a-z_]+)\\(([^)]+)\\) VALUES\\(([^)]+)\\)").matcher(sql);if(!matcher.matches())throw new AssertionError(sql);String[] columns=matcher.group(2).split(",");assertEquals(columns.length,values.length);var row=new LinkedHashMap<String,Object>();for(int n=0;n<columns.length;n++)row.put(columns[n],values[n]);add(matcher.group(1),row);return 1;}
    static Object[] args(Object[] all){var values=Arrays.copyOfRange(all,1,all.length);return values.length==1&&values[0] instanceof Object[] array?array:values;}
    String canonical(JsonNode n)throws Exception{return bootstrapSchema.canonical(n);}String digest(JsonNode n)throws Exception{return bootstrapSchema.hash(n);}static String t(JsonNode n,String f){return n.required(f).textValue();}static String iso(LocalDateTime time){return time.toInstant(ZoneOffset.UTC).toString();}static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}static String hash(char c){return String.valueOf(c).repeat(64);}
}
