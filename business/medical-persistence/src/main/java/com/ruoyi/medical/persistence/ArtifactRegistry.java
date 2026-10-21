package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class ArtifactRegistry {
    private final ArtifactRegistryMapper mapper;
    private final ResourceReadAuthority authority;
    private final DependencyManifestSchema manifests;
    private final SchemaCatalogue schemas;
    private final DependencyLineage lineage;
    private final org.springframework.beans.factory.ObjectProvider<SimulationArtifactReadGuard> simulationReads;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private org.springframework.beans.factory.ObjectProvider<FormalEvaluationRuntimeObjects> evaluationRuntimeObjects;

    public ArtifactRegistry(ArtifactRegistryMapper mapper, ResourceReadAuthority authority, DependencyManifestSchema manifests, DependencyLineage lineage)
            throws IOException, NoSuchAlgorithmException {
        this(mapper,authority,manifests,lineage,null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ArtifactRegistry(ArtifactRegistryMapper mapper, ResourceReadAuthority authority, DependencyManifestSchema manifests,
            DependencyLineage lineage,org.springframework.beans.factory.ObjectProvider<SimulationArtifactReadGuard> simulationReads)
            throws IOException, NoSuchAlgorithmException {
        this.mapper = mapper; this.authority = authority; this.manifests = manifests; this.lineage = lineage;
        this.simulationReads=simulationReads; schemas = SchemaCatalogue.fromClasspath();
    }

    private boolean requireEvaluationRuntimeOriginal(EnterpriseAccess.Admission a,ArtifactRegistryProtocol.ArtifactRow artifact,ArtifactRegistryProtocol.BindingRow binding,ArtifactRegistryProtocol.ReadRequirement required) {
        var owners=mapper.evaluationRuntimeObjectOwners(artifact.artifactId());
        boolean dedicated=binding.schemaRef()!=null&&binding.schemaRef().startsWith("urn:medical:evaluation-node-runtime:1.0.0#/$defs/")||artifact.artifactKind().startsWith("EVAL_");
        if(owners.isEmpty()&&!dedicated)return false;
        if(owners.size()!=1||!"EVALUATION_RESULTS".equals(required.purpose()))denied();
        var source=evaluationRuntimeObjects==null?null:evaluationRuntimeObjects.getIfAvailable();if(source==null)denied();
        var original=org.springframework.util.function.ThrowingSupplier.of(()->source.readOriginalArtifact(a,artifact.artifactId())).get();
        if(!original.reference().equals(artifact.reference())||!original.artifact().schemaRef().equals(required.schemaRef())||!original.artifact().schemaRef().equals(binding.schemaRef()))mismatch();
        return true;
    }

    ArtifactRegistryProtocol.Registration requireRegistration(EnterpriseAccess.Admission admission, String id,
            ArtifactRegistryProtocol.ReadRequirement required) throws IOException {
        Objects.requireNonNull(required); WireValues.uuid(id); WireValues.hash(required.artifactSchemaManifest());
        String tenant = admission.context().tenant_id();
        if (!tenant.equals(required.scope().tenant_id())) denied();
        // 主资源与制品分别授权，登记数据不能决定调用方应读取的领域类型。
        var resourceGrant = authority.require(admission, required.scope(), required.action(), required.purpose());
        var artifact = single(mapper.artifact(id)); var binding = single(mapper.binding(id));
        if(admission.controlWorker())admission.controlProof().requireVersion(id,artifact.contentSha256());
        TaskPlanDraftArtifactBoundary.requireGenericAllowed(artifact,binding,mapper.planDraftInputOwners(id),mapper.planDraftExactInputOwners(id));
        TaskSectionWriterArtifactBoundary.requireGenericAllowed(artifact,binding,mapper.sectionWriterInputOwners(id),mapper.sectionWriterExactInputOwners(id));
        TaskCandidateAssemblyArtifactBoundary.requireGenericAllowed(artifact,binding,mapper.candidateAssemblyOwners(id));
        DocumentReviewInvocationArtifactBoundary.requireGenericAllowed(artifact,binding,mapper.documentReviewExactInputOwners(id));
        TaskSourceVerifierArtifactBoundary.requireGenericAllowed(artifact,binding,mapper.sourceVerifierInputOwners(id),mapper.sourceVerifierExactInputOwners(id));
        TaskFieldTargetArtifactBoundary.requireGenericAllowed(artifact,binding,mapper.taskFieldTargetOwners(id));
        TaskNodeInputArtifactBoundary.requireGenericAllowed(artifact,binding,mapper.taskNodeInputOwners(id));
        TaskProfessionalArtifactBoundary.requireGenericAllowed(artifact,binding,mapper.runtimeProfessionalOwners(id));
        FormalPublicationArtifactBoundary.requireGenericAllowed(artifact, binding);
        RuntimeArtifactReadBoundary.requireGenericAllowed(artifact, binding);
        FormalEvaluationExactInputArtifactBoundary.requireGenericAllowed(artifact, binding, mapper.evaluationExactInputOwners(id));
        FormalEvaluationPlanArtifactBoundary.requireGenericAllowed(artifact, binding, mapper.evaluationPlanPreparationOwners(id));
        FormalEvaluationNodeInputArtifactBoundary.requireGenericAllowed(artifact, binding, mapper.evaluationNodeInputOwners(id));
        FormalEvaluationInvocationArtifactBoundary.requireGenericAllowed(artifact,binding,mapper.evaluationCallExactInputOwners(id));
        boolean evaluationRuntimeSchema = requireEvaluationRuntimeOriginal(admission, artifact, binding, required);
        var artifactGrant = authority.require(admission, new PermissionProtocol.Scope(tenant, artifact.projectId(), "ARTIFACT", id),
            "artifact.read", required.purpose());
        if (!tenant.equals(artifact.tenantId()) || !tenant.equals(binding.tenantId()) || !id.equals(artifact.artifactId())
                || !id.equals(binding.artifactId()) || !Objects.equals(required.scope().project_id(), artifact.projectId())
                || !Objects.equals(artifact.projectId(), binding.projectId()) || !required.scope().resource_kind().equals(binding.resourceKind())
                || !required.scope().resource_id().equals(binding.resourceId()) || !artifact.accessSetId().equals(binding.accessSetId())
                || !required.schemaRef().equals(binding.schemaRef()) || !required.schemaDocumentSha256().equals(binding.schemaDocumentSha256())
                || !"1.0.0".equals(artifact.schemaVersion()) || !required.artifactSchemaManifest().equals(artifact.schemaManifest())
                || !artifact.sha256().equals(artifact.contentSha256())) mismatch();
        if(!evaluationRuntimeSchema && !requireLocalSimulationSchema(artifact,required) && !requireLocalFormalInputSchema(artifact,required))
            schemas.requirePublished(required.schemaRef(), required.schemaDocumentSha256());
        StoredObjectReferences.decode(artifact.storedObject(), artifact.reference());
        requireVersion(id, artifact.projectId(), artifact.sha256());
        var row = single(mapper.manifest(artifact.accessSetId()));
        if (!tenant.equals(row.tenantId()) || !Objects.equals(artifact.projectId(), row.projectId())
                || !artifact.accessSetId().equals(row.accessSetId()) || !binding.dependencyManifestSha256().equals(row.manifestSha256())) mismatch();
        var manifest = manifests.verify(row, mapper.members(row.accessSetId()));
        requireVersion(row.accessSetId(), row.projectId(), row.manifestSha256());
        var completeLineage = lineage.require(id, manifest.members(), required.lineageLimits());
        if (List.of("MODEL_RESULT", "SIMULATION_PROVENANCE").contains(artifact.artifactKind())
                && "EVALUATION".equals(binding.resourceKind())) {
            // Lazy resolution breaks Registry -> context -> references -> version authority -> Registry.
            var guard=simulationReads==null?null:simulationReads.getIfAvailable();
            if(guard==null)denied();
            guard.require(admission,artifact,binding,required);
        }
        return new ArtifactRegistryProtocol.Registration(artifact, binding, manifest, artifactGrant, resourceGrant, completeLineage);
    }

    /** One explicit trusted local schema; all other references retain frozen catalogue validation. */
    static boolean requireLocalSimulationSchema(ArtifactRegistryProtocol.ArtifactRow artifact,
            ArtifactRegistryProtocol.ReadRequirement required)throws IOException {
        if(!SimulationAuthorizedUseEvidenceSchema.REF.equals(required.schemaRef()))return false;
        String digest=SimulationAuthorizedUseEvidenceSchema.schemaDocumentSha256();
        if(!"SIM_AUTHORIZED_USE_EVIDENCE".equals(artifact.artifactKind())
            ||!"SIMULATION_EVALUATION".equals(required.purpose())||!"SOURCE".equals(required.scope().resource_kind())
            ||required.scope().project_id()!=null||artifact.projectId()!=null||!"source.read".equals(required.action())
            ||!digest.equals(required.schemaDocumentSha256())||!digest.equals(required.artifactSchemaManifest()))mismatch();
        return true;
    }
    /** Exact local formal-input descriptor; registration alone never authorizes content transport. */
    static boolean requireLocalFormalInputSchema(ArtifactRegistryProtocol.ArtifactRow artifact,
            ArtifactRegistryProtocol.ReadRequirement required)throws IOException {
        final String ref="urn:medical:formal-evaluation-input:1.0.0#/$defs/Document";
        final String digest="4df9e483516bc858837897bb27bcc0a9df7d561d84b90800215fdc1e525aedda";
        if(!ref.equals(required.schemaRef()))return false;
        if(!"FORMAL_EVALUATION_INPUT".equals(artifact.artifactKind())
            ||!"SOURCE".equals(required.scope().resource_kind())||required.scope().project_id()!=null
            ||artifact.projectId()!=null||!"source.read".equals(required.action())
            ||required.purpose()==null||!required.purpose().matches("[A-Z][A-Z0-9_]{0,63}")
            ||!digest.equals(required.schemaDocumentSha256())||!digest.equals(required.artifactSchemaManifest()))mismatch();
        try(var in=ArtifactRegistry.class.getResourceAsStream("/formal-evaluation-input.schema.json")) {
            if(in==null||!digest.equals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(in)))mismatch();
        }
        return true;
    }
    private void requireVersion(String id, String project, String digest) {
        var version = single(mapper.version(id));
        if (!id.equals(version.id()) || !Objects.equals(project, version.projectId()) || !digest.equals(version.contentSha256())) mismatch();
    }

    private static <T> T single(List<T> rows) { if (rows.size() != 1) denied(); return rows.get(0); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
