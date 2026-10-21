package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.identity.PlatformMemberIdentityStatus;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TaskMapper;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests the actual service guards and production author-validation helper, not a mocked service.
 * Private row reflection is test-only: no Verified proof is forged, no approval fixture is claimed,
 * and no database, credential, grant or external service is created. Full journal/producer integration
 * and valid proof issuance remain separate integration requirements. */
class TaskDelegationServiceTest {
    static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
    final PermissionSchema json=new PermissionSchema();
    final TaskDelegationSchema schema=new TaskDelegationSchema(json);
    final PermissionMapper permissions=mock(PermissionMapper.class);
    final TaskMapper tasks=mock(TaskMapper.class);
    final PlatformMemberIdentityStatus identities=mock(PlatformMemberIdentityStatus.class);
    final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);
    final DataSource data=mock(DataSource.class);
    final EnterpriseAccess access=mock(EnterpriseAccess.class);
    final AgentPublicationAuthority publications=mock(AgentPublicationAuthority.class);
    final AgentExecutionPolicyAuthority policies=mock(AgentExecutionPolicyAuthority.class);
    final FormalCapabilityIdentityAuthority roles=mock(FormalCapabilityIdentityAuthority.class);
    final RunCreateBudget budgets=mock(RunCreateBudget.class);
    final TaskDelegationService service=new TaskDelegationService(access,data,publications,policies,roles,tasks,
            permissions,budgets,new PermissionAuthority(json),identities,resources,schema,json,4);
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class,RETURNS_DEEP_STUBS);
    final EnterpriseAccess.Admission admission=new EnterpriseAccess.Admission(subject,id(2),
            new BusinessSessionProtocol.Context(id(3),"1","1","1"),new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
    final LocalDateTime now=LocalDateTime.of(2026,10,19,12,0);
    TaskDelegationServiceTest()throws Exception{when(subject.user().user_id()).thenReturn(id(4));}

    PermissionProtocol.GrantRow grant(String status,LocalDateTime until,String action)throws Exception{
        return new PermissionProtocol.GrantRow(id(8),id(5),id(6),"PROJECT",id(6),action,
                "{\"schema_version\":\"1.0.0\",\"identity_modes\":[\"local\"],\"purposes\":[\"TASK_DELEGATION\"],\"delegation\":{\"actions\":[],\"role_ids\":[],\"resource_kinds\":[],\"project_ids\":[],\"resource_ids\":[]}}",status,now.minusDays(1),until,id(9),"1.0.0",json.manifestSha256(),BigInteger.ONE);
    }
    void liveAuthor(PermissionProtocol.GrantRow grant){
        when(permissions.databaseTime()).thenReturn(now);
        when(permissions.targetMember(id(5))).thenReturn(List.of(new TenantMemberRow(id(3),id(10),id(5),"ACTIVE",now.minusDays(1),null,BigInteger.ONE)));
        when(permissions.targetBinding(id(5))).thenReturn(List.of(new PermissionProtocol.BindingRow(id(5),id(11),"local",null,null,id(5),"ACTIVE")));
        when(permissions.currentGrant(id(8))).thenReturn(List.of(grant));
        when(identities.active(any())).thenReturn(true);
    }
    void validateAuthor(PermissionProtocol.GrantRow original)throws Throwable{
        // Exercise the production helper without making its immutable Row or Verified proof public.
        var rowType=Class.forName(TaskDelegationService.class.getName()+"$Row");
        var ctor=rowType.getDeclaredConstructor(String.class,String.class,String.class,String.class,JsonNode.class);ctor.setAccessible(true);
        var fixed=json.object().put("identity_mode","local");fixed.set("grant",AgentPublicationWitness.tree(original));
        var row=ctor.newInstance(id(12),id(6),id(5),"a".repeat(64),fixed);
        var method=TaskDelegationService.class.getDeclaredMethod("requireCurrentAuthor",EnterpriseAccess.Admission.class,rowType);method.setAccessible(true);
        try{method.invoke(service,admission,row);}catch(InvocationTargetException failure){throw failure.getCause();}
    }
    @Test void actualCurrentAuthorGrantPassesHelperOnly()throws Throwable{
        var original=grant("ACTIVE",null,"task.delegation.author");liveAuthor(original);validateAuthor(original);
        verify(identities).active(any());verify(permissions).currentGrant(id(8));verifyNoInteractions(data,publications,policies);
    }
    @Test void changedOrRevokedAuthorGrantIsStaleEvenIfOriginalWasActive()throws Exception{
        var original=grant("ACTIVE",null,"task.delegation.author");liveAuthor(grant("REVOKED",null,"task.delegation.author"));
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,()->validateAuthor(original)).code());
    }
    @Test void unchangedButExpiredAuthorGrantCannotAuthorize()throws Exception{
        var expired=grant("ACTIVE",now,"task.delegation.author");liveAuthor(expired);
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->validateAuthor(expired)).code());
    }
    @Test void unchangedGrantForDifferentActionCannotAuthorize()throws Exception{
        var wrong=grant("ACTIVE",null,"task.delegation.read");liveAuthor(wrong);
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->validateAuthor(wrong)).code());
    }
    @Test void disabledAuthorMemberRejectsBeforeGrantRead()throws Exception{
        var original=grant("ACTIVE",null,"task.delegation.author");liveAuthor(original);
        when(permissions.targetMember(id(5))).thenReturn(List.of(new TenantMemberRow(id(3),id(10),id(5),"DISABLED",now.minusDays(1),null,BigInteger.TWO)));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->validateAuthor(original)).code());verify(permissions,never()).currentGrant(any());
    }
    @Test void otherTenantMemberRejectsBeforeGrantRead()throws Exception{
        var original=grant("ACTIVE",null,"task.delegation.author");liveAuthor(original);
        when(permissions.targetMember(id(5))).thenReturn(List.of(new TenantMemberRow(id(99),id(10),id(5),"ACTIVE",now.minusDays(1),null,BigInteger.ONE)));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->validateAuthor(original)).code());verify(permissions,never()).currentGrant(any());
    }
    @Test void disabledBindingCannotUseHistoricalAuthorGrant()throws Exception{
        var original=grant("ACTIVE",null,"task.delegation.author");liveAuthor(original);
        when(permissions.targetBinding(id(5))).thenReturn(List.of(new PermissionProtocol.BindingRow(id(5),id(11),"local",null,null,id(5),"DISABLED")));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->validateAuthor(original)).code());
    }
    @Test void revokedPlatformIdentityCannotUseCurrentGrant()throws Exception{
        var original=grant("ACTIVE",null,"task.delegation.author");liveAuthor(original);when(identities.active(any())).thenReturn(false);
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->validateAuthor(original)).code());verify(permissions,never()).currentGrant(any());
    }
    @Test void consumerCannotReadAuthorityOutsideTransaction(){
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->service.requireForTask(admission,null,null,null,id(20),id(21),id(22))).code());
        verifyNoInteractions(tasks,data,permissions,resources);
    }
    @Test void transactionWithoutBoundResourcesIsNotProofLifetime(){
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->service.requireForTask(admission,null,null,null,id(20),id(21),id(22))).code());}
        finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
        verifyNoInteractions(tasks,data,permissions,resources);
    }
    @Test void otherActorTenantScopeCannotIssueProof(){
        Object key=new Object();TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.bindResource(key,new Object());
        try(var ignored=TenantScope.open(new TenantScope.Identity(id(3),id(99),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){
            assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->service.requireForTask(admission,null,null,null,id(20),id(21),id(22))).code());
        }finally{TransactionSynchronizationManager.unbindResource(key);TransactionSynchronizationManager.setActualTransactionActive(false);}
        verifyNoInteractions(tasks,data,permissions,resources);
    }
    @Test void noCandidateBoundDefaultIsInvented(){
        for(int invalid:new int[]{0,-1,101})assertThrows(IllegalArgumentException.class,()->new TaskDelegationService(access,data,publications,policies,roles,tasks,permissions,budgets,new PermissionAuthority(json),identities,resources,schema,json,invalid));
    }
    @Test void independentDecisionAndIrreversibleDisableUseProductionTransitionGuard(){
        for(String decision:List.of("APPROVE","REJECT"))assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->TaskDelegationService.requireDecision("DRAFT",decision,id(1),id(1))).code());
        assertDoesNotThrow(()->TaskDelegationService.requireDecision("DRAFT","APPROVE",id(1),id(2)));
        assertDoesNotThrow(()->TaskDelegationService.requireDecision("APPROVED","DISABLE",id(1),id(2)));
        for(String status:List.of("DISABLED","REJECTED","APPROVED"))assertEquals(ErrorCode.REVISION_CONFLICT,assertThrows(TenantAccessException.class,()->TaskDelegationService.requireDecision(status,"APPROVE",id(1),id(2))).code());
    }
    @Test void immutableAuditMetadataMustMatchOriginalAuthorClassificationAndTime(){
        var instant=now.toInstant(java.time.ZoneOffset.UTC);
        assertDoesNotThrow(()->TaskDelegationService.requireAuditMetadata(id(1),id(1),"INTERNAL","INTERNAL",now,instant));
        assertThrows(TenantAccessException.class,()->TaskDelegationService.requireAuditMetadata(id(2),id(1),"INTERNAL","INTERNAL",now,instant));
        assertThrows(TenantAccessException.class,()->TaskDelegationService.requireAuditMetadata(id(1),id(1),"PUBLIC","INTERNAL",now,instant));
        assertThrows(TenantAccessException.class,()->TaskDelegationService.requireAuditMetadata(id(1),id(1),"INTERNAL","INTERNAL",now.plusNanos(1000),instant));
        assertThrows(TenantAccessException.class,()->TaskDelegationService.requireAuditMetadata(id(1),id(1),"INTERNAL","INTERNAL",null,instant));
    }
    void validateDecisionMetadata(String version,String manifest,String classification)throws Throwable{
        var rowType=Class.forName(TaskDelegationService.class.getName()+"$Row");
        var rowCtor=rowType.getDeclaredConstructor(String.class,String.class,String.class,String.class,JsonNode.class);rowCtor.setAccessible(true);
        var fixed=json.object();fixed.putObject("request").put("classification","INTERNAL");
        var row=rowCtor.newInstance(id(12),id(6),id(5),"a".repeat(64),fixed);
        var decisionType=Class.forName(TaskDelegationService.class.getName()+"$Decision");
        var ctor=decisionType.getDeclaredConstructors()[0];ctor.setAccessible(true);
        var decision=ctor.newInstance("{}","a".repeat(64),id(7),id(8),id(9),id(10),"{}","b".repeat(64),"original-key",version,manifest,classification,now);
        var body=json.object().put("schema_version","1.0.0").put("created_by",id(7)).put("created_at",now.toInstant(java.time.ZoneOffset.UTC).toString());
        var method=TaskDelegationService.class.getDeclaredMethod("requireDecisionMetadata",decisionType,JsonNode.class,rowType);method.setAccessible(true);
        try{method.invoke(service,decision,body,row);}catch(InvocationTargetException failure){throw failure.getCause();}
    }
    @Test void decisionMetadataRequiresActualSchemaIdentityAndOriginalRuleClassification()throws Throwable{
        validateDecisionMetadata("1.0.0",schema.manifest(),"INTERNAL");
        assertThrows(TenantAccessException.class,()->validateDecisionMetadata("2.0.0",schema.manifest(),"INTERNAL"));
        assertThrows(TenantAccessException.class,()->validateDecisionMetadata("1.0.0","f".repeat(64),"INTERNAL"));
        assertThrows(TenantAccessException.class,()->validateDecisionMetadata("1.0.0",schema.manifest(),"PUBLIC"));
    }

}
