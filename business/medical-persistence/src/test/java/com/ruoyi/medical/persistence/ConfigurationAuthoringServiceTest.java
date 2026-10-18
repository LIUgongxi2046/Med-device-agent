package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Offline service tests: transaction, JDBC, identity admission and mapper storage are explicit doubles.
 * Schemas, current grants, source rights, artifact registration, version ownership and lineage are real.
 * All authorities below are test-only rows; this neither provisions production grants nor proves DB/S3 acceptance.
 */
class ConfigurationAuthoringServiceTest {
    private static final String TENANT=id(1),ACTOR=id(2),PROJECT=id(3),SOURCE=id(4),SOURCE_VERSION=id(5),
            RIGHTS=id(6),ARTIFACT=id(7),ACCESS_SET=id(8),MODEL=id(9),MODEL_HEAD=id(10),CONTEXT_ID=id(11),
            HASH="a".repeat(64),KEY="offline-authoring-0001",SCHEMA_REF="urn:medical:templates:1.0.0";
    private static final LocalDateTime NOW=LocalDateTime.of(2026,10,18,0,0);
    private PermissionSchema json;
    private ConfigurationAuthoringSchema schema;
    private ConfigurationAuthoringService service;
    private ConfigurationAuthoringMapper mapper;
    private PermissionMapper permissions;
    private SourceReadMapper sources;
    private ArtifactRegistryMapper artifacts;
    private ModelConfigurationService models;
    private ApplicationSessionIdentity.Verified subject;
    private BusinessSessionProtocol.Context context;
    private Store store;
    private BigInteger governance;
    private String schemaDigest,sourceLifecycle,rightsStatus,grantPurpose,grantStatus;
    private LocalDateTime grantExpiry;
    private final Set<String> deniedActions=new HashSet<>();
    private boolean failHead,failGovernance,failContext,failOutbox,wrongContextRead,wrongWatermark,worker,unverifiedScope;
    private boolean corruptSavedOperation;

