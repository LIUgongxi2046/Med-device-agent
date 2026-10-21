package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.VersionResourceMapper;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.util.*;

/** Actual source ancestry only. Governance versions never acquire invented content rights. */
final class TaskFieldTargetContentAuthority {
    private final TaskFieldSourceReceiptStore receipts;
    private final DependencyLineage lineage;
    private final java.util.function.Supplier<DependencyLineage.Limits> limitSupplier;
    private final VersionResourceMapper owners;
    TaskFieldTargetContentAuthority(TaskFieldSourceReceiptStore receipts,DependencyLineage lineage,
            java.util.function.Supplier<DependencyLineage.Limits> limitSupplier,VersionResourceMapper owners) {
        this.receipts=receipts;this.lineage=lineage;this.limitSupplier=limitSupplier;this.owners=owners;
    }
    record Content(List<String> roots,List<ArtifactRegistryProtocol.MemberRow> members,
            Map<String,ArtifactRegistryProtocol.VersionRow> versions,Set<SourceReadProtocol.ParentRow> edges) { }
    Content require(EnterpriseAccess.Admission a,JsonNode manifest,String mapping,String action) throws Exception {
        limitSupplier.get();var rights=new TreeMap<String,String>();var receiptIds=new HashSet<String>();
        for(var binding:manifest.required("entry_bindings")) {
            String id=binding.required("source_read_receipt_id").asText();
            if(!receiptIds.add(id))bad();
            var receipt=receipts.requireHistorical(a,id,mapping,action).manifest();
            if(!receipt.required("entry_key").equals(binding.required("entry_key"))
                    || !receipt.required("task_id").equals(manifest.required("target").required("identity").required("task_id")))bad();
            String source=receipt.required("source_version").asText(),right=receipt.required("rights_revision").asText();
            String previous=rights.putIfAbsent(source,right);if(previous!=null&&!previous.equals(right))bad();
        }
        var pins=new TreeMap<String,String>();for(var pin:manifest.required("source_lineage"))
            if(pins.put(pin.required("version_id").asText(),pin.required("content_sha256").asText())!=null)bad();
        return closure(a,pins,rights);
    }
    Content preflight(TaskFieldTargetBasis.Verified basis,TaskFieldMappingService.Verified mapping,
            List<TaskFieldSourceReader.Read> reads,ArtifactRegistryMapper registry) throws Exception {
        limitSupplier.get();basis.requireAction("task.field_target.produce");basis.requireUnchanged();mapping.requireUnchanged();
        var a=basis.admission();var rights=new TreeMap<String,String>();var pins=new TreeMap<String,String>();var keys=new HashSet<String>();
        for(var read:reads) {
            var stored=receipts.requireHistorical(a,read.receiptId(),mapping.mappingRevision(),"task.field_target.produce");
            var receipt=stored.manifest();
            if(!stored.sha256().equals(read.receiptSha256())||!receipt.equals(read.manifest())
                    ||!a.actorId().equals(receipt.required("actor_id").asText())
                    ||!basis.task().head().taskId().equals(receipt.required("task_id").asText())
                    ||!mapping.mappingSha256().equals(receipt.required("mapping_sha256").asText())
                    ||!keys.add(receipt.required("entry_key").asText()))bad();
            String source=receipt.required("source_version").asText(),right=receipt.required("rights_revision").asText();
            var prior=rights.putIfAbsent(source,right);if(prior!=null&&!prior.equals(right))bad();
            for(String version:List.of(source,receipt.required("parse_version").asText(),receipt.required("source_exact").required("field_revision").asText())) {
                var found=registry.version(version);if(found.size()!=1)bad();
                var row=found.get(0);if(!version.equals(row.id()))bad();
                if(version.equals(receipt.required("source_exact").required("field_revision").asText())
                        &&!row.contentSha256().equals(receipt.required("source_exact").required("manifest_sha256").asText()))bad();
                var old=pins.putIfAbsent(version,row.contentSha256());if(old!=null&&!old.equals(row.contentSha256()))bad();
            }
        }
        return closure(a,pins,rights);
    }
    private Content closure(EnterpriseAccess.Admission a,Map<String,String> pins,Map<String,String> rights) {
        var limits=limitSupplier.get();
        var roots=new TreeSet<String>();var versions=new TreeMap<String,ArtifactRegistryProtocol.VersionRow>();
        var edges=new HashSet<SourceReadProtocol.ParentRow>();
        for(var pin:pins.entrySet()) {
            String root=pin.getKey();if(!roots.add(root))bad();
            var graph=lineage.inspect(root,limits);var rootRow=graph.versions().get(root);
            if(rootRow==null||!rootRow.contentSha256().equals(pin.getValue()))bad();
            for(var entry:graph.versions().entrySet()) {var old=versions.putIfAbsent(entry.getKey(),entry.getValue());if(old!=null&&!old.equals(entry.getValue()))bad();}
            edges.addAll(graph.graph().edgeSet());
            if(versions.size()>limits.maximumVersions()||edges.size()>limits.maximumRelations())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        }
        var members=new ArrayList<ArtifactRegistryProtocol.MemberRow>();
        if(!versions.isEmpty()) {
            var rows=owners.ownersAcrossDomains(List.copyOf(versions.keySet()),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());
            var grouped=rows.stream().collect(java.util.stream.Collectors.groupingBy(r->r.owner().versionId()));
            for(var version:versions.values()) {
                var candidates=grouped.getOrDefault(version.id(),List.of());if(candidates.size()!=1)bad();
                var owner=candidates.get(0).owner();
                if(!"SOURCE".equals(owner.resourceKind())||owner.sourceVersion()==null)bad();
                String right=rights.get(owner.sourceVersion());
                if(right==null||!a.context().tenant_id().equals(owner.tenantId())||!"SOURCE".equals(owner.resourceKind())
                        ||owner.conversationId()!=null||!Objects.equals(version.projectId(),owner.versionProjectId())
                        ||owner.contentSha256()!=null&&!owner.contentSha256().equals(version.contentSha256())
                        ||!TaskRunReplayContentAuthority.reachable(version.id(),owner.sourceVersion(),edges))bad();
                members.add(new ArtifactRegistryProtocol.MemberRow(version.id(),right));
            }
        }
        if(rights.isEmpty()!=roots.isEmpty())bad();
        return new Content(List.copyOf(roots),List.copyOf(members),Map.copyOf(versions),Set.copyOf(edges));
    }
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
