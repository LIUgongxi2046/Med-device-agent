package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Exact confirmed completion bindings and frozen profile facts. Never maps model text into facts. */
public final class TaskResearchRuleInputs {
    private final JdbcTemplate db;private final PermissionSchema json;private final ProjectSchema projects;
    private final TemplateApplicabilitySchema ruleSchema;private final TemplateMapper rules;
    private final DependencyLineage lineage;private final VersionResourceAuthority versions;
    private final SourceReadAuthority sources;private final SourceReadMapper sourceRows;
    private final ArtifactRegistryMapper artifacts;private final EvaluationReferences profiles;
    private final EvaluationDependencyPolicy limits;
    public TaskResearchRuleInputs(DataSource data,PermissionSchema json,ProjectSchema projects,TemplateApplicabilitySchema ruleSchema,
            TemplateMapper rules,DependencyLineage lineage,VersionResourceAuthority versions,SourceReadAuthority sources,
            SourceReadMapper sourceRows,ArtifactRegistryMapper artifacts,EvaluationReferences profiles,EvaluationDependencyPolicy limits){
        this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.projects=projects;this.ruleSchema=ruleSchema;this.rules=rules;
        this.lineage=lineage;this.versions=versions;this.sources=sources;this.sourceRows=sourceRows;this.artifacts=artifacts;this.profiles=profiles;this.limits=limits;
    }
    public Verified require(TaskProfessionalRuntimeService.Session session)throws Exception {
        var context=new CurrentSession(session);context.requireUnchanged();var state=read(context);return new Verified(context,state);
    }
    public Verified requireOriginal(EnterpriseAccess.Admission admission,TaskResearchCompletionAuthority.Input input)throws Exception {
        var context=new OriginalInput(admission,input);context.requireUnchanged();return new Verified(context,read(context));
    }
    public Verified requireNode(FormalTaskNodeAuthority.Verified node)throws Exception {
        var context=new CurrentNode(node);context.requireUnchanged();return new Verified(context,read(context));
    }
    private record CurrentNode(FormalTaskNodeAuthority.Verified node) implements Context {
        public EnterpriseAccess.Admission admission(){return node.controlAdmission();}
        public BoundInput input()throws Exception{var i=node.currentInput();return new BoundInput(i.manifest(),i.inputSha256());}
        public void requireUnchanged()throws Exception{node.requireUnchanged();node.controlAdmission().controlProof().requireActive();}
    }
    private sealed interface Context permits CurrentSession,OriginalInput,CurrentNode {
        EnterpriseAccess.Admission admission();BoundInput input()throws Exception;void requireUnchanged()throws Exception;
    }
    private record CurrentSession(TaskProfessionalRuntimeService.Session session) implements Context {
        public EnterpriseAccess.Admission admission(){return session.admission();}
        public BoundInput input()throws Exception{return new BoundInput(session.input().manifest(),session.input().inputSha256());}
        public void requireUnchanged()throws Exception{session.requireUnchanged();}
    }
    private record OriginalInput(EnterpriseAccess.Admission admission,TaskResearchCompletionAuthority.Input original) implements Context {
        public BoundInput input()throws Exception{return new BoundInput(original.manifest(),original.inputSha256());}
        public void requireUnchanged()throws Exception{original.requireUnchanged();if(!admission.actorId().equals(original.actorId())||!admission.context().tenant_id().equals(original.tenantId()))deny();if(admission.controlWorker())admission.controlProof().requireActive();else if(admission.formalWorker())admission.formalProof().requireActive();else deny();}
    }
    private record BoundInput(JsonNode manifest,TaskNodeInputProtocol.Input value) {
        private BoundInput(JsonNode manifest,String expected)throws Exception{this(manifest.deepCopy(),new TaskNodeInputSchema().read(ContentReadAuthorization.canonicalBytes(manifest)));same(expected,hash(manifest));}
        String tenantId(){return value.tenantId();}String projectId(){return value.projectId();}String taskId(){return value.taskId();}
        String runId(){return value.runId();}String questionText(){return value.questionText();}String snapshotId(){return value.snapshotId();}
        List<TaskNodeInputProtocol.SourceSelection> fixedSources(){return value.fixedSources();}
    }
    record Check(String ruleId,String ruleSha256,TemplateApplicabilityProtocol.RuleContent rule,JsonNode authorizations) { }
    private record State(String owner,String profileId,String profileHash,List<ProjectProtocol.Attribute> attributes,List<String> requirements,
            List<Check> checks,JsonNode witness,String fingerprint) { }
    public final class Verified {
        private final Context context;private final State state;
        private Verified(Context context,State state){this.context=context;this.state=state;}
        public void requireUnchanged()throws Exception{context.requireUnchanged();if(!state.fingerprint().equals(read(context).fingerprint()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
        public String ownerId(){context.admission();return state.owner();}public String profileId(){context.admission();return state.profileId();}
        public String profileSha256(){context.admission();return state.profileHash();}
        public List<ProjectProtocol.Attribute> attributes(){context.admission();return state.attributes();}
        public List<String> requirementIds(){context.admission();return state.requirements();}
        List<Check> checks(){context.admission();return state.checks();}
        public JsonNode witness(){context.admission();return state.witness().deepCopy();}
    }
    private State read(Context context)throws Exception {
        context.requireUnchanged();limits.requireConfigured();var a=context.admission();var input=context.input();var fixed=input.manifest();
        var run=one(db.queryForList("SELECT replay_manifest,classification FROM task_run WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? AND created_by=? AND execution_kind='TASK' FOR UPDATE",input.tenantId(),input.projectId(),input.taskId(),input.runId(),a.actorId()));
        var replay=projects.read(text(run,"replay_manifest"));same(text(fixed,"replay_sha256"),hash(replay));
        var plan=replay.required("plan").required("content");same(text(fixed,"plan_sha256"),hash(plan));
        var question=one(array(plan.required("questions")).stream().filter(q->text(fixed,"question_key").equals(text(q,"question_key"))).toList());
        var section=one(array(plan.required("sections")).stream().filter(q->text(fixed,"section_id").equals(text(q,"section_id"))).toList());
        same(input.questionText(),text(question,"question"));same(text(fixed,"section_id"),text(question,"section_id"));
        String owner=section.required("owner_id").isNull()?null:text(section,"owner_id");
        if(owner!=null){WireValues.uuid(owner);if(one(db.query("SELECT COUNT(*) FROM tenant_member WHERE tenant_id=? AND user_id=? AND status='ACTIVE' AND valid_from<=UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6))",(r,n)->r.getLong(1),input.tenantId(),owner))!=1)deny();}
        var refs=new TreeMap<String,JsonNode>();for(var ref:replay.required("dependency_versions")){String id=text(ref,"version_id");if(refs.put(id,ref)!=null)bad();}
        String purpose=text(replay.required("execution_policy"),"business_purpose"),profile=text(plan,"profile_revision");
        var p=one(db.queryForList("SELECT f.*,v.content_sha256 AS registry_sha256,p.profile_revision AS current_profile FROM project_profile_revision f JOIN version_registry v ON v.tenant_id=f.tenant_id AND v.version_id=f.profile_revision AND v.owner_project_id=f.project_id JOIN project p ON p.tenant_id=f.tenant_id AND p.project_id=f.project_id WHERE f.tenant_id=? AND f.project_id=? AND f.profile_revision=? FOR UPDATE",input.tenantId(),input.projectId(),profile));
        same(profile,text(p,"current_profile"));same("1.0.0",text(p,"schema_version"));same(projects.manifestSha256(),text(p,"schema_manifest_sha256"));
        var attributes=projects.attributes(projects.read(text(p,"attributes")));var bases=projects.bases(projects.read(text(p,"authority_refs")));
        var actualBases=new TreeMap<String,PermissionProtocol.Basis>();for(var attribute:attributes)for(var b:attribute.authority_refs()){var prior=actualBases.put(b.version_id(),b);if(prior!=null&&!prior.equals(b))bad();}
        if(!List.copyOf(actualBases.values()).equals(bases))bad();
        var profileBody=json.object();profileBody.set("attributes",json.tree(attributes));profileBody.set("authority_refs",json.tree(bases));
        String profileHash=hash(profileBody);same(profileHash,text(p,"content_sha256"));same(profileHash,text(p,"registry_sha256"));
        var frozenProfile=replay.required("project_profile");same(profile,text(frozenProfile,"profile_revision"));same(profileHash,text(frozenProfile,"content_sha256"));
        if(!FormalTaskNodeAuthority.sameEvidence(profileBody.required("attributes"),frozenProfile.required("attributes"))||!FormalTaskNodeAuthority.sameEvidence(profileBody.required("authority_refs"),frozenProfile.required("authority_refs")))bad();
        var witness=json.object();witness.set("profile",dependency(a,input,replay,refs,new PermissionProtocol.Basis(profile,profileHash),purpose));
        var checks=new ArrayList<Check>();var ruleIds=ids(question.required("completion_rule_ids"));
        var valueMap=new HashMap<String,Object>();valueMap.put("userId",a.actorId());
        for(String id:ruleIds){var declared=refs.get(id);if(declared==null||!"RULE_VERSION".equals(text(declared,"entity_kind")))bad();
            valueMap.put("upstreamId",id);var row=one(rules.ruleDefinition(valueMap));same(input.tenantId(),row.tenantId());same(id,row.ruleVersion());same(text(declared,"content_sha256"),row.contentHash());
            if(!"APPROVED".equals(row.status()))throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
            var content=ruleSchema.rule(row);var parents=content.basis_manifest().stream().map(PermissionProtocol.Basis::version_id).sorted().toList();if(!parents.equals(rules.ruleParents(valueMap)))bad();
            var proof=dependency(a,input,replay,refs,new PermissionProtocol.Basis(id,row.contentHash()),purpose);
            checks.add(new Check(id,row.contentHash(),content,proof));witness.set(id,proof);
        }
        var state=json.object().put("owner_id",owner).put("profile_revision",profile).put("profile_sha256",profileHash);
        state.set("requirements",question.required("requirement_ids"));state.set("checks",json.tree(checks));state.set("authorizations",witness);
        context.requireUnchanged();return new State(owner,profile,profileHash,List.copyOf(attributes),ids(question.required("requirement_ids")),List.copyOf(checks),witness,hash(state));
    }
    private JsonNode dependency(EnterpriseAccess.Admission a,BoundInput input,JsonNode replay,Map<String,JsonNode> pins,
            PermissionProtocol.Basis root,String purpose)throws Exception {
        var graph=lineage.inspect(root.version_id(),limits.limits());var actual=graph.versions().get(root.version_id());if(actual==null||!root.content_sha256().equals(actual.contentSha256()))bad();
        var required=new HashMap<String,ArtifactRegistryProtocol.ReadRequirement>();var sourceProofs=new HashMap<String,SourceReadProtocol.VerifiedSource>();
        for(var version:graph.versions().values()){
            var pin=pins.get(version.id());if(pin==null||!version.entityKind().equals(text(pin,"entity_kind"))||!version.contentSha256().equals(text(pin,"content_sha256")))bad();
            if(version.projectId()!=null&&!input.projectId().equals(version.projectId()))deny();
            var source=sourceRows.source(version.id());if(source.size()>1)bad();if(!source.isEmpty()){
                var selection=one(input.fixedSources().stream().filter(s->s.sourceVersion().equals(version.id())).toList());
                if(!purpose.equals(selection.purpose()))deny();var policy=replay.required("execution_policy");
                sourceProofs.put(version.id(),sources.require(a,new SourceReadProtocol.ReadRequirement(selection.sourceVersion(),selection.rightsRevision(),input.projectId(),input.snapshotId(),selection.parseVersion(),null,SourceReadProtocol.Use.FORMAL,purpose,text(policy,"environment"),text(policy,"region"),null)));
            }
            if("ARTIFACT".equals(version.entityKind())){var binding=one(artifacts.binding(version.id()));var profile=profiles.profile(binding.schemaRef(),purpose,binding.resourceKind());
                required.put(version.id(),new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(input.tenantId(),binding.projectId(),binding.resourceKind(),binding.resourceId()),profile.action(),purpose,profile.schemaRef(),profile.schemaDocumentSha256(),profile.artifactSchemaManifest(),limits.limits()));}
        }
        var verified=versions.require(a,graph,purpose,new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.FORMAL,null),required);
        for(var version:verified.values())if(version.owner().sourceVersion()!=null){var source=sourceProofs.get(version.owner().sourceVersion());if(source==null||!source.source().sourceId().equals(version.owner().resourceId())||!graph.reachableFrom(version.version().id()).contains(source.source().sourceVersion()))bad();}
        for(var version:verified.values())if(version.artifactRegistration()!=null)for(var member:version.artifactRegistration().manifest().members()){
            var right=one(sourceRows.rights(member.rightsRevision()));var source=sourceProofs.get(right.sourceVersion());if(source==null||!source.rights().rightsRevision().equals(member.rightsRevision())||!graph.reachableFrom(member.upstreamVersionId()).contains(right.sourceVersion()))bad();}
        var witness=json.object();witness.set("versions",exactWitness(verified));witness.set("sources",exactWitness(sourceProofs));return witness;
    }
    private static List<JsonNode> array(JsonNode value){if(!value.isArray())bad();var result=new ArrayList<JsonNode>();value.forEach(result::add);return result;}
    private static List<String> ids(JsonNode value){var out=new ArrayList<String>();for(var id:array(value)){if(!id.isTextual())bad();WireValues.uuid(id.textValue());if(out.contains(id.textValue()))bad();out.add(id.textValue());}return List.copyOf(out);}
    private static JsonNode exactWitness(Object value){var tree=AgentPublicationWitness.tree(value);return exactIntegers(tree);}
    private static JsonNode exactIntegers(JsonNode value){
        if(value.isIntegralNumber())return com.fasterxml.jackson.databind.node.TextNode.valueOf(value.bigIntegerValue().toString());
        if(value.isObject()){var copy=((com.fasterxml.jackson.databind.node.ObjectNode)value).deepCopy();var fields=value.fields();while(fields.hasNext()){var f=fields.next();copy.set(f.getKey(),exactIntegers(f.getValue()));}return copy;}
        if(value.isArray()){var copy=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();for(var child:value)copy.add(exactIntegers(child));return copy;}return value.deepCopy();
    }
    private static String hash(Object value)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}
    private static String text(Map<String,Object> value,String key){if(!(value.get(key) instanceof String))bad();return (String)value.get(key);}
    private static String text(JsonNode value,String key){if(!value.required(key).isTextual())bad();return value.required(key).textValue();}
    private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}
    private static void same(Object expected,Object actual){if(!Objects.equals(expected,actual))bad();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
