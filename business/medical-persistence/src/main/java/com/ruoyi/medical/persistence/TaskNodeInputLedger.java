package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

/** Immutable physical input operation. Only a private, currently approved producer can write.
 * Commit the attempt before transport. A prior/uncertain attempt always means GET recovery. */
public final class TaskNodeInputLedger {
    static final String OPERATION="task.node.input.register";
    private final JdbcTemplate db; private final TaskNodeInputSchema schema; private final PermissionSchema json;
    public TaskNodeInputLedger(DataSource data,TaskNodeInputSchema schema,PermissionSchema json){this(new JdbcTemplate(data),schema,json);}
    TaskNodeInputLedger(JdbcTemplate db,TaskNodeInputSchema schema,PermissionSchema json){this.db=db;db.setQueryTimeout(5);this.schema=schema;this.json=json;}
    record Row(Map<String,Object> columns,JsonNode intent){
        Row {columns=Collections.unmodifiableMap(new LinkedHashMap<>(columns));intent=intent.deepCopy();}
        @Override public JsonNode intent(){return intent.deepCopy();}
    }
    public static final class Attempt implements RuntimeObjectAttempt {
        private final TaskNodeInputLedger owner; private final Row row; private final String attempt; private final boolean prior;
        private final java.util.concurrent.atomic.AtomicBoolean claimed=new java.util.concurrent.atomic.AtomicBoolean();
        private final Thread thread=Thread.currentThread();private volatile boolean committed;
        private Attempt(TaskNodeInputLedger owner,Row row,String attempt,boolean prior){this.owner=owner;this.row=row;this.attempt=attempt;this.prior=prior;
            if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.isSynchronizationActive())denied();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){committed=true;}
                @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)committed=false;}
            });
        }
        public String tenantId(){return text(row.columns(),"tenant_id");} public String actorId(){return text(row.columns(),"created_by");}
        public String expectedObjectKey(){return objectKey(tenantId(),runId(),writeIntent().writeId(),artifactId());}
        public S3ObjectWriter.WriteIntent writeIntent(){return new S3ObjectWriter.WriteIntent(text(row.columns(),"write_id"),text(row.intent(),"object_key"),text(row.columns(),"payload_sha256"),Integer.toString(bytes().length),"application/json");}
        public byte[] bytes(){return text(row.columns(),"canonical_payload").getBytes(StandardCharsets.UTF_8);}
        public boolean priorAttempt(){return prior;} public TransferMode claimTransferMode(){if(!committed||thread!=Thread.currentThread()||TransactionSynchronizationManager.isActualTransactionActive())denied();return prior||!claimed.compareAndSet(false,true)?TransferMode.RECOVER_ONLY:TransferMode.INITIAL_WRITE;}
        public String runId(){return text(row.columns(),"run_id");} public String stepId(){return text(row.columns(),"step_id");}
        public String artifactId(){return text(row.columns(),"artifact_id");} public String operationId(){return text(row.columns(),"operation_id");}
        public String attemptId(){return attempt;}
    }
    public Attempt prepare(TaskNodeInputAuthority.Prepared proof,String key)throws Exception {
        proof.requireUnchanged();var a=proof.bootstrap().admission();current(a);key(key);
        var originals=rows(a,key);Row row;
        if(originals.isEmpty()){
            if(!db.queryForList("SELECT write_id FROM task_node_input_intent WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2",a.context().tenant_id(),proof.input().runId(),proof.input().stepId()).isEmpty())throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            if(!operations(a,key).isEmpty())bad();row=create(proof,key);
        }else row=verify(a,one(originals));
        requireProof(row,proof);var result=attempt(a,row,true);proof.requireUnchanged();return result;
    }
    public Attempt recover(TaskNodeInputAuthority.Prepared proof,String key)throws Exception {
        proof.requireUnchanged();var a=proof.bootstrap().admission();current(a);key(key);var row=verify(a,one(rows(a,key)));requireProof(row,proof);return attempt(a,row,false);
    }
    Row require(TaskNodeInputAuthority.Prepared proof,Attempt attempt)throws Exception {
        if(attempt==null||!attempt.committed||attempt.thread!=Thread.currentThread())denied();
        proof.requireUnchanged();var row=requireStored(proof.bootstrap().admission(),attempt);requireProof(row,proof);return row;
    }
    public void requireTransfer(TaskNodeInputAuthority.Prepared proof,Attempt attempt)throws Exception {
        var row=require(proof,attempt);
        if(!attempt.priorAttempt()&&!now().isBefore(time(row.intent(),"deadline")))throw new TenantAccessException(ErrorCode.ACTION_STALE);
        proof.requireUnchanged();
    }
    Row requireStored(EnterpriseAccess.Admission a,Attempt expected)throws Exception {
        current(a);if(expected==null||expected.owner!=this||!a.actorId().equals(expected.actorId()))denied();
        var row=verify(a,one(rows(a,text(expected.row.columns(),"idempotency_key"))));
        if(!row.columns().equals(expected.row.columns())||!attempt(a,row,false).attemptId().equals(expected.attemptId()))bad();return row;
    }
    Attempt stored(EnterpriseAccess.Admission a,String run,String step)throws Exception {
        current(a);var row=verify(a,one(db.queryForList("SELECT * FROM task_node_input_intent WHERE tenant_id=? AND run_id=? AND step_id=? AND created_by=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),run,step,a.actorId())));return attempt(a,row,false);
    }
    private Row create(TaskNodeInputAuthority.Prepared p,String key)throws Exception {
        var b=p.bootstrap();var input=p.input();var now=now();String write=id(),operation=id(),artifact=id(),audit=id(),event=id();
        var row=metadata(b.tenantId(),b.projectId(),b.actorId(),b.classification(),now,true);
        row.putAll(Map.of("write_id",write,"operation_id",operation,"artifact_id",artifact,"task_id",input.taskId(),"run_id",input.runId(),"step_id",input.stepId(),"graph_id",input.graphId(),"graph_sha256",input.graphSha256(),"question_id",input.questionId()));
        row.putAll(Map.of("plan_revision",input.planRevision(),"plan_sha256",input.planSha256(),"snapshot_id",input.snapshotId(),"snapshot_sha256",input.snapshotSha256(),"producer_rule_id",p.producer().ruleId(),"producer_sha256",p.producer().ruleSha256(),"idempotency_key",key));
        row.put("producer_decision_id",p.producer().decisionId());row.put("producer_decision_sha256",p.producer().decisionSha256());
        var original=json.object();original.set("input",schema.tree(input));original.set("command",b.command());original.set("producer",p.producer().authorizations());
        String payload=new String(schema.bytes(input),StandardCharsets.UTF_8),hash=WireValues.sha256(schema.bytes(input));
        var intent=json.object();for(var e:row.entrySet())if(!e.getKey().equals("created_at"))intent.set(e.getKey(),json.tree(e.getValue()));
        intent.put("created_at",instant(now)).put("deadline",instant(now.plusMinutes(5))).put("audit_id",audit).put("event_id",event)
            .put("object_key",objectKey(b.tenantId(),b.runId(),write,artifact)).put("payload_sha256",hash).put("payload_size",Integer.toString(payload.getBytes(StandardCharsets.UTF_8).length))
            .put("request_sha256",schema.hash(original)).put("authority_sha256",b.authorizationsSha256());
        intent.set("original_request",original);intent.set("content_roots",json.tree(p.content().roots()));intent.set("content_members",json.tree(p.content().members()));
        row.put("request_sha256",schema.hash(original));row.put("canonical_intent",schema.canonical(intent));row.put("intent_sha256",schema.hash(intent));
        row.put("canonical_payload",payload);row.put("payload_sha256",hash);row.put("audit_id",audit);row.put("event_id",event);
        changed(db.update("INSERT INTO operation_record(tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,http_status,response_ref,target_id,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,'PROCESSING',202,NULL,?,'1.0.0',?,?,?,?,1,?)",b.tenantId(),operation,b.actorId(),OPERATION,key,row.get("request_sha256"),artifact,schema.manifest(),b.actorId(),now,b.classification(),now));
        var result=new Row(row,intent);journal(result,"task.node.input.prepared",audit,event,write,1,anchor(result),now);insert("task_node_input_intent",row);return verify(b.admission(),one(rows(b.admission(),key)));
    }
    private Attempt attempt(EnterpriseAccess.Admission a,Row row,boolean create)throws Exception {
        var c=row.columns();var attempts=db.queryForList("SELECT * FROM task_node_input_attempt WHERE tenant_id=? AND write_id=? LIMIT 2 FOR UPDATE",c.get("tenant_id"),c.get("write_id"));var op=one(operations(a,text(c,"idempotency_key")));
        if(attempts.isEmpty()){
            if(!create||!"PROCESSING".equals(op.get("state"))||number(op,"revision")!=1)bad();var now=now();if(!now.isBefore(time(row.intent(),"deadline")))throw new TenantAccessException(ErrorCode.ACTION_STALE);
            String attempt=id(),audit=id(),event=id();var record=metadata(text(c,"tenant_id"),text(c,"project_id"),text(c,"created_by"),text(c,"classification"),now,true);
            for(String f:List.of("write_id","operation_id","artifact_id","task_id","run_id","step_id","intent_sha256"))record.put(f,c.get(f));
            record.put("attempt_id",attempt);record.put("audit_id",audit);record.put("event_id",event);journal(row,"task.node.input.attempted",audit,event,text(c,"write_id"),2,attemptAnchor(row,attempt,now),now);insert("task_node_input_attempt",record);
            changed(db.update("UPDATE operation_record SET revision=2,updated_at=? WHERE tenant_id=? AND operation_id=? AND subject_id=? AND operation_name=? AND state='PROCESSING' AND revision=1",now,c.get("tenant_id"),c.get("operation_id"),c.get("created_by"),OPERATION));
            var checked=attempt(a,row,false);return new Attempt(this,row,checked.attemptId(),false);
        }
        var record=one(attempts);verifyMetadata(row,record);for(String f:List.of("write_id","operation_id","artifact_id","task_id","run_id","step_id","intent_sha256"))if(!c.get(f).equals(record.get(f)))bad();
        var at=time(record,"created_at");if(at.isBefore(time(c,"created_at"))||!at.isBefore(time(row.intent(),"deadline"))||at.isAfter(now())||number(op,"revision")<2||time(op,"updated_at").isBefore(at))bad();
        requireJournal(row,"task.node.input.attempted",text(record,"audit_id"),text(record,"event_id"),text(c,"write_id"),2,attemptAnchor(row,text(record,"attempt_id"),at),at);return new Attempt(this,row,text(record,"attempt_id"),true);
    }
    private Row verify(EnterpriseAccess.Admission a,Map<String,Object> c)throws Exception {
        var n=schema.readTree(text(c,"canonical_intent"));var row=new Row(c,n);if(!a.context().tenant_id().equals(c.get("tenant_id"))||!a.actorId().equals(c.get("created_by")))denied();
        if(!n.isObject()||n.size()!=36||!schema.canonical(n).equals(c.get("canonical_intent"))||!schema.hash(n).equals(c.get("intent_sha256"))||!schema.manifest().equals(c.get("schema_manifest_sha256"))||!"1.0.0".equals(c.get("schema_version")))bad();
        for(String f:List.of("tenant_id","project_id","created_by","classification","schema_version","schema_manifest_sha256","write_id","operation_id","artifact_id","task_id","run_id","step_id","graph_id","graph_sha256","question_id","plan_revision","plan_sha256","snapshot_id","snapshot_sha256","producer_rule_id","producer_sha256","producer_decision_id","producer_decision_sha256","idempotency_key","request_sha256","payload_sha256","audit_id","event_id"))if(!text(c,f).equals(text(n,f)))bad();
        if(!instant(time(c,"created_at")).equals(text(n,"created_at"))||!time(c,"created_at").plusMinutes(5).equals(time(n,"deadline"))||time(c,"created_at").isAfter(now()))bad();
        byte[] bytes=text(c,"canonical_payload").getBytes(StandardCharsets.UTF_8);var input=schema.read(bytes);
        if(!Arrays.equals(bytes,schema.bytes(input))||!WireValues.sha256(bytes).equals(c.get("payload_sha256"))||!Integer.toString(bytes.length).equals(text(n,"payload_size"))||!schema.tree(input).equals(n.required("original_request").required("input"))||!schema.hash(n.required("original_request")).equals(c.get("request_sha256")))bad();
        for(String f:List.of("tenant_id","project_id","task_id","run_id","step_id","graph_id","graph_sha256","question_id","plan_revision","plan_sha256","snapshot_id","snapshot_sha256"))if(!text(n,f).equals(text(schema.tree(input),f)))bad();
        if(!objectKey(input.tenantId(),input.runId(),text(c,"write_id"),text(c,"artifact_id")).equals(text(n,"object_key")))bad();
        var op=one(operations(a,text(c,"idempotency_key")));verifyMetadata(row,op);
        for(String f:List.of("operation_id","idempotency_key","request_sha256"))if(!c.get(f).equals(op.get(f)))bad();
        if(!c.get("created_by").equals(op.get("subject_id"))||!c.get("artifact_id").equals(op.get("target_id"))||!OPERATION.equals(op.get("operation_name"))||!time(c,"created_at").equals(time(op,"created_at"))||!Set.of("PROCESSING","COMPLETED").contains(op.get("state"))||number(op,"revision")<1||number(op,"revision")>3)bad();
        boolean completed="COMPLETED".equals(op.get("state"));
        if(completed && (number(op,"revision")!=3 || number(op,"http_status")!=201 || op.get("response_ref")==null)
                || !completed && (number(op,"revision")>2 || number(op,"http_status")!=202 || op.get("response_ref")!=null)
                || op.get("error_code")!=null || time(op,"updated_at").isBefore(time(c,"created_at")) || time(op,"updated_at").isAfter(now()))bad();
        requireJournal(row,"task.node.input.prepared",text(c,"audit_id"),text(c,"event_id"),text(c,"write_id"),1,anchor(row),time(c,"created_at"));return row;
    }
    private void requireProof(Row row,TaskNodeInputAuthority.Prepared p)throws Exception {
        p.requireUnchanged();var c=row.columns();var b=p.bootstrap();
        if(!b.tenantId().equals(c.get("tenant_id"))||!b.projectId().equals(c.get("project_id"))||!b.actorId().equals(c.get("created_by"))||!b.classification().equals(c.get("classification"))||!Arrays.equals(schema.bytes(p.input()),text(c,"canonical_payload").getBytes(StandardCharsets.UTF_8)))bad();
        var original=row.intent().required("original_request");if(!schema.canonical(b.command()).equals(schema.canonical(original.required("command")))||!schema.canonical(p.producer().authorizations()).equals(schema.canonical(original.required("producer")))||!p.producer().ruleId().equals(c.get("producer_rule_id"))||!p.producer().ruleSha256().equals(c.get("producer_sha256"))||!p.producer().decisionId().equals(c.get("producer_decision_id"))||!p.producer().decisionSha256().equals(c.get("producer_decision_sha256"))||!b.authorizationsSha256().equals(text(row.intent(),"authority_sha256"))||!json.tree(p.content().roots()).equals(row.intent().required("content_roots"))||!json.tree(p.content().members()).equals(row.intent().required("content_members")))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
    }
    private List<Map<String,Object>> rows(EnterpriseAccess.Admission a,String key){return db.queryForList("SELECT * FROM task_node_input_intent WHERE tenant_id=? AND created_by=? AND idempotency_key=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),a.actorId(),key);}
    private List<Map<String,Object>> operations(EnterpriseAccess.Admission a,String key){return db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),a.actorId(),OPERATION,key);}
    private String anchor(Row row)throws Exception{return schema.canonical(Map.of("schema_version","1.0.0","kind","TASK_NODE_INPUT_PREPARED","operation_id",row.columns().get("operation_id"),"write_id",row.columns().get("write_id"),"intent_sha256",row.columns().get("intent_sha256"),"payload_sha256",row.columns().get("payload_sha256")));}
    private String attemptAnchor(Row row,String attempt,LocalDateTime at)throws Exception{var n=(ObjectNode)schema.readTree(anchor(row));n.put("kind","TASK_NODE_INPUT_ATTEMPTED").put("attempt_id",attempt).put("attempted_at",instant(at));return schema.canonical(n);}
    void journal(Row row, String action, String audit, String event, String aggregate, int revision, String body, LocalDateTime now) {
        var c = row.columns();
        changed(db.update("INSERT INTO audit_event (tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",
                c.get("tenant_id"), c.get("project_id"), audit, c.get("created_by"), action, body, c.get("operation_id"),
                c.get("write_id"), schema.manifest(), c.get("created_by"), now, c.get("classification")));
        changed(db.update("INSERT INTO outbox_event (tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES (?,?,?,?,?,?,?)",
                c.get("tenant_id"), event, aggregate, revision, action, body, now));
    }

    void requireJournal(Row row, String action, String audit, String event, String aggregate, int revision, String body,
            LocalDateTime now) throws Exception {
        var c = row.columns();
        var a = one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2", c.get("tenant_id"),
                audit));
        var e = one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2", c.get("tenant_id"),
                event));
        verifyMetadata(row, a);
        if (!c.get("project_id").equals(a.get("project_id")) || !c.get("created_by").equals(a.get("subject_id"))
                || !action.equals(a.get("action")) || !"SUCCESS".equals(a.get("result")) || a.get("reason_code") != null
                || !c.get("operation_id").equals(a.get("request_id")) || !c.get("write_id").equals(a.get("trace_id"))
                || !now.equals(time(a, "created_at")) || !body.equals(schema.canonical(schema.readTree(text(a, "resource_ref"))))) bad();
        if (!aggregate.equals(e.get("aggregate_id")) || number(e, "aggregate_revision") != revision || !action.equals(e.get("event_type"))
                || !now.equals(time(e, "occurred_at")) || !body.equals(schema.canonical(schema.readTree(text(e, "payload"))))) bad();
    }

    void verifyMetadata(Row row, Map<String, Object> actual) {
        for (String f : List.of("tenant_id", "created_by", "classification", "schema_version", "schema_manifest_sha256")) if (!row.columns().get(f).equals(actual.get(f))) bad();
        if (actual.containsKey("project_id") && !row.columns().get("project_id").equals(actual.get("project_id"))) bad();
    }

    Map<String, Object> metadata(String tenant, String project, String actor, String classification, LocalDateTime now,
            boolean schemaFields) {
        var m = new LinkedHashMap<String, Object>();
        m.put("tenant_id", tenant);
        m.put("project_id", project);
        m.put("created_by", actor);
        m.put("created_at", now);
        m.put("classification", classification);
        if (schemaFields) {
            m.put("schema_version", "1.0.0");
            m.put("schema_manifest_sha256", schema.manifest());
        }

        return m;
    }

    void insert(String table, Map<String, Object> m) {
        var keys = new ArrayList<>(m.keySet());
        // Map.of iteration order can vary between JVMs. Keep SQL and its bound values
        // in the same stable column order for reproducible registration evidence.
        Collections.sort(keys);
        String columns = String.join(",", keys);
        String placeholders = String.join(",", Collections.nCopies(keys.size(), "?"));
        Object[] values = keys.stream().map(m::get).toArray();
        changed(db.update("INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")", values));
    }

    LocalDateTime now() {
        return one(db.query("SELECT UTC_TIMESTAMP(6)", (r, n) -> r.getTimestamp(1).toLocalDateTime()));
    }

    static String objectKey(String tenant, String execution, String write, String artifact) {
        for (String id : List.of(tenant, execution, write, artifact)) WireValues.uuid(id);
        return "runtime-input/" + tenant + "/" + execution + "/" + write + "/" + artifact;
    }

    static String text(Map<String, Object> v, String k) {
        if (v.get(k) instanceof String s) return s;
        bad();
        return null;
    }

    static String text(JsonNode n, String k) {
        var v = n.required(k);
        if (v.isTextual()) return v.textValue();
        bad();
        return null;
    }

    static LocalDateTime time(Map<String, Object> v, String k) {
        var x = v.get(k);
        if (x instanceof LocalDateTime t) return t;
        if (x instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        bad();
        return null;
    }

    static LocalDateTime time(JsonNode n, String k) {
        return LocalDateTime.ofInstant(Instant.parse(text(n, k)), ZoneOffset.UTC);
    }

    static long number(Map<String, Object> m, String k) {
        if (m.get(k) instanceof Number n) return new java.math.BigDecimal(n.toString()).longValueExact();
        bad();
        return 0;
    }

    static String instant(LocalDateTime t) {
        return t.toInstant(ZoneOffset.UTC).toString();
    }

    static String id() {
        return UUID.randomUUID().toString();
    }

    static <T > T one(List<T> v) {
        if (v.size() != 1 || v.get(0) == null) bad();
        return v.get(0);
    }

    static void changed(int n) {
        if (n != 1) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
    }

    static void current(EnterpriseAccess.Admission a) {
        if(a == null || (!a.controlWorker() && !a.formalWorker()) || !TransactionSynchronizationManager.isActualTransactionActive()) denied();
        if(a.controlWorker()) a.controlProof().requireActive(); else a.formalProof().requireActive();
        var scope=TenantScope.requireCurrent();if(!scope.tenantId().equals(a.context().tenant_id())||!scope.subjectId().equals(a.actorId()))denied();
    }

    static void key(String value) { if(value==null||!value.matches("[!-~]{16,128}")) throw new IllegalArgumentException("IDEMPOTENCY_KEY_REQUIRED"); }

    static void bad() {
        throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    }

    static void denied() {
        throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
}
