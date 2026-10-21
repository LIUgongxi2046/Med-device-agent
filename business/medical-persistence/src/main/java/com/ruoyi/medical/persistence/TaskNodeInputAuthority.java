package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.TaskNodeInputLedger.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.VersionResourceMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Private producer and consumer capabilities for real, immutable, physically registered N02 input.
 * Public JSON and UUIDs are data only. No caller can construct Prepared or Verified. */
public final class TaskNodeInputAuthority {
    private final JdbcTemplate db;private final TaskNodeInputSchema schema;private final PermissionSchema json;
    private final TaskNodeInputLedger ledger;private final TaskNodeInputRegistration registrations;
    private final TaskRunGraphService graphs;private final TaskNodeProducerAuthority producers;
    private final TaskRunReplayContentAuthority contents;private final SourceReadAuthority sources;
    private final VersionResourceMapper owners;private final RunTokenAuthoritySchema tokens;
    public TaskNodeInputAuthority(DataSource data,TaskNodeInputSchema schema,PermissionSchema json,TaskNodeInputLedger ledger,
            TaskNodeInputRegistration registrations,TaskRunGraphService graphs,TaskNodeProducerAuthority producers,
            TaskRunReplayContentAuthority contents,SourceReadAuthority sources,VersionResourceMapper owners,RunTokenAuthoritySchema tokens){
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.json=json;this.ledger=ledger;this.registrations=registrations;
        this.graphs=graphs;this.producers=producers;this.contents=contents;this.sources=sources;this.owners=owners;this.tokens=tokens;
    }
    public Prepared prepare(TaskNodeProductionBasis bootstrap,TaskRunReplayRegistration.Registered replay,
            TaskRunGraphService.Verified graph,TaskNodeProducerAuthority.Verified producer,String questionId)throws Exception {
        current(bootstrap.admission());bootstrap.requireUnchanged();graph.requireUnchanged();producer.requireUnchanged();
        if(graph.admission()!=bootstrap.admission())denied();
        graph.requirePredecessors(questionId);
        var input=compile(bootstrap,replay,graph,questionId);var content=contents.require(bootstrap);
        if(content.roots().size()>512||content.members().size()>512)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        producer.requireProduction(bootstrap.admission(),bootstrap.taskId(),bootstrap.runId(),bootstrap.principalId(),questionId,TaskNodeInputRegistration.KIND,Integer.toString(schema.bytes(input).length));
        requireReplay(bootstrap.admission(),input,replay.receipt());
        return new Prepared(bootstrap,replay,graph,producer,input,content);
    }
    public final class Prepared {
        private final TaskNodeProductionBasis bootstrap;private final TaskRunReplayRegistration.Registered replay;
        private final TaskRunGraphService.Verified graph;private final TaskNodeProducerAuthority.Verified producer;
        private final TaskNodeInputProtocol.Input input;private final TaskRunReplayContentAuthority.Verified content;
        private Prepared(TaskNodeProductionBasis bootstrap,TaskRunReplayRegistration.Registered replay,TaskRunGraphService.Verified graph,TaskNodeProducerAuthority.Verified producer,TaskNodeInputProtocol.Input input,TaskRunReplayContentAuthority.Verified content){this.bootstrap=bootstrap;this.replay=replay;this.graph=graph;this.producer=producer;this.input=input;this.content=content;}
        public TaskNodeProductionBasis bootstrap(){return bootstrap;} public TaskNodeProducerAuthority.Verified producer(){return producer;}
        public TaskNodeInputProtocol.Input input(){return input;} TaskRunReplayContentAuthority.Verified content(){return content;}
        public void requireUnchanged()throws Exception {
            current(bootstrap.admission());bootstrap.requireUnchanged();graph.requireUnchanged();graph.requirePredecessors(input.questionId());producer.requireUnchanged();content.requireUnchanged();
            if(!input.equals(compile(bootstrap,replay,graph,input.questionId())))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            producer.requireProduction(bootstrap.admission(),bootstrap.taskId(),bootstrap.runId(),bootstrap.principalId(),input.questionId(),TaskNodeInputRegistration.KIND,Integer.toString(schema.bytes(input).length));
            requireReplay(bootstrap.admission(),input,replay.receipt());
        }
    }
    private TaskNodeInputProtocol.Input compile(TaskNodeProductionBasis bootstrap,TaskRunReplayRegistration.Registered registered,
            TaskRunGraphService.Verified graph,String question)throws Exception {
        var replay=bootstrap.replay();var data=replay.content();var node=graph.question(question);
        if(!bootstrap.tenantId().equals(graph.tenantId())||!bootstrap.projectId().equals(graph.projectId())||!bootstrap.taskId().equals(graph.taskId())||!bootstrap.runId().equals(graph.runId())||!bootstrap.principalId().equals(graph.principalId())||!replay.planRevision().equals(graph.planRevision())||!text(data.required("plan"),"content_sha256").equals(graph.planSha256())||!replay.rawSha256().equals(graph.replaySha256()))bad();
        var bindings=replay.executionPolicy().bindings().stream().filter(n->"N02".equals(n.nodeKind())).toList();var binding=one(bindings);
        var fixed=new ArrayList<TaskNodeInputProtocol.SourceSelection>();for(var member:data.required("scope_snapshot").required("members"))fixed.add(source(member,replay.executionPolicy().businessPurpose()));
        var r=registered.reference();
        return new TaskNodeInputProtocol.Input("1.0.0",bootstrap.tenantId(),bootstrap.projectId(),bootstrap.taskId(),bootstrap.runId(),node.stepId(),graph.graphId(),graph.graphSha256(),"N02",node.questionId(),node.questionKey(),node.questionText(),node.sectionId(),graph.planRevision(),graph.planSha256(),replay.snapshotId(),text(data,"scope_snapshot_sha256"),replay.rawSha256(),new TaskNodeInputProtocol.ReplayArtifact(registered.artifactId(),r.sha256(),r.objectVersion()),registered.receiptSha256(),binding.roleVersion(),json.basis(data.required("execution_policy_version")),fixed,nodePermissions(data,binding),graph.predecessors(question));
    }
    /** A tool-segment admission was just created by the complete current TASK role authority. */
    public Verified require(EnterpriseAccess.Admission admission,String run,String step)throws Exception {
        if(!admission.formalWorker())denied();return require(admission,run,step,null,null);
    }
    /** Control consumers supply actual current publication/policy, without external I/O in this transaction. */
    public Verified require(EnterpriseAccess.Admission admission,String run,String step,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception {
        current(admission);if(admission.controlWorker()&&(publication==null||policy==null))denied();
        return new Verified(admission,run,step,publication,policy,read(admission,run,step,publication,policy));
    }
    private record Snapshot(TaskNodeInputProtocol.Input input,TaskNodeInputRegistration.Registered registered,String authoritySha256){}
    private Snapshot read(EnterpriseAccess.Admission a,String run,String step,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception {
        current(a);var attempt=ledger.stored(a,run,step);var row=ledger.requireStored(a,attempt);
        var registered=registrations.readStored(a,attempt).orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
        var input=schema.read(attempt.bytes());if(!run.equals(input.runId())||!step.equals(input.stepId()))bad();
        var graph=graphs.require(a,run);graph.requireUnchanged();var question=graph.question(input.questionId());
        if(!input.graphId().equals(graph.graphId())||!input.graphSha256().equals(graph.graphSha256())||!input.planRevision().equals(graph.planRevision())||!input.planSha256().equals(graph.planSha256())||!input.replaySha256().equals(graph.replaySha256())||!input.stepId().equals(question.stepId())||!input.questionKey().equals(question.questionKey())||!input.questionText().equals(question.questionText())||!input.sectionId().equals(question.sectionId()))bad();
        if(!input.predecessors().equals(graph.predecessors(input.questionId())))bad();
        var replay=requireReplay(a,input,null);checkFixedInput(a,input,replay);
        var c=row.columns();
        if(publication!=null&&policy!=null)producers.requireRegisteredInput(a,publication,policy,text(c,"producer_rule_id"),text(c,"producer_sha256"),text(c,"producer_decision_id"),text(c,"producer_decision_sha256"),schema.tree(input));
        else producers.requireRegisteredInput(a,text(c,"producer_rule_id"),text(c,"producer_sha256"),text(c,"producer_decision_id"),text(c,"producer_decision_sha256"),schema.tree(input));
        var records=db.queryForList("SELECT s.input_sha256,s.role_version,s.section_id,s.created_by,s.classification,s.created_at AS step_created,s.schema_version AS step_schema,s.schema_manifest_sha256 AS step_manifest,n.created_at AS node_created,n.schema_version AS node_schema,n.schema_manifest_sha256 AS node_manifest,n.node_kind,n.role_sha256,n.policy_version,n.policy_sha256,n.canonical_definition,n.definition_sha256,n.created_by AS node_actor,n.classification AS node_classification FROM run_step s JOIN runtime_node_definition n ON n.tenant_id=s.tenant_id AND n.project_id=s.project_id AND n.run_id=s.run_id AND n.step_id=s.step_id WHERE s.tenant_id=? AND s.project_id=? AND s.run_id=? AND s.step_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),run,step);
        var record=one(records);if(!attempt.writeIntent().sha256().equals(record.get("input_sha256"))||!input.roleVersion().version_id().equals(record.get("role_version"))||!input.roleVersion().content_sha256().equals(record.get("role_sha256"))||!input.sectionId().equals(record.get("section_id"))||!"N02".equals(record.get("node_kind"))||!input.executionPolicyVersion().version_id().equals(record.get("policy_version"))||!input.executionPolicyVersion().content_sha256().equals(record.get("policy_sha256"))||!a.actorId().equals(record.get("created_by"))||!a.actorId().equals(record.get("node_actor"))||!c.get("classification").equals(record.get("classification"))||!c.get("classification").equals(record.get("node_classification")))bad();
        var definition=schema.readTree(text(record,"canonical_definition"));if(!schema.hash(definition).equals(record.get("definition_sha256"))||!definition.equals(TaskNodeMaterializer.definition(json,input,registered.artifactId(),registered.object().sha256(),registered.receiptSha256())))bad();
        var created=time(record,"node_created");
        if(!created.equals(time(record,"step_created"))||created.isBefore(time(registered.receipt(),"created_at"))||created.isAfter(ledger.now())
                ||!"1.0.0".equals(record.get("step_schema"))||!"1.0.0".equals(record.get("node_schema"))
                ||!tokens.manifestSha256().equals(record.get("step_manifest"))||!schema.manifest().equals(record.get("node_manifest")))bad();
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND request_id=? AND action='task.node.materialized' LIMIT 2",input.tenantId(),input.stepId()));
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='task.node.materialized' LIMIT 2",input.tenantId(),input.stepId()));
        requireMaterializationJournal(input,a.actorId(),text(c,"classification"),created,schema.manifest(),schema.canonical(definition),audit,event);
        var witness=json.object();witness.put("receipt_sha256",registered.receiptSha256()).put("intent_sha256",text(c,"intent_sha256")).put("graph_sha256",graph.graphSha256()).put("runtime_definition_sha256",text(record,"definition_sha256"));
        witness.set("source_authorizations",sourceWitness(a,input,replay));graph.requireUnchanged();return new Snapshot(input,registered,schema.hash(witness));
    }
    /** Data-only journal comparison, shared with materialization; never constructs authority. */
    static void requireMaterializationJournal(TaskNodeInputProtocol.Input input,String actor,String classification,
            LocalDateTime created,String manifest,String canonical,Map<String,Object> audit,Map<String,Object> event){
        TaskNodeInputRegistration.sameColumns(Map.of("tenant_id",input.tenantId(),"project_id",input.projectId(),"subject_id",actor,
                "created_by",actor,"created_at",created,"classification",classification,"schema_version","1.0.0","schema_manifest_sha256",manifest),audit);
        TaskNodeInputRegistration.sameColumns(Map.of("action","task.node.materialized","request_id",input.stepId(),"trace_id",input.runId(),"resource_ref",canonical,"result","SUCCESS"),audit);
        if(audit.get("reason_code")!=null)bad();
        TaskNodeInputRegistration.sameColumns(Map.of("tenant_id",input.tenantId(),"aggregate_id",input.stepId(),"aggregate_revision",1,
                "event_type","task.node.materialized","payload",canonical,"occurred_at",created),event);
    }
    private JsonNode sourceWitness(EnterpriseAccess.Admission a,TaskNodeInputProtocol.Input input,JsonNode replay)throws Exception {
        var result=json.object().arrayNode();var policy=replay.required("execution_policy");var members=replay.required("scope_snapshot").required("members");
        for(var fixed:input.fixedSources()){
            var source=sources.require(a,new SourceReadProtocol.ReadRequirement(fixed.sourceVersion(),fixed.rightsRevision(),input.projectId(),input.snapshotId(),fixed.parseVersion(),null,SourceReadProtocol.Use.FORMAL,fixed.purpose(),text(policy,"environment"),text(policy,"region"),null));
            var matches=new ArrayList<JsonNode>();for(var member:members)if(fixed.sourceVersion().equals(text(member,"source_version")))matches.add(member);var original=one(matches);
            if(!text(original,"original_sha256").equals(source.source().originalSha256())||source.selection()==null||!fixed.indexVersion().equals(source.selection().indexVersion())||!fixed.approvalRevision().equals(source.selection().approvalRevision())||!fixed.bindingRevision().equals(source.selection().bindingRevision()))bad();
            result.add(AgentPublicationWitness.tree(source));
        }return result;
    }
    private void checkFixedInput(EnterpriseAccess.Admission a,TaskNodeInputProtocol.Input input,JsonNode replay)throws Exception {
        for(String field:List.of("tenant_id","project_id","task_id","run_id","plan_revision","snapshot_id"))if(!schema.tree(input).required(field).equals(replay.required(field)))bad();
        if(!input.planSha256().equals(text(replay.required("plan"),"content_sha256"))||!input.snapshotSha256().equals(text(replay,"scope_snapshot_sha256"))||!json.tree(input.executionPolicyVersion()).equals(replay.required("execution_policy_version")))bad();
        var selected=new ArrayList<TaskNodeInputProtocol.SourceSelection>();for(var member:replay.required("scope_snapshot").required("members"))selected.add(source(member,text(replay.required("execution_policy"),"business_purpose")));if(!selected.equals(input.fixedSources()))bad();
        var bindingNodes=new ArrayList<JsonNode>();for(var n:replay.required("execution_policy").required("bindings"))if("N02".equals(text(n,"node_kind")))bindingNodes.add(n);var binding=one(bindingNodes);
        if(!json.tree(input.roleVersion()).equals(binding.required("role_version")))bad();
        var matches=new ArrayList<JsonNode>();for(var question:replay.required("plan").required("content").required("questions"))if(input.questionKey().equals(text(question,"question_key")))matches.add(question);var question=one(matches);if(!input.questionText().equals(text(question,"question"))||!input.sectionId().equals(text(question,"section_id")))bad();
    }
    private JsonNode requireReplay(EnterpriseAccess.Admission a,TaskNodeInputProtocol.Input input,JsonNode expectedReceipt)throws Exception {
        var records=db.queryForList("SELECT r.replay_manifest,r.classification,x.receipt_json,x.receipt_sha256,x.object_sha256,x.object_version,x.created_by,x.created_at,x.write_id,x.artifact_id,a.object_ref,a.object_key,a.sha256,a.size_bytes,a.media_type,a.dependency_access_set_id,b.resource_id,b.schema_ref,b.schema_document_sha256 FROM task_run r JOIN task_run_replay_registration x ON x.tenant_id=r.tenant_id AND x.project_id=r.project_id AND x.run_id=r.run_id JOIN artifact a ON a.tenant_id=x.tenant_id AND a.project_id=x.project_id AND a.run_id=x.run_id AND a.artifact_id=x.artifact_id AND a.artifact_kind='REPLAY_MANIFEST' JOIN artifact_content_binding b ON b.tenant_id=a.tenant_id AND b.project_id=a.project_id AND b.artifact_id=a.artifact_id AND b.resource_kind='TASK' WHERE r.tenant_id=? AND r.project_id=? AND r.task_id=? AND r.run_id=? AND r.created_by=? AND r.execution_kind='TASK' AND x.artifact_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),input.taskId(),input.runId(),a.actorId(),input.replayArtifact().artifactId());
        var row=one(records);var replay=schema.readTree(text(row,"replay_manifest"));var receipt=schema.readTree(text(row,"receipt_json"));
        if(!input.replaySha256().equals(schema.hash(replay))||!input.replaySha256().equals(row.get("object_sha256"))||!input.replaySha256().equals(row.get("sha256"))||!input.replayArtifact().objectVersion().equals(row.get("object_version"))||!input.replayReceiptSha256().equals(row.get("receipt_sha256"))||!input.replayReceiptSha256().equals(schema.hash(receipt))||!a.actorId().equals(row.get("created_by"))||!input.taskId().equals(row.get("resource_id"))||!RuntimeReplaySchema.REF.equals(row.get("schema_ref"))||expectedReceipt!=null&&!expectedReceipt.equals(receipt))bad();
        for(String field:List.of("tenant_id","project_id","run_id","task_id"))if(!schema.tree(input).required(field).equals(receipt.required(field)))bad();
        var object=receipt.required("object_ref");if(!input.replayArtifact().artifactId().equals(text(receipt,"artifact_id"))||!input.replaySha256().equals(text(object,"sha256"))||!input.replayArtifact().objectVersion().equals(text(object,"object_version"))||!text(row,"object_key").equals(text(object,"object_key"))||!String.valueOf(row.get("size_bytes")).equals(text(object,"size_bytes"))||!text(row,"media_type").equals(text(object,"media_type")))bad();
        StoredObjectReferences.decode(text(row,"object_ref"),new com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef(text(object,"object_key"),text(object,"object_version"),text(object,"sha256"),text(object,"size_bytes"),text(object,"media_type")));
        var events=db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type='task.run.replay.registered' AND aggregate_revision=1 AND occurred_at=? LIMIT 2",input.tenantId(),input.replayArtifact().artifactId(),row.get("write_id"),row.get("created_at"));if(!receipt.equals(schema.readTree(text(one(events),"payload"))))bad();
        return replay;
    }
    private List<RunTokenAuthoritySchema.Permission> nodePermissions(JsonNode replay,AgentExecutionPolicyProtocol.NodeBinding binding)throws Exception {
        String tenant=text(replay,"tenant_id"),project=text(replay,"project_id"),task=text(replay,"task_id");var requested=new LinkedHashSet<RunTokenAuthoritySchema.Permission>();
        for(var action:binding.actions()){
            var scopes=new ArrayList<PermissionProtocol.Scope>();switch(action.targetSelector()){
                case "TASK"->scopes.add(new PermissionProtocol.Scope(tenant,project,"TASK",task));
                case "PROJECT"->scopes.add(new PermissionProtocol.Scope(tenant,project,"PROJECT",project));
                case "CURRENT_EXECUTION_POLICY"->scopes.add(owner(tenant,project,json.basis(replay.required("execution_policy_version")),"CONFIGURATION"));
                case "FIXED_VERSION"->scopes.add(owner(tenant,project,action.fixedVersion(),action.resourceKind()));
                case "SELECTED_SOURCE"->{for(var source:replay.required("scope_snapshot").required("members"))scopes.add(owner(tenant,project,new PermissionProtocol.Basis(text(source,"source_version"),text(source,"original_sha256")),"SOURCE"));}
                case "RUN_REGISTERED_ARTIFACT"->{ /* separate exact receipt-bound content capability */ }
                default->denied();
            }
            for(var scope:scopes){if(!action.resourceKind().equals(scope.resource_kind()))bad();requested.add(new RunTokenAuthoritySchema.Permission(scope,action.action(),action.purpose()));}
        }
        if(binding.modelVersion()!=null){
            var pinned=schema.readTree(text(replay.required("authorizations"),"canonical_witnesses")).required("additional_authorizations");
            if(!pinned.has("N02"))denied();
            requested.addAll(TaskModelNodePermissions.declared(binding.modelVersion(),owner(tenant,project,binding.modelVersion(),"CONFIGURATION"),pinned.required("N02")));
        }
        var result=List.copyOf(requested);RuntimeNodePermissions.intersect(tokens.permissions(replay.required("principal_manifest")),result,result.stream().map(RunTokenAuthoritySchema.Permission::action).distinct().toList());return result;
    }
    private PermissionProtocol.Scope owner(String tenant,String project,PermissionProtocol.Basis basis,String kind){
        if(basis==null)denied();var row=one(owners.ownersAcrossDomains(List.of(basis.version_id()),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList())).owner();
        if(!tenant.equals(row.tenantId())||!basis.version_id().equals(row.versionId())||row.contentSha256()!=null&&!basis.content_sha256().equals(row.contentSha256())||!kind.equals(row.resourceKind())||row.resourceProjectId()!=null&&!project.equals(row.resourceProjectId())||row.versionProjectId()!=null&&!project.equals(row.versionProjectId())||row.conversationId()!=null)bad();
        if(one(db.query("SELECT COUNT(*) FROM version_registry WHERE tenant_id=? AND version_id=? AND content_sha256=? AND owner_project_id <=> ? FOR UPDATE",(r,n)->r.getInt(1),tenant,basis.version_id(),basis.content_sha256(),row.versionProjectId()))!=1)bad();return new PermissionProtocol.Scope(tenant,row.resourceProjectId(),kind,row.resourceId());
    }
    private static TaskNodeInputProtocol.SourceSelection source(JsonNode member,String purpose){return new TaskNodeInputProtocol.SourceSelection(text(member,"source_version"),text(member,"parse_version"),text(member,"index_version"),text(member,"approval_revision"),text(member,"binding_revision"),text(member,"rights_revision"),purpose);}
    public final class Verified {
        private final EnterpriseAccess.Admission admission;private final String run,step;private final Snapshot snapshot;
        private final AgentPublicationAuthority.VerifiedPublication publication;private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;
        private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();
        private final Map<Object,Object> transaction=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private Verified(EnterpriseAccess.Admission a,String run,String step,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,Snapshot snapshot){this.admission=a;this.run=run;this.step=step;this.publication=publication;this.policy=policy;this.snapshot=snapshot;if(transaction.isEmpty())denied();}
        private void active(){current(admission);if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent())denied();var actual=TransactionSynchronizationManager.getResourceMap();if(actual.size()!=transaction.size()||transaction.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))denied();}
        public String tenantId(){active();return snapshot.input().tenantId();}public String projectId(){active();return snapshot.input().projectId();}
        public String taskId(){active();return snapshot.input().taskId();}public String runId(){active();return run;}public String stepId(){active();return step;}
        public String graphId(){active();return snapshot.input().graphId();}public String graphSha256(){active();return snapshot.input().graphSha256();}
        public String inputId(){active();return snapshot.registered().artifactId();}public String receiptSha256(){active();return snapshot.registered().receiptSha256();}
        public com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef objectReference(){active();return snapshot.registered().object();}
        public String inputSha256(){active();return snapshot.registered().object().sha256();}public String questionId(){active();return snapshot.input().questionId();}
        public String questionText(){active();return snapshot.input().questionText();}public String snapshotId(){active();return snapshot.input().snapshotId();}
        public JsonNode manifest(){active();return schema.tree(snapshot.input());}public List<TaskNodeInputProtocol.SourceSelection> fixedSources(){active();return snapshot.input().fixedSources();}
        /** Governance only; the physical host must reacquire this proof for every segment. */
        void requireHostInputConsume() throws Exception {
            active();
            if (!admission.formalWorker()) denied();
            // The outer private TASK admission already revalidates published086/approved078.
            // Derive only this exact registered input under its real current HOST_CONTROL lease.
            producers.requireLeased(admission,this).requireHostInputConsume(this);
        }
        public void requireUnchanged()throws Exception {active();var fresh=read(admission,run,step,publication,policy);if(!snapshot.input().equals(fresh.input())||!snapshot.registered().receiptSha256().equals(fresh.registered().receiptSha256())||!snapshot.authoritySha256().equals(fresh.authoritySha256()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
    }
}
