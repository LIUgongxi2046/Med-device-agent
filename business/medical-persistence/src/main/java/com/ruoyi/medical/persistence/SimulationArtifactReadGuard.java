package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.persistence.mapper.EvaluationMapper;
import com.ruoyi.medical.persistence.mapper.SimulationFixtureMapper;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;

/** Current READ authority for 062 outputs. Historical execution authority never grants read or write permission. */
@Component
public final class SimulationArtifactReadGuard {
    static final String PURPOSE="SIMULATION_EVALUATION";
    private final SimulationFixtureMapper mapper;
    private final SimulationContextDependencies contexts;
    private final DependencyAccessAuthority dependencies;
    private final DependencyLineage lineage;
    private final EvaluationMapper evaluations;
    private final EvaluationSchema evaluationSchema;
    private final SimulationEvaluationCandidateAuthority candidates;
    private final SimulationInvocationSchema schema;
    private final SchemaCatalogue catalogue;
    private final com.ruoyi.medical.persistence.mapper.PermissionMapper permissions;
    private final PermissionAuthority grants;
    private final EvaluationRuleAuthority rules;
    private final org.springframework.beans.factory.ObjectProvider<SimulationAuthorizedUseEvidenceAuthority> authorizedUses;
    // Candidate graphs can themselves contain artifacts. Reject hidden context/artifact recursion.
    private final ThreadLocal<Set<String>> reading=ThreadLocal.withInitial(HashSet::new);

    public SimulationArtifactReadGuard(SimulationFixtureMapper mapper,SimulationContextDependencies contexts,
            DependencyAccessAuthority dependencies,DependencyLineage lineage,EvaluationMapper evaluations,
            EvaluationSchema evaluationSchema,SimulationEvaluationCandidateAuthority candidates,
            com.ruoyi.medical.persistence.mapper.PermissionMapper permissions,PermissionAuthority grants,
            EvaluationRuleAuthority rules,
            org.springframework.beans.factory.ObjectProvider<SimulationAuthorizedUseEvidenceAuthority> authorizedUses)throws Exception {
        this.mapper=mapper;this.contexts=contexts;this.dependencies=dependencies;this.lineage=lineage;
        this.evaluations=evaluations;this.evaluationSchema=evaluationSchema;this.candidates=candidates;
        this.permissions=permissions;this.grants=grants;this.rules=rules;this.authorizedUses=authorizedUses;
        schema=new SimulationInvocationSchema();catalogue=SchemaCatalogue.fromClasspath();
    }
    public record Settled(String tenantId,String projectId,String invocationId,String contextId,String runId,String stepId,
            String actionId,String configRevision,String roleVersion,String evaluationId,String authorityId,
            String resultArtifactId,String resultSha256,String provenanceArtifactId,String provenanceSha256,
            String requestJson,String bindingSha256,String proofSha256,String payload,String payloadSha256,String classification) { }

    public record Retention(String tenantId,String projectId,String policyId,String evaluationId,String approvalGrantId,
            java.math.BigInteger approvalGrantRevision,String createdBy,java.math.BigInteger maximumRecordBytes,java.time.LocalDateTime retainUntil) { }

    void require(EnterpriseAccess.Admission admission,ArtifactRegistryProtocol.ArtifactRow artifact,
            ArtifactRegistryProtocol.BindingRow binding,ArtifactRegistryProtocol.ReadRequirement required)throws IOException {
        // No purpose substitution: EVALUATION_RESULTS, export, download and production consumers are not enabled.
        if(!PURPOSE.equals(required.purpose())||!"evaluation.read".equals(required.action())
                ||!"EVALUATION".equals(binding.resourceKind()))denied();
        var active=reading.get();if(active.size()>=32||!active.add(artifact.artifactId()))denied();
        try { requireCurrent(admission,artifact,binding); }
        catch(IOException|RuntimeException failure){throw failure;}
        catch(Exception failure){throw new IOException("Simulation artifact read verification failed",failure);}
        finally {active.remove(artifact.artifactId());if(active.isEmpty())reading.remove();}
    }

