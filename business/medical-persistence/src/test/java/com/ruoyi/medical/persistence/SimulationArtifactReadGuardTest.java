package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import org.jgrapht.graph.DirectedPseudograph;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No database, runtime, object store or external models. Real published request/receipt/context schemas. */
class SimulationArtifactReadGuardTest {
    final SimulationFixtureMapper mapper=mock(SimulationFixtureMapper.class);
    final SimulationContextDependencies contexts=mock(SimulationContextDependencies.class);
    final DependencyAccessAuthority dependencies=mock(DependencyAccessAuthority.class);
    final DependencyLineage lineage=mock(DependencyLineage.class);
    final EvaluationMapper evaluations=mock(EvaluationMapper.class);
    final EvaluationSchema evaluationSchema=mock(EvaluationSchema.class);
    final SimulationEvaluationCandidateAuthority candidates=mock(SimulationEvaluationCandidateAuthority.class);
    final PermissionMapper permissions=mock(PermissionMapper.class);
    final PermissionAuthority grants=mock(PermissionAuthority.class);
    final EvaluationRuleAuthority rules=mock(EvaluationRuleAuthority.class);
    final SimulationAuthorizedUseEvidenceAuthority authorizedUses=mock(SimulationAuthorizedUseEvidenceAuthority.class);
    @SuppressWarnings("unchecked") final org.springframework.beans.factory.ObjectProvider<SimulationAuthorizedUseEvidenceAuthority> evidenceProvider=mock(org.springframework.beans.factory.ObjectProvider.class);
    final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
    final SimulationInvocationSchema schema=new SimulationInvocationSchema();
    final PermissionSchema json=new PermissionSchema();
    final SchemaCatalogue catalogue=SchemaCatalogue.fromClasspath();
    final JsonNode request=schema.request(SimulationInvocationSchemaTest.resource("admission"));
    final JsonNode proof=request.required("authority_proof"),identity=request.required("binding").required("identity");
    final JsonNode payload=schema.receipt(request,SimulationInvocationSchemaTest.resource("result"),SimulationInvocationSchemaTest.resource("provenance"));
    final SimulationArtifactReadGuard guard=new SimulationArtifactReadGuard(mapper,contexts,dependencies,lineage,evaluations,evaluationSchema,candidates,permissions,grants,rules,evidenceProvider);
    final String tenant=text(identity,"tenant_id"),project=identity.required("project_id").isNull()?null:text(identity,"project_id"),candidate=text(proof,"agent_version_id");
    final String HASH="a".repeat(64),role=id(90),prompt=id(91),access=id(92),result=id(93),provenance=id(94);
    SimulationArtifactReadGuard.Settled settled;
    SimulationInvocationSources.Context context;
    ArtifactRegistryProtocol.ArtifactRow artifact;
    ArtifactRegistryProtocol.BindingRow binding;
    SimulationArtifactReadGuardTest()throws Exception {
        when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(tenant,"1","1","1"));when(admission.actorId()).thenReturn(id(99));
        settled=new SimulationArtifactReadGuard.Settled(tenant,project,text(identity,"invocation_id"),text(identity,"context_manifest_id"),text(identity,"run_id"),text(identity,"step_id"),text(identity,"action_id"),text(identity,"model_config_revision"),role,text(proof,"evaluation_id"),text(proof,"authority_id"),result,schema.hash(payload.required("result")),provenance,schema.hash(payload.required("provenance")),schema.canonical(request),schema.hash(request.required("binding")),schema.hash(proof),schema.canonical(payload),schema.hash(payload),"INTERNAL");
        when(mapper.settledArtifact(anyMap())).thenReturn(List.of(settled));
        String doc="model-stream",digest=catalogue.schemaDigest(doc);
        artifact=new ArtifactRegistryProtocol.ArtifactRow(tenant,project,result,"MODEL_RESULT","{}","key","version",settled.resultSha256(),BigInteger.valueOf(SimulationInvocationSchemaTest.resource("result").length),"application/json",access,settled.resultSha256(),"1.0.0",digest,"INTERNAL");
        binding=new ArtifactRegistryProtocol.BindingRow(tenant,project,result,"EVALUATION",settled.evaluationId(),catalogue.schemaRef(doc,"ModelResult"),digest,access,HASH);
        when(evidenceProvider.getIfAvailable()).thenReturn(authorizedUses);
        setupRetention();setupContext();setupCandidate();
    }
    private void setupRetention()throws Exception {
        var retention=new SimulationArtifactReadGuard.Retention(tenant,project,text(proof,"retention_policy_id"),settled.evaluationId(),id(87),BigInteger.ONE,id(88),BigInteger.valueOf(16777216),PermissionAuthority.time(text(proof,"retention_deadline")));
        when(mapper.artifactRetention(anyMap())).thenReturn(List.of(retention));
        when(permissions.databaseTime()).thenReturn(retention.retainUntil().minusSeconds(1));
        var grant=mock(PermissionProtocol.GrantRow.class);when(grant.grantId()).thenReturn(id(87));when(grant.revision()).thenReturn(BigInteger.ONE);
        when(permissions.currentGrant(id(87))).thenReturn(List.of(grant));
        var approver=mock(PermissionProtocol.BindingRow.class);when(approver.mode()).thenReturn("LOCAL");when(permissions.targetBinding(id(88))).thenReturn(List.of(approver));
        when(grants.requireResource(any(),eq(id(88)),eq("LOCAL"),eq("model.simulation.retention.configure"),eq("SIMULATION_EVALUATION"),anyList(),any())).thenReturn(grant);
    }
    private void setupContext()throws Exception {
        var originalCase=new SimulationInvocationSources.Original(tenant,id(81),HASH,"case","v1",BigInteger.ONE,"application/json");
        var originalInput=new SimulationInvocationSources.Original(tenant,id(82),HASH,"input","v1",BigInteger.ONE,"application/json");
        var sources=json.object().arrayNode();int n=0;
        var checked=new HashMap<String,SourceReadProtocol.VerifiedSource>();
        for(var original:List.of(originalCase,originalInput)) {
            sources.addObject().put("kind",n++==0?"CASE":"EXACT_INPUT").put("source_version",original.sourceVersion()).put("rights_revision",id(83)).put("original_sha256",HASH).put("purpose","SIMULATION_EVALUATION");
            var source=mock(SourceReadProtocol.SourceRow.class);when(source.classification()).thenReturn("INTERNAL");
            var rights=mock(SourceReadProtocol.RightsRow.class);when(rights.rightsRevision()).thenReturn(id(83));
            checked.put(original.sourceVersion(),new SourceReadProtocol.VerifiedSource(source,rights,null,null,null,null,null));
        }
        when(mapper.original(anyMap())).thenAnswer(call->{Map<String,Object> v=call.getArgument(0);return List.of(id(81).equals(v.get("sourceVersion"))?originalCase:originalInput);});
        var manifest=json.object().put("schema_version","1.0.0").put("input_data_kind","SIMULATION").put("production_eligible",false).put("tenant_id",tenant).put("project_id",project)
            .put("context_manifest_id",settled.contextId()).put("prompt_revision",prompt).put("dependency_access_set_id",access).put("created_by",id(99)).put("created_at","2026-10-18T00:00:00Z").put("classification","INTERNAL");
        manifest.set("source_manifest",sources);manifest.set("exact_input_ref",json.tree(originalInput.reference()));
        String hash=schema.hash(manifest);
        context=new SimulationInvocationSources.Context(tenant,project,settled.contextId(),json.canonical(json.tree(originalInput.reference())),access,hash,hash,"SIMULATION",json.canonical(sources),prompt,"1.0.0",schema.manifest(),id(99),LocalDateTime.of(2026,10,18,0,0),"INTERNAL");
        when(mapper.context(anyMap())).thenReturn(List.of(context));
        when(contexts.sourceRequirements(access,project)).thenReturn(List.of());
        when(dependencies.require(eq(admission),eq(access),any(),anyList(),any(),anyMap())).thenReturn(new DependencyAccessProtocol.VerifiedAccess(null,null,null,checked,Map.of()));
        when(mapper.registration(anyMap())).thenReturn(List.of(new SimulationInvocationSources.Registration(tenant,project,text(request.required("binding"),"case_id"),settled.configRevision(),id(81),id(83),id(82),id(83),schema.hash(request.required("simulation_case")),schema.hash(request.required("exact_input")),schema.hash(request.required("settings")),schema.hash(request.required("request_parameters")),text(request.required("binding"),"case_receipt_id"),"1.0.0",schema.manifest(),id(99),LocalDateTime.of(2026,10,18,0,0),"INTERNAL")));
    }
    private void setupCandidate()throws Exception {
        var graph=new DirectedPseudograph<String,SourceReadProtocol.ParentRow>(null,null,false);var versions=new LinkedHashMap<String,ArtifactRegistryProtocol.VersionRow>();
        var kinds=Map.of(candidate,"AGENT_VERSION",role,"CAPABILITY_VERSION",prompt,"CAPABILITY_VERSION",settled.configRevision(),"MODEL_CONFIG");
        var manifest=json.object().put("schema_version","1.0.0").put("manifest_kind","SIMULATION_CANDIDATE_DEPENDENCIES");var refs=manifest.putArray("dependencies");
        for(var e:kinds.entrySet()){graph.addVertex(e.getKey());versions.put(e.getKey(),new ArtifactRegistryProtocol.VersionRow(e.getKey(),project,e.getValue(),HASH));}
        for(String key:kinds.keySet())if(!candidate.equals(key)){graph.addEdge(candidate,key,new SourceReadProtocol.ParentRow(candidate,key,"CONFIGURATION_DEPENDENCY"));refs.addObject().put("version_id",key).put("content_sha256",HASH);}
        when(lineage.inspect(eq(candidate),any())).thenReturn(new DependencyLineage.VerifiedLineage(candidate,graph,versions));
        var frozen=new EvaluationProtocol.Candidate(candidate,HASH,id(85),id(99),manifest,json.object(),"1.0.0",HASH);
        var create=mock(EvaluationProtocol.Create.class);when(create.environment()).thenReturn("SIMULATION");when(create.samples()).thenReturn(List.of(EvaluationFixture.sample(1000,"SIMULATION","DEVELOPMENT")));when(create.rule_revision()).thenReturn(new PermissionProtocol.Basis(id(96),HASH));when(create.project_id()).thenReturn(project);when(create.candidates()).thenReturn(List.of(new PermissionProtocol.Basis(candidate,HASH)));
        var definition=new EvaluationProtocol.Definition("1.0.0",tenant,settled.evaluationId(),create,List.of(frozen),List.of(),id(86),"1",id(99),"2026-10-18T00:00:00Z");
        String hash=text(proof,"evaluation_definition_sha256");
        when(evaluationSchema.definition("{}" )).thenReturn(definition);when(evaluationSchema.hash(definition)).thenReturn(hash);when(evaluationSchema.manifestSha256()).thenReturn(HASH);
        when(evaluations.definition(anyMap())).thenReturn(List.of(new EvaluationProtocol.Stored(tenant,project,settled.evaluationId(),"{}",hash,hash,"{}","COMPLETED",BigInteger.ONE,null,null,"1.0.0",HASH,id(99),LocalDateTime.of(2026,10,18,0,0),"INTERNAL")));
        when(evaluations.candidate(anyMap())).thenReturn(List.of(mock(EvaluationProtocol.AgentRow.class)));
        when(evaluations.configurationStates(anyMap())).thenReturn(List.of(new SimulationEvaluationCandidateAuthority.ConfigurationState(candidate,"AGENT","DRAFT","DRAFT",null),new SimulationEvaluationCandidateAuthority.ConfigurationState(role,"ROLE","DRAFT","DRAFT",null),new SimulationEvaluationCandidateAuthority.ConfigurationState(prompt,"PROMPT","DRAFT","DRAFT",null),new SimulationEvaluationCandidateAuthority.ConfigurationState(settled.configRevision(),"MODEL","DRAFT","DRAFT","SIMULATION")));
    }
    private ArtifactRegistryProtocol.ReadRequirement requirement(String purpose){return new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(tenant,project,"EVALUATION",settled.evaluationId()),"evaluation.read",purpose,binding.schemaRef(),binding.schemaDocumentSha256(),artifact.schemaManifest(),new DependencyLineage.Limits(512,2048));}
    @Test void currentReaderChecksStoredCandidateAndCompleteSourcesEvenAfterEvaluationCompleted()throws Exception {
        guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION"));
        verify(contexts).require(admission,context,candidate);verify(dependencies).require(eq(admission),eq(access),any(),anyList(),any(),eq(Map.of()));
        verify(candidates).requireSnapshot(eq(tenant),eq(project),any(),any());
    }
    @Test void provenanceUsesItsOwnSettlementMembershipAndPublishedSchema()throws Exception {
        String doc="local-simulation-provenance",digest=catalogue.schemaDigest(doc);
        var row=new ArtifactRegistryProtocol.ArtifactRow(tenant,project,provenance,"SIMULATION_PROVENANCE","{}","key","version",settled.provenanceSha256(),BigInteger.valueOf(SimulationInvocationSchemaTest.resource("provenance").length),"application/json",access,settled.provenanceSha256(),"1.0.0",digest,"INTERNAL");
        var bound=new ArtifactRegistryProtocol.BindingRow(tenant,project,provenance,"EVALUATION",settled.evaluationId(),catalogue.schemaRef(doc,null),digest,access,HASH);
        guard.require(admission,row,bound,new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(tenant,project,"EVALUATION",settled.evaluationId()),"evaluation.read","SIMULATION_EVALUATION",bound.schemaRef(),digest,digest,new DependencyLineage.Limits(512,2048)));
        verify(contexts).require(admission,context,candidate);
    }
    @Test void wrongStoredTenantInvocationContextCandidateHashAndMembershipCannotBorrowAuthority()throws Exception {
        for(String field:List.of("tenantId","projectId","invocationId","contextId","evaluationId","resultArtifactId","bindingSha256","proofSha256","payloadSha256","resultSha256","provenanceSha256")) {
            var changed=changed(settled,field,field.endsWith("Sha256")?"f".repeat(64):id(77));
            when(mapper.settledArtifact(anyMap())).thenReturn(List.of(changed));
            assertThrows(TenantAccessException.class,()->guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION")),field);
        }
        verifyNoInteractions(contexts);
    }
    @Test void completeSourceAuthorityRevocationPropagatesWithoutOriginalRead()throws Exception {
        when(dependencies.require(eq(admission),eq(access),any(),anyList(),any(),anyMap())).thenThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class,()->guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION")));
        verify(mapper,never()).original(anyMap());
    }
    @Test void terminalResultStillRequiresCurrentAuthorizationWitnessAndApprovedRule()throws Exception {
        guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION"));
        verify(authorizedUses).require(eq(admission),any());
        verify(rules).require(eq(admission),any(),eq(true),eq(EvaluationDependencyUse.SIMULATION));
        doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED)).when(authorizedUses).require(eq(admission),any());
        assertThrows(TenantAccessException.class,()->guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION")));
        reset(authorizedUses);
        when(rules.require(eq(admission),any(),eq(true),eq(EvaluationDependencyUse.SIMULATION))).thenThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.SOURCE_NOT_READY));
        assertThrows(TenantAccessException.class,()->guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION")));
    }
    @Test void ruleDependencyResultCycleFailsClosedAndGuardRecoversAfterFailure()throws Exception {
        when(rules.require(eq(admission),any(),eq(true),eq(EvaluationDependencyUse.SIMULATION))).thenAnswer(call->{
            guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION"));return null;
        });
        assertThrows(TenantAccessException.class,()->guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION")));
        reset(rules);
        assertDoesNotThrow(()->guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION")));
    }
    private static SimulationArtifactReadGuard.Settled changed(SimulationArtifactReadGuard.Settled row,String field,String value)throws Exception {
        var components=row.getClass().getRecordComponents();var args=new Object[components.length];var types=new Class<?>[components.length];
        for(int index=0;index<components.length;index++){var component=components[index];types[index]=component.getType();args[index]=component.getName().equals(field)?value:component.getAccessor().invoke(row);}
        return row.getClass().getDeclaredConstructor(types).newInstance(args);
    }
    @Test void unsupportedPurposeAndMissingOrAmbiguousSettlementFailClosed()throws Exception {
        assertThrows(TenantAccessException.class,()->guard.require(admission,artifact,binding,requirement("EVALUATION_RESULTS")));verifyNoInteractions(mapper);
        when(mapper.settledArtifact(anyMap())).thenReturn(List.of());assertThrows(TenantAccessException.class,()->guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION")));
        when(mapper.settledArtifact(anyMap())).thenReturn(List.of(settled,settled));assertThrows(TenantAccessException.class,()->guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION")));
    }
    @Test void revocationAndChangedGraphFailBeforeSourceDelivery()throws Exception {
        doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED)).when(contexts).require(admission,context,candidate);
        assertThrows(TenantAccessException.class,()->guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION")));verifyNoInteractions(dependencies);
    }
    @Test void actualCandidateConfigurationStatusCannotBeBorrowedOrDisabled()throws Exception {
        when(evaluations.configurationStates(anyMap())).thenReturn(List.of());
        assertThrows(TenantAccessException.class,()->guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION")));verify(contexts,never()).require(any(),any(),any());
    }
    @Test void expiredRevokedOrMissingRetentionAndRevokedOriginalApprovalDenyRead()throws Exception {
        when(permissions.databaseTime()).thenReturn(PermissionAuthority.time(text(proof,"retention_deadline")));
        assertThrows(TenantAccessException.class,()->guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION")));
        setupRetention();when(mapper.artifactRetention(anyMap())).thenReturn(List.of());
        assertThrows(TenantAccessException.class,()->guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION")));
        setupRetention();when(permissions.currentGrant(id(87))).thenReturn(List.of());
        assertThrows(TenantAccessException.class,()->guard.require(admission,artifact,binding,requirement("SIMULATION_EVALUATION")));
        verifyNoInteractions(contexts);
    }
    @Test void alteredArtifactHashAndWrongEvaluationAreRejectedBeforeContextAuthorization()throws Exception {
        var wrong=new ArtifactRegistryProtocol.BindingRow(tenant,project,result,"EVALUATION",id(88),binding.schemaRef(),binding.schemaDocumentSha256(),access,HASH);
        assertThrows(TenantAccessException.class,()->guard.require(admission,artifact,wrong,requirement("SIMULATION_EVALUATION")));verifyNoInteractions(contexts);
    }
    static String text(JsonNode n,String key){return n.required(key).textValue();}
    static String id(int n){return EvaluationFixture.id(n);}
}
