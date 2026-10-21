package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;

/** Complete frozen rule-version set from the run's approved dependency graph. It cannot be
 * filtered by a model or supplied by the author. Current rule approvals and source ancestors
 * are re-authorized independently at every boundary. */
public final class TaskSourceVerifierRules {
    private final PermissionSchema json;private final TemplateApplicabilitySchema schema;private final TemplateMapper rows;
    private final DependencyLineage lineage;private final VersionResourceAuthority versions;private final SourceReadAuthority sources;
    private final SourceReadMapper sourceRows;private final ArtifactRegistryMapper artifacts;private final EvaluationReferences profiles;private final EvaluationDependencyPolicy limits;
    public TaskSourceVerifierRules(PermissionSchema json,TemplateApplicabilitySchema schema,TemplateMapper rows,DependencyLineage lineage,
        VersionResourceAuthority versions,SourceReadAuthority sources,SourceReadMapper sourceRows,ArtifactRegistryMapper artifacts,
        EvaluationReferences profiles,EvaluationDependencyPolicy limits){this.json=json;this.schema=schema;this.rows=rows;this.lineage=lineage;this.versions=versions;this.sources=sources;this.sourceRows=sourceRows;this.artifacts=artifacts;this.profiles=profiles;this.limits=limits;}
    public Verified require(TaskNodeProductionAuthority.Verified production)throws Exception{return new Verified(production,read(production));}
    public record Rule(String ruleId,String sha256,TemplateApplicabilityProtocol.RuleContent content){ }
    private record State(List<Rule> rules,JsonNode witness,String hash){}
    private State read(TaskNodeProductionAuthority.Verified p)throws Exception {
        p.requireUnchanged();limits.requireConfigured();var replay=p.replay().content();var pinned=new TreeMap<String,JsonNode>();
        for(var version:replay.required("dependency_versions"))if(pinned.put(text(version,"version_id"),version)!=null)bad();
        var out=new ArrayList<Rule>();var witnesses=json.object();var query=new HashMap<String,Object>();query.put("userId",p.actorId());
        String purpose=text(replay.required("execution_policy"),"business_purpose");
        for(var entry:pinned.entrySet())if("RULE_VERSION".equals(text(entry.getValue(),"entity_kind"))){
            query.put("upstreamId",entry.getKey());var row=one(rows.ruleDefinition(query));
            if(!p.tenantId().equals(row.tenantId())||!entry.getKey().equals(row.ruleVersion())||!"APPROVED".equals(row.status())||!text(entry.getValue(),"content_sha256").equals(row.contentHash()))bad();
            var rule=schema.rule(row);var parents=rule.basis_manifest().stream().map(PermissionProtocol.Basis::version_id).sorted().toList();if(!parents.equals(rows.ruleParents(query)))bad();
            var evidence=dependency(p,pinned,new PermissionProtocol.Basis(entry.getKey(),row.contentHash()),purpose);witnesses.set(entry.getKey(),evidence);out.add(new Rule(entry.getKey(),row.contentHash(),rule));
        }
        if(out.isEmpty())throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        var fixed=json.object();fixed.set("rules",json.tree(out));fixed.set("authorizations",witnesses);p.requireUnchanged();return new State(List.copyOf(out),fixed,WireValues.sha256(ContentReadAuthorization.canonicalBytes(fixed)));
    }
    private JsonNode dependency(TaskNodeProductionAuthority.Verified p,Map<String,JsonNode> pins,PermissionProtocol.Basis root,String purpose)throws Exception {
        var replay=p.replay().content();var a=p.admission();var graph=lineage.inspect(root.version_id(),limits.limits());var actual=graph.versions().get(root.version_id());if(actual==null||!root.content_sha256().equals(actual.contentSha256()))bad();
        var required=new HashMap<String,ArtifactRegistryProtocol.ReadRequirement>();var sourceProofs=new HashMap<String,SourceReadProtocol.VerifiedSource>();
        for(var version:graph.versions().values()){
            var pin=pins.get(version.id());if(pin==null||!version.entityKind().equals(text(pin,"entity_kind"))||!version.contentSha256().equals(text(pin,"content_sha256")))bad();
            if(version.projectId()!=null&&!p.projectId().equals(version.projectId()))deny();
            var source=sourceRows.source(version.id());if(source.size()>1)bad();if(!source.isEmpty()){
                var selected=new ArrayList<JsonNode>();for(var candidate:replay.required("scope_snapshot").required("members"))if(version.id().equals(text(candidate,"source_version")))selected.add(candidate);var selection=one(selected);var policy=replay.required("execution_policy");
                sourceProofs.put(version.id(),sources.require(a,new SourceReadProtocol.ReadRequirement(version.id(),text(selection,"rights_revision"),p.projectId(),text(replay,"snapshot_id"),text(selection,"parse_version"),null,SourceReadProtocol.Use.FORMAL,purpose,text(policy,"environment"),text(policy,"region"),null)));
            }
            if("ARTIFACT".equals(version.entityKind())){var binding=one(artifacts.binding(version.id()));var profile=profiles.profile(binding.schemaRef(),purpose,binding.resourceKind());
                required.put(version.id(),new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(p.tenantId(),binding.projectId(),binding.resourceKind(),binding.resourceId()),profile.action(),purpose,profile.schemaRef(),profile.schemaDocumentSha256(),profile.artifactSchemaManifest(),limits.limits()));}
        }
        var verified=versions.require(a,graph,purpose,new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.FORMAL,null),required);
        for(var version:verified.values())if(version.owner().sourceVersion()!=null){var source=sourceProofs.get(version.owner().sourceVersion());if(source==null||!source.source().sourceId().equals(version.owner().resourceId())||!graph.reachableFrom(version.version().id()).contains(source.source().sourceVersion()))bad();}
        for(var version:verified.values())if(version.artifactRegistration()!=null)for(var member:version.artifactRegistration().manifest().members()){
            var right=one(sourceRows.rights(member.rightsRevision()));var source=sourceProofs.get(right.sourceVersion());if(source==null||!source.rights().rightsRevision().equals(member.rightsRevision())||!graph.reachableFrom(member.upstreamVersionId()).contains(right.sourceVersion()))bad();}
        var witness=json.object();witness.set("versions",AgentPublicationWitness.tree(verified));witness.set("sources",AgentPublicationWitness.tree(sourceProofs));return witness;
    }
    public final class Verified {
        private final TaskNodeProductionAuthority.Verified production;private final State state;
        private Verified(TaskNodeProductionAuthority.Verified p,State state){production=p;this.state=state;}
        public void requireUnchanged()throws Exception{production.requireUnchanged();if(!state.hash.equals(read(production).hash))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
        public List<Rule> rules(){production.admission();return state.rules;}public JsonNode witness(){production.admission();return state.witness.deepCopy();}
        public List<PermissionProtocol.Basis> bases(){return state.rules.stream().map(r->new PermissionProtocol.Basis(r.ruleId,r.sha256)).toList();}
        public JsonNode manifest(String revision){WireValues.uuid(revision);var n=json.object().put("schema_version","1.0.0").put("node_kind","N05").put("revision_id",revision);n.set("rules",json.tree(state.rules));return n;}
    }
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String text(JsonNode n,String k){if(!n.required(k).isTextual())bad();return n.required(k).textValue();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
