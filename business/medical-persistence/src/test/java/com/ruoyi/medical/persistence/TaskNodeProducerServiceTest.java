package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.identity.PlatformMemberIdentityStatus;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Adversarial tests of production guards only. No grant, approval, registration or private authority
 * proof is manufactured. These tests do not claim live SQL, object readback or endpoint integration. */
class TaskNodeProducerServiceTest {
    @Test void preparationMetadataDoesNotSelectOrAuthorizeAnything()throws Exception{
        var choices=TaskNodeProducerService.preparationChoices(new PermissionSchema());
        assertEquals(4,choices.size());
        assertEquals(8,choices.required("artifact_kinds").size());
        assertEquals(6,choices.required("operations").size());
        assertEquals(11,choices.required("required_selections").size());
        assertFalse(choices.has("selected"));assertFalse(choices.has("status"));
        assertFalse(choices.has("rule_id"));assertFalse(choices.has("decision_id"));
    }
    static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
    final PermissionSchema json=new PermissionSchema();
    final TaskNodeProducerSchema schema=new TaskNodeProducerSchema();
    final PermissionMapper permissions=mock(PermissionMapper.class);
    final PlatformMemberIdentityStatus identities=mock(PlatformMemberIdentityStatus.class);
    final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);
    final DataSource data=mock(DataSource.class);
    final EnterpriseAccess access=mock(EnterpriseAccess.class);
    final AgentPublicationAuthority publications=mock(AgentPublicationAuthority.class);
    final AgentExecutionPolicyAuthority policies=mock(AgentExecutionPolicyAuthority.class);
    final FormalCapabilityIdentityAuthority roles=mock(FormalCapabilityIdentityAuthority.class);
    final TaskService tasks=mock(TaskService.class);
    final TaskPlanService plans=mock(TaskPlanService.class);
    final TaskNodeProducerOriginalAuthority originals=mock(TaskNodeProducerOriginalAuthority.class);
    final TaskNodeProducerService service=new TaskNodeProducerService(access,data,publications,
            policies,roles,tasks,plans,permissions,new PermissionAuthority(json),identities,resources,originals,schema,json);
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class,RETURNS_DEEP_STUBS);
    final EnterpriseAccess.Admission admission=new EnterpriseAccess.Admission(subject,id(2),
            new BusinessSessionProtocol.Context(id(3),"1","1","1"),new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
    final LocalDateTime now=LocalDateTime.of(2026,10,20,0,0);
    TaskNodeProducerServiceTest()throws Exception{when(subject.user().user_id()).thenReturn(id(4));}

    PermissionProtocol.GrantRow grant(String actor,String status,LocalDateTime until,String action,String purpose)throws Exception{
        return new PermissionProtocol.GrantRow(id(8),actor,id(6),"TASK",id(7),action,
                "{\"schema_version\":\"1.0.0\",\"identity_modes\":[\"local\"],\"purposes\":[\""+purpose+"\"],\"delegation\":{\"actions\":[],\"role_ids\":[],\"resource_kinds\":[],\"project_ids\":[],\"resource_ids\":[]}}",
                status,now.minusDays(1),until,id(9),"1.0.0",json.manifestSha256(),BigInteger.ONE);
    }
    void live(String actor,PermissionProtocol.GrantRow grant){
        when(permissions.databaseTime()).thenReturn(now);
        when(permissions.targetMember(actor)).thenReturn(List.of(new TenantMemberRow(id(3),id(10),actor,"ACTIVE",now.minusDays(1),null,BigInteger.ONE)));
        when(permissions.targetBinding(actor)).thenReturn(List.of(new PermissionProtocol.BindingRow(actor,id(11),"local",null,null,actor,"ACTIVE")));
        when(permissions.currentGrant(id(8))).thenReturn(List.of(grant)); when(identities.active(any())).thenReturn(true);
    }
    void current(String actor,PermissionProtocol.GrantRow original,String action)throws Throwable{
        var method=TaskNodeProducerService.class.getDeclaredMethod("requireCurrentActor",EnterpriseAccess.Admission.class,
                String.class,String.class,JsonNode.class,JsonNode.class,String.class); method.setAccessible(true);
        var request=json.object().put("project_id",id(6)).put("task_id",id(7));
        try{method.invoke(service,admission,actor,"local",AgentPublicationWitness.tree(original),request,action);}
        catch(InvocationTargetException failure){throw failure.getCause();}
    }
    @Test void currentAuthorAndApproverUseDistinctExactActionsOnly()throws Throwable{
        for(String action:List.of(TaskNodeProducerService.WRITE,TaskNodeProducerService.APPROVE)){
            var original=grant(id(5),"ACTIVE",null,action,TaskNodeProducerService.PURPOSE); live(id(5),original);
            current(id(5),original,action);
        }
        verifyNoInteractions(data,publications,policies,roles,tasks,plans,originals);
    }
    @Test void originalAuthorGrantRevokedAfterApprovalIsStale()throws Exception{
        var original=grant(id(5),"ACTIVE",null,TaskNodeProducerService.WRITE,TaskNodeProducerService.PURPOSE);
        live(id(5),grant(id(5),"REVOKED",null,TaskNodeProducerService.WRITE,TaskNodeProducerService.PURPOSE));
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskNodeProducerService.WRITE)).code());
    }
    @Test void originalApproverGrantRevokedAfterApprovalIsStale()throws Exception{
        var original=grant(id(5),"ACTIVE",null,TaskNodeProducerService.APPROVE,TaskNodeProducerService.PURPOSE);
        live(id(5),grant(id(5),"REVOKED",null,TaskNodeProducerService.APPROVE,TaskNodeProducerService.PURPOSE));
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskNodeProducerService.APPROVE)).code());
    }
    @Test void expiryCannotBeBypassedByUnchangedOriginalGrant()throws Exception{
        var original=grant(id(5),"ACTIVE",now,TaskNodeProducerService.APPROVE,TaskNodeProducerService.PURPOSE); live(id(5),original);
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskNodeProducerService.APPROVE)).code());
    }
    @Test void oldTaskRunTaskReadOr093RightsNeverAuthorizeNewAction()throws Exception{
        for(String action:List.of("task.run","task.read","task.delegation.author","task.delegation.approve")){
            var original=grant(id(5),"ACTIVE",null,action,TaskNodeProducerService.PURPOSE); live(id(5),original);
            assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskNodeProducerService.WRITE)).code());
        }
    }
    @Test void wrongPurposeCannotAuthorizeExactInput()throws Exception{
        var original=grant(id(5),"ACTIVE",null,TaskNodeProducerService.WRITE,"TASK_DELEGATION"); live(id(5),original);
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskNodeProducerService.WRITE)).code());
    }
    @Test void platformRevocationPrecedesGrantConsumption()throws Exception{
        var original=grant(id(5),"ACTIVE",null,TaskNodeProducerService.APPROVE,TaskNodeProducerService.PURPOSE); live(id(5),original);
        when(identities.active(any())).thenReturn(false);
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskNodeProducerService.APPROVE)).code());
        verify(permissions,never()).currentGrant(any());
    }
    @Test void otherTenantOrDisabledApproverCannotKeepAnApprovalAlive()throws Exception{
        var original=grant(id(5),"ACTIVE",null,TaskNodeProducerService.APPROVE,TaskNodeProducerService.PURPOSE); live(id(5),original);
        when(permissions.targetMember(id(5))).thenReturn(List.of(new TenantMemberRow(id(99),id(10),id(5),"ACTIVE",now.minusDays(1),null,BigInteger.ONE)));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskNodeProducerService.APPROVE)).code());
        when(permissions.targetMember(id(5))).thenReturn(List.of(new TenantMemberRow(id(3),id(10),id(5),"DISABLED",now.minusDays(1),null,BigInteger.ONE)));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskNodeProducerService.APPROVE)).code());
    }
    @Test void changedIdentityModeCannotUsePersistedGrant()throws Exception{
        var original=grant(id(5),"ACTIVE",null,TaskNodeProducerService.APPROVE,TaskNodeProducerService.PURPOSE); live(id(5),original);
        when(permissions.targetBinding(id(5))).thenReturn(List.of(new PermissionProtocol.BindingRow(id(5),id(11),"oidc","issuer","subject",null,"ACTIVE")));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskNodeProducerService.APPROVE)).code());
    }
    @Test void approvalRequiresIndependentActorAndDisableCannotBeReapproved(){
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->TaskNodeProducerService.requireDecision("DRAFT","APPROVE",id(1),id(1))).code());
        assertDoesNotThrow(()->TaskNodeProducerService.requireDecision("DRAFT","APPROVE",id(1),id(2)));
        for(String previous:List.of("DRAFT","APPROVED"))assertDoesNotThrow(()->TaskNodeProducerService.requireDecision(previous,"DISABLE",id(1),id(2)));
        for(String decision:List.of("APPROVE","DISABLE"))assertEquals(ErrorCode.REVISION_CONFLICT,assertThrows(TenantAccessException.class,()->TaskNodeProducerService.requireDecision("DISABLED",decision,id(1),id(2))).code());
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->TaskNodeProducerService.requireDecision("DRAFT","REJECT",id(1),id(2))).code());
    }
    @Test void exactOccurrenceCoverageRejectsSubsetSupersetAndDuplicate(){
        var original=json.object().arrayNode().add(id(1)).add(id(2));
        assertDoesNotThrow(()->TaskNodeProducerService.requireCoverage(original,List.of(id(2),id(1))));
        for(var changed:List.of(List.of(id(1)),List.of(id(1),id(2),id(3)),List.of(id(1),id(1))))
            assertThrows(TenantAccessException.class,()->TaskNodeProducerService.requireCoverage(original,changed));
    }
    @Test void actualObjectCannotExceedExplicitMaximumReadBytes(){
        assertDoesNotThrow(()->TaskNodeProducerService.requireReadBound("42","42"));
        assertThrows(TenantAccessException.class,()->TaskNodeProducerService.requireReadBound("41","42"));
        assertThrows(RuntimeException.class,()->TaskNodeProducerService.requireReadBound("0","42"));
    }
    @Test void transactionlessConsumerCannotReadAnyRecords(){
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->service.requireCurrent(admission,null,null,null,id(1),"a".repeat(64),id(2),null,null)).code());
        verifyNoInteractions(data,permissions,resources,originals);
    }
    @Test void emptyTransactionResourcesCannotBecomeProofLifetime(){
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->service.requireCurrent(admission,null,null,null,id(1),"a".repeat(64),id(2),null,null)).code());}
        finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
        verifyNoInteractions(data,permissions,resources,originals);
    }

    @Test void actualReplayCannotSubstituteAnotherApprovedOriginalRule()throws Exception {
        var fixed=json.object().put("rule_id",id(1));var decision=json.object().put("decision_id",id(2));
        var original=json.object();original.set("rule",fixed);original.set("approval",decision);
        var witnesses=json.object();witnesses.putObject("additional_authorizations").set("N02",original);
        var replay=json.object().put("budget_revision",id(3));replay.putObject("authorizations").put("canonical_witnesses",schema.canonical(witnesses));
        var rule=json.object().put("original_rule_id",id(1)).put("original_rule_sha256",schema.hash(fixed))
            .put("original_approval_sha256",schema.hash(decision)).put("budget_revision",id(3));
        assertDoesNotThrow(()->service.requireReplayPins(rule,replay));
        for(String field:List.of("original_rule_id","original_rule_sha256","original_approval_sha256","budget_revision")) {
            var changed=rule.deepCopy();changed.put(field,field.endsWith("sha256")?"f".repeat(64):id(99));
            assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.requireReplayPins(changed,replay)).code());
        }
        verifyNoInteractions(data,permissions,resources,originals);
    }

    @Test void approvedClassificationCannotDowngradeTheActualTaskOrRun(){
        assertDoesNotThrow(()->TaskNodeProducerService.requireClassification("PERSONAL_HEALTH","PERSONAL_HEALTH"));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->TaskNodeProducerService.requireClassification("PUBLIC","PERSONAL_HEALTH")).code());
        assertThrows(TenantAccessException.class,()->TaskNodeProducerService.requireClassification(null,null));
    }


    @Test void enterprisePinsRejectMissingCrossAccountStaleAndExpiredApprovals()throws Exception{
        var producer=new TaskNodeProducerSchemaTest().author();
        var limits=new BudgetProtocol.Limits("100.00","100","100","1","1","2026-10-22T00:00:00Z");
        var approval=new BudgetProtocol.Approval("1.0.0",id(3),null,id(80),"USD",limits,new PermissionProtocol.Basis(id(81),"b".repeat(64)),id(82),"Approved enterprise cap",id(83),"1",null);
        String hash=org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonicalObject(approval));
        producer.putObject("enterprise_budget_basis").put("version_id",id(84)).put("content_sha256",hash);
        var row=new java.util.HashMap<String,Object>();
        row.put("tenant_id",id(3));row.put("budget_id",id(80));row.put("budget_revision",id(84));row.put("current_budget_revision",id(84));
        row.put("currency","USD");row.put("account_currency","USD");row.put("entity_kind","BUDGET");row.put("content_sha256",hash);row.put("registry_sha256",hash);
        row.put("schema_version","1.0.0");row.put("schema_manifest_sha256","manifest");row.put("approver_id",id(82));row.put("created_by",id(82));row.put("reason",approval.reason());
        assertDoesNotThrow(()->TaskNodeProducerService.requireEnterprisePins(producer,row,approval,"manifest",id(3),json));
        row.put("budget_id",id(85));assertThrows(RuntimeException.class,()->TaskNodeProducerService.requireEnterprisePins(producer,row,approval,"manifest",id(3),json));row.put("budget_id",id(80));
        row.put("project_id",id(86));assertThrows(RuntimeException.class,()->TaskNodeProducerService.requireEnterprisePins(producer,row,approval,"manifest",id(3),json));row.remove("project_id");
        row.put("current_budget_revision",id(87));assertThrows(RuntimeException.class,()->TaskNodeProducerService.requireEnterprisePins(producer,row,approval,"manifest",id(3),json));row.put("current_budget_revision",id(84));
        row.remove("registry_sha256");assertThrows(RuntimeException.class,()->TaskNodeProducerService.requireEnterprisePins(producer,row,approval,"manifest",id(3),json));
        var totals=json.object();for(String field:List.of("spent","reserved","spent_tokens","reserved_tokens","spent_calls","reserved_calls"))totals.put(field,"0");
        assertThrows(RuntimeException.class,()->RunCreateBudget.requireWithinBounds(totals,limits,LocalDateTime.of(2026,10,22,0,0)));
    }
    @Test void originalPickerNeverSilentlyTruncatesCandidateOverflow(){assertDoesNotThrow(()->TaskNodeProducerOriginalAuthority.requireCandidateBound(100,100));assertThrows(TenantAccessException.class,()->TaskNodeProducerOriginalAuthority.requireCandidateBound(101,100));assertThrows(TenantAccessException.class,()->TaskNodeProducerOriginalAuthority.requireCandidateBound(1,0));}
    @Test void readableRuleListProjectionDoesNotSelectOrInventApproval()throws Exception{
        var view=json.object().put("rule_sha256","a".repeat(64)).put("status","DRAFT").put("revision","1");
        view.putObject("manifest").put("rule_id",id(91)).put("created_by",id(92));
        var item=TaskNodeProducerService.listItem(view,json);
        assertEquals("DRAFT",item.required("status").textValue());assertEquals(id(91),item.required("rule_id").textValue());
        assertEquals(id(92),item.required("created_by").textValue());assertEquals(5,item.size());
        assertFalse(item.has("selected"));assertFalse(item.has("decision_id"));assertFalse(item.has("authorization"));
    }
}
