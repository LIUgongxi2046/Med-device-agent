package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;

/** Immutable N04 intent, single initial PUT attempt, complete physical readback and registration.
 * Unknown transfer outcomes are recovered at the same key, never replaced by another write. */
public final class TaskSectionWriterObjectLedger {
    private final JdbcTemplate db;private final TaskSectionWriterSchema json;private final DependencyManifestSchema dependencies;
    private final TaskSectionWriterInputAuthority inputs;
    public TaskSectionWriterObjectLedger(DataSource data,TaskSectionWriterSchema json,DependencyManifestSchema dependencies,TaskSectionWriterInputAuthority inputs){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.dependencies=dependencies;this.inputs=inputs;}
    public Attempt prepareInput(TaskSectionWriterInputAuthority.Prepared input,TaskSectionWriterProducerAuthority.Prepared producer)throws Exception {
        input.requireUnchanged();producer.requireUnchanged();producer.requireBytes("TASK_SECTION_WRITER_INPUT",input.bytes().length);
        return prepare(new Payload(input,producer,null,null,"TASK_SECTION_WRITER_INPUT",input.bytes(),input.input().inputId(),input.input().inputId(),producer.canonicalPins()));
    }
    Attempt prepareProduced(TaskSectionWriterCandidateService.Produced produced,TaskSectionWriterProducerAuthority.Verified producer)throws Exception {
        produced.requireUnchanged();producer.requireFor(produced.input());producer.requireBytes(produced.kind(),produced.bytes().length);
        return prepare(new Payload(null,null,produced,producer,produced.kind(),produced.bytes(),"SECTION_CANDIDATE".equals(produced.kind())?TaskSectionWriterFinishOrchestrator.plannedCandidate(produced.actionId()):null,produced.actionId(),producer.canonicalPins()));
    }
    Attempt prepareEnvelope(TaskSectionWriterRuntimeAdapter.Envelope envelope,TaskSectionWriterProducerAuthority.Verified producer)throws Exception{return prepare(new Payload(envelope,producer));}
    void requireEnvelopeTransfer(TaskSectionWriterRuntimeAdapter.Envelope envelope,TaskSectionWriterProducerAuthority.Verified producer,Attempt attempt)throws Exception{requireAttempt(new Payload(envelope,producer),attempt);}
    Registered registerEnvelope(TaskSectionWriterRuntimeAdapter.Envelope envelope,TaskSectionWriterProducerAuthority.Verified producer,Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception{return register(new Payload(envelope,producer),attempt,physical);}
    private Attempt prepare(Payload p)throws Exception {
        p.require();var i=p.input();String digest=WireValues.sha256(p.bytes);var rows=db.queryForList("SELECT * FROM task_section_writer_object_intent WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? AND kind=? LIMIT 2 FOR UPDATE",i.tenantId(),i.runId(),i.stepId(),p.action,p.kind);
        boolean prior=!rows.isEmpty();Map<String,Object> row;
        if(prior){row=one(rows);if(!digest.equals(str(row,"payload_sha256"))||!json.canonical(p.pins).equals(str(row,"producer_pins_json")))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);}
        else {
            String write=id(),artifact=p.artifact==null?id():p.artifact;var at=now();String key="tenants/"+i.tenantId()+"/task-writer/"+i.runId()+"/"+i.stepId()+"/"+write+"/"+artifact+".json";
            var intent=json.tree(Map.of("tenant_id",i.tenantId(),"run_id",i.runId(),"step_id",i.stepId(),"action_id",p.action,"kind",p.kind,"write_id",write,"artifact_id",artifact,"payload_sha256",digest,"producer_pins",p.pins,"object_key",key));
            row=meta(p,at);row.putAll(Map.of("task_id",i.taskId(),"run_id",i.runId(),"step_id",i.stepId(),"action_id",p.action,"kind",p.kind,"write_id",write,"artifact_id",artifact,"object_key",key,"payload_sha256",digest));
            row.put("bound_action_id",p.prepared==null?p.action:null);row.put("canonical_payload",new String(p.bytes,StandardCharsets.UTF_8));row.put("producer_pins_json",json.canonical(p.pins));row.put("intent_json",json.canonical(intent));row.put("intent_sha256",json.hash(intent));insert("task_section_writer_object_intent",row);
        }
        verifyIntent(p,row);var attempts=db.queryForList("SELECT * FROM task_section_writer_object_attempt WHERE tenant_id=? AND write_id=? LIMIT 2 FOR UPDATE",i.tenantId(),str(row,"write_id"));
        if(attempts.isEmpty()){if(prior)bad();var attempt=meta(p,now());attempt.put("write_id",str(row,"write_id"));attempt.put("intent_sha256",str(row,"intent_sha256"));insert("task_section_writer_object_attempt",attempt);}
        else {var a=one(attempts);if(!str(row,"intent_sha256").equals(str(a,"intent_sha256"))||!p.actor().equals(str(a,"created_by")))bad();prior=true;}
        var result=new Attempt(p,row,prior);p.require();return result;
    }
    public void requireTransfer(TaskSectionWriterInputAuthority.Prepared input,TaskSectionWriterProducerAuthority.Prepared producer,Attempt attempt)throws Exception {
        input.requireUnchanged();producer.requireUnchanged();var p=new Payload(input,producer,null,null,"TASK_SECTION_WRITER_INPUT",input.bytes(),input.input().inputId(),input.input().inputId(),producer.canonicalPins());requireAttempt(p,attempt);
    }
    void requireTransfer(TaskSectionWriterCandidateService.Produced produced,TaskSectionWriterProducerAuthority.Verified producer,Attempt attempt)throws Exception {
        produced.requireUnchanged();producer.requireFor(produced.input());requireAttempt(new Payload(null,null,produced,producer,produced.kind(),produced.bytes(),"SECTION_CANDIDATE".equals(produced.kind())?TaskSectionWriterFinishOrchestrator.plannedCandidate(produced.actionId()):null,produced.actionId(),producer.canonicalPins()),attempt);
    }
    private void requireAttempt(Payload p,Attempt attempt)throws Exception {
        p.require();if(attempt==null||attempt.owner!=this||!attempt.committed||attempt.thread!=Thread.currentThread())deny();
        var row=one(db.queryForList("SELECT * FROM task_section_writer_object_intent WHERE tenant_id=? AND write_id=? LIMIT 2 FOR UPDATE",p.input().tenantId(),attempt.writeIntent().writeId()));verifyIntent(p,row);
        if(!str(row,"intent_sha256").equals(str(attempt.row,"intent_sha256"))||!str(row,"artifact_id").equals(attempt.artifactId()))bad();
        var a=one(db.queryForList("SELECT intent_sha256,created_by FROM task_section_writer_object_attempt WHERE tenant_id=? AND write_id=? LIMIT 2 FOR UPDATE",p.input().tenantId(),str(row,"write_id")));
        if(!str(row,"intent_sha256").equals(str(a,"intent_sha256"))||!p.actor().equals(str(a,"created_by")))bad();p.require();
    }
    private void verifyIntent(Payload p,Map<String,Object> row)throws Exception {
        var i=p.input();if(!i.tenantId().equals(str(row,"tenant_id"))||!i.projectId().equals(str(row,"project_id"))||!i.runId().equals(str(row,"run_id"))||!i.stepId().equals(str(row,"step_id"))
                ||!p.action.equals(str(row,"action_id"))||!p.kind.equals(str(row,"kind"))||!p.actor().equals(str(row,"created_by"))||!WireValues.sha256(p.bytes).equals(str(row,"payload_sha256"))
                ||!Arrays.equals(p.bytes,str(row,"canonical_payload").getBytes(StandardCharsets.UTF_8))||!json.canonical(p.pins).equals(str(row,"producer_pins_json")))bad();
        var intent=json.read(str(row,"intent_json"));if(!json.hash(intent).equals(str(row,"intent_sha256")))bad();
        for(String f:List.of("tenant_id","run_id","step_id","action_id","kind","write_id","artifact_id","payload_sha256","object_key"))if(!str(row,f).equals(intent.required(f).textValue()))bad();
        if(!p.pins.equals(intent.required("producer_pins"))||!p.schemaHash().equals(str(row,"schema_manifest_sha256")))bad();
    }
    public TaskSectionWriterInputAuthority.Verified registerInput(TaskSectionWriterInputAuthority.Prepared input,TaskSectionWriterProducerAuthority.Prepared producer,
            Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception {
        var p=new Payload(input,producer,null,null,"TASK_SECTION_WRITER_INPUT",input.bytes(),input.input().inputId(),input.input().inputId(),producer.canonicalPins());var registered=register(p,attempt,physical);
        var i=input.input();var rows=db.queryForList("SELECT * FROM task_section_writer_input WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",i.tenantId(),i.runId(),i.stepId());
        if(rows.isEmpty()){
            var row=meta(p,now());row.putAll(Map.of("task_id",i.taskId(),"run_id",i.runId(),"step_id",i.stepId(),"input_id",i.inputId(),"input_sha256",json.hash(i),"object_version",registered.reference().objectVersion()));
            row.put("audit_id",registered.receipt().required("audit_id").textValue());row.put("event_id",registered.receipt().required("event_id").textValue());row.put("canonical_input",json.canonical(i));row.put("baseline_json",json.canonical(input.baselineBody()));row.put("receipt_json",json.canonical(registered.receipt()));row.put("receipt_sha256",registered.receiptSha256());
            row.put("producer_pins_json",json.canonical(p.pins));for(String f:List.of("producer_rule_id","producer_sha256","producer_decision_id","producer_decision_sha256"))row.put(f,p.pins.required(f).textValue());insert("task_section_writer_input",row);
        }
        var row=one(db.queryForList("SELECT * FROM task_section_writer_input WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",i.tenantId(),i.runId(),i.stepId()));
        return inputs.registered(input,row);
    }
    Registered registerProduced(TaskSectionWriterCandidateService.Produced produced,TaskSectionWriterProducerAuthority.Verified producer,Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception {
        return register(new Payload(null,null,produced,producer,produced.kind(),produced.bytes(),"SECTION_CANDIDATE".equals(produced.kind())?TaskSectionWriterFinishOrchestrator.plannedCandidate(produced.actionId()):null,produced.actionId(),producer.canonicalPins()),attempt,physical);
    }
    private Registered register(Payload p,Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception {
        requireAttempt(p,attempt);if(physical==null||physical.originalAttempt()!=attempt||!attempt.writeIntent().sha256().equals(physical.rawSha256()))bad();var i=p.input();
        var existing=db.queryForList("SELECT * FROM task_section_writer_object WHERE tenant_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",i.tenantId(),attempt.artifactId());
        if(!existing.isEmpty())return verified(p,attempt,physical,one(existing));
        String artifact=attempt.artifactId(),access=plannedAccessSet(i,p.action,p.kind),audit=id(),event=id();var at=now();var object=physical.reference();var members=p.members().stream().sorted(Comparator.comparing(ArtifactRegistryProtocol.MemberRow::upstreamVersionId)).toList();
        var manifest=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",i.tenantId(),i.projectId(),access,members);String manifestHash=json.hash(manifest);
        version(p,access,"DEPENDENCY_ACCESS_SET",manifestHash);version(p,artifact,"ARTIFACT",object.sha256());
        var set=meta(p,at);set.put("access_set_id",access);set.put("manifest_sha256",manifestHash);set.put("content_sha256",manifestHash);removeSchema(set);insert("dependency_access_set",set);
        var full=meta(p,at);full.put("schema_manifest_sha256",dependencies.manifestSha256());full.put("access_set_id",access);full.put("manifest_json",json.canonical(manifest));full.put("manifest_sha256",manifestHash);full.put("content_sha256",manifestHash);full.put("member_count",members.size());insert("dependency_access_manifest",full);
        for(var member:members){var m=meta(p,at);removeSchema(m);m.remove("project_id");m.put("access_set_id",access);m.put("upstream_version_id",member.upstreamVersionId());m.put("rights_revision",member.rightsRevision());insert("dependency_access_member",m);}
        var ar=meta(p,at);ar.putAll(Map.of("artifact_id",artifact,"run_id",i.runId(),"artifact_kind",p.kind,"object_ref",StoredObjectReferences.encode(object),"object_key",object.objectKey(),"object_version",object.objectVersion(),"sha256",object.sha256(),"size_bytes",object.sizeBytes(),"media_type",object.mediaType(),"dependency_access_set_id",access));ar.put("content_sha256",object.sha256());ar.put("document_revision",null);insert("artifact",ar);
        var binding=meta(p,at);removeSchema(binding);binding.putAll(Map.of("artifact_id",artifact,"resource_kind","TASK","resource_id",i.taskId(),"schema_ref",p.schemaRef(),"schema_document_sha256",p.schemaHash(),"dependency_access_set_id",access,"dependency_manifest_sha256",manifestHash));insert("artifact_content_binding",binding);
        for(String root:p.roots())insert("lineage_edge",Map.of("tenant_id",i.tenantId(),"consumer_version_id",artifact,"upstream_version_id",root,"relation_kind","SECTION_WRITER_SOURCE","actor_id",p.actor(),"created_at",at));
        var receipt=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("tenant_id",i.tenantId()).put("project_id",i.projectId()).put("run_id",i.runId()).put("step_id",i.stepId())
                .put("kind",p.kind).put("action_id",p.action).put("artifact_id",artifact).put("write_id",attempt.writeIntent().writeId()).put("intent_sha256",str(attempt.row,"intent_sha256")).put("input_sha256",json.hash(i))
                .put("payload_sha256",object.sha256()).put("producer_pins_sha256",json.hash(p.pins)).put("audit_id",audit).put("event_id",event).put("created_at",at.toInstant(ZoneOffset.UTC).toString());
        receipt.set("object_ref",json.tree(object));receipt.set("dependency_manifest",json.tree(manifest));receipt.set("content_roots",json.tree(p.roots()));receipt.set("provenance",p.provenance());
        String raw=json.canonical(receipt),hash=json.hash(receipt);journal(p,audit,event,artifact,raw,at);
        var row=meta(p,at);row.putAll(Map.of("task_id",i.taskId(),"run_id",i.runId(),"step_id",i.stepId(),"action_id",p.action,"kind",p.kind,"artifact_id",artifact,"write_id",attempt.writeIntent().writeId(),"state","REGISTERED","payload_sha256",object.sha256(),"object_version",object.objectVersion()));
        row.put("bound_action_id",p.prepared==null?p.action:null);row.put("audit_id",audit);row.put("event_id",event);row.put("producer_rule_id",p.pins.required("producer_rule_id").textValue());row.put("canonical_payload",new String(p.bytes,StandardCharsets.UTF_8));row.put("receipt_json",raw);row.put("receipt_sha256",hash);insert("task_section_writer_object",row);
        return verified(p,attempt,physical,one(db.queryForList("SELECT * FROM task_section_writer_object WHERE tenant_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",i.tenantId(),artifact)));
    }
    private Registered verified(Payload p,Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical,Map<String,Object> row)throws Exception {
        requireAttempt(p,attempt);var receipt=json.read(str(row,"receipt_json"));if(!json.hash(receipt).equals(str(row,"receipt_sha256"))||!p.kind.equals(str(row,"kind"))||!attempt.artifactId().equals(str(row,"artifact_id"))
                ||!physical.reference().objectVersion().equals(str(row,"object_version"))||!physical.rawSha256().equals(str(row,"payload_sha256"))||!Arrays.equals(p.bytes,str(row,"canonical_payload").getBytes(StandardCharsets.UTF_8))
                ||!json.tree(physical.reference()).equals(receipt.required("object_ref"))||!json.hash(p.pins).equals(receipt.required("producer_pins_sha256").textValue())||!p.provenance().equals(receipt.required("provenance")))bad();
        var audit=one(db.queryForList("SELECT resource_ref,result,subject_id FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",p.input().tenantId(),receipt.required("audit_id").textValue()));
        if(!"SUCCESS".equals(str(audit,"result"))||!p.actor().equals(str(audit,"subject_id"))||!receipt.equals(json.read(str(audit,"resource_ref"))))bad();
        var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",p.input().tenantId(),receipt.required("event_id").textValue()));if(!receipt.equals(json.read(str(event,"payload"))))bad();
        var ar=one(db.queryForList("SELECT object_ref,sha256,artifact_kind,dependency_access_set_id FROM artifact WHERE tenant_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",p.input().tenantId(),attempt.artifactId()));
        StoredObjectReferences.decode(str(ar,"object_ref"),physical.reference());if(!p.kind.equals(str(ar,"artifact_kind"))||!physical.rawSha256().equals(str(ar,"sha256"))||!str(ar,"dependency_access_set_id").equals(receipt.required("dependency_manifest").required("access_set_id").textValue()))bad();
        var binding=one(db.queryForList("SELECT schema_ref,schema_document_sha256 FROM artifact_content_binding WHERE tenant_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",p.input().tenantId(),attempt.artifactId()));
        if(!p.schemaRef().equals(str(binding,"schema_ref"))||!p.schemaHash().equals(str(binding,"schema_document_sha256"))||!p.schemaHash().equals(str(row,"schema_manifest_sha256")))bad();
        p.require();return new Registered(p,attempt,physical,receipt,str(row,"receipt_sha256"));
    }
    public final class Registered {
        private final Payload payload;private final Attempt attempt;private final RuntimeObjectTransport.VerifiedTransfer transfer;private final JsonNode receipt;private final String digest;
        private Registered(Payload p,Attempt a,RuntimeObjectTransport.VerifiedTransfer t,JsonNode receipt,String digest){payload=p;attempt=a;transfer=t;this.receipt=receipt.deepCopy();this.digest=digest;}
        public void requireUnchanged()throws Exception{var row=one(db.queryForList("SELECT * FROM task_section_writer_object WHERE tenant_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",payload.input().tenantId(),artifactId()));var fresh=verified(payload,attempt,transfer,row);if(!digest.equals(fresh.digest))bad();}
        public String artifactId(){return attempt.artifactId();}public InterfaceProtocol.ObjectRef reference(){return transfer.reference();}public String receiptSha256(){return digest;}public JsonNode receipt(){return receipt.deepCopy();}public String kind(){return payload.kind;}public String actionId(){return payload.action;}public String runId(){return payload.input().runId();}public String stepId(){return payload.input().stepId();}public String accessSetId(){return receipt.required("dependency_manifest").required("access_set_id").textValue();}
    }
    public final class Attempt implements RuntimeObjectAttempt {
        private final TaskSectionWriterObjectLedger owner=TaskSectionWriterObjectLedger.this;private final Payload payload;private final Map<String,Object> row;private final boolean prior;private boolean committed;private final Thread thread=Thread.currentThread();private final java.util.concurrent.atomic.AtomicBoolean claimed=new java.util.concurrent.atomic.AtomicBoolean();
        private Attempt(Payload p,Map<String,Object> row,boolean prior){payload=p;this.row=Collections.unmodifiableMap(new LinkedHashMap<>(row));this.prior=prior;if(!TransactionSynchronizationManager.isSynchronizationActive())deny();TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){committed=true;}});}
        public String tenantId(){return str(row,"tenant_id");}public String actorId(){return str(row,"created_by");}public String expectedObjectKey(){return str(row,"object_key");}public String artifactId(){return str(row,"artifact_id");}
        public S3ObjectWriter.WriteIntent writeIntent(){return new S3ObjectWriter.WriteIntent(str(row,"write_id"),expectedObjectKey(),str(row,"payload_sha256"),Integer.toString(bytes().length),"application/json");}
        public byte[] bytes(){return str(row,"canonical_payload").getBytes(StandardCharsets.UTF_8);}public boolean priorAttempt(){return prior;}
        public TransferMode claimTransferMode(){if(!committed||thread!=Thread.currentThread()||TransactionSynchronizationManager.isActualTransactionActive())deny();return prior||!claimed.compareAndSet(false,true)?TransferMode.RECOVER_ONLY:TransferMode.INITIAL_WRITE;}
    }
    private final class Payload {
        private final TaskSectionWriterRuntimeAdapter.Envelope envelope;private final TaskSectionWriterInputAuthority.Prepared prepared;private final TaskSectionWriterProducerAuthority.Prepared inputProducer;
        private final TaskSectionWriterCandidateService.Produced produced;private final TaskSectionWriterProducerAuthority.Verified outputProducer;private final String kind,artifact,action;private final byte[] bytes;private final JsonNode pins;
        private Payload(TaskSectionWriterInputAuthority.Prepared p,TaskSectionWriterProducerAuthority.Prepared ip,TaskSectionWriterCandidateService.Produced o,TaskSectionWriterProducerAuthority.Verified op,String kind,byte[] bytes,String artifact,String action,JsonNode pins){envelope=null;prepared=p;inputProducer=ip;produced=o;outputProducer=op;this.kind=kind;this.bytes=bytes.clone();this.artifact=artifact;this.action=action;this.pins=pins.deepCopy();}
        private Payload(TaskSectionWriterRuntimeAdapter.Envelope e,TaskSectionWriterProducerAuthority.Verified producer)throws Exception{envelope=e;prepared=null;inputProducer=null;produced=null;outputProducer=producer;kind="STEP_ENVELOPE";artifact=null;action=e.authority().actionId();bytes=json.bytes(e.document());pins=producer.canonicalPins();}
        void require()throws Exception{if(envelope!=null){envelope.requireProductionReady();outputProducer.requireFor(envelope.authority().input());outputProducer.requireBytes(kind,bytes.length);if(!outputProducer.canonicalPins().equals(pins)||!Arrays.equals(json.bytes(envelope.document()),bytes))bad();}else if(prepared!=null){prepared.requireUnchanged();inputProducer.requireFor(prepared);inputProducer.requireBytes(kind,bytes.length);if(!inputProducer.canonicalPins().equals(pins)||!Arrays.equals(prepared.bytes(),bytes))bad();}else {produced.requireUnchanged();outputProducer.requireFor(produced.input());outputProducer.requireBytes(kind,bytes.length);if(!outputProducer.canonicalPins().equals(pins)||!Arrays.equals(produced.bytes(),bytes))bad();}}
        TaskSectionWriterInputAuthority.Verified registeredInput(){return envelope!=null?envelope.authority().input():produced.input();}
        TaskSectionWriterInput input(){return prepared!=null?prepared.input():registeredInput().input();}String actor(){return prepared!=null?prepared.production().actorId():registeredInput().production().actorId();}String classification(){return prepared!=null?prepared.production().classification():registeredInput().production().classification();}
        List<String> roots(){return prepared!=null?prepared.contentRoots():registeredInput().contentRoots();}List<ArtifactRegistryProtocol.MemberRow> members(){return prepared!=null?prepared.contentMembers():registeredInput().contentMembers();}
        JsonNode provenance()throws Exception{return envelope!=null?envelope.document().required("prompt_provenance"):prepared!=null?json.tree(Map.of("input_sha256",json.hash(input()),"baseline_sha256",json.hash(prepared.baselineBody()))):produced.provenance();}
        String schemaRef()throws Exception{return envelope!=null?TaskSectionWriterDispatchJournal.ENVELOPE_REF:prepared!=null?TaskSectionWriterSchema.INPUT_REF:produced.schemaRef();}
        String schemaHash()throws Exception{return envelope!=null?TaskSectionWriterDispatchJournal.MANIFEST:prepared!=null?json.manifest():produced.schemaSha();}
    }
    static String plannedAccessSet(TaskSectionWriterInput input,String action,String kind){return UUID.nameUUIDFromBytes(("N04_ACCESS/"+input.tenantId()+"/"+input.runId()+"/"+input.stepId()+"/"+action+"/"+kind).getBytes(StandardCharsets.UTF_8)).toString();}
    private Map<String,Object> meta(Payload p,LocalDateTime at)throws Exception{var m=new LinkedHashMap<String,Object>();m.put("tenant_id",p.input().tenantId());m.put("project_id",p.input().projectId());m.put("created_by",p.actor());m.put("created_at",at);m.put("classification",p.classification());m.put("schema_version","1.0.0");m.put("schema_manifest_sha256",p.schemaHash());return m;}
    private void journal(Payload p,String audit,String event,String artifact,String raw,LocalDateTime at)throws Exception{var a=meta(p,at);a.putAll(Map.of("audit_id",audit,"subject_id",p.actor(),"action","task.section_writer.object.registered","resource_ref",raw,"result","SUCCESS","request_id",p.action,"trace_id",p.input().runId()));insert("audit_event",a);insert("outbox_event",Map.of("tenant_id",p.input().tenantId(),"event_id",event,"aggregate_id",artifact,"aggregate_revision",1,"event_type","task.section_writer.object.registered","payload",raw,"occurred_at",at));}
    private void version(Payload p,String id,String kind,String hash){insert("version_registry",Map.of("tenant_id",p.input().tenantId(),"version_id",id,"owner_project_id",p.input().projectId(),"entity_kind",kind,"content_sha256",hash));}
    private static void removeSchema(Map<String,Object> m){m.remove("schema_version");m.remove("schema_manifest_sha256");}
    private void insert(String table,Map<String,Object> values){var keys=new ArrayList<>(values.keySet());if(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(values::get).toArray())!=1)bad();}
    private LocalDateTime now(){return db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class);}private static String id(){return UUID.randomUUID().toString();}private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String str(Map<String,Object> row,String key){var v=row.get(key);if(v==null)bad();return v.toString();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
