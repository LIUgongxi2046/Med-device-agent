package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.io.IOException;
import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Rechecks current rights and every upstream owner even though this workflow transmits no artifact bytes. */
@Component
@EnableConfigurationProperties(EvaluationDependencyPolicy.class)
public final class EvaluationReferences {
    static final String PURPOSE="EVALUATION_RESULTS";
    private final DependencyLineage lineage;private final VersionResourceAuthority versions;private final SourceReadAuthority authority;
    private final SourceReadMapper sources;private final ArtifactRegistryMapper artifacts;private final EvaluationDependencyPolicy policy;
    public EvaluationReferences(DependencyLineage lineage,VersionResourceAuthority versions,SourceReadAuthority authority,
            SourceReadMapper sources,ArtifactRegistryMapper artifacts,EvaluationDependencyPolicy policy) {
        this.lineage=lineage;this.versions=versions;this.authority=authority;this.sources=sources;this.artifacts=artifacts;this.policy=policy;
    }
    void require(EnterpriseAccess.Admission admission,String project,List<PermissionProtocol.Basis> refs)throws IOException {
        require(admission,project,refs,PURPOSE);
    }
    void require(EnterpriseAccess.Admission admission,String project,List<PermissionProtocol.Basis> refs,String purpose)throws IOException {
        if(!Set.of(PURPOSE,"SIMULATION_EVALUATION").contains(purpose))mismatch();
        policy.requireConfigured();var unique=new LinkedHashMap<String,PermissionProtocol.Basis>();
        for(var ref:refs){var old=unique.putIfAbsent(ref.version_id(),ref);if(old!=null&&!old.equals(ref))mismatch();}
        var allVersions=new HashSet<String>();var allRelations=new HashSet<SourceReadProtocol.ParentRow>();
        for(var ref:unique.values()) {
            var graph=lineage.inspect(ref.version_id(),policy.limits());allVersions.addAll(graph.versions().keySet());allRelations.addAll(graph.graph().edgeSet());
            if(allVersions.size()>policy.maximumVersions()||allRelations.size()>policy.maximumRelations())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            var root=graph.versions().get(ref.version_id());if(root==null||!ref.content_sha256().equals(root.contentSha256()))mismatch();
            var requirements=new LinkedHashMap<String,ArtifactRegistryProtocol.ReadRequirement>();var verified=new LinkedHashMap<String,SourceReadProtocol.VerifiedSource>();
            for(var version:graph.versions().values()) {
                if(version.projectId()!=null&&!version.projectId().equals(project))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
                var rows=sources.source(version.id());if(rows.size()>1)mismatch();
                if(!rows.isEmpty()) {
                    var source=rows.get(0);
                    verified.put(version.id(),authority.require(admission,new SourceReadProtocol.ReadRequirement(source.sourceVersion(),source.rightsRevision(),project,
                        null,null,null,SourceReadProtocol.Use.REVIEW,purpose,policy.environment(),policy.region(),null)));
                }
                if(version.entityKind().equals("ARTIFACT")) {
                    var binding=single(artifacts.binding(version.id()));var p=profile(binding.schemaRef(),purpose,binding.resourceKind());
                    if(!p.resourceKind().equals(binding.resourceKind()))mismatch();
                    requirements.put(version.id(),new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(admission.context().tenant_id(),binding.projectId(),p.resourceKind(),binding.resourceId()),
                        p.action(),purpose,p.schemaRef(),p.schemaDocumentSha256(),p.artifactSchemaManifest(),policy.limits()));
                }
            }
            var checked=versions.require(admission,graph,purpose,new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW,null),requirements);
            for(var version:checked.values()) {
                if(version.owner().sourceVersion()!=null) {
                    var source=verified.get(version.owner().sourceVersion());
                    if(source==null||!source.source().sourceId().equals(version.owner().resourceId())||!graph.reachableFrom(version.version().id()).contains(source.source().sourceVersion()))mismatch();
                }
                if(version.artifactRegistration()!=null)for(var member:version.artifactRegistration().manifest().members()) {
                    var rights=single(sources.rights(member.rightsRevision()));var source=verified.get(rights.sourceVersion());
                    if(source==null||!source.rights().rightsRevision().equals(member.rightsRevision())||!graph.reachableFrom(member.upstreamVersionId()).contains(rights.sourceVersion()))mismatch();
                }
            }
        }
    }
    EvaluationDependencyPolicy.ArtifactProfile profile(String schemaRef,String purpose)throws IOException {
        return profile(schemaRef,purpose,null);
    }
    EvaluationDependencyPolicy.ArtifactProfile profile(String schemaRef,String purpose,String resourceKind)throws IOException {
        var configured=policy.artifactProfiles().stream().filter(p->p.schemaRef().equals(schemaRef)).toList();
        if(PURPOSE.equals(purpose)&&schemaRef.startsWith(FormalEvaluationDeterministicSchema.URI+"#/$defs/")) {
            if(resourceKind!=null&&!"EVALUATION".equals(resourceKind))mismatch();
            String kind=FormalEvaluationDeterministicSchema.ref("STEP_ENVELOPE").equals(schemaRef)?"STEP_ENVELOPE":FormalEvaluationDeterministicSchema.ref("STEP_RESULT").equals(schemaRef)?"STEP_RESULT":null;if(kind==null)mismatch();
            try{String digest=FormalEvaluationDeterministicSchema.digest(kind);var fixed=new EvaluationDependencyPolicy.ArtifactProfile("EVALUATION","evaluation.read",schemaRef,digest,digest);if(configured.size()>1||!configured.isEmpty()&&!fixed.equals(configured.get(0)))mismatch();return fixed;}catch(IOException failure){throw failure;}catch(Exception failure){throw new IOException("EVALUATION_DETERMINISTIC_SCHEMA_UNAVAILABLE",failure);}
        }
        if(PURPOSE.equals(purpose)&&schemaRef.startsWith("urn:medical:evaluation-node-runtime:1.0.0#/$defs/")) {
            try {
                var runtime=new FormalEvaluationRuntimeSchema();String kind=null;
                for(String k:List.of("STEP_ENVELOPE","SESSION_EXPORT","STEP_RESULT"))if(runtime.ref(k).equals(schemaRef))kind=k;
                if(kind==null||resourceKind!=null&&!"EVALUATION".equals(resourceKind))mismatch();
                var fixed=new EvaluationDependencyPolicy.ArtifactProfile("EVALUATION","evaluation.read",schemaRef,runtime.manifest(kind),runtime.manifest(kind));
                if(configured.size()>1||!configured.isEmpty()&&!fixed.equals(configured.get(0)))mismatch();return fixed;
            } catch(java.io.IOException failure){throw failure;}catch(RuntimeException failure){throw failure;}catch(Exception failure){throw new IOException("EVALUATION_RUNTIME_SCHEMA_UNAVAILABLE",failure);}
        }

        if(PURPOSE.equals(purpose)&&FormalEvaluationBusinessSchema.known(schemaRef)) {
            if(resourceKind!=null&&!"EVALUATION".equals(resourceKind))mismatch();
            try{String digest=FormalEvaluationBusinessSchema.digest(schemaRef);var fixed=new EvaluationDependencyPolicy.ArtifactProfile("EVALUATION","evaluation.read",schemaRef,digest,digest);if(configured.size()>1||!configured.isEmpty()&&!fixed.equals(configured.get(0)))mismatch();return fixed;}catch(IOException failure){throw failure;}catch(Exception failure){throw new IOException("EVALUATION_BUSINESS_SCHEMA_UNAVAILABLE",failure);}
        }
        if((PURPOSE.equals(purpose)||"AGENT_PUBLICATION".equals(purpose))&&(schemaRef.startsWith("urn:medical:evaluation-stage-input:1.0.0#/$defs/")||FormalEvaluationStageInputSchema.CASE_V2.equals(schemaRef))) {
            if(!Set.of(FormalEvaluationStageInputSchema.CASE,FormalEvaluationStageInputSchema.CASE_V2,FormalEvaluationStageInputSchema.PROMPT,FormalEvaluationStageInputSchema.TOOL).contains(schemaRef)||resourceKind!=null&&!"EVALUATION".equals(resourceKind))mismatch();
            try{String digest=new FormalEvaluationStageInputSchema().digest(FormalEvaluationStageInputSchema.CASE_V2.equals(schemaRef)?"2.0.0":"1.0.0");var fixed=new EvaluationDependencyPolicy.ArtifactProfile("EVALUATION","evaluation.read",schemaRef,digest,digest);if(configured.size()>1||!configured.isEmpty()&&!fixed.equals(configured.get(0)))mismatch();return fixed;}catch(IOException failure){throw failure;}catch(Exception failure){throw new IOException("EVALUATION_STAGE_SCHEMA_UNAVAILABLE",failure);}
        }
        // Explicit additive package publications only. This selects schema metadata, not access rights.
        if("AGENT_PUBLICATION".equals(purpose)) {
            try {
                var catalogue=com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath();
                for(String name:List.of("task-section-writer-package-v2","task-source-verifier-package-v2","document-review-package-v2"))
                    for(String definition:List.of("PromptPackage","RolePackage","ProfilePackage"))
                        if(catalogue.schemaRef(name,definition).equals(schemaRef)) {
                            if(resourceKind!=null&&!"PROJECT".equals(resourceKind))mismatch();
                            String digest=catalogue.schemaDigest(name);
                            var fixed=new EvaluationDependencyPolicy.ArtifactProfile("PROJECT","project.read",schemaRef,digest,digest);
                            if(configured.size()>1||!configured.isEmpty()&&!fixed.equals(configured.get(0)))mismatch();return fixed;
                        }
            } catch(IOException failure){throw failure;}catch(RuntimeException failure){throw failure;}catch(Exception failure){throw new IOException("PROFESSIONAL_V2_PACKAGE_SCHEMA_UNAVAILABLE",failure);}
        }
        if("SIMULATION_EVALUATION".equals(purpose)) {
            var known=SimulationArtifactProfiles.known(schemaRef,resourceKind);
            if(known.isPresent()) {
                var fixed=known.get();
                if(configured.size()>1||!configured.isEmpty()&&!configured.get(0).equals(fixed))mismatch();
                return fixed;
            }
        }
        if(configured.size()!=1)throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
        return configured.get(0);
    }
    void artifact(String project,PermissionProtocol.Basis ref,String requiredSchema)throws IOException {
        artifact(project,ref);var binding=single(artifacts.binding(ref.version_id()));
        if(!requiredSchema.equals(binding.schemaRef()))mismatch();
    }
    void artifact(String project,PermissionProtocol.Basis ref) throws IOException {
        var artifact=single(artifacts.artifact(ref.version_id()));var version=single(artifacts.version(ref.version_id()));
        if(!TenantScope.requireCurrent().tenantId().equals(artifact.tenantId())||!artifact.artifactId().equals(ref.version_id())
            ||artifact.projectId()!=null&&!artifact.projectId().equals(project)||!ref.content_sha256().equals(artifact.sha256())||!artifact.sha256().equals(artifact.contentSha256())
            ||!"ARTIFACT".equals(version.entityKind())||!ref.version_id().equals(version.id())||!ref.content_sha256().equals(version.contentSha256())
            ||!Objects.equals(artifact.projectId(),version.projectId()))mismatch();
        StoredObjectReferences.decode(artifact.storedObject(),artifact.reference());
    }
    private static <T>T single(List<T> values){if(values.size()!=1)mismatch();return values.get(0);}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
