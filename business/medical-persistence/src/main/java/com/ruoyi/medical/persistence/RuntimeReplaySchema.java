package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ArtifactBindings;
import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.springframework.stereotype.Component;

/** Fixed bytes/structure integrity only. This type does not grant current access or authorize execution. */
@Component
public final class RuntimeReplaySchema {
    public static final String REF="urn:medical:replay-manifest:1.0.0";
    private final SchemaCatalogue catalogue;
    private final PermissionSchema json;
    private final JsonMapper reader=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    @org.springframework.beans.factory.annotation.Autowired
    public RuntimeReplaySchema(PermissionSchema json)throws IOException,java.security.NoSuchAlgorithmException{
        this(json,SchemaCatalogue.fromClasspath());
    }
    RuntimeReplaySchema(PermissionSchema json,SchemaCatalogue catalogue){this.json=json;this.catalogue=catalogue;}

    public VerifiedInputs read(byte[] bytes,String expectedRawSha256,String expectedSchemaSha256)throws IOException{
        WireValues.hash(expectedRawSha256);WireValues.hash(expectedSchemaSha256);
        if(!DigestUtils.sha256Hex(bytes).equals(expectedRawSha256))mismatch();
        var bound=catalogue.bindPublished(REF,expectedSchemaSha256,bytes);
        var value=reader.readTree(bound.canonicalJson());
        var plan=value.required("plan");var snapshot=value.required("scope_snapshot");var release=value.required("agent_release");
        for(String field:List.of("tenant_id","project_id","task_id","task_revision","plan_revision","snapshot_id"))same(value,field,plan,field);
        for(String field:List.of("tenant_id","project_id","snapshot_id"))same(value,field,snapshot,field);
        same(value,"tenant_id",release,"tenant_id");same(value,"agent_release_id",release,"release_id");
        same(plan,"profile_revision",snapshot,"profile_revision");
        same(plan,"profile_revision",value.required("project_profile"),"profile_revision");
        same(plan,"template_version",value.required("template"),"template_version");
        same(value,"budget_revision",value.required("budget_approval"),"budget_revision");
        var input=value.required("task_input");same(value,"project_id",input,"project_id");same(value,"agent_release_id",input,"agent_release_id");
        same(plan,"profile_revision",input,"profile_revision");same(plan,"template_version",input,"template_version");
        if(!input.required("scope_selection").equals(snapshot.required("scope_selection"))||!"CONFIRMED".equals(plan.required("status").textValue()))mismatch();
        hash(plan.required("content"),plan.required("content_sha256"));hash(snapshot,value.required("scope_snapshot_sha256"));
        hash(release,value.required("agent_release_sha256"));hash(value.required("template").required("content"),value.required("template").required("content_sha256"));
        hash(value.required("budget_approval").required("approval"),value.required("budget_approval").required("content_sha256"));
        var profile=value.required("project_profile");var profileContent=json.object();profileContent.set("attributes",profile.required("attributes"));profileContent.set("authority_refs",profile.required("authority_refs"));hash(profileContent,profile.required("content_sha256"));
        var execution=value.required("execution_policy");hash(execution,value.required("execution_policy_version").required("content_sha256"));
        if(!execution.required("source_selection").required("version").equals(release.required("release_scope").required("source_scope_policy")))mismatch();
        var expectedRetrieval=new HashSet<JsonNode>();release.required("retrieval_profiles").forEach(expectedRetrieval::add);
        var actualRetrieval=new HashSet<JsonNode>();execution.required("retrieval_profiles").forEach(v->actualRetrieval.add(v.required("version")));
        if(!expectedRetrieval.equals(actualRetrieval))mismatch();
        for(String field:List.of("locale","timezone","training_allowed"))same(value,field,execution,field);
        var dependencies=new HashMap<String,JsonNode>();var graph=new DefaultDirectedGraph<String,DefaultEdge>(DefaultEdge.class);
        for(var dependency:value.required("dependency_versions")){
            String id=dependency.required("version_id").textValue();if(dependencies.put(id,dependency)!=null)mismatch();graph.addVertex(id);
        }
        for(var edge:value.required("dependency_edges")){
            String child=edge.required("consumer_version_id").textValue(),parent=edge.required("upstream_version_id").textValue();
            if(!graph.containsVertex(child)||!graph.containsVertex(parent)||child.equals(parent))mismatch();
            if(!graph.containsEdge(child,parent))graph.addEdge(child,parent);
        }
        if(new CycleDetector<>(graph).detectCycles())mismatch();
        for(String field:List.of("task_revision","plan_revision","snapshot_id","budget_revision","agent_release_id"))requireVersion(dependencies,value.required(field).textValue(),null);
        requireVersion(dependencies,value.required("plan_revision").textValue(),plan.required("content_sha256").textValue());
        requireVersion(dependencies,value.required("snapshot_id").textValue(),value.required("scope_snapshot_sha256").textValue());
        requireVersion(dependencies,value.required("agent_release_id").textValue(),value.required("agent_release_sha256").textValue());
        requireVersion(dependencies,value.required("budget_revision").textValue(),value.required("budget_approval").required("content_sha256").textValue());
        requireVersion(dependencies,snapshot.required("profile_revision").textValue(),profile.required("content_sha256").textValue());
        requireVersion(dependencies,plan.required("template_version").textValue(),value.required("template").required("content_sha256").textValue());
        requireVersion(dependencies,value.required("execution_policy_version").required("version_id").textValue(),value.required("execution_policy_version").required("content_sha256").textValue());
        requireVersion(dependencies,value.required("retention").required("policy_revision").textValue(),value.required("retention").required("content_sha256").textValue());
        for(var source:snapshot.required("members")){
            requireVersion(dependencies,source.required("source_version").textValue(),source.required("original_sha256").textValue());
            for(String field:List.of("parse_version","index_version","approval_revision","binding_revision","rights_revision"))requireVersion(dependencies,source.required(field).textValue(),null);
        }
        var expectedModels=new HashMap<String,String>();for(var model:release.required("model_config_revisions"))expectedModels.put(model.required("version_id").textValue(),model.required("content_sha256").textValue());
        var actualModels=new HashMap<String,String>();boolean unavailable=false;
        for(var model:value.required("model_bindings")){
            String id=model.required("model_config_revision").textValue(),hash=model.required("config_content_sha256").textValue();
            if(actualModels.put(id,hash)!=null)mismatch();requireVersion(dependencies,id,hash);
            unavailable|="UNKNOWN".equals(model.required("model_version").textValue());
        }
        if(!expectedModels.equals(actualModels)||!(unavailable?"ORIGINAL_VERSION_UNAVAILABLE":"EXACT_VERSION_AVAILABLE").equals(value.required("provider_reexecution").textValue()))mismatch();
        var auth=value.required("authorizations");String witnesses=auth.required("canonical_witnesses").textValue();
        if(!json.canonical(reader.readTree(witnesses)).equals(witnesses)||!DigestUtils.sha256Hex(witnesses).equals(auth.required("witnesses_sha256").textValue()))mismatch();
        var principal=value.required("principal_manifest");var scope=value.required("scope_manifest");
        for(String field:List.of("tenant_id","project_id","initiator_id","run_id")){same(value,field,scope,field);same(value,field,principal,field);}
        same(value,"principal_id",principal,"principal_id");hash(scope,principal.required("scope_sha256"));
        var scopeResources=new HashSet<JsonNode>();scope.required("resources").forEach(scopeResources::add);
        for(var permission:principal.required("permissions"))if(!scopeResources.contains(permission.required("scope")))mismatch();
        var expectedService=json.object();expectedService.set("service_uri",execution.required("worker").required("service_uri"));
        expectedService.set("audience",execution.required("worker").required("audience"));
        if(principal.required("services").size()!=1||!principal.required("services").get(0).equals(expectedService))mismatch();
        var planContent=TypedSchemaBindings.bind(catalogue,ArtifactBindings.PLAN_CANDIDATE,plan.required("content")).read();
        var definition=reader.treeToValue(execution,AgentExecutionPolicyProtocol.Definition.class);
        return new VerifiedInputs(value,expectedRawSha256,bound.schemaSha256(),planContent,definition);
    }