    private void requireCurrent(EnterpriseAccess.Admission admission,ArtifactRegistryProtocol.ArtifactRow artifact,
            ArtifactRegistryProtocol.BindingRow binding)throws Exception {
        var values=new HashMap<String,Object>();values.put("userId",admission.actorId());
        values.put("artifactId",artifact.artifactId());values.put("schemaHash",schema.manifest());
        var settled=one(mapper.settledArtifact(values));
        same(admission.context().tenant_id(),settled.tenantId());same(artifact.tenantId(),settled.tenantId());
        same(artifact.projectId(),settled.projectId());same(binding.resourceId(),settled.evaluationId());
        same(artifact.classification(),settled.classification());
        var request=schema.parse(settled.requestJson());schema.validate("SimulationInvocationAdmission",request);schema.check(request);
        var proof=request.required("authority_proof");var identity=request.required("binding").required("identity");
        same(schema.hash(request.required("binding")),settled.bindingSha256());same(schema.hash(proof),settled.proofSha256());
        same(text(proof,"authority_id"),settled.authorityId());same(text(proof,"evaluation_id"),settled.evaluationId());
        same(text(identity,"tenant_id"),settled.tenantId());same(nullable(identity,"project_id"),settled.projectId());
        same(text(identity,"invocation_id"),settled.invocationId());same(text(identity,"context_manifest_id"),settled.contextId());
        same(text(identity,"run_id"),settled.runId());same(text(identity,"step_id"),settled.stepId());
        same(text(identity,"action_id"),settled.actionId());same(text(identity,"model_config_revision"),settled.configRevision());
        var payload=schema.parse(settled.payload());same(schema.hash(payload),settled.payloadSha256());
        requireRetention(settled,proof,schema.canonical(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8).length,values);
        var resultSchema=new SimulationResultSchema(schema);
        same(payload,schema.receipt(request,resultSchema.bytes(payload,"result"),resultSchema.bytes(payload,"provenance")));
        same(schema.hash(payload.required("result")),settled.resultSha256());
        same(schema.hash(payload.required("provenance")),settled.provenanceSha256());
        if(settled.resultArtifactId().equals(settled.provenanceArtifactId()))mismatch();
        boolean result="MODEL_RESULT".equals(artifact.artifactKind());
        if(!result&&!"SIMULATION_PROVENANCE".equals(artifact.artifactKind()))mismatch();
        same(artifact.artifactId(),result?settled.resultArtifactId():settled.provenanceArtifactId());
        same(artifact.sha256(),result?settled.resultSha256():settled.provenanceSha256());
        same(artifact.sizeBytes(),java.math.BigInteger.valueOf(resultSchema.bytes(payload,result?"result":"provenance").length));
        same(artifact.mediaType(),"application/json");
        String document=result?"model-stream":"local-simulation-provenance";
        same(binding.schemaRef(),catalogue.schemaRef(document,result?"ModelResult":null));
        same(binding.schemaDocumentSha256(),catalogue.schemaDigest(document));same(artifact.schemaManifest(),catalogue.schemaDigest(document));

        values.put("contextId",settled.contextId());var context=one(mapper.context(values));
        same(context.tenantId(),settled.tenantId());same(context.projectId(),settled.projectId());same(context.contextId(),settled.contextId());
        same(context.executionMode(),"SIMULATION");same(context.accessSetId(),artifact.accessSetId());
        same(context.contentHash(),context.registryHash());requireContextContent(context);
        if(!SimulationContextDependencies.maximum(artifact.classification(),context.classification()).equals(artifact.classification()))mismatch();
        String candidate=text(proof,"agent_version_id");
        requireEvaluation(admission,settled,proof,context,candidate,values);
        // Recomputes exact candidate/configuration graph, all grants and current classification; validates frozen context graph.
        contexts.require(admission,context,candidate);
        var sourceRequirements=contexts.sourceRequirements(context.accessSetId(),context.projectId());
        var checked=dependencies.require(admission,context.accessSetId(),
            new DependencyAccessProtocol.ReadRequirement(context.projectId(),PURPOSE,new DependencyLineage.Limits(257,1027)),
            sourceRequirements,new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW,null),Map.of());
        // Full actual source closure, including derived sources, must fit the output classification.
        for(var source:checked.sources().values())
            if(!SimulationContextDependencies.maximum(artifact.classification(),source.source().classification()).equals(artifact.classification()))mismatch();
        requireOriginals(context,request,values,checked.sources());
    }

    private void requireRetention(Settled settled,JsonNode proof,long bytes,Map<String,Object> values)throws Exception {
        values.put("policyId",text(proof,"retention_policy_id"));values.put("authorityId",settled.authorityId());values.put("invocationId",settled.invocationId());
        var retention=one(mapper.artifactRetention(values));
        same(retention.tenantId(),settled.tenantId());same(retention.projectId(),settled.projectId());same(retention.evaluationId(),settled.evaluationId());
        same(retention.policyId(),text(proof,"retention_policy_id"));same(retention.retainUntil(),PermissionAuthority.time(text(proof,"retention_deadline")));
        var now=Objects.requireNonNull(permissions.databaseTime());
        if(!now.isBefore(retention.retainUntil())||retention.maximumRecordBytes()==null||retention.maximumRecordBytes().compareTo(java.math.BigInteger.valueOf(bytes))<0)denied();
        var grant=one(permissions.currentGrant(retention.approvalGrantId()));var approver=one(permissions.targetBinding(retention.createdBy()));
        // Verify the original retention approver, not a write permission on the current viewer.
        var approved=grants.requireResource(new PermissionProtocol.Scope(settled.tenantId(),settled.projectId(),"EVALUATION",settled.evaluationId()),
            retention.createdBy(),approver.mode(),"model.simulation.retention.configure",PURPOSE,List.of(grant),now);
        same(approved.grantId(),retention.approvalGrantId());same(approved.revision(),retention.approvalGrantRevision());
    }

    private void requireEvaluation(EnterpriseAccess.Admission admission,Settled settled,JsonNode proof,
            SimulationInvocationSources.Context context,String candidate,Map<String,Object> values)throws Exception {
        values.put("evaluationId",settled.evaluationId());var stored=one(evaluations.definition(values));
        var definition=evaluationSchema.definition(stored.definition());String hash=evaluationSchema.hash(definition);
        same(stored.tenantId(),settled.tenantId());same(stored.projectId(),settled.projectId());same(stored.evaluationId(),settled.evaluationId());
        same(definition.tenant_id(),settled.tenantId());same(definition.evaluation_id(),settled.evaluationId());
        same(definition.request().project_id(),settled.projectId());same(hash,stored.contentHash());same(hash,stored.registryHash());
        same(hash,text(proof,"evaluation_definition_sha256"));same(stored.schemaVersion(),"1.0.0");same(stored.manifest(),evaluationSchema.manifestSha256());
        var frozen=one(definition.candidate_snapshots().stream().filter(c->candidate.equals(c.version_id())).toList());
        var requested=one(definition.request().candidates().stream().filter(c->candidate.equals(c.version_id())).toList());
        same(requested.content_sha256(),frozen.content_sha256());
        values.put("candidateId",candidate);candidates.requireSnapshot(settled.tenantId(),settled.projectId(),frozen,one(evaluations.candidate(values)));
        var graph=lineage.inspect(candidate,new DependencyLineage.Limits(256,1024));
        catalogue.validate("simulation-execution",frozen.dependency_manifest(),"SimulationCandidateDependencyManifest");
        SimulationEvaluationCandidateAuthority.requireFrozenGraph(frozen,graph);
        values.put("versionIds",graph.versions().keySet().stream().sorted().toList());
        SimulationEvaluationCandidateAuthority.requireConfigurationGraph(graph,evaluations.configurationStates(values),
            settled.roleVersion(),settled.configRevision(),context.promptRevision());
        SimulationEvaluationLegalUseAuthority.require(admission,definition.request(),rules,
            authorizedUses==null?null:authorizedUses.getIfAvailable());
        // Evaluation lifecycle and historic execution lease expiry do not confer read authority or block history by themselves.
    }

    private void requireContextContent(SimulationInvocationSources.Context context)throws Exception {
        var manifest=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        manifest.put("schema_version",context.schemaVersion());manifest.put("input_data_kind","SIMULATION");manifest.put("production_eligible",false);
        manifest.put("tenant_id",context.tenantId());manifest.put("project_id",context.projectId());manifest.put("context_manifest_id",context.contextId());
        manifest.set("exact_input_ref",schema.parse(context.exactInputRef()));manifest.set("source_manifest",schema.parse(context.sourceManifest()));
        manifest.put("prompt_revision",context.promptRevision());manifest.put("dependency_access_set_id",context.accessSetId());
        manifest.put("created_by",context.createdBy());manifest.put("created_at",context.createdAt().toInstant(java.time.ZoneOffset.UTC).toString());
        manifest.put("classification",context.classification());
        var bound=catalogue.bind("simulation-execution",manifest,"SimulationContextManifest");
        same(bound.schemaSha256(),context.manifest());same(schema.hash(manifest),context.contentHash());
    }

    private void requireOriginals(SimulationInvocationSources.Context context,JsonNode request,Map<String,Object> values,
            Map<String,SourceReadProtocol.VerifiedSource> checked)throws Exception {
        values.put("projectId",context.projectId());values.put("configRevision",text(request.required("authority_proof"),"model_config_revision"));
        values.put("caseId",text(request.required("binding"),"case_id"));var fixture=one(mapper.registration(values));
        same(fixture.tenantId(),context.tenantId());same(fixture.projectId(),context.projectId());
        same(fixture.configRevision(),values.get("configRevision"));same(fixture.caseId(),values.get("caseId"));
        same(fixture.caseSha256(),schema.hash(request.required("simulation_case")));same(fixture.inputSha256(),schema.hash(request.required("exact_input")));
        same(fixture.settingsSha256(),schema.hash(request.required("settings")));same(fixture.parametersSha256(),schema.hash(request.required("request_parameters")));
        same(fixture.caseReceiptId(),text(request.required("binding"),"case_receipt_id"));same(fixture.schemaVersion(),"1.0.0");same(fixture.manifest(),schema.manifest());
        if(!SimulationContextDependencies.maximum(context.classification(),fixture.classification()).equals(context.classification()))mismatch();
        var declared=schema.parse(context.sourceManifest());if(!declared.isArray()||declared.size()!=2)mismatch();
        for(int index=0;index<2;index++) {
            String sourceId=index==0?fixture.caseSourceVersion():fixture.inputSourceVersion();
            String rights=index==0?fixture.caseRightsRevision():fixture.inputRightsRevision();
            var item=declared.get(index);same(text(item,"kind"),index==0?"CASE":"EXACT_INPUT");same(text(item,"source_version"),sourceId);
            same(text(item,"rights_revision"),rights);same(text(item,"purpose"),PURPOSE);
            var authorized=checked.get(sourceId);if(authorized==null)mismatch();same(authorized.rights().rightsRevision(),rights);
            values.put("sourceVersion",sourceId);var original=one(mapper.original(values));same(original.tenantId(),context.tenantId());
            same(original.sourceVersion(),sourceId);same(original.sha256(),text(item,"original_sha256"));
            if(index==1)same(schema.parse(context.exactInputRef()),schema.parse(ContentReadAuthorization.canonicalBytes(original.reference())));
        }
    }
    private static String text(JsonNode node,String key){var v=node.required(key);if(!v.isTextual())mismatch();return v.textValue();}
    private static String nullable(JsonNode node,String key){return node.required(key).isNull()?null:text(node,key);}
    private static <T>T one(List<T> rows){if(rows.size()!=1)denied();return rows.get(0);}
    private static void same(Object a,Object b){if(!Objects.equals(a,b))mismatch();}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
