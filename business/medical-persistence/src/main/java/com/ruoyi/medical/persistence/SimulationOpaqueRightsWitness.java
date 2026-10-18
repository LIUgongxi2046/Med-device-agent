package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Internal validation of an already-approved rights decision's private witness.
 * This is NOT SourceReadAuthority content admission and grants no file access.
 * Bounded v1 support: a configured SOURCE-owned original artifact with exactly
 * one enterprise SOURCE_VERSION parent. Derived or nested proofs fail closed.
 */
@Component
public final class SimulationOpaqueRightsWitness {
    private final PermissionSchema json;private final SourceRightsPolicy policy;private final SourceRightsSchema rights;
    private final PermissionMapper permissions;private final SourceReadMapper sources;private final ArtifactRegistryMapper artifacts;
    private final SimulationFixtureMapper originals;private final DependencyManifestSchema manifests;private final DependencyLineage lineage;
    public SimulationOpaqueRightsWitness(PermissionSchema json,SourceRightsPolicy policy,SourceRightsSchema rights,
            PermissionMapper permissions,SourceReadMapper sources,ArtifactRegistryMapper artifacts,SimulationFixtureMapper originals,
            DependencyManifestSchema manifests,DependencyLineage lineage) {
        this.json=json;this.policy=policy;this.rights=rights;this.permissions=permissions;this.sources=sources;
        this.artifacts=artifacts;this.originals=originals;this.manifests=manifests;this.lineage=lineage;
    }
    void require(EnterpriseAccess.Admission a,SimulationAuthorizedUseRightsVerifier.Verified approved)throws Exception {
        policy.requireConfigured();
        // A changed review policy requires renewed governance; never silently reinterpret historical review.
        same(json.tree(policy),approved.reviewPolicy());
        if(approved.evidence().isEmpty()||approved.evidence().size()>policy.maximumEvidence())bad();
        long bytes=0;var allVersions=new HashSet<String>();var allEdges=new HashSet<SourceReadProtocol.ParentRow>();
        for(var evidence:approved.evidence()) {
            var artifact=one(artifacts.artifact(evidence.ref().version_id()));var binding=one(artifacts.binding(artifact.artifactId()));
            same(a.context().tenant_id(),artifact.tenantId());same(null,artifact.projectId());same(artifact.tenantId(),binding.tenantId());same(null,binding.projectId());
            same(evidence.ref().version_id(),artifact.artifactId());same(artifact.artifactId(),binding.artifactId());same("SOURCE",binding.resourceKind());
            same(evidence.ref().content_sha256(),artifact.sha256());same(artifact.sha256(),artifact.contentSha256());same("1.0.0",artifact.schemaVersion());
            same(evidence.object(),StoredObjectReferences.decode(artifact.storedObject(),artifact.reference()));
            var metadata=json.object();metadata.set("artifact",json.tree(evidence.ref()));metadata.put("artifact_kind",artifact.artifactKind());
            metadata.put("classification",artifact.classification());metadata.put("media_type",artifact.mediaType());metadata.put("size_bytes",artifact.sizeBytes().toString());
            metadata.put("schema_ref",binding.schemaRef());metadata.put("schema_document_sha256",binding.schemaDocumentSha256());same(metadata,evidence.manifest());
            var profiles=policy.artifactProfiles().stream().filter(p->"AUTHORIZATION".equals(p.role())&&"SOURCE".equals(p.resourceKind())
                &&artifact.artifactKind().equals(p.artifactKind())&&binding.schemaRef().equals(p.schemaRef())).toList();
            var profile=one(profiles);same("source.read",profile.action());same(profile.schemaDocumentSha256(),binding.schemaDocumentSha256());
            same(profile.artifactSchemaManifest(),artifact.schemaManifest());SchemaCatalogue.fromClasspath().requirePublished(profile.schemaRef(),profile.schemaDocumentSha256());
            same(json.tree(binding),evidence.authorization().required("binding"));same(artifact.accessSetId(),binding.accessSetId());
            var manifestRow=one(artifacts.manifest(artifact.accessSetId()));same(artifact.tenantId(),manifestRow.tenantId());same(null,manifestRow.projectId());
            same(binding.dependencyManifestSha256(),manifestRow.manifestSha256());
            var manifest=manifests.verify(manifestRow,artifacts.members(artifact.accessSetId()));same(json.tree(manifest),evidence.authorization().required("dependency_manifest"));
            var accessVersion=one(artifacts.version(artifact.accessSetId()));same(artifact.accessSetId(),accessVersion.id());same(null,accessVersion.projectId());
            same("DEPENDENCY_ACCESS_SET",accessVersion.entityKind());same(manifestRow.manifestSha256(),accessVersion.contentSha256());
            var graph=lineage.require(artifact.artifactId(),manifest.members(),policy.limits());
            allVersions.addAll(graph.versions().keySet());allEdges.addAll(graph.graph().edgeSet());
            if(graph.versions().size()!=2||graph.graph().edgeSet().size()!=1||manifest.members().size()!=1)bad();
            var parent=manifest.members().get(0);var source=one(sources.source(parent.upstreamVersionId()));
            same("SOURCE_VERSION",graph.versions().get(source.sourceVersion()).entityKind());
            same("ARTIFACT",graph.versions().get(artifact.artifactId()).entityKind());
            same(artifact.sha256(),graph.versions().get(artifact.artifactId()).contentSha256());
            same(source.originalSha256(),graph.versions().get(source.sourceVersion()).contentSha256());
            for(var edge:graph.graph().edgeSet()) {same(artifact.artifactId(),edge.consumerVersionId());same(source.sourceVersion(),edge.upstreamVersionId());}
            for(var version:graph.versions().values()) {
                same(null,version.projectId());same(json.tree(version),evidence.authorization().required("versions").required(version.id()).required("version"));
            }
            if(evidence.authorization().required("versions").size()!=2||evidence.authorization().required("sources").size()!=1)bad();
            same(a.context().tenant_id(),source.tenantId());same(null,source.ownerProjectId());same("ENTERPRISE",source.ownership());
            same(null,source.registeredProjectId());same(source.originalSha256(),source.registeredSha256());same(source.sourceId(),binding.resourceId());
            var prior=evidence.authorization().required("sources").required(source.sourceVersion());same(json.tree(source),prior.required("source"));
            same(parent.rightsRevision(),source.rightsRevision());var currentRights=one(sources.rights(source.rightsRevision()));
            same(rights.verify(currentRights),prior.required("rights"));
            rights.require(currentRights,source,new SourceReadProtocol.ReadRequirement(source.sourceVersion(),source.rightsRevision(),null,null,null,null,
                SourceReadProtocol.Use.REVIEW,SourceRightsEvidenceAuthority.PURPOSE,policy.environment(),policy.region(),null),Objects.requireNonNull(permissions.databaseTime()));
            var gates=sources.gates(source.sourceVersion());if(gates.isEmpty())bad();
            for(var gate:gates){com.ruoyi.medical.contracts.WireValues.revision(gate.revision().toString());if(!"ACTIVE".equals(gate.lifecycle())||!"RECEIVED".equals(gate.uploadStatus())||!"APPROVED".equals(gate.approvalStatus()))bad();}
            var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("sourceVersion",source.sourceVersion());
            var original=one(originals.original(values));same(source.tenantId(),original.tenantId());same(source.sourceVersion(),original.sourceVersion());
            same(source.originalSha256(),original.sha256());same(evidence.object(),original.reference());
            bytes=Math.addExact(bytes,artifact.sizeBytes().longValueExact());
            if(bytes>policy.maximumEvidenceBytes()||allVersions.size()>policy.maximumVersions()||allEdges.size()>policy.maximumRelations())
                throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        }
    }
    private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}
    private static void same(Object left,Object right){if(!Objects.equals(left,right))bad();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
