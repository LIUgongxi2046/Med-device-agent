package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.ruoyi.medical.persistence.FormalEvaluationExecutionProtocol.*;

/** Actual SYSTEM_JOB producer. Every matrix obligation is persisted before dispatch. A queued
 * cell has no provider invocation, lease, result, score or professional-completion claim. */
public final class FormalEvaluationJobs {
    private final JdbcTemplate db;private final PermissionSchema json;private final RunTokenAuthoritySchema runtime;
    private final ResourceReadAuthority resources;private final VersionResourceMapper owners;private final SourceReadMapper sources;
    private final JsonMapper decoder=JsonMapper.builder().propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
            .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS,com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    public FormalEvaluationJobs(DataSource data,PermissionSchema json,RunTokenAuthoritySchema runtime,ResourceReadAuthority resources,VersionResourceMapper owners,SourceReadMapper sources){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.runtime=runtime;this.resources=resources;this.owners=owners;this.sources=sources;}
    public record Job(String tenantId,String projectId,String executionId,String runId,String principalId,String actorId,
            String originalKey,String admissionSha256,String canonicalAdmission,String classification,List<NodeRow> nodes) {
        public Job {nodes=List.copyOf(nodes);}
    }
    public record NodeRow(String stepId,String nodeSha256,Node node) {}
    /** All cells are written atomically in the caller's tenant transaction. No partial new matrix. */
    public List<Job> queued(EnterpriseAccess.Admission a,String key,FormalEvaluationExecutionInputs.Prepared input,FormalEvaluationBoundExecutions.Bound bound)throws Exception {
        FormalEvaluationExecutionAdmission.current(a);if(key==null||!key.matches("[!-~]{16,128}"))fail(ErrorCode.SCHEMA_INVALID);
        var root=input.requireCurrent(a);bound.requireConsumer(a,bound.executionId());String tenant=a.context().tenant_id(),actor=a.actorId(),execution=bound.executionId();
        one(db.queryForList("SELECT user_id FROM tenant_member WHERE tenant_id=? AND user_id=? FOR UPDATE",tenant,actor));
        var prior=db.queryForList("SELECT run_id,original_key FROM formal_evaluation_job WHERE tenant_id=? AND execution_id=? ORDER BY run_id LIMIT 10001",tenant,execution);
        if(!prior.isEmpty()) {if(prior.stream().anyMatch(r->!key.equals(r.get("original_key"))))fail(ErrorCode.IDEMPOTENCY_CONFLICT);var jobs=new ArrayList<Job>();for(var r:prior)jobs.add(byRun(a,text(r,"run_id")));exact(root,jobs);return List.copyOf(jobs);}
        if(!db.queryForList("SELECT run_id FROM formal_evaluation_cell WHERE tenant_id=? AND execution_id=? LIMIT 1",tenant,execution).isEmpty())fail(ErrorCode.CONTENT_MISMATCH);
        var permissions=permissions(a,root,bound);var groups=new LinkedHashMap<Cell,List<Node>>();for(var node:root.nodes())groups.computeIfAbsent(node.cell(),ignored->new ArrayList<>()).add(node);
        var jobs=new ArrayList<Job>();for(var e:groups.entrySet())jobs.add(create(a,key,execution,root,input,e.getKey(),e.getValue(),permissions,bound.context().binding(),bound.scope()));
        exact(root,jobs);input.requireCurrent(a);bound.requireUnchanged();if(!permissions.equals(permissions(a,input.requireCurrent(a),bound)))fail(ErrorCode.SCOPE_STALE);return List.copyOf(jobs);
    }
    private Job create(EnterpriseAccess.Admission a,String key,String execution,FormalEvaluationExecutionAdmission.Prepared root,FormalEvaluationExecutionInputs.Prepared input,Cell cell,List<Node> nodes,List<RunTokenAuthoritySchema.Permission> permissions,PermissionProtocol.Basis contextBinding,FormalEvaluationExecutionScopes.Prepared originalScope)throws Exception {
        String tenant=a.context().tenant_id(),actor=a.actorId(),project=root.projectId(),run=id(),principal=id(),scopeId=id(),rulesId=id(),audit=id(),event=id();
        LocalDateTime now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
        var scope=json.object().put("schema_version","1.0.0").put("tenant_id",tenant).put("project_id",project).put("initiator_id",actor).put("run_id",run).put("execution_kind","SYSTEM_JOB");
        var scopes=scope.putArray("resources");permissions.stream().map(RunTokenAuthoritySchema.Permission::scope).distinct().forEach(s->scopes.add(json.tree(s)));
        runtime.scopeManifest(json.canonical(scope),tenant,project,actor,run,"SYSTEM_JOB");
        var principalDoc=json.object().put("schema_version","1.0.0").put("tenant_id",tenant).put("project_id",project).put("initiator_id",actor).put("run_id",run).put("principal_id",principal).put("scope_sha256",hash(scope));
        principalDoc.putArray("services").add(json.object().put("service_uri",root.policy().worker().serviceUri()).put("audience",root.policy().worker().audience()));principalDoc.set("permissions",json.tree(permissions));
        runtime.principalManifest(json.canonical(principalDoc),tenant,project,actor,run,principal,scope);
        // The already physically verified 079 artifact is the cell input. It is not a TASK manifest,
        // a prompt, or permission to send expected answers to the provider.
        var original=input.document(a,cell.sampleId());same(cell.input().content_sha256(),hash(original));String canonicalInput=json.canonical(original);
        var rows=new ArrayList<NodeRow>();for(var node:nodes)rows.add(new NodeRow(id(),hash(json.tree(node)),node));
        var admitted=json.object().put("schema_version","1.0.0").put("execution_kind","SYSTEM_JOB").put("job_type","EVALUATION_VERIFICATION")
                .put("tenant_id",tenant).put("project_id",project).put("execution_id",execution).put("run_id",run).put("principal_id",principal).put("actor_id",actor)
                .put("original_key",key).put("scope_manifest_id",scopeId).put("rule_manifest_id",rulesId);
        admitted.set("cell",json.tree(cell));admitted.set("nodes",json.tree(rows));admitted.set("scope_manifest",scope);admitted.set("principal_manifest",principalDoc);admitted.set("input_receipt",input.receipt(a,cell.sampleId()));
        admitted.put("input_manifest_sha256",hash(original));admitted.put("root_request_sha256",hash(json.tree(root.request())));admitted.set("context_binding",json.tree(contextBinding));admitted.set("root_request",json.tree(root.request()));admitted.set("source_snapshot",json.tree(originalScope.snapshot()));admitted.put("profile_revision",originalScope.profileRevision());
        String admissionHash=hash(admitted);var job=new Job(tenant,project,execution,run,principal,actor,key,admissionHash,json.canonical(admitted),root.classification(),rows);
        var registry=metadata(job,now,runtime.manifestSha256());registry.put("run_id",run);registry.put("project_id",project);registry.put("execution_kind","SYSTEM_JOB");registry.put("scope_manifest",json.canonical(scope));insert("execution_registry",registry);
        var p=metadata(job,now,runtime.manifestSha256());p.put("project_id",project);p.put("principal_id",principal);p.put("run_id",run);p.put("initiator_id",actor);p.put("capability_manifest",json.canonical(principalDoc));p.put("status","ACTIVE");p.put("revision",1);p.put("updated_at",now);insert("run_principal",p);
        version(job,scopeId,hash(scope));version(job,rulesId,hash(json.tree(root.definition().request().rule_revision())));
        var j=metadata(job,now,runtime.manifestSha256());j.put("project_id",project);j.put("run_id",run);j.put("execution_kind","SYSTEM_JOB");j.put("job_type","EVALUATION_VERIFICATION");j.put("initiator_id",actor);j.put("input_manifest",canonicalInput);j.put("config_revision",cell.candidate().version_id());j.put("budget_revision",root.request().enterpriseBudget().version_id());
        j.put("parent_budget_id",one(db.query("SELECT budget_id FROM budget_revision WHERE tenant_id=? AND budget_revision=? LIMIT 2",(r,n)->r.getString(1),tenant,root.request().parentBudget().version_id())));
        j.put("target_kind","EVALUATION");j.put("target_id",cell.evaluationId());j.put("target_revision",cell.evaluationId());j.put("input_sha256",hash(original));j.put("scope_manifest_id",scopeId);j.put("rule_manifest_id",rulesId);j.put("status","QUEUED");j.put("workflow_id",null);j.put("revision",1);j.put("updated_at",now);insert("system_job_run",j);
        var c=metadata(job,now,json.manifestSha256());c.put("project_id",project);c.put("execution_id",execution);c.put("sample_id",cell.sampleId());c.put("repeat_index",cell.repeatIndex());c.put("source_family",cell.sourceFamily());c.put("split",cell.split());c.put("run_id",run);c.put("principal_id",principal);c.put("input_version",cell.input().version_id());c.put("input_sha256",cell.input().content_sha256());c.put("expected_version",cell.expected().version_id());c.put("expected_sha256",cell.expected().content_sha256());c.put("input_manifest_id",cell.input().version_id());c.put("input_manifest_sha256",hash(original));c.put("canonical_cell",json.canonical(json.tree(cell)));c.put("cell_sha256",hash(json.tree(cell)));c.put("canonical_scope",json.canonical(scope));c.put("canonical_principal",json.canonical(principalDoc));c.put("canonical_input",canonicalInput);insert("formal_evaluation_cell",c);
        for(var n:rows){var step=metadata(job,now,runtime.manifestSha256());step.put("project_id",project);step.put("run_id",run);step.put("step_id",n.stepId());step.put("role_version",n.node().role().version_id());step.put("input_sha256",hash(original));step.put("status","READY");step.put("lease_generation",0);step.put("revision",1);step.put("updated_at",now);insert("run_step",step);
            var obligation=metadata(job,now,json.manifestSha256());obligation.put("project_id",project);obligation.put("run_id",run);obligation.put("step_id",n.stepId());obligation.put("node_kind",n.node().nodeKind());obligation.put("canonical_node",json.canonical(json.tree(n.node())));obligation.put("node_sha256",n.nodeSha256());insert("formal_evaluation_node",obligation);}
        var evidence=json.object().put("schema_version","1.0.0").put("kind","FORMAL_EVALUATION_CELL_QUEUED").put("execution_id",execution).put("run_id",run).put("admission_sha256",admissionHash).put("expected_nodes",nodes.size());
        var au=metadata(job,now,json.manifestSha256());au.put("project_id",project);au.put("audit_id",audit);au.put("subject_id",actor);au.put("action","evaluation.cell.queued");au.put("resource_ref",json.canonical(evidence));au.put("result","SUCCESS");au.put("request_id",run);au.put("trace_id",execution);insert("audit_event",au);
        insert("outbox_event",Map.of("tenant_id",tenant,"event_id",event,"aggregate_id",run,"aggregate_revision",1,"event_type","evaluation.cell.queued","payload",json.canonical(evidence),"occurred_at",now));
        var side=metadata(job,now,json.manifestSha256());side.put("project_id",project);side.put("execution_id",execution);side.put("run_id",run);side.put("original_key",key);side.put("canonical_admission",job.canonicalAdmission());side.put("admission_sha256",admissionHash);side.put("audit_id",audit);side.put("event_id",event);insert("formal_evaluation_job",side);return byRun(a,run);
    }
    public Job byRun(EnterpriseAccess.Admission a,String run)throws Exception {var job=original(a,run);same(a.actorId(),job.actorId());return job;}
    /** Original identity facts for independently authorized scoring; never grants worker authority. */
    public Job original(EnterpriseAccess.Admission a,String run)throws Exception {
        FormalEvaluationExecutionAdmission.current(a);WireValues.uuid(run);String tenant=a.context().tenant_id();var row=one(db.queryForList("SELECT * FROM formal_evaluation_job WHERE tenant_id=? AND run_id=? LIMIT 2",tenant,run));
        String originalActor=text(row,"created_by");same("1.0.0",text(row,"schema_version"));same(json.manifestSha256(),text(row,"schema_manifest_sha256"));var admitted=decoder.readTree(text(row,"canonical_admission"));same(hash(admitted),text(row,"admission_sha256"));same(json.canonical(admitted),text(row,"canonical_admission"));
        same("SYSTEM_JOB",text(admitted,"execution_kind"));same("EVALUATION_VERIFICATION",text(admitted,"job_type"));
        for(String f:List.of("tenant_id","project_id","execution_id","run_id","original_key"))same(text(row,f),text(admitted,f));same(originalActor,text(admitted,"actor_id"));
        var originalRoot=one(db.queryForList("SELECT * FROM formal_evaluation_execution WHERE tenant_id=? AND execution_id=? LIMIT 2",tenant,text(row,"execution_id")));
        same(text(row,"project_id"),text(originalRoot,"project_id"));same(originalActor,text(originalRoot,"created_by"));same(text(row,"classification"),text(originalRoot,"classification"));
        same(hash(admitted.required("root_request")),text(admitted,"root_request_sha256"));same(text(originalRoot,"request_sha256"),text(admitted,"root_request_sha256"));same(json.canonical(admitted.required("root_request")),text(originalRoot,"canonical_request"));
        var rootAdmission=decoder.readTree(text(originalRoot,"canonical_admission"));same(hash(rootAdmission),text(originalRoot,"admission_sha256"));same(rootAdmission.required("source_snapshot"),admitted.required("source_snapshot"));same(rootAdmission.required("profile_revision"),admitted.required("profile_revision"));
        var contextLink=one(db.queryForList("SELECT context_version,context_sha256 FROM formal_evaluation_execution_context WHERE tenant_id=? AND execution_id=? LIMIT 2",tenant,text(row,"execution_id")));
        same(text(contextLink,"context_version"),text(admitted.required("context_binding"),"version_id"));same(text(contextLink,"context_sha256"),text(admitted.required("context_binding"),"content_sha256"));
        var cell=decoder.treeToValue(admitted.required("cell"),Cell.class);
        same(cell.evaluationId(),text(originalRoot,"evaluation_id"));same(cell.definitionSha256(),text(originalRoot,"definition_sha256"));same(cell.candidate().version_id(),text(originalRoot,"candidate_version"));same(cell.candidate().content_sha256(),text(originalRoot,"candidate_sha256"));same(cell.policy().version_id(),text(originalRoot,"policy_version"));same(cell.policy().content_sha256(),text(originalRoot,"policy_sha256"));same(cell.runtimeManifestSha256(),text(originalRoot,"runtime_manifest_sha256"));
        var expectedNodes=new ArrayList<Node>();for(var n:rootAdmission.required("nodes")){var candidate=decoder.treeToValue(n,Node.class);if(candidate.cell().equals(cell))expectedNodes.add(candidate);}
        var nodes=new ArrayList<NodeRow>();for(var n:admitted.required("nodes"))nodes.add(decoder.treeToValue(n,NodeRow.class));
        if(nodes.isEmpty()||nodes.size()>10||nodes.size()!=expectedNodes.size()||!new HashSet<>(nodes.stream().map(NodeRow::node).toList()).equals(new HashSet<>(expectedNodes)))bad();var job=new Job(tenant,text(row,"project_id"),text(row,"execution_id"),run,text(admitted,"principal_id"),originalActor,text(row,"original_key"),text(row,"admission_sha256"),text(row,"canonical_admission"),text(row,"classification"),nodes);
        runtime.scopeManifest(json.canonical(admitted.required("scope_manifest")),tenant,job.projectId(),originalActor,run,"SYSTEM_JOB");runtime.principalManifest(json.canonical(admitted.required("principal_manifest")),tenant,job.projectId(),originalActor,run,job.principalId(),admitted.required("scope_manifest"));
        var c=one(db.queryForList("SELECT * FROM formal_evaluation_cell WHERE tenant_id=? AND run_id=? LIMIT 2",tenant,run));same(json.canonical(json.tree(cell)),text(c,"canonical_cell"));same(hash(json.tree(cell)),text(c,"cell_sha256"));same(cell.input().version_id(),text(c,"input_manifest_id"));same(cell.input().content_sha256(),text(c,"input_manifest_sha256"));same(job.executionId(),text(c,"execution_id"));same(job.principalId(),text(c,"principal_id"));same(job.projectId(),text(c,"project_id"));same(originalActor,text(c,"created_by"));
        for(var pair:Map.of("canonical_scope","scope_manifest","canonical_principal","principal_manifest").entrySet())same(json.canonical(admitted.required(pair.getValue())),text(c,pair.getKey()));
        same(cell.input().content_sha256(),WireValues.sha256(text(c,"canonical_input").getBytes(StandardCharsets.UTF_8)));
        var actual=db.queryForList("SELECT * FROM formal_evaluation_node WHERE tenant_id=? AND run_id=? LIMIT 11",tenant,run);if(actual.size()!=nodes.size())bad();var seen=new HashSet<String>();for(var node:nodes){if(!seen.add(node.stepId()))bad();same(cell,node.node().cell());same(hash(json.tree(node.node())),node.nodeSha256());var n=one(actual.stream().filter(r->node.stepId().equals(r.get("step_id"))).toList());same(node.nodeSha256(),text(n,"node_sha256"));same(json.canonical(json.tree(node.node())),text(n,"canonical_node"));same(node.node().nodeKind(),text(n,"node_kind"));same(job.projectId(),text(n,"project_id"));same(originalActor,text(n,"created_by"));same("1.0.0",text(n,"schema_version"));same(json.manifestSha256(),text(n,"schema_manifest_sha256"));
            var step=one(db.queryForList("SELECT project_id,created_by,role_version FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2",tenant,run,node.stepId()));same(job.projectId(),text(step,"project_id"));same(originalActor,text(step,"created_by"));same(node.node().role().version_id(),text(step,"role_version"));}
        var registry=one(db.queryForList("SELECT * FROM execution_registry WHERE tenant_id=? AND run_id=? LIMIT 2",tenant,run));same("SYSTEM_JOB",text(registry,"execution_kind"));same(job.projectId(),text(registry,"project_id"));same(originalActor,text(registry,"created_by"));same(text(c,"canonical_scope"),json.canonical(decoder.readTree(text(registry,"scope_manifest"))));
        var principal=one(db.queryForList("SELECT * FROM run_principal WHERE tenant_id=? AND run_id=? AND principal_id=? LIMIT 2",tenant,run,job.principalId()));same(text(c,"canonical_principal"),json.canonical(decoder.readTree(text(principal,"capability_manifest"))));same(job.projectId(),text(principal,"project_id"));same(originalActor,text(principal,"initiator_id"));same(originalActor,text(principal,"created_by"));
        var system=one(db.queryForList("SELECT * FROM system_job_run WHERE tenant_id=? AND run_id=? LIMIT 2",tenant,run));same("SYSTEM_JOB",text(system,"execution_kind"));same(job.projectId(),text(system,"project_id"));same(originalActor,text(system,"initiator_id"));same(originalActor,text(system,"created_by"));same(text(admitted,"scope_manifest_id"),text(system,"scope_manifest_id"));same(text(admitted,"rule_manifest_id"),text(system,"rule_manifest_id"));same("EVALUATION_VERIFICATION",text(system,"job_type"));same("EVALUATION",text(system,"target_kind"));same(cell.evaluationId(),text(system,"target_id"));same(cell.evaluationId(),text(system,"target_revision"));same(cell.candidate().version_id(),text(system,"config_revision"));same(cell.input().content_sha256(),text(system,"input_sha256"));same(text(c,"canonical_input"),json.canonical(decoder.readTree(text(system,"input_manifest"))));
        var evidence=json.object().put("schema_version","1.0.0").put("kind","FORMAL_EVALUATION_CELL_QUEUED").put("execution_id",job.executionId()).put("run_id",run).put("admission_sha256",job.admissionSha256()).put("expected_nodes",nodes.size());
        same(json.canonical(evidence),one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND action='evaluation.cell.queued' AND result='SUCCESS' AND request_id=? AND trace_id=? LIMIT 2",(r,n)->r.getString(1),tenant,job.projectId(),text(row,"audit_id"),originalActor,run,job.executionId())));
        same(json.canonical(evidence),one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type='evaluation.cell.queued' AND aggregate_revision=1 LIMIT 2",(r,n)->r.getString(1),tenant,text(row,"event_id"),run)));return job;
    }
    public Cell cell(Job job)throws Exception{return decoder.treeToValue(decoder.readTree(job.canonicalAdmission()).required("cell"),Cell.class);}
    private List<RunTokenAuthoritySchema.Permission> permissions(EnterpriseAccess.Admission a,FormalEvaluationExecutionAdmission.Prepared root,FormalEvaluationBoundExecutions.Bound bound)throws Exception {
        var result=new LinkedHashSet<RunTokenAuthoritySchema.Permission>();var evaluation=new PermissionProtocol.Scope(a.context().tenant_id(),root.projectId(),"EVALUATION",root.request().evaluationId());resources.require(a,evaluation,"evaluation.run",EvaluationReferences.PURPOSE);result.add(new RunTokenAuthoritySchema.Permission(evaluation,"evaluation.run",EvaluationReferences.PURPOSE));
        for(var node:root.policy().bindings())for(var binding:node.actions()){
            if(Set.of("plan.confirm","agent.publish","evaluation.accept","evaluation.score").contains(binding.action()))fail(ErrorCode.ACCESS_DENIED);
            var targets=new ArrayList<PermissionProtocol.Scope>();switch(binding.targetSelector()){
                case "PROJECT"->targets.add(new PermissionProtocol.Scope(a.context().tenant_id(),root.projectId(),"PROJECT",root.projectId()));
                case "SELECTED_SOURCE"->{for(var s:root.policy().sourceSelection().sources()){var source=one(sources.source(s.version_id()));same(s.content_sha256(),source.registeredSha256());targets.add(new PermissionProtocol.Scope(a.context().tenant_id(),source.ownerProjectId(),"SOURCE",source.sourceId()));}}
                case "FIXED_VERSION","CURRENT_EXECUTION_POLICY"->{var fixed="CURRENT_EXECUTION_POLICY".equals(binding.targetSelector())?root.request().executionPolicy():binding.fixedVersion();if(fixed==null)bad();var owner=one(owners.ownersAcrossDomains(List.of(fixed.version_id()),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList())).owner();same(a.context().tenant_id(),owner.tenantId());same(fixed.content_sha256(),owner.contentSha256());targets.add(new PermissionProtocol.Scope(owner.tenantId(),owner.resourceProjectId(),owner.resourceKind(),owner.resourceId()));}
                case "RUN_REGISTERED_ARTIFACT"->{continue;}
                default->throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            }
            for(var target:targets){same(binding.resourceKind(),target.resource_kind());resources.require(a,target,binding.action(),binding.purpose());result.add(new RunTokenAuthoritySchema.Permission(target,binding.action(),binding.purpose()));}
        }
        // Read delegation is derived solely from the immutable candidate/context closure. It is
        // separate from tool authority and cannot add write/send actions or a grant the actor lacks.
        var pins=new TreeMap<String,String>();collectReadPins(json.tree(root.request()),pins);collectReadPins(json.tree(root.nodes()),pins);collectReadPins(json.tree(bound.context().definition()),pins);for(var candidate:root.definition().candidate_snapshots())if(candidate.version_id().equals(root.request().candidate().version_id()))collectReadPins(json.tree(candidate),pins);
        pins.put(bound.context().binding().version_id(),bound.context().binding().content_sha256());
        var queue=new ArrayDeque<String>(pins.keySet());var seen=new HashSet<String>();
        while(!queue.isEmpty()){
            String id=queue.removeFirst();if(!seen.add(id))continue;if(seen.size()>2048)fail(ErrorCode.CONTEXT_LIMIT);
            var version=one(db.queryForList("SELECT content_sha256,owner_project_id FROM version_registry WHERE tenant_id=? AND version_id=? LIMIT 2",a.context().tenant_id(),id));
            if(pins.containsKey(id))same(pins.get(id),version.get("content_sha256"));if(version.get("owner_project_id")!=null&&!root.projectId().equals(version.get("owner_project_id")))fail(ErrorCode.ACCESS_DENIED);
            for(var parent:db.query("SELECT upstream_version_id FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? LIMIT 2049",(r,n)->r.getString(1),a.context().tenant_id(),id))queue.addLast(parent);
            var scopes=new LinkedHashSet<PermissionProtocol.Scope>();
            for(var owned:owners.ownersAcrossDomains(List.of(id),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList())){
                var owner=owned.owner();if(owner.conversationId()!=null||!a.context().tenant_id().equals(owner.tenantId())||owner.resourceProjectId()!=null&&!root.projectId().equals(owner.resourceProjectId()))fail(ErrorCode.ACCESS_DENIED);
                scopes.add(new PermissionProtocol.Scope(owner.tenantId(),owner.resourceProjectId(),owner.resourceKind(),owner.resourceId()));
            }
            for(var artifact:db.queryForList("SELECT project_id,resource_kind,resource_id FROM artifact_content_binding WHERE tenant_id=? AND artifact_id=? LIMIT 2",a.context().tenant_id(),id))scopes.add(new PermissionProtocol.Scope(a.context().tenant_id(),(String)artifact.get("project_id"),text(artifact,"resource_kind"),text(artifact,"resource_id")));
            for(var scope:scopes){String action=scope.resource_kind().toLowerCase(java.util.Locale.ROOT)+".read";
                for(String purpose:List.of("AGENT_PUBLICATION","EVALUATION_RESULTS","RUN_CREATION"))if(resources.find(a,scope,action,purpose).isPresent())result.add(new RunTokenAuthoritySchema.Permission(scope,action,purpose));
            }
        }
        var projectRead=new PermissionProtocol.Scope(a.context().tenant_id(),root.projectId(),"PROJECT",root.projectId());
        if(resources.find(a,projectRead,"project.read","AGENT_PUBLICATION").isPresent())result.add(new RunTokenAuthoritySchema.Permission(projectRead,"project.read","AGENT_PUBLICATION"));
        bound.requireUnchanged();root.requireUnchanged();return List.copyOf(result);
    }
    private static void collectReadPins(JsonNode value,Map<String,String> pins){
        if(value.isObject()){
            String id=value.hasNonNull("version_id")?value.path("version_id").asText():value.hasNonNull("artifact_id")?value.path("artifact_id").asText():null;
            String hash=value.hasNonNull("content_sha256")?value.path("content_sha256").asText():value.hasNonNull("sha256")?value.path("sha256").asText():null;
            if(id!=null&&hash!=null){WireValues.uuid(id);WireValues.hash(hash);var old=pins.putIfAbsent(id,hash);if(old!=null&&!old.equals(hash))bad();}
            value.elements().forEachRemaining(child->collectReadPins(child,pins));
        }else if(value.isArray())value.elements().forEachRemaining(child->collectReadPins(child,pins));
    }
    private void exact(FormalEvaluationExecutionAdmission.Prepared root,List<Job> jobs){var actual=new ArrayList<Node>();for(var job:jobs)for(var node:job.nodes())actual.add(node.node());if(actual.size()!=root.nodes().size()||!new HashSet<>(actual).equals(new HashSet<>(root.nodes())))bad();}
    private void version(Job j,String id,String hash){insert("version_registry",Map.of("tenant_id",j.tenantId(),"version_id",id,"owner_project_id",j.projectId(),"entity_kind","FORMAL_EVALUATION_MANIFEST","content_sha256",hash));}
    private Map<String,Object> metadata(Job j,LocalDateTime now,String manifest){var m=new LinkedHashMap<String,Object>();m.put("tenant_id",j.tenantId());m.put("schema_version","1.0.0");m.put("schema_manifest_sha256",manifest);m.put("created_by",j.actorId());m.put("created_at",now);m.put("classification",j.classification());return m;}
    private void insert(String table,Map<String,Object> row){var keys=new ArrayList<>(row.keySet());if(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(row::get).toArray())!=1)bad();}
    private String hash(JsonNode n)throws Exception{return WireValues.sha256(json.canonical(n).getBytes(StandardCharsets.UTF_8));}
    private static String id(){return UUID.randomUUID().toString();}private static String text(JsonNode n,String k){if(!n.required(k).isTextual())bad();return n.required(k).textValue();}private static String text(Map<String,Object> r,String k){if(!(r.get(k) instanceof String))bad();return (String)r.get(k);}private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void bad(){fail(ErrorCode.CONTENT_MISMATCH);}private static void fail(ErrorCode c){throw new TenantAccessException(c);}
}
