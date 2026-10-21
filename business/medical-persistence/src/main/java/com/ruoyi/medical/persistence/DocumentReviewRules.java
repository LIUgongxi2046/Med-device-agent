package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** N07 complete approved rule closure, independently authorized against the saved document's scope. */
@Component
public final class DocumentReviewRules {
    private final TemplateMapper rows;private final TemplateApplicabilitySchema schema;private final DependencyLineage lineage;
    private final VersionResourceAuthority versions;private final SourceReadAuthority sources;private final SourceReadMapper sourceRows;
    private final ArtifactRegistryMapper artifacts;private final EvaluationReferences profiles;private final EvaluationDependencyPolicy limits;
    private final org.springframework.jdbc.core.JdbcTemplate db;private final ProjectSchema projects;private final RuleExpression engine;
    private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
    public DocumentReviewRules(TemplateMapper rows,TemplateApplicabilitySchema schema,DependencyLineage lineage,VersionResourceAuthority versions,SourceReadAuthority sources,SourceReadMapper sourceRows,ArtifactRegistryMapper artifacts,EvaluationReferences profiles,EvaluationDependencyPolicy limits,@org.springframework.beans.factory.annotation.Qualifier("masterDataSource") javax.sql.DataSource data,ProjectSchema projects,RuleExpression engine){this.db=new org.springframework.jdbc.core.JdbcTemplate(data);db.setQueryTimeout(5);this.projects=projects;this.engine=engine;this.rows=rows;this.schema=schema;this.lineage=lineage;this.versions=versions;this.sources=sources;this.sourceRows=sourceRows;this.artifacts=artifacts;this.profiles=profiles;this.limits=limits;}
    Verified require(EnterpriseAccess.Admission a,DocumentReviewAuthority.Verified target)throws Exception{return new Verified(target,inspect(a,target));}
    private JsonNode inspect(EnterpriseAccess.Admission a,DocumentReviewAuthority.Verified target)throws Exception {
        target.requireCurrent(a);limits.requireConfigured();var h=target.header();var rules=new TreeMap<String,TaskSourceVerifierRules.Rule>();var witnesses=new LinkedHashMap<String,Object>();
        var scopedSources=new HashMap<String,SourceReadProtocol.ReadRequirement>();for(var r:target.bodyIntent().sources())if(scopedSources.put(r.sourceVersion(),r)!=null)bad();
        for(String root:List.of(h.templateVersion(),h.planRevision(),h.profileRevision())){
            var graph=lineage.inspect(root,limits.limits());var required=new HashMap<String,ArtifactRegistryProtocol.ReadRequirement>();var verifiedSources=new HashMap<String,SourceReadProtocol.VerifiedSource>();
            for(var v:graph.versions().values()){
                if(v.projectId()!=null&&!h.projectId().equals(v.projectId()))deny();
                var sr=sourceRows.source(v.id());if(sr.size()>1)bad();if(!sr.isEmpty()){var requirement=scopedSources.get(v.id());if(requirement==null)deny();verifiedSources.put(v.id(),sources.require(a,requirement));}
                if("ARTIFACT".equals(v.entityKind())){var b=one(artifacts.binding(v.id()));var p=profiles.profile(b.schemaRef(),"DOCUMENT_BODY",b.resourceKind());required.put(v.id(),new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(h.tenantId(),b.projectId(),b.resourceKind(),b.resourceId()),p.action(),"DOCUMENT_BODY",p.schemaRef(),p.schemaDocumentSha256(),p.artifactSchemaManifest(),limits.limits()));}
                if("RULE_VERSION".equals(v.entityKind())){
                    var query=Map.<String,Object>of("userId",a.actorId(),"upstreamId",v.id());var row=one(rows.ruleDefinition(query));
                    if(!h.tenantId().equals(row.tenantId())||!v.id().equals(row.ruleVersion())||!"APPROVED".equals(row.status())||!v.contentSha256().equals(row.contentHash()))bad();
                    var content=schema.rule(row);if(!content.basis_manifest().stream().map(PermissionProtocol.Basis::version_id).sorted().toList().equals(rows.ruleParents(query)))bad();
                    var rule=new TaskSourceVerifierRules.Rule(v.id(),v.contentSha256(),content);var old=rules.putIfAbsent(v.id(),rule);if(old!=null&&!old.equals(rule))bad();
                }
            }
            var authorized=versions.require(a,graph,"DOCUMENT_BODY",new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.FORMAL,null),required);
            for(var v:authorized.values())if(v.owner().sourceVersion()!=null&&!verifiedSources.containsKey(v.owner().sourceVersion()))deny();
            witnesses.put(root,AgentPublicationWitness.tree(Map.of("versions",authorized,"sources",verifiedSources)));
        }
        if(rules.isEmpty())throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        var n=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("node_kind","N07").put("target_kind","DOCUMENT").put("document_revision",h.documentRevision()).put("target_sha256",h.bodySha256());n.set("rules",json.tree(rules.values()));n.set("authorizations",json.tree(witnesses));
        var plan=one(db.queryForList("SELECT c.content_json,c.content_sha256,v.content_sha256 AS registry_hash FROM plan_content_revision c JOIN version_registry v ON v.tenant_id=c.tenant_id AND v.version_id=c.plan_revision WHERE c.tenant_id=? AND c.project_id=? AND c.plan_revision=? LIMIT 2 FOR UPDATE",h.tenantId(),h.projectId(),h.planRevision()));var planJson=json.read(plan.get("content_json").toString());if(!json.hash(planJson).equals(plan.get("content_sha256"))||!plan.get("content_sha256").equals(plan.get("registry_hash")))bad();
        var parsed=TypedSchemaBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.PLAN_CANDIDATE,planJson).read();var completion=new LinkedHashSet<String>();parsed.sections().forEach(section->completion.addAll(section.completionRuleIds()));parsed.questions().forEach(question->completion.addAll(question.completionRuleIds()));if(!rules.keySet().containsAll(completion))bad();
        var profile=one(db.queryForList("SELECT p.attributes,p.authority_refs,p.content_sha256,v.content_sha256 AS registry_hash FROM project_profile_revision p JOIN version_registry v ON v.tenant_id=p.tenant_id AND v.version_id=p.profile_revision WHERE p.tenant_id=? AND p.project_id=? AND p.profile_revision=? LIMIT 2 FOR UPDATE",h.tenantId(),h.projectId(),h.profileRevision()));var attrs=json.read(profile.get("attributes").toString());var refs=json.read(profile.get("authority_refs").toString());var profileBody=json.tree(Map.of("attributes",attrs,"authority_refs",refs));if(!json.hash(profileBody).equals(profile.get("content_sha256"))||!profile.get("content_sha256").equals(profile.get("registry_hash")))bad();var attributes=projects.attributes(attrs);
        var outcomes=new TreeMap<String,String>();for(String id:completion){var result=TaskResearchCompletionChecks.evaluate(engine,id,List.copyOf(completion),rules.get(id).content(),attributes);outcomes.put(id,switch(result.outcome()){case SATISFIED->"PASS";case NOT_SATISFIED->"FAIL";case UNKNOWN->"BLOCKED";});}n.set("completion_results",json.tree(outcomes));n.put("profile_sha256",profile.get("content_sha256").toString());target.requireCurrent(a);return n;
    }
    public final class Verified {
        private final DocumentReviewAuthority.Verified target;private final JsonNode manifest;
        private Verified(DocumentReviewAuthority.Verified target,JsonNode manifest){this.target=target;this.manifest=manifest.deepCopy();}
        public void requireCurrent(EnterpriseAccess.Admission a)throws Exception{target.requireCurrent(a);if(!manifest.equals(inspect(a,target)))throw new TenantAccessException(ErrorCode.REVIEW_STALE);}
        public JsonNode manifest(){return manifest.deepCopy();}
        public String sha256()throws Exception{return json.hash(manifest);}
        public List<String> ruleIds(){var ids=new ArrayList<String>();manifest.required("rules").forEach(r->ids.add(r.required("rule_id").asText()));return List.copyOf(ids);}
        public ArtifactProtocol.RuleResult deterministicResult(String rule){var value=manifest.required("completion_results").get(rule);return value==null?null:ArtifactProtocol.RuleResult.valueOf(value.asText());}
        public DocumentReviewAuthority.Verified target(){return target;}
    }
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
