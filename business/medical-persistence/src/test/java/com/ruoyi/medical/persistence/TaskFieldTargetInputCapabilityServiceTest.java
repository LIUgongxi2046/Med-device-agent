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
class TaskFieldTargetInputCapabilityServiceTest {
    static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
    final PermissionSchema json=new PermissionSchema();
    final TaskFieldTargetSchema schema=new TaskFieldTargetSchema();
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
    final TaskFieldTargetRegistration registrations=mock(TaskFieldTargetRegistration.class);
    final TaskFieldTargetInputCapabilityService service=new TaskFieldTargetInputCapabilityService(access,data,publications,
            policies,roles,tasks,plans,permissions,new PermissionAuthority(json),identities,resources,registrations,schema,json);
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class,RETURNS_DEEP_STUBS);
    final EnterpriseAccess.Admission admission=new EnterpriseAccess.Admission(subject,id(2),
            new BusinessSessionProtocol.Context(id(3),"1","1","1"),new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
    final LocalDateTime now=LocalDateTime.of(2026,10,20,0,0);
    TaskFieldTargetInputCapabilityServiceTest()throws Exception{when(subject.user().user_id()).thenReturn(id(4));}

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
        var method=TaskFieldTargetInputCapabilityService.class.getDeclaredMethod("requireCurrentActor",EnterpriseAccess.Admission.class,
                String.class,String.class,JsonNode.class,JsonNode.class,String.class); method.setAccessible(true);
        var request=json.object().put("project_id",id(6)).put("task_id",id(7));
        try{method.invoke(service,admission,actor,"local",AgentPublicationWitness.tree(original),request,action);}
        catch(InvocationTargetException failure){throw failure.getCause();}
    }
    @Test void currentAuthorAndApproverUseDistinctExactActionsOnly()throws Throwable{
        for(String action:List.of(TaskFieldTargetInputCapabilityService.WRITE,TaskFieldTargetInputCapabilityService.APPROVE)){
            var original=grant(id(5),"ACTIVE",null,action,TaskFieldTargetInputCapabilityService.PURPOSE); live(id(5),original);
            current(id(5),original,action);
        }
        verifyNoInteractions(data,publications,policies,roles,tasks,plans,registrations);
    }
    @Test void originalAuthorGrantRevokedAfterApprovalIsStale()throws Exception{
        var original=grant(id(5),"ACTIVE",null,TaskFieldTargetInputCapabilityService.WRITE,TaskFieldTargetInputCapabilityService.PURPOSE);
        live(id(5),grant(id(5),"REVOKED",null,TaskFieldTargetInputCapabilityService.WRITE,TaskFieldTargetInputCapabilityService.PURPOSE));
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskFieldTargetInputCapabilityService.WRITE)).code());
    }
    @Test void originalApproverGrantRevokedAfterApprovalIsStale()throws Exception{
        var original=grant(id(5),"ACTIVE",null,TaskFieldTargetInputCapabilityService.APPROVE,TaskFieldTargetInputCapabilityService.PURPOSE);
        live(id(5),grant(id(5),"REVOKED",null,TaskFieldTargetInputCapabilityService.APPROVE,TaskFieldTargetInputCapabilityService.PURPOSE));
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskFieldTargetInputCapabilityService.APPROVE)).code());
    }
    @Test void expiryCannotBeBypassedByUnchangedOriginalGrant()throws Exception{
        var original=grant(id(5),"ACTIVE",now,TaskFieldTargetInputCapabilityService.APPROVE,TaskFieldTargetInputCapabilityService.PURPOSE); live(id(5),original);
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskFieldTargetInputCapabilityService.APPROVE)).code());
    }
    @Test void oldTaskRunTaskReadOr093RightsNeverAuthorizeNewAction()throws Exception{
        for(String action:List.of("task.run","task.read","task.delegation.author","task.delegation.approve")){
            var original=grant(id(5),"ACTIVE",null,action,TaskFieldTargetInputCapabilityService.PURPOSE); live(id(5),original);
            assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskFieldTargetInputCapabilityService.WRITE)).code());
        }
    }
    @Test void wrongPurposeCannotAuthorizeExactInput()throws Exception{
        var original=grant(id(5),"ACTIVE",null,TaskFieldTargetInputCapabilityService.WRITE,"TASK_DELEGATION"); live(id(5),original);
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskFieldTargetInputCapabilityService.WRITE)).code());
    }
    @Test void platformRevocationPrecedesGrantConsumption()throws Exception{
        var original=grant(id(5),"ACTIVE",null,TaskFieldTargetInputCapabilityService.APPROVE,TaskFieldTargetInputCapabilityService.PURPOSE); live(id(5),original);
        when(identities.active(any())).thenReturn(false);
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskFieldTargetInputCapabilityService.APPROVE)).code());
        verify(permissions,never()).currentGrant(any());
    }
    @Test void otherTenantOrDisabledApproverCannotKeepAnApprovalAlive()throws Exception{
        var original=grant(id(5),"ACTIVE",null,TaskFieldTargetInputCapabilityService.APPROVE,TaskFieldTargetInputCapabilityService.PURPOSE); live(id(5),original);
        when(permissions.targetMember(id(5))).thenReturn(List.of(new TenantMemberRow(id(99),id(10),id(5),"ACTIVE",now.minusDays(1),null,BigInteger.ONE)));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskFieldTargetInputCapabilityService.APPROVE)).code());
        when(permissions.targetMember(id(5))).thenReturn(List.of(new TenantMemberRow(id(3),id(10),id(5),"DISABLED",now.minusDays(1),null,BigInteger.ONE)));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskFieldTargetInputCapabilityService.APPROVE)).code());
    }
    @Test void changedIdentityModeCannotUsePersistedGrant()throws Exception{
        var original=grant(id(5),"ACTIVE",null,TaskFieldTargetInputCapabilityService.APPROVE,TaskFieldTargetInputCapabilityService.PURPOSE); live(id(5),original);
        when(permissions.targetBinding(id(5))).thenReturn(List.of(new PermissionProtocol.BindingRow(id(5),id(11),"oidc","issuer","subject",null,"ACTIVE")));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->current(id(5),original,TaskFieldTargetInputCapabilityService.APPROVE)).code());
    }
    @Test void approvalRequiresIndependentActorAndDisableCannotBeReapproved(){
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->TaskFieldTargetInputCapabilityService.requireDecision("DRAFT","APPROVE",id(1),id(1))).code());
        assertDoesNotThrow(()->TaskFieldTargetInputCapabilityService.requireDecision("DRAFT","APPROVE",id(1),id(2)));
        for(String previous:List.of("DRAFT","APPROVED"))assertDoesNotThrow(()->TaskFieldTargetInputCapabilityService.requireDecision(previous,"DISABLE",id(1),id(2)));
        for(String decision:List.of("APPROVE","DISABLE"))assertEquals(ErrorCode.REVISION_CONFLICT,assertThrows(TenantAccessException.class,()->TaskFieldTargetInputCapabilityService.requireDecision("DISABLED",decision,id(1),id(2))).code());
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->TaskFieldTargetInputCapabilityService.requireDecision("DRAFT","REJECT",id(1),id(2))).code());
    }
    @Test void exactOccurrenceCoverageRejectsSubsetSupersetAndDuplicate(){
        var original=json.object().arrayNode().add(id(1)).add(id(2));
        assertDoesNotThrow(()->TaskFieldTargetInputCapabilityService.requireCoverage(original,List.of(id(2),id(1))));
        for(var changed:List.of(List.of(id(1)),List.of(id(1),id(2),id(3)),List.of(id(1),id(1))))
            assertThrows(TenantAccessException.class,()->TaskFieldTargetInputCapabilityService.requireCoverage(original,changed));
    }
    @Test void actualObjectCannotExceedExplicitMaximumReadBytes(){
        assertDoesNotThrow(()->TaskFieldTargetInputCapabilityService.requireReadBound("42","42"));
        assertThrows(TenantAccessException.class,()->TaskFieldTargetInputCapabilityService.requireReadBound("41","42"));
        assertThrows(RuntimeException.class,()->TaskFieldTargetInputCapabilityService.requireReadBound("0","42"));
    }
    @Test void transactionlessConsumerCannotReadAnyRecords(){
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->service.requireCurrent(admission,null,null,null,id(1),"a".repeat(64),id(2),null,null)).code());
        verifyNoInteractions(data,permissions,resources,registrations);
    }
    @Test void emptyTransactionResourcesCannotBecomeProofLifetime(){
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->service.requireCurrent(admission,null,null,null,id(1),"a".repeat(64),id(2),null,null)).code());}
        finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
        verifyNoInteractions(data,permissions,resources,registrations);
    }
    @Test void validLifetimeGuardIsNotProofAndRejectsReboundResourcesAndDifferentAdmission(){
        Object key=new Object(),resource=new Object(); TransactionSynchronizationManager.setActualTransactionActive(true); TransactionSynchronizationManager.bindResource(key,resource);
        try(var scope=TenantScope.open(new TenantScope.Identity(id(3),id(4),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){
            var map=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
            assertDoesNotThrow(()->TaskFieldTargetInputAuthority.requireLifetime(admission,admission,Thread.currentThread(),scope,map));
            var other=new EnterpriseAccess.Admission(subject,id(2),admission.context(),admission.watermarks());
            assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->TaskFieldTargetInputAuthority.requireLifetime(admission,other,Thread.currentThread(),scope,map)).code());
            assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->TaskFieldTargetInputAuthority.requireLifetime(admission,admission,new Thread(),scope,map)).code());
            TransactionSynchronizationManager.unbindResource(key); TransactionSynchronizationManager.bindResource(key,new Object());
            assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->TaskFieldTargetInputAuthority.requireLifetime(admission,admission,Thread.currentThread(),scope,map)).code());
        }finally{TransactionSynchronizationManager.unbindResource(key);TransactionSynchronizationManager.setActualTransactionActive(false);}
    }
    @Test void proofConstructorIsPrivateAndAuthorityOffersNoCandidateSelection(){
        for(var c:TaskFieldTargetInputAuthority.Verified.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(c.getModifiers()));
        assertEquals(1,java.util.Arrays.stream(TaskFieldTargetInputAuthority.class.getDeclaredMethods()).filter(m->m.getName().equals("require")).count());
    }

    JsonNode exactManifest(){
        var n=json.object().put("task_id",id(7)).put("consumer_node_kind","N03").put("artifact_kind","STRICT_FIELD_TARGET")
                .put("action","artifact.read").put("purpose",TaskFieldTargetInputCapabilityService.PURPOSE).put("maximum_read_bytes","42");
        n.putArray("occurrence_ids").add(id(1)); n.putObject("object_ref").put("object_key","exact/key").put("object_version","actual-v1")
                .put("sha256","a".repeat(64)).put("size_bytes","42").put("media_type","application/json"); return n;
    }
    void exact(JsonNode m,JsonNode object,String task,String node,List<String> occurrences,String bytes,String action,String purpose){
        TaskFieldTargetInputAuthority.requireExactRead(m,object,id(7),task,node,occurrences,bytes,action,purpose);
    }
    @Test void exactDataComparisonIsNotAProofAndRejectsWidening(){
        var m=exactManifest();var object=m.required("object_ref");
        assertDoesNotThrow(()->exact(m,object,id(7),"N03",List.of(id(1)),"42","artifact.read",TaskFieldTargetInputCapabilityService.PURPOSE));
        assertThrows(TenantAccessException.class,()->exact(m,object,id(99),"N03",List.of(id(1)),"42","artifact.read",TaskFieldTargetInputCapabilityService.PURPOSE));
        assertThrows(TenantAccessException.class,()->exact(m,object,id(7),"N04",List.of(id(1)),"42","artifact.read",TaskFieldTargetInputCapabilityService.PURPOSE));
        assertThrows(TenantAccessException.class,()->exact(m,object,id(7),"N03",List.of(id(1)),"43","artifact.read",TaskFieldTargetInputCapabilityService.PURPOSE));
        assertThrows(TenantAccessException.class,()->exact(m,object,id(7),"N03",List.of(id(1)),"42","artifact.register",TaskFieldTargetInputCapabilityService.PURPOSE));
        assertThrows(TenantAccessException.class,()->exact(m,object,id(7),"N03",List.of(id(1)),"42","artifact.read","TASK_EXECUTION"));
        assertThrows(TenantAccessException.class,()->exact(m,object,id(7),"N03",List.of(id(1),id(2)),"42","artifact.read",TaskFieldTargetInputCapabilityService.PURPOSE));
        var replaced=object.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)replaced).put("object_version","future-v2");
        assertThrows(TenantAccessException.class,()->exact(m,replaced,id(7),"N03",List.of(id(1)),"42","artifact.read",TaskFieldTargetInputCapabilityService.PURPOSE));
    }
}
