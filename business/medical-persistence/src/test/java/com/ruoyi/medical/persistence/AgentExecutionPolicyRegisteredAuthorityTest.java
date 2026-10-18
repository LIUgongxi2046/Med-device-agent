package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real policy-store ledger/permission checks; worker control and capability identity are explicit proof-boundary fixtures. */
final class AgentExecutionPolicyRegisteredAuthorityTest {
 final AgentExecutionPolicyStoreTest f=new AgentExecutionPolicyStoreTest();
 final FormalWorkerControlAuthority.VerifiedControl control=mock(FormalWorkerControlAuthority.VerifiedControl.class);
 final FormalCapabilityIdentityAuthority capabilities=mock(FormalCapabilityIdentityAuthority.class);
 final AgentExecutionPolicyRegisteredAuthority authority=new AgentExecutionPolicyRegisteredAuthority(f.store,f.schema,f.json,capabilities);
 AgentExecutionPolicyRegisteredAuthorityTest()throws Exception{
  f.decide("APPROVE",f.id(110));f.currentActor=f.id(778);var d=f.fixed.definition();
  when(control.tenantId()).thenReturn(f.tenant);when(control.actorId()).thenReturn(f.currentActor);when(control.projectId()).thenReturn(d.projectId());
  when(control.policyVersion()).thenReturn(f.version);when(control.policySha256()).thenReturn(f.fixed.definitionSha256());when(control.identityMode()).thenReturn("local");
  when(control.businessPurpose()).thenReturn(d.businessPurpose());when(control.serviceUri()).thenReturn(d.worker().serviceUri());when(control.audience()).thenReturn(d.worker().audience());when(control.runOnly()).thenReturn(true);
  var condition=new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of("TASK_EXECUTION"),new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),null,null));
  var grant=new PermissionProtocol.GrantRow(f.id(779),f.currentActor,d.projectId(),"CONFIGURATION",f.policy,"configuration.read",f.json.canonicalObject(condition),"ACTIVE",f.created.minusDays(1),f.created.plusDays(1),f.id(9),"1.0.0",f.json.manifestSha256(),BigInteger.ONE);
  when(f.permissions.resourceOwner("CONFIGURATION",f.policy)).thenReturn(List.of(new PermissionProtocol.OwnerRow(f.policy,d.projectId())));when(f.permissions.resourceGrants(f.currentActor,"configuration.read")).thenReturn(List.of(grant));
  for(var node:d.bindings()){var role=mock(FormalCapabilityIdentityAuthority.RegisteredIdentity.class);when(role.version()).thenReturn(node.roleVersion());when(role.metadataSha256()).thenReturn("a".repeat(64));when(capabilities.requireRegisteredIdentity(f.tenant,f.currentActor,node.roleVersion(),"ROLE",d.projectId())).thenReturn(role);}
 }
 AgentExecutionPolicyRegisteredAuthority.RegisteredPolicyIdentity read()throws Exception{return authority.requireRegisteredPolicyIdentity(control);}
 @Test void realApprovedPolicyProvidesFixedOwnerAndNode()throws Exception{var p=read();assertEquals(new PermissionProtocol.Scope(f.tenant,f.fixed.definition().projectId(),"CONFIGURATION",f.policy),p.ownerScope());assertEquals(f.fixed.definition().bindings(),p.bindings());var n=p.bindings().get(0);assertEquals(n,p.requireNode(n.nodeKind(),n.roleVersion(),p.worker().serviceUri(),p.worker().audience()));verify(control,atLeastOnce()).requireLiteralMetadataPermission(p.ownerScope(),"TASK_EXECUTION");}
 @Test void changedControlPolicyHashRejects(){when(control.policySha256()).thenReturn("c".repeat(64));assertThrows(TenantAccessException.class,this::read);}
 @Test void wrongProjectRejects(){when(control.projectId()).thenReturn(f.id(999));assertThrows(TenantAccessException.class,this::read);}
 @Test void wrongServiceOrAudienceRejects(){when(control.serviceUri()).thenReturn("spiffe://wrong/service");assertThrows(TenantAccessException.class,this::read);when(control.serviceUri()).thenReturn(f.fixed.definition().worker().serviceUri());when(control.audience()).thenReturn("wrong");assertThrows(TenantAccessException.class,this::read);}
 @Test void cannotSubstituteBusinessPurpose(){when(control.businessPurpose()).thenReturn("MODEL_SEND");assertThrows(TenantAccessException.class,this::read);}
 @Test void missingOriginalAuthorLedgerRejects(){f.journals.remove(f.authorRecord);assertThrows(TenantAccessException.class,this::read);}
 @Test void missingDecisionLedgerRejects(){f.journals.remove(f.state.latestDecisionId());assertThrows(TenantAccessException.class,this::read);}
 @Test void revokedCurrentReadRejects(){when(f.permissions.resourceGrants(f.currentActor,"configuration.read")).thenReturn(List.of());assertThrows(TenantAccessException.class,this::read);}
 @Test void currentApproverGrantStillRequired(){f.grants.clear();assertThrows(TenantAccessException.class,this::read);}
 @Test void lateControlRevocationInvalidatesProof()throws Exception{var p=read();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(control).requireUnchanged();assertThrows(TenantAccessException.class,p::requireUnchanged);}
 @Test void disabledPolicyCannotRemainExecutable()throws Exception{var p=read();f.decide("DISABLE",f.id(111));assertThrows(TenantAccessException.class,p::requireUnchanged);}
 @Test void foreignNodeAndRunOnlyDerivedArtifactReject()throws Exception{var p=read();assertThrows(TenantAccessException.class,()->p.requireNode("N06",new PermissionProtocol.Basis(f.id(999),"d".repeat(64)),p.worker().serviceUri(),p.worker().audience()));assertThrows(TenantAccessException.class,()->p.requireDerivedArtifact("N00","DOCUMENT_BODY","artifact.read","DOCUMENT_BODY"));}
 @Test void ownsOnlyExactMainAndGeneratedComponentVersions()throws Exception{var p=read();assertTrue(p.ownsVersion(f.version,f.fixed.definitionSha256()));for(var c:f.schema.components(f.fixed.definition()))assertTrue(p.ownsVersion(c.version().version_id(),c.version().content_sha256()));}
 @Test void membershipRejectsWrongHashesForeignIdsAndOrdinaryDependencies()throws Exception{var p=read();assertFalse(p.ownsVersion(f.version,"f".repeat(64)));for(var c:f.schema.components(f.fixed.definition()))assertFalse(p.ownsVersion(c.version().version_id(),"f".repeat(64)));assertFalse(p.ownsVersion(f.id(999),f.fixed.definitionSha256()));for(var b:f.schema.dependencies(f.fixed.definition()))assertFalse(p.ownsVersion(b.version_id(),b.content_sha256()));assertFalse(p.ownsVersion(null,null));}
 @Test void membershipRechecksCurrentControlAndApproval()throws Exception{var p=read();f.decide("DISABLE",f.id(111));assertThrows(TenantAccessException.class,()->p.ownsVersion(f.version,f.fixed.definitionSha256()));}
}
