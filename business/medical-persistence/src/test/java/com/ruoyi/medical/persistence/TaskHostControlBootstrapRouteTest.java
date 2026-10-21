package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.workflow.WorkflowProtocol;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real worker bootstrap routing with explicit mTLS, current-node, journal and JDBC
 * doubles. No real token issuance, deployed certificate, or isolation claim. */
class TaskHostControlBootstrapRouteTest {
    final JdbcTemplate db=mock(JdbcTemplate.class);final FormalTaskNodeAuthority nodes=mock(FormalTaskNodeAuthority.class);
    final FormalTaskNodeAuthority.Verified node=mock(FormalTaskNodeAuthority.Verified.class);final RunTokenAuthority tokens=mock(RunTokenAuthority.class);
    final TaskProfessionalHostAccess hosts=mock(TaskProfessionalHostAccess.class);final TaskProfessionalHostAccess.Handle handle=mock(TaskProfessionalHostAccess.Handle.class);
    final ServiceCertificateIdentity services=mock(ServiceCertificateIdentity.class);final ServiceCertificateIdentity.Verified service=mock(ServiceCertificateIdentity.Verified.class);
    final TaskProfessionalRuntimeService.Deployment deployment=mock(TaskProfessionalRuntimeService.Deployment.class);final TaskRuntimeJournal journal=mock(TaskRuntimeJournal.class);
    final TaskRunLaunchRecoveryAuthority recoveries=mock(TaskRunLaunchRecoveryAuthority.class);final TaskRunWorkflowLedger launches=mock(TaskRunWorkflowLedger.class);final TaskRunLaunchRecoveryAuthority.Verified recovery=mock(TaskRunLaunchRecoveryAuthority.Verified.class);final TaskRunWorkflowLedger.Intent launch=mock(TaskRunWorkflowLedger.Intent.class);final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
    final TaskNodeInputAuthority.Verified input=mock(TaskNodeInputAuthority.Verified.class);final HttpServletRequest http=mock(HttpServletRequest.class);
    final PermissionSchema json=new PermissionSchema();final RunTokenAuthoritySchema schema=mock(RunTokenAuthoritySchema.class);
    final String tenant=id(1),actor=id(2),project=id(3),run=id(4),step=id(5),task=id(6),command=id(7),inputId=id(8),artifact=id(9);
    final String worker="spiffe://test/task-host",inputHash="a".repeat(64),envelopeHash="b".repeat(64),manifest="c".repeat(64);
    final String action=TaskHostControlBootstrap.actionId(tenant,run,step,BigInteger.ONE);
    final ProfessionalRuntimeObservations.Binding binding=new ProfessionalRuntimeObservations.Binding(tenant,run,step,action,1,inputHash,envelopeHash);
    final Map<String,Object> actionRow=new LinkedHashMap<>();boolean actorPresent=true,leasePresent=true,actionPresent=true;
    TaskHostControlBootstrap bootstrap;
    final WorkflowProtocol.Reference reference=new WorkflowProtocol.Reference("1.0.0",tenant,project,run,WorkflowProtocol.ExecutionKind.TASK,id(12),"f".repeat(64),id(13),1800000000000L,100,10,2,1000,1000,1000,"original-control","original-professional");
    final WorkflowProtocol.Dispatch dispatch=new WorkflowProtocol.Dispatch(run,step,action,command,inputHash,1,artifact,envelopeHash);
    TaskHostControlBootstrapRouteTest()throws Exception {}
    @BeforeEach void setup()throws Exception {
        when(services.require(http)).thenReturn(service);when(service.serviceUri()).thenReturn(worker);when(service.certificateSha256()).thenReturn("d".repeat(64));when(service.trustRevision()).thenReturn(id(10));when(deployment.workerService()).thenReturn(worker);when(deployment.value()).thenReturn(json.object().put("host_tool_version","host-tool-v1").put("token_lifetime_seconds",60));when(schema.manifestSha256()).thenReturn(manifest);
        when(input.tenantId()).thenReturn(tenant);when(input.projectId()).thenReturn(project);when(input.taskId()).thenReturn(task);when(input.runId()).thenReturn(run);when(input.stepId()).thenReturn(step);when(input.inputId()).thenReturn(inputId);when(input.inputSha256()).thenReturn(inputHash);when(input.receiptSha256()).thenReturn("e".repeat(64));when(node.currentInput()).thenReturn(input);
        when(node.nodePermissions()).thenReturn(List.of(new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(tenant,project,"TASK",task),"task.run","TASK_EXECUTION")));
        when(nodes.execute(eq(http),eq(tenant),eq(actor),eq(run),eq(step),isNull(),eq(false),any())).thenAnswer(call->((FormalTaskNodeAuthority.Operation<?>)call.getArgument(7)).apply(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE),node));
        when(journal.requireExistingStep(node,binding)).thenReturn(dispatch);when(node.controlAdmission()).thenReturn(admission);when(recoveries.require(admission,http,run,id(11))).thenReturn(recovery);when(launches.recover(recovery)).thenReturn(launch);when(launch.reference()).thenReturn(reference);when(launches.read(recovery)).thenReturn(Optional.of(mock(TaskRunWorkflowLedger.Receipt.class)));
        for(var pin:Map.ofEntries(Map.entry("tenant_id",tenant),Map.entry("project_id",project),Map.entry("run_id",run),Map.entry("step_id",step),Map.entry("action_id",action),Map.entry("input_sha256",inputHash),Map.entry("origin_run_id",run),Map.entry("origin_step_id",step),Map.entry("origin_action_id",action),Map.entry("kind","HOST_CONTROL"),Map.entry("arguments_sha256",TaskProfessionalRuntimeService.hostArguments(input)),Map.entry("tool_version","host-tool-v1"),Map.entry("state","BOUND"),Map.entry("schema_version","1.0.0"),Map.entry("schema_manifest_sha256",manifest),Map.entry("created_by",actor),Map.entry("classification","INTERNAL"),Map.entry("lease_generation",BigInteger.ONE)).entrySet())actionRow.put(pin.getKey(),pin.getValue());
        when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);if(sql.startsWith("SELECT created_by"))return actorPresent?List.of(Map.of("created_by",actor)):List.of();if(sql.startsWith("SELECT command_id FROM task_run_replay_intent"))return List.of(Map.of("command_id",id(11)));if(sql.contains("FROM run_step s"))return leasePresent?List.of(Map.of("input_sha256",inputHash,"run_classification","INTERNAL")):List.of();if(sql.contains("FROM step_action_binding"))return actionPresent?List.of(new LinkedHashMap<>(actionRow)):List.of();throw new AssertionError(sql);});
        var issued=mock(RunTokenAuthority.Issued.class);when(issued.token()).thenReturn("explicit-token-boundary-double");when(tokens.issue(eq(http),any())).thenReturn(issued);when(hosts.open(eq(http),eq("explicit-token-boundary-double"),any())).thenReturn(handle);
        bootstrap=new TaskHostControlBootstrap(db,nodes,tokens,hosts,services,deployment,json,schema,journal,recoveries,launches);clearInvocations(db);
    }
    @Test void workerOpenDerivesActorAndExactOriginalCommandUnderCurrentAuthority()throws Exception {assertSame(handle,bootstrap.open(http,binding));verify(nodes).execute(eq(http),eq(tenant),eq(actor),eq(run),eq(step),isNull(),eq(false),any());verify(journal).requireExistingStep(node,binding);verify(tokens).issue(eq(http),argThat(request->actor.equals(request.initiatorId())&&request.actions().equals(List.of("task.run"))));verify(db,never()).update(anyString(),any(Object[].class));}
    @Test void commandLookupIsReadonlyAndReturnsOriginalNotCallerValue()throws Exception {assertEquals(command,bootstrap.commandId(http,binding));verifyNoInteractions(tokens,hosts);verify(db,never()).update(anyString(),any(Object[].class));}
    @Test void rejectedMtlsCannotReadActorOrJournal()throws Exception {when(services.require(http)).thenThrow(new SecurityException("mTLS denied"));assertThrows(SecurityException.class,()->bootstrap.open(http,binding));verifyNoInteractions(db,nodes,journal,tokens,hosts);}
    @Test void otherServiceCannotDiscoverActor()throws Exception {when(service.serviceUri()).thenReturn("spiffe://test/other");assertThrows(TenantAccessException.class,()->bootstrap.commandId(http,binding));verifyNoInteractions(db,nodes,journal,tokens,hosts);}
    @Test void missingTaskActorCannotEnterNodeAuthority()throws Exception {actorPresent=false;assertThrows(TenantAccessException.class,()->bootstrap.open(http,binding));verifyNoInteractions(nodes,journal,tokens,hosts);}
    @Test void nondeterministicHostActionIsRejectedBeforeNodeAuthorization()throws Exception {var substituted=new ProfessionalRuntimeObservations.Binding(tenant,run,step,id(99),1,inputHash,envelopeHash);assertThrows(TenantAccessException.class,()->bootstrap.open(http,substituted));verifyNoInteractions(nodes,journal,tokens,hosts);}
    @Test void missingOrAlteredOriginalDispatchCannotIssueToken()throws Exception {when(journal.requireExistingStep(node,binding)).thenThrow(new TenantAccessException(ErrorCode.CONTENT_MISMATCH));assertThrows(TenantAccessException.class,()->bootstrap.open(http,binding));verifyNoInteractions(tokens,hosts);verify(db,never()).update(anyString(),any(Object[].class));}
    @Test void missingActionIsNotCreatedForWorkerRecovery()throws Exception {actionPresent=false;assertThrows(TenantAccessException.class,()->bootstrap.open(http,binding));verifyNoInteractions(tokens,hosts);verify(db,never()).update(anyString(),any(Object[].class));}
    @Test void expiredOrWrongServiceLeaseCannotIssueToken()throws Exception {leasePresent=false;assertThrows(TenantAccessException.class,()->bootstrap.open(http,binding));verifyNoInteractions(tokens,hosts);}
    @Test void substitutedToolOrModelCallBindingCannotIssueToken()throws Exception {actionRow.put("kind","MODEL_CALL");assertThrows(TenantAccessException.class,()->bootstrap.open(http,binding));verifyNoInteractions(tokens,hosts);}
    @Test void changedCertificateBetweenLookupAndReturnIsRejected()throws Exception {var other=mock(ServiceCertificateIdentity.Verified.class);when(other.serviceUri()).thenReturn(worker);when(other.certificateSha256()).thenReturn("f".repeat(64));when(other.trustRevision()).thenReturn(id(10));when(services.require(http)).thenReturn(service,other);assertThrows(TenantAccessException.class,()->bootstrap.commandId(http,binding));verifyNoInteractions(tokens,hosts);}
    @Test void exactOriginalReferenceAndDispatchAreRequiredBeforeRecovery()throws Exception {bootstrap.requireDispatch(http,binding,reference,dispatch);verify(launches).recover(recovery);verify(launches).read(recovery);verifyNoInteractions(tokens,hosts);}
    @Test void substitutedReferenceQueueOrDeadlineIsNotAccepted()throws Exception {var other=new WorkflowProtocol.Reference("1.0.0",tenant,project,run,WorkflowProtocol.ExecutionKind.TASK,reference.inputManifestId(),reference.inputSha256(),reference.policyRevision(),reference.deadlineEpochMillis()+1,100,10,2,1000,1000,1000,"different-control","original-professional");assertThrows(TenantAccessException.class,()->bootstrap.requireDispatch(http,binding,other,dispatch));verifyNoInteractions(tokens,hosts);}
    @Test void missingDurableAcceptanceCannotOpenWorker()throws Exception {when(launches.read(recovery)).thenReturn(Optional.empty());assertThrows(TenantAccessException.class,()->bootstrap.open(http,binding));verifyNoInteractions(tokens,hosts);}
    @Test void currentRunReadIsNotInferredFromTaskRunPermission()throws Exception {when(recoveries.require(admission,http,run,id(11))).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,()->bootstrap.open(http,binding));verifyNoInteractions(tokens,hosts);}
    @Test void callerDispatchCannotReplaceOriginalCommand()throws Exception {var other=new WorkflowProtocol.Dispatch(run,step,action,id(99),inputHash,1,artifact,envelopeHash);assertThrows(TenantAccessException.class,()->bootstrap.requireDispatch(http,binding,reference,other));verifyNoInteractions(tokens,hosts);}
    static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
}
