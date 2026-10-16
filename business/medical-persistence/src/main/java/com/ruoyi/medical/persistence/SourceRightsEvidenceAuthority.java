package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class SourceRightsEvidenceAuthority {
    static final String PURPOSE="SOURCE_RIGHTS_REVIEW";
    private final ArtifactRegistry registry;
    private final ArtifactSourceAuthority artifacts;
    private final ArtifactRegistryMapper bindings;
    private final SourceReadMapper sources;
    private final SourceRightsPolicy policy;
    private final PermissionSchema json;

    public SourceRightsEvidenceAuthority(ArtifactRegistry registry,ArtifactSourceAuthority artifacts,
            ArtifactRegistryMapper bindings,SourceReadMapper sources,SourceRightsPolicy policy,PermissionSchema json) {
        this.registry=registry; this.artifacts=artifacts; this.bindings=bindings; this.sources=sources; this.policy=policy; this.json=json;
    }

    List<SourceRightsProtocol.Evidence> require(EnterpriseAccess.Admission admission,SourceRightsProtocol.Proposal proposal) throws IOException {
        policy.requireConfigured();
        if(proposal==null) return List.of();
        if(proposal.evidence_manifest().authorization_evidence().size()>policy.maximumEvidence()) denied(ErrorCode.CONTEXT_LIMIT);
        var results=new ArrayList<SourceRightsProtocol.Evidence>();
        var allVersions=new java.util.HashSet<String>(); var allRelations=new java.util.HashSet<SourceReadProtocol.ParentRow>();
        long bytes=0;
        for(var ref:proposal.evidence_manifest().authorization_evidence()) {
            var required=requirement(admission,ref.version_id(),"AUTHORIZATION");
            var registration=registry.requireRegistration(admission,ref.version_id(),required);
            if(!ref.content_sha256().equals(registration.artifact().sha256())) denied(ErrorCode.CONTENT_MISMATCH);
            var graph=registration.lineage(); allVersions.addAll(graph.versions().keySet()); allRelations.addAll(graph.graph().edgeSet());
            if(allVersions.size()>policy.maximumVersions() || allRelations.size()>policy.maximumRelations()) denied(ErrorCode.CONTEXT_LIMIT);
            var sourceRequirements=new ArrayList<SourceReadProtocol.ReadRequirement>();
            var upstreamArtifacts=new LinkedHashMap<String,ArtifactRegistryProtocol.ReadRequirement>();
            for(var version:graph.versions().values()) {
                var rows=sources.source(version.id()); if(rows.size()>1) denied(ErrorCode.CONTENT_MISMATCH);
                if(!rows.isEmpty()) {
                    var source=rows.get(0);
                    sourceRequirements.add(new SourceReadProtocol.ReadRequirement(source.sourceVersion(),source.rightsRevision(),
                        registration.artifact().projectId(),null,null,null,SourceReadProtocol.Use.REVIEW,PURPOSE,
                        policy.environment(),policy.region(),null));
                }
                if("ARTIFACT".equals(version.entityKind()) && !version.id().equals(ref.version_id()))
                    upstreamArtifacts.put(version.id(),requirement(admission,version.id(),"DEPENDENCY"));
            }
            var verified=artifacts.require(admission,ref.version_id(),required,sourceRequirements,
                new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW,null),upstreamArtifacts);
            var artifact=verified.registration().artifact(); bytes=Math.addExact(bytes,artifact.sizeBytes().longValueExact());
            if(bytes>policy.maximumEvidenceBytes()) denied(ErrorCode.FILE_TOO_LARGE);
            var manifest=json.object(); manifest.set("artifact",json.tree(ref));
            manifest.put("artifact_kind",artifact.artifactKind()); manifest.put("classification",artifact.classification());
            manifest.put("media_type",artifact.mediaType()); manifest.put("size_bytes",artifact.sizeBytes().toString());
            manifest.put("schema_ref",required.schemaRef()); manifest.put("schema_document_sha256",required.schemaDocumentSha256());
            results.add(new SourceRightsProtocol.Evidence(verified,artifact.reference(),manifest));
        }
        return List.copyOf(results);
    }

    private ArtifactRegistryProtocol.ReadRequirement requirement(EnterpriseAccess.Admission admission,String id,String role) {
        var binding=single(bindings.binding(id)); var artifact=single(bindings.artifact(id));
        var profiles=policy.artifactProfiles().stream().filter(profile -> role.equals(profile.role())
            && profile.schemaRef().equals(binding.schemaRef()) && profile.artifactKind().equals(artifact.artifactKind())).toList();
        if(profiles.size()!=1) denied(ErrorCode.SCHEMA_UNSUPPORTED);
        var profile=profiles.get(0);
        if(!profile.resourceKind().equals(binding.resourceKind()) || !profile.artifactKind().equals(artifact.artifactKind()))
            denied(ErrorCode.CONTENT_MISMATCH);
        return new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(admission.context().tenant_id(),
            binding.projectId(),profile.resourceKind(),binding.resourceId()),profile.action(),PURPOSE,
            profile.schemaRef(),profile.schemaDocumentSha256(),profile.artifactSchemaManifest(),policy.limits());
    }

    private static <T> T single(List<T> rows) { if(rows.size()!=1) denied(ErrorCode.OBJECT_UNAVAILABLE); return rows.get(0); }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
}
