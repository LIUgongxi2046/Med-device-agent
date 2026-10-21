package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.IdentityBindingSchema;
import com.ruoyi.medical.persistence.mapper.AgentReleaseMapper;
import com.ruoyi.medical.persistence.mapper.InvitationMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real StartRun is minted by the control authority; Stored, JDBC and mTLS boundaries are doubles.
 * Real shared release-scope, invitation-schema and permission validators are exercised, not live DB/TLS. */
class AgentReleaseScopeControlTest {
    FormalRunControlAccessTest control;
    FormalWorkerAccessTest f;
    final PermissionSchema json=new PermissionSchema();
    final InvitationSchema invitationSchema=new InvitationSchema(json,new IdentityBindingSchema());
    final AgentReleaseMapper mapper=mock(AgentReleaseMapper.class);
    final AgentReleaseStore releases=mock(AgentReleaseStore.class);
    final InvitationMapper invitations=mock(InvitationMapper.class);
    AgentReleaseScopeAuthority authority;
    AgentReleaseStore.Stored stored;
    AgentReleaseProtocol.ReleaseScope approved;
    AgentReleaseStoredProtocol.ScopeRow scope;
    PermissionProtocol.GrantRow grant;
    PermissionProtocol.Condition condition;
    InvitationProtocol.RoleRow role;
    InvitationProtocol.Row invitation;
    final LocalDateTime now=LocalDateTime.of(2026,10,19,12,0);
    final DependencyLineage.Limits limits=new DependencyLineage.Limits(10,20);
    AgentReleaseScopeControlTest()throws Exception{}
    @BeforeEach void setup()throws Exception{
        control=new FormalRunControlAccessTest();control.setup();f=control.f;
        var permissions=new PermissionAuthority(json);
        authority=new AgentReleaseScopeAuthority(mapper,releases,f.permissions,invitations,
                new ResourceReadAuthority(f.permissions,permissions,json),permissions,json,invitationSchema);
        stored=mock(AgentReleaseStore.Stored.class,RETURNS_DEEP_STUBS);
        when(stored.purpose()).thenReturn("RUN_CREATION");when(stored.agent().projectId()).thenReturn(f.project);
        when(stored.release().releaseId()).thenReturn(f.id(62));when(stored.release().releasedAt()).thenReturn(now.minusDays(2));
        when(releases.manifestSha256()).thenReturn("a".repeat(64));
        when(f.permissions.projectStatus(f.project)).thenReturn(List.of("ACTIVE"));
        when(f.permissions.databaseTime()).thenReturn(now);
        when(f.permissions.resourceOwner("TASK",f.task)).thenReturn(List.of(new PermissionProtocol.OwnerRow(f.task,f.project)));
        condition=condition("RUN_CREATION");grant=grant(condition,"ACTIVE");
        when(f.permissions.resourceGrants(f.actor,"task.run")).thenReturn(List.of(grant));
        audience("MEMBERS","ACTIVE");
    }
    @AfterEach void cleanup(){control.cleanup();}
    PermissionProtocol.Condition condition(String purpose){return new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of(purpose),new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),List.of(),List.of()));}
    PermissionProtocol.GrantRow grant(PermissionProtocol.Condition c,String status)throws Exception{return new PermissionProtocol.GrantRow(f.id(80),f.actor,f.project,"TASK",f.task,"task.run",json.canonicalObject(c),status,now.minusDays(1),null,f.id(81),"1.0.0",json.manifestSha256(),BigInteger.ONE);}
    void audience(String mode,String status)throws Exception{
        approved=new AgentReleaseProtocol.ReleaseScope(f.tenant,"PROJECTS",List.of(f.project),mode,
                "MEMBERS".equals(mode)?List.of(f.actor):List.of(),"ROLES".equals(mode)?List.of(f.id(82)):List.of(),
                new AgentReleaseProtocol.VersionRef(f.id(83),"b".repeat(64)),List.of("INTERNAL"));
        when(stored.content().manifest().releaseScope()).thenReturn(approved);
        String raw=json.canonicalObject(approved),hash=DigestUtils.sha256Hex(raw);
        scope=new AgentReleaseStoredProtocol.ScopeRow(f.tenant,f.project,f.id(84),f.id(62),raw,status,hash,"1.0.0","a".repeat(64),f.actor,now.minusDays(1),"INTERNAL",BigInteger.ONE,now,f.project,"RELEASE_SCOPE",hash);
        when(mapper.scopes(anyMap())).thenReturn(List.of(scope));
    }
    void roleAudience(String invitationStatus,LocalDateTime roleUntil)throws Exception{
        audience("ROLES","ACTIVE");
        var definition=new InvitationProtocol.RoleDefinition("1.0.0",f.id(82),List.of("task.run"),condition);
        String raw=json.canonicalObject(definition),hash=DigestUtils.sha256Hex(raw);
        role=new InvitationProtocol.RoleRow(f.id(85),f.id(82),raw,"ACTIVE",f.id(86),now.minusDays(2),now.minusDays(1),roleUntil,"1.0.0",invitationSchema.manifestSha256(),BigInteger.ONE);
        when(invitations.role(f.id(85))).thenReturn(List.of(role));
        when(f.permissions.basis(f.id(85))).thenReturn(List.of(new PermissionProtocol.VersionRow(f.id(85),null,hash)));
        var refs=List.of(new InvitationProtocol.RoleReference(f.id(82),f.id(85),hash));
        var authorizations=List.of(new InvitationProtocol.Authorization(f.id(82),f.id(85),"task.run",condition,f.id(88),"1",null));
        invitation=new InvitationProtocol.Row(f.id(87),f.actor,f.id(89),json.canonicalObject(refs),json.canonicalObject(f.resource),json.canonicalObject(authorizations),f.id(81),"c".repeat(64),now.plusDays(1),invitationStatus,f.id(86),now.minusDays(1),"1.0.0",invitationSchema.manifestSha256(),BigInteger.ONE);
        when(invitations.currentInvitation(f.id(87))).thenReturn(List.of(invitation));
        when(mapper.roleOrigins(anyMap())).thenReturn(List.of(new AgentReleaseStoredProtocol.RoleOrigin(f.id(80),f.id(85),f.id(82),f.id(87))));
        when(f.permissions.currentGrant(f.id(80))).thenReturn(List.of(grant));
    }
    AgentReleaseScopeAuthority.VerifiedScope require()throws Exception{
        return control.access.withReads(f.http,f.tenant,f.run,control.command,reads->reads.execute(a->{
            when(stored.admission()).thenReturn(a);
            try{return authority.requireStartRun(stored,f.resource,"INTERNAL",limits,a.controlProof().startRun());}
            catch(java.io.IOException e){throw new IllegalStateException(e);}
        }));
    }
    @Test void currentMemberAudienceAndSeparateRunCreationGrantPassSharedValidator()throws Exception{
        var proof=require();assertEquals(grant,proof.grant());assertEquals(List.of(scope),proof.scopes());assertTrue(proof.roles().isEmpty());
        verify(mapper,never()).roleOrigins(anyMap());verify(releases,times(2)).requireUnchanged(stored);
    }
    @Test void currentRoleAndAcceptedInvitationPassSharedValidator()throws Exception{
        roleAudience("ACCEPTED",null);var proof=require();assertEquals(1,proof.roles().size());assertEquals(invitation,proof.roles().get(0).invitation());assertEquals(role,proof.roles().get(0).role());
    }
    @Test void inactivePersistentReleaseScopeIsNotAudienceAuthority()throws Exception{
        audience("MEMBERS","DISABLED");assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::require).code());
    }
    @Test void revokedInvitationCannotPreserveRoleAudience()throws Exception{
        roleAudience("REVOKED",null);assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::require).code());
    }
    @Test void roleValidityEndsAtDatabaseTime()throws Exception{
        roleAudience("ACCEPTED",now);assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::require).code());
    }
    @Test void executionGrantDoesNotReplaceCreationGrant()throws Exception{
        when(f.permissions.resourceGrants(f.actor,"task.run")).thenReturn(List.of(grant(condition("TASK_EXECUTION"),"ACTIVE")));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,this::require).code());verify(mapper,never()).scopes(anyMap());
    }
    @Test void revokedCurrentRoleGrantCannotPreserveAcceptedInvitation()throws Exception{
        roleAudience("ACCEPTED",null);when(f.permissions.currentGrant(f.id(80))).thenReturn(List.of(grant(condition,"REVOKED")));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::require).code());
    }
    @Test void otherReleaseCannotUseStartRunCapability()throws Exception{
        when(stored.release().releaseId()).thenReturn(f.id(99));assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,this::require).code());verify(mapper,never()).scopes(anyMap());
    }
    @Test void otherTaskCannotUseStartRunCapability()throws Exception{
        control.access.withReads(f.http,f.tenant,f.run,control.command,reads->reads.execute(a->{when(stored.admission()).thenReturn(a);
            assertThrows(TenantAccessException.class,()->authority.requireStartRun(stored,new PermissionProtocol.Scope(f.tenant,f.project,"TASK",f.id(99)),"INTERNAL",limits,a.controlProof().startRun()));return null;}));
        verify(mapper,never()).scopes(anyMap());
    }
    @Test void changedScopeStatusFailsRevalidationWithinSameCapability()throws Exception{
        control.access.withReads(f.http,f.tenant,f.run,control.command,reads->reads.execute(a->{when(stored.admission()).thenReturn(a);
            try{var proof=authority.requireStartRun(stored,f.resource,"INTERNAL",limits,a.controlProof().startRun());audience("MEMBERS","DISABLED");assertThrows(TenantAccessException.class,()->authority.requireUnchanged(proof));}
            catch(Exception e){throw new IllegalStateException(e);}return null;}));
    }
}