    public static final class VerifiedInputs {
        private final JsonNode content;
        private final String rawSha256,schemaSha256;
        private final ArtifactProtocol.PlanCandidate plan;
        private final AgentExecutionPolicyProtocol.Definition policy;
        private VerifiedInputs(JsonNode content,String rawSha256,String schemaSha256,ArtifactProtocol.PlanCandidate plan,AgentExecutionPolicyProtocol.Definition policy){this.content=content.deepCopy();this.rawSha256=rawSha256;this.schemaSha256=schemaSha256;this.plan=plan;this.policy=policy;}
        public JsonNode content(){return content.deepCopy();}
        public String rawSha256(){return rawSha256;}
        public String schemaSha256(){return schemaSha256;}
        public String runId(){return content.required("run_id").textValue();}
        public String taskId(){return content.required("task_id").textValue();}
        public String taskRevision(){return content.required("task_revision").textValue();}
        public String planRevision(){return content.required("plan_revision").textValue();}
        public String snapshotId(){return content.required("snapshot_id").textValue();}
        public ArtifactProtocol.PlanCandidate plan(){return plan;}
        public AgentExecutionPolicyProtocol.Definition executionPolicy(){return policy;}
    }
    private void hash(JsonNode content,JsonNode expected)throws IOException{if(!DigestUtils.sha256Hex(json.canonical(content)).equals(expected.textValue()))mismatch();}
    private static void same(JsonNode left,String field,JsonNode right,String target){if(!left.required(field).equals(right.required(target)))mismatch();}
    private static void requireVersion(Map<String,JsonNode> versions,String id,String hash){var row=versions.get(id);if(row==null||hash!=null&&!hash.equals(row.required("content_sha256").textValue()))mismatch();}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
