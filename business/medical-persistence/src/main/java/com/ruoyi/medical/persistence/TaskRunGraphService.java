package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Run-owned immutable planning receipt. It does not materialize a step or fulfill
 * later-stage obligations. Every proof is current-transaction and exact-run bound. */
public final class TaskRunGraphService {
    private final JdbcTemplate db;private final PermissionSchema json;private final int maximumQuestions;private final TaskResearchCompletionAuthority completions;
    private final JsonMapper reader=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final String ACTION="task.run.graph.prepared";
    private static final String MANIFEST=manifest();
    private static String manifest(){try(var in=TaskRunGraphService.class.getResourceAsStream("/task-run-planned-graph.schema.json")){if(in==null)throw new IllegalStateException("GRAPH_SCHEMA_MISSING");return org.apache.commons.codec.digest.DigestUtils.sha256Hex(in.readAllBytes());}catch(java.io.IOException e){throw new IllegalStateException(e);}}

    public TaskRunGraphService(DataSource data,PermissionSchema json,int maximumQuestions,TaskResearchCompletionAuthority completions){if(maximumQuestions<1)throw new IllegalArgumentException("maximumQuestions");this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.maximumQuestions=maximumQuestions;this.completions=Objects.requireNonNull(completions);}
    public record Node(String questionId,String questionKey,String stepId,String questionText,String sectionId,List<String> dependsOnStepIds){public Node{dependsOnStepIds=List.copyOf(dependsOnStepIds);}}
    private record Basis(JsonNode replay,String replayHash,String receiptHash,String actor,String classification,TaskRunGraphCompiler.Graph graph){ }
    public Verified prepare(TaskRunBootstrapAuthority.Verified bootstrap,TaskRunReplayRegistration.Registered registered)throws Exception{
        bootstrap.requireUnchanged();var a=bootstrap.admission();var basis=basis(a,bootstrap.runId());
        if(!basis.replayHash().equals(bootstrap.replay().rawSha256())||!basis.receiptHash().equals(registered.receiptSha256())
            ||!basis.replayHash().equals(registered.reference().sha256()))bad();
        var existing=rows(a,bootstrap.runId());
        if(existing.isEmpty())write(a,basis);else if(existing.size()!=1)bad();
        var result=require(a,bootstrap.runId());bootstrap.requireUnchanged();return result;
    }
    public Verified require(EnterpriseAccess.Admission a,String run)throws Exception{
        active(a,run,null);var basis=basis(a,run);var row=one(rows(a,run));verify(a,basis,row);return new Verified(a,run,basis,row);
    }
    private Basis basis(EnterpriseAccess.Admission a,String run)throws Exception{return basis(a,run,false);}
    private Basis basis(EnterpriseAccess.Admission a,String run,boolean originalRead)throws Exception{return basis(a,run,originalRead,null);}
    private Basis basis(EnterpriseAccess.Admission a,String run,boolean originalRead,TaskRunResumeOriginalAuthority.Source source)throws Exception{
        if(source!=null)source.requireOriginal(a,run,source.originalStepId());else if(originalRead)originalActive(a,run,null);else active(a,run,null);
        var row=one(db.queryForList("SELECT r.task_id,r.project_id,p.principal_id,r.replay_manifest,r.classification,r.created_by FROM task_run r JOIN run_principal p ON p.tenant_id=r.tenant_id AND p.project_id=r.project_id AND p.run_id=r.run_id AND p.initiator_id=r.created_by WHERE r.tenant_id=? AND r.run_id=? AND r.execution_kind='TASK' LIMIT 2 FOR UPDATE",a.context().tenant_id(),run));
        String task=s(row,"task_id");if(source!=null)source.requireOriginal(a,run,source.originalStepId());else if(originalRead)originalActive(a,run,task);else active(a,run,task);if(!a.actorId().equals(s(row,"created_by")))deny();
        var replay=read(s(row,"replay_manifest"));for(var e:Map.of("tenant_id",a.context().tenant_id(),"project_id",s(row,"project_id"),"task_id",task,"run_id",run,"principal_id",s(row,"principal_id"),"initiator_id",a.actorId()).entrySet())if(!e.getValue().equals(replay.required(e.getKey()).asText()))bad();
        String replayHash=hash(replay);var plan=replay.required("plan");String revision=plan.required("plan_revision").asText(),planHash=plan.required("content_sha256").asText();
        if(!"CONFIRMED".equals(plan.required("status").asText())||!planHash.equals(hash(plan.required("content"))))bad();
        var current=one(db.queryForList("SELECT p.status,c.content_json,c.content_sha256,v.content_sha256 AS registry_hash FROM plan_content_revision c JOIN plan_revision p ON p.tenant_id=c.tenant_id AND p.project_id=c.project_id AND p.task_id=c.task_id AND p.plan_revision=c.plan_revision JOIN version_registry v ON v.tenant_id=c.tenant_id AND v.version_id=c.plan_revision WHERE c.tenant_id=? AND c.project_id=? AND c.task_id=? AND c.plan_revision=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),s(row,"project_id"),task,revision));
        if(!originalRead&&!"CONFIRMED".equals(s(current,"status"))||!planHash.equals(s(current,"content_sha256"))||!planHash.equals(s(current,"registry_hash"))||!plan.required("content").equals(read(s(current,"content_json"))))bad();
        if(a.controlWorker())a.controlProof().requireVersion(revision,planHash);
        var original=one(db.queryForList("SELECT x.receipt_json,x.receipt_sha256,x.artifact_id,x.object_sha256,x.object_version,a.sha256,a.object_version AS artifact_version,a.run_id AS artifact_run FROM task_run_replay_registration x JOIN artifact a ON a.tenant_id=x.tenant_id AND a.artifact_id=x.artifact_id AND a.project_id=x.project_id WHERE x.tenant_id=? AND x.project_id=? AND x.run_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),s(row,"project_id"),run));
        var receipt=read(s(original,"receipt_json"));String receiptHash=s(original,"receipt_sha256");
        if(!receiptHash.equals(hash(receipt))||!replayHash.equals(s(original,"object_sha256"))||!replayHash.equals(s(original,"sha256"))||!run.equals(s(original,"artifact_run"))||!s(original,"object_version").equals(s(original,"artifact_version"))||!run.equals(receipt.required("run_id").asText())||!s(original,"artifact_id").equals(receipt.required("artifact_id").asText()))bad();
        var questionRows=db.query("SELECT q.question_id,r.question_key,r.question_key_sha256,q.section_id,q.question,q.position FROM plan_question q JOIN research_question r ON r.tenant_id=q.tenant_id AND r.project_id=q.project_id AND r.task_id=q.task_id AND r.question_id=q.question_id WHERE q.tenant_id=? AND q.project_id=? AND q.task_id=? AND q.plan_revision=? ORDER BY q.position FOR UPDATE",(rs,n)->{
            String key=rs.getString(2);if(!org.apache.commons.codec.digest.DigestUtils.sha256Hex(key.getBytes(StandardCharsets.UTF_8)).equals(rs.getString(3)))bad();
            return new TaskRunGraphCompiler.Question(rs.getString(1),key,rs.getString(4),rs.getString(5),rs.getInt(6));
        },a.context().tenant_id(),s(row,"project_id"),task,revision);
        var graph=TaskRunGraphCompiler.compile(json,replay,questionRows,maximumQuestions);
        return new Basis(replay,replayHash,receiptHash,a.actorId(),s(row,"classification"),graph);
    }
    private List<Map<String,Object>> rows(EnterpriseAccess.Admission a,String run){return db.queryForList("SELECT * FROM task_run_planned_graph WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),run);}
    private void write(EnterpriseAccess.Admission a,Basis b)throws Exception{
        var r=b.replay();String tenant=a.context().tenant_id(),project=r.required("project_id").asText(),run=r.required("run_id").asText();
        String graph=id(),operation=id(),audit=id(),event=id();LocalDateTime now=db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class);
        var c=new LinkedHashMap<String,Object>();for(String f:List.of("tenant_id","project_id","task_id","run_id","principal_id","plan_revision"))c.put(f,r.required(f).asText());
        c.put("graph_id",graph);c.put("graph_sha256",b.graph().sha256());c.put("plan_sha256",r.required("plan").required("content_sha256").asText());c.put("replay_sha256",b.replayHash());c.put("replay_receipt_sha256",b.receiptHash());c.put("graph_json",json.canonical(b.graph().content()));c.put("operation_id",operation);c.put("audit_id",audit);c.put("event_id",event);c.put("schema_version","1.0.0");c.put("schema_manifest_sha256",MANIFEST);c.put("created_by",a.actorId());c.put("created_at",now);c.put("classification",b.classification());
        String anchor=anchor(c),key="task-graph:"+run;
        changed(db.update("INSERT INTO operation_record(tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,http_status,response_ref,target_id,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,'COMPLETED',200,?,?,'1.0.0',?,?,?,?,1,?)",tenant,operation,a.actorId(),ACTION,key,b.graph().sha256(),anchor,graph,MANIFEST,a.actorId(),now,b.classification(),now));
        changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",tenant,project,audit,a.actorId(),ACTION,anchor,graph,run,MANIFEST,a.actorId(),now,b.classification()));
        changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,?,?,?)",tenant,event,graph,ACTION,anchor,now));
        insert("task_run_planned_graph",c);
        for(var node:b.graph().nodes())for(String dependency:node.dependsOnStepIds()){
            var edge=new LinkedHashMap<String,Object>();for(String f:List.of("tenant_id","project_id","task_id","run_id","graph_id","graph_sha256","schema_version","schema_manifest_sha256","created_by","created_at","classification"))edge.put(f,c.get(f));
            edge.put("step_id",node.stepId());edge.put("depends_on_step_id",dependency);insert("task_run_planned_edge",edge);
        }
    }
    private void verify(EnterpriseAccess.Admission a,Basis b,Map<String,Object> c)throws Exception{
        for(String f:List.of("tenant_id","project_id","task_id","run_id","principal_id","plan_revision"))if(!b.replay().required(f).asText().equals(s(c,f)))bad();
        if(!b.actor().equals(s(c,"created_by"))||!b.classification().equals(s(c,"classification"))||!MANIFEST.equals(s(c,"schema_manifest_sha256"))||!"1.0.0".equals(s(c,"schema_version"))||!b.graph().sha256().equals(s(c,"graph_sha256"))||!b.graph().content().equals(read(s(c,"graph_json")))||!b.replayHash().equals(s(c,"replay_sha256"))||!b.receiptHash().equals(s(c,"replay_receipt_sha256"))||!b.replay().required("plan").required("content_sha256").asText().equals(s(c,"plan_sha256")))bad();
        var edges=db.queryForList("SELECT * FROM task_run_planned_edge WHERE tenant_id=? AND graph_id=?",s(c,"tenant_id"),s(c,"graph_id"));var expected=new HashSet<String>();
        for(var n:b.graph().nodes())for(String d:n.dependsOnStepIds())expected.add(n.stepId()+":"+d);
        if(edges.size()!=expected.size())bad();for(var edge:edges){for(String f:List.of("tenant_id","project_id","task_id","run_id","graph_id","graph_sha256","schema_version","schema_manifest_sha256","created_by","created_at","classification"))if(!Objects.equals(c.get(f),edge.get(f)))bad();if(!expected.remove(s(edge,"step_id")+":"+s(edge,"depends_on_step_id")))bad();}
        String anchor=anchor(c);var op=one(db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? LIMIT 2",s(c,"tenant_id"),s(c,"operation_id")));
        for(String f:List.of("tenant_id","created_by","created_at","classification","schema_version","schema_manifest_sha256"))if(!Objects.equals(c.get(f),op.get(f)))bad();
        if(!ACTION.equals(s(op,"operation_name"))||!s(c,"created_by").equals(s(op,"subject_id"))||!s(c,"graph_sha256").equals(s(op,"request_sha256"))||!s(c,"graph_id").equals(s(op,"target_id"))||!anchor.equals(s(op,"response_ref"))||!"COMPLETED".equals(s(op,"state"))||((Number)op.get("http_status")).intValue()!=200||((Number)op.get("revision")).longValue()!=1||!Objects.equals(c.get("created_at"),op.get("updated_at")))bad();
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",s(c,"tenant_id"),s(c,"audit_id")));
        for(String f:List.of("tenant_id","project_id","created_by","created_at","classification","schema_version","schema_manifest_sha256"))if(!Objects.equals(c.get(f),audit.get(f)))bad();
        if(!ACTION.equals(s(audit,"action"))||!s(c,"created_by").equals(s(audit,"subject_id"))||!anchor.equals(s(audit,"resource_ref"))||!"SUCCESS".equals(s(audit,"result"))||!s(c,"graph_id").equals(s(audit,"request_id"))||!s(c,"run_id").equals(s(audit,"trace_id")))bad();
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",s(c,"tenant_id"),s(c,"event_id")));
        if(!ACTION.equals(s(event,"event_type"))||!s(c,"graph_id").equals(s(event,"aggregate_id"))||((Number)event.get("aggregate_revision")).longValue()!=1||!anchor.equals(s(event,"payload"))||!Objects.equals(c.get("created_at"),event.get("occurred_at")))bad();
    }
    private String anchor(Map<String,Object> row)throws java.io.IOException{var n=json.object().put("kind","TASK_RUN_PLANNED_GRAPH").put("schema_version","1.0.0");for(String f:List.of("tenant_id","project_id","task_id","run_id","graph_id","graph_sha256","plan_revision","plan_sha256","replay_sha256","replay_receipt_sha256","operation_id","audit_id","event_id"))n.put(f,s(row,f));return json.canonical(n);}
    /** Immutable graph inventory for original recovery; no predecessor content or
     * producer permission is exposed, and it cannot enter materialization APIs. */
    Original requireOriginal(EnterpriseAccess.Admission a,String run)throws Exception{
        originalActive(a,run,null);var basis=basis(a,run,true);var row=one(rows(a,run));verify(a,basis,row);return new Original(a,run,basis,row);
    }
    Original requireOriginal(TaskRunResumeOriginalAuthority.Source source)throws Exception{
        source.requireUnchanged();var a=source.admission();String run=source.originalRunId();var basis=basis(a,run,true,source);var row=one(rows(a,run));verify(a,basis,row);return new Original(a,run,basis,row,source);
    }
    final class Original {
        private final EnterpriseAccess.Admission a;private final String run;private final Basis basis;private final Map<String,Object> row;private final TaskRunResumeOriginalAuthority.Source source;
        private Original(EnterpriseAccess.Admission a,String run,Basis basis,Map<String,Object> row){this(a,run,basis,row,null);}
        private Original(EnterpriseAccess.Admission a,String run,Basis basis,Map<String,Object> row,TaskRunResumeOriginalAuthority.Source source){this.a=a;this.run=run;this.basis=basis;this.row=Collections.unmodifiableMap(new HashMap<>(row));this.source=source;}
        private void active(){if(source==null)originalActive(a,run,s(row,"task_id"));else org.springframework.util.function.ThrowingSupplier.of(()->{source.requireOriginal(a,run,source.originalStepId());return null;}).get();}
        void requireUnchanged()throws Exception{active();var fresh=basis(a,run,true,source);var actual=one(rows(a,run));verify(a,fresh,actual);if(!row.equals(actual)||!basis.graph().sha256().equals(fresh.graph().sha256()))bad();}
        String graphId(){active();return s(row,"graph_id");}String graphSha256(){active();return s(row,"graph_sha256");}String planRevision(){active();return s(row,"plan_revision");}
        List<Node> nodes(){active();return basis.graph().nodes().stream().map(n->new Node(n.questionId(),n.questionKey(),n.stepId(),n.questionText(),n.sectionId(),n.dependsOnStepIds())).toList();}
        List<String> requiredStages(){active();return basis.graph().requiredStages();}
    }
    private static void originalActive(EnterpriseAccess.Admission a,String run,String task){if(!TransactionSynchronizationManager.isActualTransactionActive()||!a.recoveryWorker())deny();var p=a.recoveryProof();p.requireActive();if(!run.equals(p.identity().runId())||task!=null&&!task.equals(p.identity().taskId()))deny();}
    public final class Verified {
        private final EnterpriseAccess.Admission a;private final String run;private final Basis basis;private final Map<String,Object> row;
        private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();private final Map<Object,Object> tx=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private Verified(EnterpriseAccess.Admission a,String run,Basis basis,Map<String,Object> row){this.a=a;this.run=run;this.basis=basis;this.row=Collections.unmodifiableMap(new LinkedHashMap<>(row));if(tx.isEmpty())deny();}
        private void active(){if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent()||!TransactionSynchronizationManager.isActualTransactionActive())deny();var actual=TransactionSynchronizationManager.getResourceMap();if(tx.size()!=actual.size()||tx.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))deny();TaskRunGraphService.active(a,run,taskIdInternal());}
        private String taskIdInternal(){return s(row,"task_id");}
        public EnterpriseAccess.Admission admission(){active();return a;}public String tenantId(){active();return s(row,"tenant_id");}public String projectId(){active();return s(row,"project_id");}public String taskId(){active();return taskIdInternal();}public String runId(){active();return run;}public String principalId(){active();return s(row,"principal_id");}
        public String graphId(){active();return s(row,"graph_id");}public String graphSha256(){active();return s(row,"graph_sha256");}public String planRevision(){active();return s(row,"plan_revision");}public String planSha256(){active();return s(row,"plan_sha256");}public String replaySha256(){active();return basis.replayHash();}
        public List<Node> nodes(){active();return basis.graph().nodes().stream().map(n->new Node(n.questionId(),n.questionKey(),n.stepId(),n.questionText(),n.sectionId(),n.dependsOnStepIds())).toList();}
        public Node question(String id){return nodes().stream().filter(n->n.questionId().equals(id)).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.ACCESS_DENIED));}
        public void requirePredecessors(String questionId)throws Exception{predecessors(questionId);}
        public List<TaskNodeInputProtocol.Predecessor> predecessors(String questionId)throws Exception{
            active();var node=question(questionId);var result=new ArrayList<TaskNodeInputProtocol.Predecessor>();
            for(String dependency:node.dependsOnStepIds()){
                var proof=completions.require(a,run,dependency);proof.requireUnchanged();
                result.add(new TaskNodeInputProtocol.Predecessor(dependency,proof.resultArtifactId(),proof.resultSha256(),
                        proof.resultObjectVersion(),proof.resultReceiptSha256(),proof.evidenceArtifactId(),proof.evidenceSha256(),
                        proof.evidenceObjectVersion(),proof.evidenceReceiptSha256(),proof.completionReceiptSha256()));
                proof.requireUnchanged();
            }
            requireUnchanged();return List.copyOf(result);
        }
        public List<String> requiredStages(){active();return basis.graph().requiredStages();}
        public void requireUnchanged()throws Exception{active();var fresh=basis(a,run);var actual=one(rows(a,run));verify(a,fresh,actual);if(!row.equals(actual)||!basis.graph().sha256().equals(fresh.graph().sha256()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
    }
    private static void active(EnterpriseAccess.Admission a,String run,String task){if(!TransactionSynchronizationManager.isActualTransactionActive()||a.simulationWorker()||a.recoveryWorker())deny();if(a.controlWorker()){a.controlProof().requireActive();if(task!=null)a.controlProof().requireBootstrap(task,run);}else if(a.formalWorker()){var i=a.formalProof().identity();if(!"TASK".equals(i.executionKind())||!run.equals(i.run()))deny();}else deny();}
    private JsonNode read(String value)throws Exception{return reader.readTree(value);}private String hash(JsonNode n)throws java.io.IOException{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(n).getBytes(StandardCharsets.UTF_8));}
    private void insert(String table,LinkedHashMap<String,Object> values){changed(db.update("INSERT INTO "+table+"("+String.join(",",values.keySet())+") VALUES("+String.join(",",Collections.nCopies(values.size(),"?"))+")",values.values().toArray()));}
    private static String s(Map<String,Object> row,String key){Object v=row.get(key);if(v==null)bad();return String.valueOf(v);}private static String id(){return UUID.randomUUID().toString();}
    private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}private static void changed(int count){if(count!=1)bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