    @BeforeEach @SuppressWarnings({"unchecked","rawtypes"}) void setup()throws Exception {
        json=new PermissionSchema();schema=new ConfigurationAuthoringSchema(json);store=new Store();
        context=new BusinessSessionProtocol.Context(TENANT,"1","1","1");governance=BigInteger.ONE;
        sourceLifecycle="ACTIVE";rightsStatus="APPROVED";grantStatus="ACTIVE";
        subject=mock(ApplicationSessionIdentity.Verified.class);var binding=mock(AuthenticatedIdentityBinding.class);
        when(subject.user()).thenReturn(new BusinessSessionProtocol.User(ACTOR,"offline-test","Offline Test","USER"));
        when(subject.sessionId()).thenReturn("offline-authoring-session");when(subject.binding()).thenReturn(binding);
        when(binding.view()).thenReturn(new AuthenticatedIdentityBinding.View("local",ACTOR,null,null));
        mapper=mock(ConfigurationAuthoringMapper.class);permissions=mock(PermissionMapper.class);
        sources=mock(SourceReadMapper.class);artifacts=mock(ArtifactRegistryMapper.class);models=mock(ModelConfigurationService.class);
        var evaluations=mock(EvaluationMapper.class);var owners=mock(VersionResourceMapper.class);
        when(permissions.databaseTime()).thenReturn(NOW);
        when(permissions.projectStatus(PROJECT)).thenReturn(List.of("ACTIVE"));
        when(permissions.resourceOwner(anyString(),anyString())).thenAnswer(c->resourceOwner(c.getArgument(0),c.getArgument(1)));
        when(permissions.resourceGrants(eq(ACTOR),anyString())).thenAnswer(c->deniedActions.contains(c.<String>getArgument(1))?List.of():List.of(grant(c.getArgument(1))));
        when(permissions.saveAudit(anyString(),nullable(String.class),anyString(),anyString(),anyString(),anyString(),anyString(),anyString()))
                .thenAnswer(c->{write();assertEquals("configuration.draft_created",c.getArgument(3));store.audits.put(c.getArgument(2),c.getArgument(4));return 1;});
        when(permissions.saveEvent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString()))
                .thenAnswer(c->{write();if(failOutbox)throw new IllegalStateException("OFFLINE_OUTBOX_FAILURE");
                    assertEquals("configuration.draft_created",c.getArgument(4));store.events.put(c.getArgument(1),c.getArgument(5));return 1;});
        when(artifacts.version(anyString())).thenAnswer(c->optional(store.versions.get(c.getArgument(0))));
        when(sources.parents(anyString())).thenAnswer(c->List.copyOf(store.parents.getOrDefault(c.getArgument(0),List.of())));
        when(sources.source(anyString())).thenAnswer(c->SOURCE_VERSION.equals(c.getArgument(0))
                ?List.of(new SourceReadProtocol.SourceRow(TENANT,SOURCE,SOURCE_VERSION,null,"ENTERPRISE","INTERNAL",RIGHTS,HASH,HASH,null)):List.of());
        var rightsSchema=new SourceRightsSchema();
        when(sources.rights(RIGHTS)).thenAnswer(c->List.of(rights(rightsSchema)));
        when(sources.gates(SOURCE_VERSION)).thenAnswer(c->List.of(new SourceReadProtocol.GateRow(id(12),"RECEIVED","APPROVED",sourceLifecycle,BigInteger.ONE)));
        when(owners.ownersAcrossDomains(anyList(),anyList())).thenAnswer(c->{List<String> ids=c.getArgument(0);return ids.stream().map(this::owner).toList();});
        var resourceAuthority=new ResourceReadAuthority(permissions,new PermissionAuthority(json),json);
        var lineage=new DependencyLineage(sources,artifacts);
        var manifestSchema=new DependencyManifestSchema();
        seedArtifact(manifestSchema);
        var limits=new EvaluationDependencyPolicy("OFFLINE_TEST","LOCAL",20,30,
                List.of(new EvaluationDependencyPolicy.ArtifactProfile("ARTIFACT","artifact.read",SCHEMA_REF,schemaDigest,HASH)));
        var registry=new ArtifactRegistry(artifacts,resourceAuthority,manifestSchema,lineage);
        var references=new EvaluationReferences(lineage,new VersionResourceAuthority(owners,resourceAuthority,registry),
                new SourceReadAuthority(sources,permissions,resourceAuthority,rightsSchema),sources,artifacts,limits);
        when(evaluations.configurationStates(anyMap())).thenAnswer(c->{Map<String,Object> values=c.getArgument(0);
            List<String> ids=(List<String>)values.get("versionIds");var rows=new ArrayList<SimulationEvaluationCandidateAuthority.ConfigurationState>();
            for(String id:ids){var row=store.rows.get(id);if(row!=null)rows.add(new SimulationEvaluationCandidateAuthority.ConfigurationState(id,row.kind(),row.status(),row.headStatus(),null));
                else if(MODEL.equals(id))rows.add(new SimulationEvaluationCandidateAuthority.ConfigurationState(id,"MODEL","DRAFT","DRAFT","SIMULATION"));}return rows;});
        when(models.read(any(EnterpriseAccess.Admission.class),eq(MODEL),eq(true))).thenAnswer(c->{
            // Existing model read is a bounded dependency double, never an executor or provider call.
            resourceAuthority.require(c.getArgument(0),new PermissionProtocol.Scope(TENANT,null,"CONFIGURATION",MODEL_HEAD),"configuration.read",ConfigurationAuthoringService.DEPENDENCY_PURPOSE);
            return json.tree(new InterfaceProtocol.Envelope("1.0.0",id(13),"1",json.object().put("content_sha256",HASH)));});
        wireStore();
        var watermarks=mock(TenantWatermarkMapper.class);
        when(watermarks.lockCurrentTenant()).thenAnswer(c->new TenantWatermarks(BigInteger.ONE,wrongWatermark?governance.add(BigInteger.ONE):governance));
        var access=mock(EnterpriseAccess.class);
        when(access.execute(eq(subject),any())).thenAnswer(c->transaction(c.getArgument(1)));
        try(var construction=mockConstruction(JdbcTemplate.class,(jdbc,ignored)->{
            when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(c->{write();Object[] args=(Object[])c.getRawArguments()[1];
                assertEquals(CONTEXT_ID,args[2]);assertEquals(subject.sessionId(),args[3]);assertEquals(ACTOR,args[4]);assertEquals(context.tenant_id(),args[5]);
                if(failContext||!context.context_revision().equals(args[6])||!context.auth_epoch().equals(args[7])||!context.governance_epoch().equals(args[8]))return 0;
                context=new BusinessSessionProtocol.Context((String)args[5],(String)args[0],context.auth_epoch(),(String)args[1]);return 1;});
            when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(c->wrongContextRead?List.of():List.of(context));
        })) {
            service=new ConfigurationAuthoringService(access,mapper,evaluations,artifacts,models,permissions,resourceAuthority,
                    references,lineage,limits,json,schema,new IdentityDirectoryPolicy("INTERNAL"),watermarks,mock(DataSource.class));
            assertEquals(1,construction.constructed().size());
        }
    }

    @Test void createReadAndExactReplayPreserveFixedDraftAndSingleAudit()throws Exception {
        var request=capability("ROLE");var response=create(request,"1",KEY,false);var receipt=response.required("data");
        assertEquals("DRAFT",receipt.path("status").textValue());assertFalse(receipt.path("production_eligible").booleanValue());
        assertEquals("2",context.context_revision());assertEquals(BigInteger.TWO,governance);
        assertEquals(1,store.rows.size());assertEquals(1,store.heads.size());assertEquals(1,store.operations.size());
        assertEquals(1,store.audits.size());assertEquals(1,store.events.size());
        String version=receipt.path("version_id").textValue();var view=service.read(subject,"2",version,false).required("data");
        assertEquals(request,view.at("/definition/request"));assertEquals(ACTOR,view.at("/definition/created_by").textValue());
        assertEquals(schema.hash(view.required("definition")),receipt.path("content_sha256").textValue());
        assertEquals(List.of(new SourceReadProtocol.ParentRow(version,ARTIFACT,"CAPABILITY_SOURCE_ARTIFACT")),store.parents.get(version));
        int writes=store.writes;assertEquals(response,create(request,"1",KEY,false));assertEquals(writes,store.writes);
        assertEquals("2",context.context_revision());verify(sources,atLeast(3)).rights(RIGHTS);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());assertThrows(TenantAccessException.class,TenantScope::requireCurrent);
        verifyNoInteractions(models);
    }

    @Test void sessionBodyAndContextArePartOfReplayIdentity()throws Exception {
        var request=capability("ROLE");create(request,"1",KEY,false);int writes=store.writes;
        deny(ErrorCode.IDEMPOTENCY_CONFLICT,()->create(capability("PROMPT"),"1",KEY,false));
        deny(ErrorCode.IDEMPOTENCY_CONFLICT,()->create(request,"2",KEY,false));
        when(subject.sessionId()).thenReturn("different-current-session");
        deny(ErrorCode.IDEMPOTENCY_CONFLICT,()->create(request,"1",KEY,false));assertEquals(writes,store.writes);
    }

    @Test void staleContextAndUnverifiedTenantScopeCannotWrite()throws Exception {
        deny(ErrorCode.CONTEXT_CHANGED,()->create(capability("ROLE"),"0",KEY,false));assertClean();
        unverifiedScope=true;deny(ErrorCode.SCOPE_STALE,()->create(capability("ROLE"),"1",KEY,false));assertClean();
    }

    @Test void workerAdmissionCannotAuthorEvenWithOtherwiseValidGrants()throws Exception {
        worker=true;deny(ErrorCode.ACCESS_DENIED,()->create(capability("ROLE"),"1",KEY,false));assertClean();
        verify(mapper,never()).currentOperation(anyMap());verify(permissions,never()).resourceGrants(anyString(),anyString());
    }

    @Test void creationRequiresCurrentConfigureReadAndSourceGrants()throws Exception {
        for(String action:List.of("capability.configure","configuration.read","source.read","artifact.read")) {
            deniedActions.add(action);deny(ErrorCode.ACCESS_DENIED,()->create(capability("ROLE"),"1",KEY,false));assertClean();deniedActions.clear();
        }
        grantPurpose="EVALUATION_RESULTS";deny(ErrorCode.ACCESS_DENIED,()->create(capability("ROLE"),"1",KEY,false));assertClean();
        grantPurpose=null;grantStatus="REVOKED";
        deny(ErrorCode.ACCESS_DENIED,()->create(capability("ROLE"),"1",KEY,false));assertClean();
        grantStatus="ACTIVE";grantExpiry=NOW;deny(ErrorCode.ACCESS_DENIED,()->create(capability("ROLE"),"1",KEY,false));assertClean();
    }

    @Test void readAndReplayRecheckRevokedGrantsRightsAndSourceLifecycle()throws Exception {
        var request=capability("ROLE");var response=create(request,"1",KEY,false);String version=response.at("/data/version_id").textValue();int writes=store.writes;
        deniedActions.add("capability.configure");deny(ErrorCode.ACCESS_DENIED,()->create(request,"1",KEY,false));
        assertDoesNotThrow(()->service.read(subject,"2",version,false));deniedActions.clear();
        for(String action:List.of("configuration.read","source.read","artifact.read")) {
            deniedActions.add(action);deny(ErrorCode.ACCESS_DENIED,()->service.read(subject,"2",version,false));
            deny(ErrorCode.ACCESS_DENIED,()->create(request,"1",KEY,false));deniedActions.clear();
        }
        rightsStatus="REVOKED";deny(ErrorCode.ACCESS_DENIED,()->service.read(subject,"2",version,false));
        deny(ErrorCode.ACCESS_DENIED,()->create(request,"1",KEY,false));rightsStatus="APPROVED";
        sourceLifecycle="PURGE_PENDING";deny(ErrorCode.ACCESS_DENIED,()->service.read(subject,"2",version,false));
        deny(ErrorCode.ACCESS_DENIED,()->create(request,"1",KEY,false));assertEquals(writes,store.writes);
    }

    @Test void pausedProjectAndCrossProjectSourceCannotCreate()throws Exception {
        var request=capability("ROLE").put("project_id",PROJECT);
        for(String status:List.of("PAUSED","ARCHIVED")) {
            when(permissions.projectStatus(PROJECT)).thenReturn(List.of(status));
            deny(ErrorCode.ACCESS_DENIED,()->create(request,"1",KEY,false));assertClean();
        }
        when(permissions.projectStatus(PROJECT)).thenReturn(List.of("ACTIVE"));
        store.versions.put(SOURCE_VERSION,new ArtifactRegistryProtocol.VersionRow(SOURCE_VERSION,id(30),"SOURCE_VERSION",HASH));
        deny(ErrorCode.ACCESS_DENIED,()->create(request,"1",KEY,false));assertClean();
    }

    @Test void missingOrChangedUpstreamRegistryAndCyclicGraphCannotCreate()throws Exception {
        var source=store.versions.remove(SOURCE_VERSION);deny(ErrorCode.ACCESS_DENIED,()->create(capability("ROLE"),"1",KEY,false));assertClean();
        store.versions.put(SOURCE_VERSION,source);store.versions.put(ARTIFACT,new ArtifactRegistryProtocol.VersionRow(ARTIFACT,null,"ARTIFACT","b".repeat(64)));
        deny(ErrorCode.CONTENT_MISMATCH,()->create(capability("ROLE"),"1",KEY,false));assertClean();
        store.versions.put(ARTIFACT,new ArtifactRegistryProtocol.VersionRow(ARTIFACT,null,"ARTIFACT",HASH));
        store.parents.put(SOURCE_VERSION,List.of(new SourceReadProtocol.ParentRow(SOURCE_VERSION,ARTIFACT,"OFFLINE_CYCLE")));
        deny(ErrorCode.CONTENT_MISMATCH,()->create(capability("ROLE"),"1",KEY,false));assertClean();
    }

    @Test void headGovernanceSessionAndOutboxFailuresRollbackAllDoubleState()throws Exception {
        failHead=true;deny(ErrorCode.REVISION_CONFLICT,()->create(capability("ROLE"),"1",KEY,false));assertClean();failHead=false;
        failGovernance=true;deny(ErrorCode.REVISION_CONFLICT,()->create(capability("ROLE"),"1",KEY,false));assertClean();failGovernance=false;
        failContext=true;deny(ErrorCode.REVISION_CONFLICT,()->create(capability("ROLE"),"1",KEY,false));assertClean();failContext=false;
        wrongContextRead=true;deny(ErrorCode.CONTENT_MISMATCH,()->create(capability("ROLE"),"1",KEY,false));assertClean();wrongContextRead=false;
        wrongWatermark=true;deny(ErrorCode.CONTENT_MISMATCH,()->create(capability("ROLE"),"1",KEY,false));assertClean();wrongWatermark=false;
        failOutbox=true;assertThrows(IllegalStateException.class,()->create(capability("ROLE"),"1",KEY,false));assertClean();failOutbox=false;
        assertDoesNotThrow(()->create(capability("ROLE"),"1",KEY,false));assertEquals(1,store.rows.size());
    }

    @Test void storedProjectionRegistryAndTypedLineageAreReconstructedOnReadAndReplay()throws Exception {
        var request=capability("ROLE");var response=create(request,"1",KEY,false);String version=response.at("/data/version_id").textValue();
        var parents=store.parents.remove(version);denyReadAndReplay(version,request);store.parents.put(version,parents);
        store.parents.put(version,List.of(new SourceReadProtocol.ParentRow(version,ARTIFACT,"UNTRUSTED_RELATION")));denyReadAndReplay(version,request);store.parents.put(version,parents);
        var row=store.rows.get(version);var projection=(ObjectNode)schema.read(row.projection());projection.put("artifact_digest","b".repeat(64));
        store.rows.put(version,withProjection(row,json.canonical(projection)));denyReadAndReplay(version,request);store.rows.put(version,row);
        store.versions.put(version,new ArtifactRegistryProtocol.VersionRow(version,null,"CAPABILITY_VERSION","b".repeat(64)));denyReadAndReplay(version,request);
    }

    @Test void foreignTenantCannotReadEvenIfStorageDoubleReturnsKnownRow()throws Exception {
        var response=create(capability("ROLE"),"1",KEY,false);context=new BusinessSessionProtocol.Context(id(31),"2","1","2");
        deny(ErrorCode.CONTENT_MISMATCH,()->service.read(subject,"2",response.at("/data/version_id").textValue(),false));
    }

    @Test void replayRejectsReceiptIdentityAndPublicationTampering()throws Exception {
        var request=capability("ROLE");create(request,"1",KEY,false);String key=operationKey(false,KEY);var original=store.operations.get(key);
        for(String field:List.of("version_id","config_id","operation_id","tenant_id")) {
            var response=(ObjectNode)schema.read(original.responseRef());((ObjectNode)response.required("data")).put(field,id(40));
            store.operations.put(key,withResponse(original,json.canonical(response)));
            deny(ErrorCode.CONTENT_MISMATCH,()->create(request,"1",KEY,false));
        }
        var response=(ObjectNode)schema.read(original.responseRef());((ObjectNode)response.required("data")).put("production_eligible",true);
        store.operations.put(key,withResponse(original,json.canonical(response)));
        assertThrows(RuntimeException.class,()->create(request,"1",KEY,false));
        response=(ObjectNode)schema.read(original.responseRef());((ObjectNode)response.required("data")).put("runtime_token","forged");
        store.operations.put(key,withResponse(original,json.canonical(response)));
        assertThrows(RuntimeException.class,()->create(request,"1",KEY,false));
    }

    @Test void persistedOperationMetadataIsVerifiedBeforeReturningSuccess()throws Exception {
        corruptSavedOperation=true;deny(ErrorCode.CONTENT_MISMATCH,()->create(capability("ROLE"),"1",KEY,false));assertClean();
    }

    @Test void agentFreezesActualAuthoredCapabilitiesAndTransitiveDependencies()throws Exception {
        var role=create(capability("ROLE"),"1",KEY,false).required("data");
        var prompt=create(capability("PROMPT"),"2",KEY+"-prompt",false).required("data");
        seedModel();var request=agent(role,prompt);var response=create(request,"3",KEY+"-agent",true);var receipt=response.required("data");
        assertFalse(receipt.path("production_eligible").booleanValue());assertEquals("DRAFT",receipt.path("status").textValue());
        String version=receipt.path("version_id").textValue();var view=service.read(subject,"4",version,true).required("data");
        assertEquals(request,view.at("/definition/request"));assertEquals(schema.hash(view.required("definition")),receipt.path("content_sha256").textValue());
        var ids=new HashSet<String>();view.at("/definition/dependency_manifest/dependencies").forEach(ref->ids.add(ref.path("version_id").textValue()));
        assertEquals(Set.of(role.path("version_id").textValue(),prompt.path("version_id").textValue(),MODEL,ARTIFACT,SOURCE_VERSION),ids);
        assertEquals(Set.of("SIMULATION_ROLE","SIMULATION_PROMPT","SIMULATION_MODEL"),store.parents.get(version).stream().map(SourceReadProtocol.ParentRow::relationKind).collect(java.util.stream.Collectors.toSet()));
        int writes=store.writes;assertEquals(response,create(request,"3",KEY+"-agent",true));assertEquals(writes,store.writes);
        deniedActions.add("agent.configure");deny(ErrorCode.ACCESS_DENIED,()->create(request,"3",KEY+"-agent",true));assertEquals(writes,store.writes);
        verify(models,atLeastOnce()).read(any(EnterpriseAccess.Admission.class),eq(MODEL),eq(true));
    }

    @Test void agentRejectsSwappedRoleAndPromptKindsWithoutWriting()throws Exception {
        var role=create(capability("ROLE"),"1",KEY,false).required("data");
        var prompt=create(capability("PROMPT"),"2",KEY+"-prompt",false).required("data");seedModel();
        int writes=store.writes;
        deny(ErrorCode.CONTENT_MISMATCH,()->create(agent(prompt,role),"3",KEY+"-agent",true));
        assertEquals(writes,store.writes);assertEquals(2,store.rows.size());assertEquals("3",context.context_revision());
        verifyNoInteractions(models);
    }

    @Test void mismatchedArtifactClassificationRejectsCreateReadAndReplay()throws Exception {
        var request=capability("ROLE");var response=create(request,"1",KEY,false);String version=response.at("/data/version_id").textValue();
        var artifact=artifacts.artifact(ARTIFACT).get(0);
        when(artifacts.artifact(ARTIFACT)).thenReturn(List.of(new ArtifactRegistryProtocol.ArtifactRow(artifact.tenantId(),artifact.projectId(),artifact.artifactId(),artifact.artifactKind(),
                artifact.storedObject(),artifact.objectKey(),artifact.objectVersion(),artifact.sha256(),artifact.sizeBytes(),artifact.mediaType(),artifact.accessSetId(),artifact.contentSha256(),
                artifact.schemaVersion(),artifact.schemaManifest(),"RESTRICTED")));
        int writes=store.writes;
        deny(ErrorCode.ACCESS_DENIED,()->create(capability("PROMPT"),"2",KEY+"-prompt",false));
        deny(ErrorCode.ACCESS_DENIED,()->service.read(subject,"2",version,false));
        deny(ErrorCode.ACCESS_DENIED,()->create(request,"1",KEY,false));assertEquals(writes,store.writes);
    }

    private void seedArtifact(DependencyManifestSchema manifests)throws Exception {
        byte[] published;try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("templates.schema.json"))){published=input.readAllBytes();}
        schemaDigest=DigestUtils.sha256Hex(published);
        store.versions.put(SOURCE_VERSION,new ArtifactRegistryProtocol.VersionRow(SOURCE_VERSION,null,"SOURCE_VERSION",HASH));
        store.versions.put(ARTIFACT,new ArtifactRegistryProtocol.VersionRow(ARTIFACT,null,"ARTIFACT",HASH));
        store.parents.put(ARTIFACT,List.of(new SourceReadProtocol.ParentRow(ARTIFACT,SOURCE_VERSION,"SOURCE_CONTENT")));
        var manifest=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",TENANT,null,ACCESS_SET,List.of(new ArtifactRegistryProtocol.MemberRow(SOURCE_VERSION,RIGHTS)));
        String body=json.canonicalObject(manifest),digest=DigestUtils.sha256Hex(body);
        store.versions.put(ACCESS_SET,new ArtifactRegistryProtocol.VersionRow(ACCESS_SET,null,"DEPENDENCY_ACCESS_SET",digest));
        var object=new InterfaceProtocol.ObjectRef("offline-test/source-artifact","offline-object-version",HASH,"1","application/json");
        var artifact=new ArtifactRegistryProtocol.ArtifactRow(TENANT,null,ARTIFACT,"CONFIGURATION_TEST",StoredObjectReferences.encode(object),object.objectKey(),object.objectVersion(),HASH,BigInteger.ONE,object.mediaType(),ACCESS_SET,HASH,"1.0.0",HASH,"INTERNAL");
        when(artifacts.artifact(ARTIFACT)).thenReturn(List.of(artifact));
        when(artifacts.binding(ARTIFACT)).thenReturn(List.of(new ArtifactRegistryProtocol.BindingRow(TENANT,null,ARTIFACT,"ARTIFACT",ARTIFACT,SCHEMA_REF,schemaDigest,ACCESS_SET,digest)));
        when(artifacts.manifest(ACCESS_SET)).thenReturn(List.of(new ArtifactRegistryProtocol.ManifestRow(TENANT,null,ACCESS_SET,body,digest,digest,BigInteger.ONE,"1.0.0",manifests.manifestSha256())));
        when(artifacts.members(ACCESS_SET)).thenReturn(manifest.members());
    }

    private SourceReadProtocol.RightsRow rights(SourceRightsSchema rightsSchema)throws Exception {
        var evidence=json.object().put("owner","offline-test-owner").put("license","offline-test-only");
        evidence.putArray("environments").add("OFFLINE_TEST");evidence.putArray("classifications").add("INTERNAL");
        evidence.putArray("authorization_evidence").add(json.tree(new InterfaceProtocol.ObjectRef("offline-test/evidence","offline-version",HASH,"1","application/json")));
        var body=json.object().put("schema_version","1.0.0").put("tenant_id",TENANT).put("rights_revision",RIGHTS).put("source_version",SOURCE_VERSION)
                .put("purpose",ConfigurationAuthoringService.DEPENDENCY_PURPOSE).put("classification","INTERNAL");
        body.putArray("allowed_actions").add("READ");body.putArray("providers");body.putArray("regions").add("LOCAL");
        body.putNull("valid_from");body.putNull("valid_to");body.set("evidence_manifest",evidence);String digest=schema.hash(body);
        return new SourceReadProtocol.RightsRow(TENANT,RIGHTS,SOURCE_VERSION,ConfigurationAuthoringService.DEPENDENCY_PURPOSE,"[\"READ\"]","[]","[\"LOCAL\"]",null,null,
                rightsStatus,json.canonical(evidence),digest,"1.0.0",rightsSchema.manifestSha256(),"INTERNAL",digest,null);
    }

    private List<PermissionProtocol.OwnerRow> resourceOwner(String kind,String id) {
        if("ENTERPRISE".equals(kind)&&TENANT.equals(id))return List.of(new PermissionProtocol.OwnerRow(id,null));
        if("PROJECT".equals(kind)&&PROJECT.equals(id))return List.of(new PermissionProtocol.OwnerRow(id,id));
        if("SOURCE".equals(kind)&&SOURCE.equals(id)||"ARTIFACT".equals(kind)&&ARTIFACT.equals(id)
                ||"CONFIGURATION".equals(kind)&&MODEL_HEAD.equals(id))return List.of(new PermissionProtocol.OwnerRow(id,null));
        if("CONFIGURATION".equals(kind)&&store.heads.containsKey(id))return List.of(new PermissionProtocol.OwnerRow(id,(String)store.heads.get(id).get("projectId")));
        return List.of();
    }
    private PermissionProtocol.GrantRow grant(String action)throws Exception {
        String purpose=grantPurpose!=null?grantPurpose:action.endsWith(".configure")?ConfigurationAuthoringService.PURPOSE:ConfigurationAuthoringService.DEPENDENCY_PURPOSE;
        var condition=new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of(purpose),new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),null,null));
        return new PermissionProtocol.GrantRow(id(20),ACTOR,null,"ENTERPRISE",TENANT,action,json.canonicalObject(condition),grantStatus,NOW.minusDays(1),grantExpiry,id(21),"1.0.0",json.manifestSha256(),BigInteger.ONE);
    }
    private VersionResourceProtocol.DomainOwnerRow owner(String id) {
        var version=store.versions.get(id);String kind,resource,source=null,project=version.projectId();
        if(SOURCE_VERSION.equals(id)){kind="SOURCE";resource=SOURCE;source=SOURCE_VERSION;}
        else if(ARTIFACT.equals(id)){kind="ARTIFACT";resource=ARTIFACT;}
        else if(MODEL.equals(id)){kind="CONFIGURATION";resource=MODEL_HEAD;}
        else {kind="CONFIGURATION";resource=store.rows.get(id).configId();}
        return new VersionResourceProtocol.DomainOwnerRow(version.entityKind(),new VersionResourceProtocol.OwnerRow(TENANT,id,project,project,kind,resource,version.contentSha256(),source,null,null,null));
    }

    private void wireStore() {
        when(mapper.currentOperation(anyMap())).thenAnswer(c->{Map<String,Object> values=c.getArgument(0);return optional(store.operations.get(operationKey((boolean)values.get("agent"),(String)values.get("key"))));});
        when(mapper.stored(anyMap())).thenAnswer(c->{Map<String,Object> values=c.getArgument(0);String id=(String)values.get("versionId");var row=store.rows.get(id);
            if(row==null)return List.of();String current=(String)store.heads.get(row.configId()).get("current");
            return List.of(new ConfigurationAuthoringProtocol.Stored(row.tenantId(),row.projectId(),row.configId(),row.versionId(),row.kind(),row.name(),row.projection(),row.contentHash(),store.versions.get(id).contentSha256(),row.status(),row.headStatus(),current,row.schemaVersion(),row.manifest(),row.createdBy(),row.createdAt(),row.classification()));});
        when(mapper.parents(anyMap())).thenAnswer(c->{Map<String,Object> values=c.getArgument(0);return store.parents.getOrDefault(values.get("versionId"),List.of()).stream()
                .map(p->new ConfigurationAuthoringProtocol.Parent(p.upstreamVersionId(),p.relationKind(),store.versions.get(p.upstreamVersionId()).contentSha256())).toList();});
        when(mapper.insertHead(anyMap())).thenAnswer(c->{write();Map<String,Object> values=c.getArgument(0);store.heads.put((String)values.get("configId"),new HashMap<>(values));return 1;});
        when(mapper.insertVersion(anyMap())).thenAnswer(c->{write();Map<String,Object> values=c.getArgument(0);String id=(String)values.get("versionId");
            store.versions.put(id,new ArtifactRegistryProtocol.VersionRow(id,(String)values.get("projectId"),(String)values.get("entityKind"),(String)values.get("contentHash")));return 1;});
        when(mapper.insertCapability(anyMap())).thenAnswer(c->insert(c.getArgument(0),false));
        when(mapper.insertAgent(anyMap())).thenAnswer(c->insert(c.getArgument(0),true));
        when(mapper.insertLineage(anyMap())).thenAnswer(c->{write();Map<String,Object> values=c.getArgument(0);String id=(String)values.get("versionId");
            store.parents.computeIfAbsent(id,k->new ArrayList<>()).add(new SourceReadProtocol.ParentRow(id,(String)values.get("upstreamId"),(String)values.get("relation")));return 1;});
        when(mapper.initializeHead(anyMap())).thenAnswer(c->{write();if(failHead)return 0;Map<String,Object> values=c.getArgument(0);
            store.heads.get(values.get("configId")).put("current",values.get("versionId"));return 1;});
        when(mapper.advanceGovernance(anyMap())).thenAnswer(c->{write();Map<String,Object> values=c.getArgument(0);
            if(failGovernance||!governance.toString().equals(values.get("governanceEpoch")))return 0;governance=governance.add(BigInteger.ONE);return 1;});
        when(mapper.saveOperation(anyMap())).thenAnswer(c->{write();Map<String,Object> values=c.getArgument(0);
            store.operations.put(operationKey((boolean)values.get("agent"),(String)values.get("key")),new SelectionOperationRow((String)values.get("operationId"),(String)values.get("requestHash"),
                    corruptSavedOperation?"IN_PROGRESS":"COMPLETED",201,(String)values.get("response"),(String)values.get("versionId"),"1.0.0",schema.manifest()));return 1;});
        when(mapper.audit(anyMap())).thenAnswer(c->{Map<String,Object> values=c.getArgument(0);return optional(store.audits.get(values.get("auditId")));});
        when(mapper.event(anyMap())).thenAnswer(c->{Map<String,Object> values=c.getArgument(0);return optional(store.events.get(values.get("eventId")));});
    }
    private int insert(Map<String,Object> values,boolean agent)throws Exception {
        write();var projection=json.object();
        if(agent){projection.set("dependency_manifest",schema.read((String)values.get("dependencyManifest")));projection.set("limits",schema.read((String)values.get("limits")));}
        else {projection.put("artifact_digest",(String)values.get("artifactDigest"));projection.set("input_schema_ref",schema.read((String)values.get("inputSchema")));
            projection.set("output_schema_ref",schema.read((String)values.get("outputSchema")));projection.set("tool_scope",schema.read((String)values.get("toolScope")));}
        String id=(String)values.get("versionId");store.rows.put(id,new ConfigurationAuthoringProtocol.Stored(TENANT,(String)values.get("projectId"),(String)values.get("configId"),id,
                agent?"AGENT":(String)values.get("kind"),(String)values.get("name"),json.canonical(projection),(String)values.get("contentHash"),(String)values.get("contentHash"),
                "DRAFT","DRAFT",null,"1.0.0",schema.manifest(),ACTOR,NOW,"INTERNAL"));return 1;
    }
    private Object transaction(Function<EnterpriseAccess.Admission,Object> work) {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());var before=store.copy();var beforeContext=context;var beforeGovernance=governance;
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try(var scope=TenantScope.open(new TenantScope.Identity(context.tenant_id(),ACTOR,new BigInteger(context.context_revision()),BigInteger.ONE,governance))) {
            var epochs=new TenantWatermarks(BigInteger.ONE,governance);if(!unverifiedScope)scope.verifyTransaction(epochs);
            try{var admission=worker?new EnterpriseAccess.Admission(mock(SimulationWorkerAccess.Proof.class),context,epochs):new EnterpriseAccess.Admission(subject,CONTEXT_ID,context,epochs);
                return work.apply(admission);
            }catch(RuntimeException|Error failure){store=before;context=beforeContext;governance=beforeGovernance;throw failure;}
            finally{scope.finishTransaction();}
        }finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
    }
    private void write(){assertTrue(TransactionSynchronizationManager.isActualTransactionActive());assertEquals(TENANT,TenantScope.requireCurrent().tenantId());store.writes++;}
    private ObjectNode capability(String kind) {
        var node=json.object().put("schema_version","1.0.0").put("execution_mode","SIMULATION").putNull("project_id").put("kind",kind);
        node.set("source_artifact",json.tree(new PermissionProtocol.Basis(ARTIFACT,HASH)));
        var binding=json.object().put("schema_ref",SCHEMA_REF).put("schema_sha256",schemaDigest);
        node.set("input_schema_ref",binding);node.set("output_schema_ref",binding.deepCopy());node.putArray("tool_scope");return node;
    }
    private void seedModel(){store.versions.put(MODEL,new ArtifactRegistryProtocol.VersionRow(MODEL,null,"MODEL_CONFIG",HASH));}
    private ObjectNode agent(JsonNode role,JsonNode prompt)throws Exception {
        var node=json.object().put("schema_version","1.0.0").put("execution_mode","SIMULATION").putNull("project_id").put("name","Offline candidate");
        node.set("role_version",json.tree(new PermissionProtocol.Basis(role.path("version_id").textValue(),role.path("content_sha256").textValue())));
        node.set("prompt_revision",json.tree(new PermissionProtocol.Basis(prompt.path("version_id").textValue(),prompt.path("content_sha256").textValue())));
        node.set("model_config_revision",json.tree(new PermissionProtocol.Basis(MODEL,HASH)));
        var limits=json.object();try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("configuration-authoring.schema.json"))) {
            var definition=schema.read(new String(input.readAllBytes(),StandardCharsets.UTF_8));
            definition.at("/$defs/Limits/properties").fields().forEachRemaining(e->{var property=e.getValue();
                if(property.has("const"))limits.set(e.getKey(),property.get("const"));
                else if(property.path("$ref").asText().endsWith("/Seconds"))limits.put(e.getKey(),30);
                else limits.put(e.getKey(),e.getKey().equals("currency")?"CNY":"10");});
        }
        limits.put("heartbeat_interval_seconds",5).put("max_total_tokens","20");node.set("limits",limits);return node;
    }
    private JsonNode create(JsonNode request,String header,String key,boolean agent)throws Exception {return service.create(subject,header,key,json.canonical(request).getBytes(StandardCharsets.UTF_8),agent);}
    private void denyReadAndReplay(String version,JsonNode request)throws Exception {
        deny(ErrorCode.CONTENT_MISMATCH,()->service.read(subject,"2",version,false));deny(ErrorCode.CONTENT_MISMATCH,()->create(request,"1",KEY,false));
    }
    private void assertClean(){assertTrue(store.rows.isEmpty());assertTrue(store.heads.isEmpty());assertTrue(store.operations.isEmpty());assertTrue(store.audits.isEmpty());assertTrue(store.events.isEmpty());
        assertTrue(store.versions.values().stream().noneMatch(v->Set.of("CAPABILITY_VERSION","AGENT_VERSION").contains(v.entityKind())));
        assertEquals(0,store.writes);assertEquals("1",context.context_revision());assertEquals(BigInteger.ONE,governance);}
    private static void deny(ErrorCode code,org.junit.jupiter.api.function.Executable action){assertEquals(code,assertThrows(TenantAccessException.class,action).code());}
    private static <T>List<T> optional(T row){return row==null?List.of():List.of(row);}
    private static String operationKey(boolean agent,String key){return (agent?"SIMULATION-AGENT-CREATE:":"CP-05:")+key;}
    private static String id(int value){return String.format("00000000-0000-4000-8000-%012d",value);}
    private static SelectionOperationRow withResponse(SelectionOperationRow row,String value){return new SelectionOperationRow(row.operationId(),row.requestSha256(),row.state(),row.httpStatus(),value,row.targetId(),row.schemaVersion(),row.schemaManifestSha256());}
    private static ConfigurationAuthoringProtocol.Stored withProjection(ConfigurationAuthoringProtocol.Stored row,String value){return new ConfigurationAuthoringProtocol.Stored(row.tenantId(),row.projectId(),row.configId(),row.versionId(),row.kind(),row.name(),value,row.contentHash(),row.registryHash(),row.status(),row.headStatus(),row.currentVersion(),row.schemaVersion(),row.manifest(),row.createdBy(),row.createdAt(),row.classification());}
    private static final class Store {
        final Map<String,ConfigurationAuthoringProtocol.Stored> rows=new HashMap<>();
        final Map<String,ArtifactRegistryProtocol.VersionRow> versions=new HashMap<>();
        final Map<String,List<SourceReadProtocol.ParentRow>> parents=new HashMap<>();
        final Map<String,Map<String,Object>> heads=new HashMap<>();
        final Map<String,SelectionOperationRow> operations=new HashMap<>();
        final Map<String,String> audits=new HashMap<>(),events=new HashMap<>();int writes;
        Store copy(){var copy=new Store();copy.rows.putAll(rows);copy.versions.putAll(versions);parents.forEach((k,v)->copy.parents.put(k,new ArrayList<>(v)));
            heads.forEach((k,v)->copy.heads.put(k,new HashMap<>(v)));copy.operations.putAll(operations);copy.audits.putAll(audits);copy.events.putAll(events);copy.writes=writes;return copy;}
    }
}
