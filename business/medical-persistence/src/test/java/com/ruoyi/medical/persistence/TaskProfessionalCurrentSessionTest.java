package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real session verifier and formal admission bridge against explicit SQL/token/input doubles.
 * The SQL fixture models the same-session context/action join, not a live database, mTLS,
 * source-rights decision, process, or isolation acceptance. No production proof is fabricated. */
class TaskProfessionalCurrentSessionTest {
    Fixture f;
    @BeforeEach void setup()throws Exception {f=new Fixture();TransactionSynchronizationManager.setActualTransactionActive(true);}
    @AfterEach void cleanup(){TransactionSynchronizationManager.clear();}

    @Test void exactLaterContextSelectsOnlyItsPersistedRowAndRechecksSamePin()throws Exception {
        var session=f.exact();session.requireUnchanged();
        assertEquals(f.session,session.sessionId());assertEquals(f.binding,session.binding());
        var sessionQueries=f.queries.stream().filter(q->q.sql().contains("FROM professional_runtime_session")).toList();
        assertEquals(2,sessionQueries.size());
        for(var query:sessionQueries)assertArrayEquals(new Object[]{f.tenant,f.project,f.run,f.step,f.session,"1",f.inputHash,f.worker,f.actor},query.args());
        var queries=f.contextQueries();assertEquals(2,queries.size());
        for(var query:queries){
            assertTrue(query.sql().contains("c.tenant_id=? AND c.project_id=? AND c.context_manifest_id=?"));
            assertTrue(query.sql().contains("v.version_id=c.context_manifest_id AND v.owner_project_id=c.project_id"));
            assertTrue(query.sql().contains("a.kind='MODEL_CALL' AND a.lease_generation=? AND a.input_sha256=?"));
            assertTrue(query.sql().endsWith("LIMIT 2 FOR UPDATE"));
            assertArrayEquals(new Object[]{f.tenant,f.project,f.currentContext,f.session,f.run,f.step,action(f.currentContext),"1",f.inputHash},query.args());
        }
        verify(f.input,atLeast(2)).requireUnchanged();verify(f.proof,atLeast(2)).requireActive();
        verify(f.db,never()).update(anyString(),any(Object[].class));
    }
    @Test void originalFourArgumentModelCallRemainsValidWithOneContext()throws Exception {
        f.contexts.remove(0);var session=f.runtime.requireCurrentSession(f.admission,f.input,f.binding,f.session);session.requireUnchanged();
        for(var query:f.contextQueries()){assertFalse(query.sql().contains("AND c.context_manifest_id=?"));assertEquals(8,query.args().length);}
    }
    @Test void legacyAmbiguousSessionCannotSilentlyChooseAnOldOrNewContext() {
        assertThrows(TenantAccessException.class,()->f.runtime.requireCurrentSession(f.admission,f.input,f.binding,f.session));
    }
    @Test void originalHostSessionPathStillNeedsNoModelContext()throws Exception {
        f.identity(f.hostAction);f.contexts.clear();
        f.runtime.requireSession(f.admission,f.input,f.session).requireUnchanged();
        f.runtime.requireCurrentSession(f.admission,f.input,f.binding,f.session).requireUnchanged();
        assertTrue(f.contextQueries().isEmpty());
    }
    @Test void hostActionCannotPretendToBeAContextBoundModelCall() {
        f.identity(f.hostAction);assertThrows(TenantAccessException.class,f::exact);assertTrue(f.contextQueries().isEmpty());
    }
    @Test void nullOrMalformedContextAndAbsentOriginalBindingFailBeforeSql() {
        f.queries.clear();
        for(String context:Arrays.asList(null,"","not-a-context"))
            assertThrows(RuntimeException.class,()->f.runtime.requireCurrentSession(f.admission,f.input,f.binding,f.session,context));
        assertThrows(TenantAccessException.class,()->f.runtime.requireCurrentSession(f.admission,f.input,null,f.session,f.currentContext));
        assertTrue(f.queries.isEmpty());
    }
    @Test void missingRequestedContextDoesNotFallBackToOtherSessionContext() {
        f.contexts.remove(1);assertThrows(TenantAccessException.class,f::exact);
        assertEquals(f.currentContext,f.contextQueries().get(0).args()[2]);
    }
    @Test void oldContextCannotUseLaterContextActionEvenWithMatchingRequestBytes()throws Exception {
        f.contexts.get(0).put("source_manifest",f.contexts.get(1).get("source_manifest"));
        assertThrows(TenantAccessException.class,()->f.runtime.requireCurrentSession(f.admission,f.input,f.binding,f.session,f.oldContext));
    }
    @Test void returnedWrongContextIsDeniedEvenIfSqlAdapterIgnoresExactPredicate() {
        f.forcedContext=f.oldContext;assertThrows(TenantAccessException.class,f::exact);
    }
    @Test void duplicateExactContextRowsAreNeverReducedToFirstMatch() {
        f.contexts.add(new LinkedHashMap<>(f.contexts.get(1)));assertThrows(TenantAccessException.class,f::exact);
    }
    @Test void recheckNeverSwitchesToRemainingSiblingAfterOriginalDisappears()throws Exception {
        var session=f.exact();f.contexts.remove(1);assertThrows(TenantAccessException.class,session::requireUnchanged);
        assertEquals(f.currentContext,f.contextQueries().get(1).args()[2]);
    }
    @Test void unrelatedNewContextDoesNotInvalidateExactOriginalRecheck()throws Exception {
        var session=f.exact();f.contexts.add(f.context(id(90),3));session.requireUnchanged();
        assertEquals(f.currentContext,f.contextQueries().get(1).args()[2]);
    }
    @Test void sameIdButChangedContextEvidenceFailsRecheck()throws Exception {
        var session=f.exact();var row=f.contexts.get(1);var source=(ObjectNode)f.json.readTree((String)row.get("source_manifest"));
        source.putObject("dependencies").put("different_history_sha256",hash('f'));row.put("source_manifest",source.toString());
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,session::requireUnchanged).code());
    }
    @Test void originalHostBindingCannotBeReplacedByOtherSessionBinding() {
        var wrong=f.binding.deepCopy().put("action_id",id(91));
        assertThrows(TenantAccessException.class,()->f.runtime.requireCurrentSession(f.admission,f.input,wrong,f.session,f.currentContext));
        assertTrue(f.contextQueries().isEmpty());
    }
    @Test void immutableRequestDigestRegistryAndModelVersionRemainRequired()throws Exception {
        for(String field:List.of("arguments_sha256","tool_version","registered_sha256")){
            var original=f.joinOverrides.put(field,field.equals("tool_version")?id(91):hash('f'));
            assertThrows(TenantAccessException.class,f::exact,field);if(original==null)f.joinOverrides.remove(field);else f.joinOverrides.put(field,original);
        }
    }
    @Test void allContextNodePinsStayBoundToAdmissionInputAndOriginalSession()throws Exception {
        for(String field:List.of("tenant_id","project_id","run_id","step_id","host_action_id","session_id","worker_service_uri","node_input_artifact_id","node_input_sha256","node_input_receipt_sha256","lease_generation","principal_id","supervisor_receipt_id","supervisor_receipt_sha256")){
            var row=f.contexts.get(1);String original=(String)row.get("source_manifest");var source=(ObjectNode)f.json.readTree(original);
            ((ObjectNode)source.required("request").required("node")).put(field,field.endsWith("sha256")?hash('f'):field.equals("lease_generation")?"2":field.equals("worker_service_uri")?"spiffe://other/worker":id(91));
            row.put("source_manifest",source.toString());f.joinOverrides.put("arguments_sha256",digest(source.required("request")));
            assertThrows(TenantAccessException.class,f::exact,field);row.put("source_manifest",original);f.joinOverrides.clear();
        }
    }
    @Test void sameHashInputFromDifferentScopeCannotBeMixedWithAdmission() {
        when(f.input.runId()).thenReturn(id(91));assertThrows(TenantAccessException.class,f::exact);assertTrue(f.queries.isEmpty());
    }
    @Test void wrongSessionOwnerLeaseInputOrActionFailsCurrentSqlScope()throws Exception {
        for(String field:List.of("tenant_id","project_id","run_id","step_id","session_id","lease_generation","input_sha256","service_uri","created_by")){
            Object original=f.sessionRow.put(field,"other");assertThrows(TenantAccessException.class,f::exact,field);f.sessionRow.put(field,original);
        }
        f.identity(id(91));assertThrows(TenantAccessException.class,f::exact);
    }
    @Test void sessionMutationRevokedAdmissionAndInputAllFailBeforeProofReuse()throws Exception {
        var session=f.exact();f.sessionRow.put("admission_state","CLOSED");assertThrows(TenantAccessException.class,session::requireUnchanged);
        f.sessionRow.put("admission_state","OPEN");doThrow(new TenantAccessException(ErrorCode.SCOPE_STALE)).when(f.input).requireUnchanged();assertThrows(TenantAccessException.class,session::requireUnchanged);
        doNothing().when(f.input).requireUnchanged();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.proof).requireActive();assertThrows(TenantAccessException.class,session::requireUnchanged);
    }
    @Test void changedAdmissionIdentityCannotReuseAnOtherwiseIdenticalSessionRow()throws Exception {
        var session=f.exact();f.identity(id(91));assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,session::requireUnchanged).code());
    }
    @Test void missingOrAlteredOpeningJournalsCannotCreateSessionProof()throws Exception {
        f.missingAudit=true;assertThrows(TenantAccessException.class,f::exact);f.missingAudit=false;
        f.eventPayload="{}";assertThrows(TenantAccessException.class,f::exact);
    }
    @Test void transactionIsRequiredBeforeAnySessionSql() {
        TransactionSynchronizationManager.setActualTransactionActive(false);assertThrows(TenantAccessException.class,f::exact);assertTrue(f.queries.isEmpty());
    }
    @Test void realFormalAdmissionChecksCurrentLeaseAndDoesNotEscapeCallback()throws Exception {
        try(var auth=new CurrentAdmission(f)){
            var escaped=new AtomicReference<TaskProfessionalRuntimeService.Session>();
            assertEquals("verified",auth.execute(a->{var session=f.exact(a);session.requireUnchanged();escaped.set(session);return "verified";}));
            assertThrows(TenantAccessException.class,escaped.get()::requireUnchanged);
            assertThrows(TenantAccessException.class,escaped.get()::sessionId);
        }
    }
    @Test void realExpiredLeaseDeniesBeforeSessionReadAndExpiryDuringReadPreventsReturn()throws Exception {
        try(var auth=new CurrentAdmission(f)){
            when(auth.leased.expires()).thenReturn(auth.now);assertThrows(TenantAccessException.class,()->auth.execute(f::exact));assertTrue(f.queries.isEmpty());
            when(auth.leased.expires()).thenReturn(auth.now.plusMinutes(1));
            assertThrows(TenantAccessException.class,()->auth.execute(a->{var session=f.exact(a);when(auth.store.now()).thenReturn(auth.now.plusMinutes(1));return session;}));
        }
    }
    @Test void realChangedLeaseOwnerActorOrGenerationDeniesBeforeSessionRead()throws Exception {
        try(var auth=new CurrentAdmission(f)){
            when(auth.leased.owner()).thenReturn("spiffe://other/worker");assertThrows(TenantAccessException.class,()->auth.execute(f::exact));
            when(auth.leased.owner()).thenReturn(f.worker);when(auth.leased.generation()).thenReturn(BigInteger.TWO);assertThrows(TenantAccessException.class,()->auth.execute(f::exact));
            when(auth.leased.generation()).thenReturn(BigInteger.ONE);when(auth.permissions.currentBinding(f.actor)).thenReturn(List.of());assertThrows(TenantAccessException.class,()->auth.execute(f::exact));assertTrue(f.queries.isEmpty());
        }
    }

    private record Query(String sql,Object[] args) { }
    private static final class Fixture {
        final String tenant=id(1),project=id(2),actor=id(3),run=id(4),principal=id(5),step=id(6),task=id(7),hostAction=id(8),session=id(9),inputId=id(10),modelVersion=id(11),supervisor=id(12),oldContext=id(21),currentContext=id(22);
        final String worker="spiffe://test/worker",inputHash=hash('a'),receiptHash=hash('b'),envelopeHash=hash('c'),runtimeHash=hash('d'),supervisionHash=hash('e');
        final com.fasterxml.jackson.databind.json.JsonMapper json=new com.fasterxml.jackson.databind.json.JsonMapper();
        final JdbcTemplate db=mock(JdbcTemplate.class);final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);final FormalWorkerAccess.Proof proof=mock(FormalWorkerAccess.Proof.class);
        final TaskNodeInputAuthority.Verified input=mock(TaskNodeInputAuthority.Verified.class);final TaskProfessionalRuntimeService runtime;
        final ObjectNode binding,start;final Map<String,Object> sessionRow=new LinkedHashMap<>(),hostRow=new LinkedHashMap<>(),modelRow=new LinkedHashMap<>();
        final List<Map<String,Object>> contexts=new ArrayList<>();final List<Query> queries=new ArrayList<>();final Map<String,Object> joinOverrides=new HashMap<>();
        String forcedContext,eventPayload;boolean missingAudit;
        Fixture()throws Exception {
            when(admission.formalWorker()).thenReturn(true);when(admission.formalProof()).thenReturn(proof);identity(action(currentContext));
            when(input.tenantId()).thenReturn(tenant);when(input.projectId()).thenReturn(project);when(input.runId()).thenReturn(run);when(input.stepId()).thenReturn(step);when(input.taskId()).thenReturn(task);when(input.inputId()).thenReturn(inputId);when(input.inputSha256()).thenReturn(inputHash);when(input.receiptSha256()).thenReturn(receiptHash);
            var deployment=deployment();runtime=new TaskProfessionalRuntimeService(db,mock(PermissionSchema.class),mock(TaskNodeInputAuthority.class),deployment,mock(ServiceCertificateIdentity.class),mock(TaskNodeProducerAuthority.class));
            binding=json.createObjectNode().put("tenant_id",tenant).put("run_id",run).put("step_id",step).put("action_id",hostAction).put("lease_generation",1).put("input_sha256",inputHash).put("step_envelope_sha256",envelopeHash);
            start=json.createObjectNode().put("session_id",session).put("purpose","TASK").put("node_kind","N02").put("state","START").put("deployment_sha256",deployment.sha256()).put("runtime_manifest_sha256",runtimeHash).put("isolation_receipt_id",supervisor).put("supervision_sha256",supervisionHash);start.set("binding",binding);
            sessionRow.putAll(Map.of("tenant_id",tenant,"project_id",project,"run_id",run,"step_id",step,"session_id",session,"lease_generation","1","input_sha256",inputHash,"service_uri",worker,"created_by",actor,"action_id",hostAction));
            sessionRow.putAll(Map.of("envelope_sha256",envelopeHash,"canonical_start",start.toString(),"start_sha256",digest(start),"admission_state","OPEN"));eventPayload=start.toString();
            hostRow.putAll(Map.of("tenant_id",tenant,"project_id",project,"run_id",run,"step_id",step,"action_id",hostAction,"input_sha256",inputHash,"lease_generation","1","arguments_sha256",TaskProfessionalRuntimeService.hostArguments(input),"tool_version","host-v1"));
            contexts.add(context(oldContext,1));contexts.add(context(currentContext,2));modelRow.putAll(hostRow);modelRow.put("action_id",action(currentContext));modelRow.put("arguments_sha256",digest(json.readTree((String)contexts.get(1).get("source_manifest")).required("request")));modelRow.put("tool_version",modelVersion);
            when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(call->select(call.getArgument(0),arguments(call.getArguments())));
        }
        void identity(String action){when(proof.identity()).thenReturn(new FormalWorkerAccess.Identity(tenant,project,run,principal,actor,"TASK",step,action,"1",worker,"{}","{}",List.of("model.send"),null));}
        TaskProfessionalRuntimeService.Session exact()throws Exception{return exact(admission);}
        TaskProfessionalRuntimeService.Session exact(EnterpriseAccess.Admission a)throws Exception{return runtime.requireCurrentSession(a,input,binding,session,currentContext);}
        List<Query> contextQueries(){return queries.stream().filter(q->q.sql().contains("FROM context_manifest")).toList();}
        Map<String,Object> context(String id,int ordinal)throws Exception {
            var node=json.createObjectNode().put("tenant_id",tenant).put("project_id",project).put("run_id",run).put("step_id",step).put("host_action_id",hostAction).put("session_id",session).put("worker_service_uri",worker).put("node_input_artifact_id",inputId).put("node_input_sha256",inputHash).put("node_input_receipt_sha256",receiptHash).put("lease_generation","1").put("principal_id",principal).put("supervisor_receipt_id",supervisor).put("supervisor_receipt_sha256",supervisionHash);
            var request=json.createObjectNode().put("model_config_revision",modelVersion);request.set("node",node);
            var source=json.createObjectNode().put("execution_kind","TASK");source.set("request",request);source.putObject("dependencies").put("model_request_ordinal",ordinal);
            return new LinkedHashMap<>(Map.of("context_manifest_id",id,"source_manifest",source.toString(),"content_sha256",digest(source)));
        }
        List<Map<String,Object>> select(String sql,Object[] args)throws Exception {
            queries.add(new Query(sql,args));
            if(sql.contains("FROM professional_runtime_session"))return matches(sql,sessionRow,args)?List.of(new LinkedHashMap<>(sessionRow)):List.of();
            if(sql.contains("FROM step_action_binding")){assertTrue(sql.contains("kind='HOST_CONTROL'"));assertTrue(sql.contains("invocation_id IS NULL"));return matches(sql,hostRow,args)?List.of(new LinkedHashMap<>(hostRow)):List.of();}
            if(sql.contains("FROM context_manifest")){
                boolean exact=sql.contains("AND c.context_manifest_id=?");int n=0;
                String tenantPin=(String)args[n++],projectPin=(String)args[n++],contextPin=exact?(String)args[n++]:null,sessionPin=(String)args[n++];
                Object[] actionPins=Arrays.copyOfRange(args,n,args.length);assertEquals(5,actionPins.length);
                if(!Arrays.equals(new Object[]{run,step,modelRow.get("action_id"),"1",inputHash},actionPins))return List.of();
                var result=new ArrayList<Map<String,Object>>();
                for(var context:contexts){var source=json.readTree((String)context.get("source_manifest"));var node=source.required("request").required("node");
                    if(!tenant.equals(tenantPin)||!project.equals(projectPin)||!sessionPin.equals(node.required("session_id").asText())||!run.equals(node.required("run_id").asText())||!step.equals(node.required("step_id").asText())||!"TASK".equals(source.required("execution_kind").asText()))continue;
                    if(forcedContext!=null?!forcedContext.equals(context.get("context_manifest_id")):exact&&!contextPin.equals(context.get("context_manifest_id")))continue;
                    var joined=new LinkedHashMap<>(context);joined.put("arguments_sha256",modelRow.get("arguments_sha256"));joined.put("tool_version",modelRow.get("tool_version"));joined.put("registered_sha256",context.get("content_sha256"));joined.putAll(joinOverrides);result.add(joined);
                }return result;
            }
            if(sql.contains("FROM audit_event"))return missingAudit?List.of():List.of(Map.of("resource_ref",start.toString()));
            if(sql.contains("FROM outbox_event"))return List.of(Map.of("payload",eventPayload));
            throw new AssertionError(sql);
        }
        TaskProfessionalRuntimeService.Deployment deployment()throws Exception {
            var d=json.createObjectNode().put("schema_version","1.0.0").put("deployment_revision",id(30)).put("worker_service_uri",worker).put("supervisor_endpoint","https://supervisor.test").put("supervisor_service_uri","spiffe://test/supervisor").put("supervisor_certificate_sha256",hash('f')).put("supervisor_trust_revision",id(31)).put("runtime_manifest_sha256",runtimeHash).put("host_tool_version","host-v1").put("jobs_root","/fixture/jobs").put("maximum_object_bytes",16777216).put("token_lifetime_seconds",60).put("supervisor_gate_lifetime_seconds",2).put("role_name","Researcher");
            d.putObject("role_version").put("version_id",id(32)).put("content_sha256",hash('f'));d.putObject("profile_artifact").put("artifact_id",id(33)).put("sha256",hash('f')).put("size_bytes","10").put("media_type","application/json");
            d.putObject("sdk").put("model","fixture-model").put("reasoning_effort","none").put("max_tokens",10).put("initialize_timeout_seconds",10).put("request_timeout_seconds",10).put("shutdown_timeout_seconds",10).put("dsh_bin","/fixture/bin/dsh").put("sdk_version","1.0.0").put("runtime_version","1.0.0");
            d.putArray("profile_files").addObject().put("source","/fixture/profiles/profile.json").put("relative","profiles/profile.json").put("sha256",hash('f'));
            var inventory=d.putObject("expected_inventory");inventory.putArray("plugin_names").add("fixture-plugin");inventory.putObject("tool_schema_hashes").put("fixture-tool",hash('f'));inventory.putArray("providers").addObject().put("id","enterprise-model").put("name","企业模型接口");inventory.putArray("models").add("fixture-model");inventory.put("model_stream_schema_ref","urn:fixture:model-stream");
            return new TaskProfessionalRuntimeService.Deployment(d,digest(d));
        }
    }
    @FunctionalInterface private interface AdmittedOperation<T>{T apply(EnterpriseAccess.Admission admission)throws Exception;}
    private static final class CurrentAdmission implements AutoCloseable {
        final Fixture f;final RunTokenAuthorityStore store=mock(RunTokenAuthorityStore.class);final RunTokenAuthority tokens=mock(RunTokenAuthority.class);final RunTokenAuthority.Admission token=mock(RunTokenAuthority.Admission.class);final PermissionMapper permissions=mock(PermissionMapper.class);final RunTokenAuthoritySchema schema=new RunTokenAuthoritySchema();
        final RunTokenAuthorityStore.Run persisted=mock(RunTokenAuthorityStore.Run.class);final RunTokenAuthorityStore.Step leased=mock(RunTokenAuthorityStore.Step.class);final HttpServletRequest http=mock(HttpServletRequest.class);final FormalWorkerAccess access;
        final LocalDateTime now=LocalDateTime.of(2026,10,20,12,0);final TenantScope scope;final Object transactionKey=new Object(),transactionIdentity=new Object();final RunTokenAuthority.ActionRequest request;
        CurrentAdmission(Fixture f)throws Exception {
            this.f=f;scope=TenantScope.open(new TenantScope.Identity(f.tenant,f.actor,BigInteger.ZERO,BigInteger.ONE,BigInteger.ONE));TransactionSynchronizationManager.bindResource(transactionKey,transactionIdentity);scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
            var resource=new PermissionProtocol.Scope(f.tenant,f.project,"CONFIGURATION",id(40));request=new RunTokenAuthority.ActionRequest(f.run,f.step,action(f.currentContext),BigInteger.ONE,f.inputHash,(String)f.modelRow.get("arguments_sha256"),"MODEL_CALL",f.modelVersion,resource,"model.send","DOCUMENT_BODY");
            access=new FormalWorkerAccess(mock(JdbcTemplate.class),store,tokens,schema,permissions);
            when(tokens.execute(eq(http),eq("fixture-token"),eq(request),any())).thenAnswer(call->((RunTokenAuthority.Operation<?>)call.getArgument(3)).apply(token));
            when(token.tenantId()).thenReturn(f.tenant);when(token.projectId()).thenReturn(f.project);when(token.initiatorId()).thenReturn(f.actor);when(token.principalId()).thenReturn(f.principal);when(token.runId()).thenReturn(f.run);when(token.stepId()).thenReturn(f.step);when(token.actionId()).thenReturn(action(f.currentContext));when(token.leaseGeneration()).thenReturn(BigInteger.ONE);when(token.authEpoch()).thenReturn(BigInteger.ONE);when(token.governanceEpoch()).thenReturn(BigInteger.ONE);when(token.delegatedActions()).thenReturn(List.of("model.send"));when(token.serviceUri()).thenReturn(f.worker);
            when(store.run(f.run,f.actor)).thenReturn(persisted);when(persisted.tenant()).thenReturn(f.tenant);when(persisted.project()).thenReturn(f.project);when(persisted.id()).thenReturn(f.run);when(persisted.actor()).thenReturn(f.actor);when(persisted.executionActor()).thenReturn(f.actor);when(persisted.principal()).thenReturn(f.principal);when(persisted.principalStatus()).thenReturn("ACTIVE");when(persisted.kind()).thenReturn("TASK");when(persisted.task()).thenReturn(f.task);when(persisted.status()).thenReturn("RUNNING");
            var manifest=schema.object().put("schema_version","1.0.0").put("tenant_id",f.tenant).put("project_id",f.project).put("initiator_id",f.actor).put("run_id",f.run).put("execution_kind","TASK");manifest.putArray("resources").add(schema.tree(resource));
            var capabilities=manifest.deepCopy();capabilities.remove(List.of("execution_kind","resources"));capabilities.put("principal_id",f.principal).put("scope_sha256",schema.sha256(manifest));capabilities.putArray("services").addObject().put("service_uri",f.worker).put("audience","medical-runtime");capabilities.putArray("permissions").addObject().put("action","model.send").put("purpose","DOCUMENT_BODY").set("scope",schema.tree(resource));
            when(token.delegatedPermissions()).thenReturn(schema.permissions(capabilities));when(persisted.scope()).thenReturn(schema.canonical(manifest));when(persisted.capabilities()).thenReturn(schema.canonical(capabilities));when(store.step(persisted,f.step)).thenReturn(leased);when(leased.status()).thenReturn("RUNNING");when(leased.generation()).thenReturn(BigInteger.ONE);when(leased.owner()).thenReturn(f.worker);when(leased.expires()).thenReturn(now.plusMinutes(1));when(store.now()).thenReturn(now);
            when(permissions.currentBinding(f.actor)).thenReturn(List.of(new PermissionProtocol.BindingRow(f.actor,null,"local",null,null,f.actor,"ACTIVE")));
        }
        <T>T execute(AdmittedOperation<T> operation)throws Exception{return access.execute(http,"fixture-token",request,a->org.springframework.util.function.ThrowingSupplier.of(()->operation.apply(a)).get());}
        @Override public void close(){scope.finishTransaction();TransactionSynchronizationManager.unbindResourceIfPossible(transactionKey);scope.close();}
    }
    private static boolean matches(String sql,Map<String,Object> row,Object[] args){
        // SQL identifiers include digits (input_sha256, arguments_sha256). Always consume
        // every placeholder, even after a mismatch, so a broken fixture cannot mask a test.
        var fields=Pattern.compile("([a-z_][a-z_0-9]*)=\\?").matcher(sql.substring(sql.indexOf(" WHERE ")));
        int n=0;boolean matched=true;
        while(fields.find()){assertTrue(n<args.length,"unexpected SQL placeholder: "+sql);matched&=Objects.equals(row.get(fields.group(1)),args[n++]);}
        assertEquals(args.length,n,"unmatched SQL parameter: "+sql);return matched;
    }
    private static Object[] arguments(Object[] args){var values=Arrays.copyOfRange(args,1,args.length);return values.length==1&&values[0] instanceof Object[] array?array:values;}
    private static String id(int n){return String.format("10000000-0000-4000-8000-%012d",n);}
    private static String hash(char c){return String.valueOf(c).repeat(64);}
    private static String action(String context){return UUID.nameUUIDFromBytes(("TASK_C07_MODEL_CALL:"+context).getBytes(StandardCharsets.UTF_8)).toString();}
    private static String digest(Object value)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}
}
