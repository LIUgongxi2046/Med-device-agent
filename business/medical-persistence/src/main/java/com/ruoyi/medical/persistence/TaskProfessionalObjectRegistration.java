package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.TaskProfessionalObjectGovernance.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Private physically registered TASK objects in the existing 075 journal. Pre-admission
 * envelopes prove prepared bytes only. Independent admission/closure is a separate 099
 * binding, never synthesized from a caller reference, registration or host FINISH. */
public final class TaskProfessionalObjectRegistration {
    private final JdbcTemplate db;private final PermissionSchema json;private final TaskProfessionalSchema schema;
    private final TaskProfessionalObjectLedger ledger;private final TaskProfessionalObjectGovernance governance;
    private final TaskProfessionalContentAuthority contents;private final DependencyManifestSchema manifests;
    private final DependencyLineage lineage;private final DependencyLineage.Limits limits;
    public TaskProfessionalObjectRegistration(DataSource data,PermissionSchema json,TaskProfessionalSchema schema,
            TaskProfessionalObjectLedger ledger,TaskProfessionalObjectGovernance governance,TaskProfessionalContentAuthority contents,
            DependencyManifestSchema manifests,DependencyLineage lineage,DependencyLineage.Limits limits) {
        this(new JdbcTemplate(data),json,schema,ledger,governance,contents,manifests,lineage,limits);
    }
    TaskProfessionalObjectRegistration(JdbcTemplate db,PermissionSchema json,TaskProfessionalSchema schema,
            TaskProfessionalObjectLedger ledger,TaskProfessionalObjectGovernance governance,TaskProfessionalContentAuthority contents,
            DependencyManifestSchema manifests,DependencyLineage lineage,DependencyLineage.Limits limits) {
        this.db=db;db.setQueryTimeout(5);this.json=json;this.schema=schema;this.ledger=ledger;this.governance=governance;
        this.contents=contents;this.manifests=manifests;this.lineage=lineage;this.limits=limits;
    }
    public Registered register(TaskNodeProducerAuthority.LeasedProduction authority,TaskProfessionalRuntimeService.Envelope envelope,
            TaskProfessionalObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception {
        return register(new Original(authority,envelope,null),attempt,physical);
    }
    public Registered register(TaskNodeProducerAuthority.LeasedProduction authority,TaskResearchExecutionService.Produced produced,
            TaskProfessionalObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception {
        return register(new Original(authority,null,produced),attempt,physical);
    }
    public Registered readVerified(TaskNodeProducerAuthority.LeasedProduction authority,TaskProfessionalRuntimeService.Envelope envelope,
            TaskProfessionalObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception {
        return read(new Original(authority,envelope,null),attempt,physical);
    }
    public Registered readVerified(TaskNodeProducerAuthority.LeasedProduction authority,TaskResearchExecutionService.Produced produced,
            TaskProfessionalObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception {
        return read(new Original(authority,null,produced),attempt,physical);
    }
    public Registered register(TaskNodeProducerAuthority.LeasedProduction authority,TaskInvocationContext.Prepared exact,TaskProfessionalObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception{return register(new Original(authority,null,null,exact),attempt,physical);}
    public Registered readVerified(TaskNodeProducerAuthority.LeasedProduction authority,TaskInvocationContext.Prepared exact,TaskProfessionalObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception{return read(new Original(authority,null,null,exact),attempt,physical);}
    public void requireTransferCurrent(TaskNodeProducerAuthority.LeasedProduction authority,TaskInvocationContext.Prepared exact,TaskProfessionalObjectLedger.Attempt attempt)throws Exception{require(new Original(authority,null,null,exact),attempt);}
    /** Call inside a new formal authentication transaction for every physical I/O segment. */
    public void requireTransferCurrent(TaskNodeProducerAuthority.LeasedProduction authority,TaskProfessionalRuntimeService.Envelope envelope,
            TaskProfessionalObjectLedger.Attempt attempt)throws Exception {require(new Original(authority,envelope,null),attempt);}
    public void requireTransferCurrent(TaskNodeProducerAuthority.LeasedProduction authority,TaskResearchExecutionService.Produced produced,
            TaskProfessionalObjectLedger.Attempt attempt)throws Exception {require(new Original(authority,null,produced),attempt);}
    private TaskProfessionalContentAuthority.Verified require(Original original,TaskProfessionalObjectLedger.Attempt attempt)throws Exception {
        Objects.requireNonNull(attempt);original.require();
        if(original.envelope!=null)ledger.requireAttempt(original.authority,original.envelope,attempt.intent());
        else if(original.exact!=null)ledger.requireAttempt(original.authority,original.exact,attempt.intent());else ledger.requireAttempt(original.authority,original.produced,attempt.intent());
        var content=contents.require(original.input());if(original.exact!=null&&(!content.members().equals(original.exact.contentMembers())||!content.roots().equals(original.exact.contentRoots())))bad();governance.requireCurrent(original.admission(),original.input(),original.authority,attempt.intent(),original.provenance());content.requireUnchanged();original.require();return content;
    }
    private Registered read(Original original,TaskProfessionalObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception {
        requireTransfer(attempt,physical);var content=require(original,attempt);return verify(original,attempt,physical,content,one(rows(original,attempt)));
    }
    private Registered register(Original original,TaskProfessionalObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception {
        requireTransfer(attempt,physical);var content=require(original,attempt);var found=rows(original,attempt);
        if(!found.isEmpty())return verify(original,attempt,physical,content,one(found));
        var a=original.admission();var i=a.formalProof().identity();var intent=attempt.intent();var object=physical.reference();
        if(!db.queryForList("SELECT artifact_id FROM artifact WHERE tenant_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",i.tenant(),intent.artifactId()).isEmpty())bad();
        var saved=governance.prepare(a,original.input(),original.authority,intent,original.provenance());
        var now=now();String set=UUID.randomUUID().toString();
        if(original.produced!=null)bindClosed(original,intent,now,true);if(original.exact!=null)bindPreSend(original,intent,now,true);
        var manifest=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",i.tenant(),i.project(),set,content.members());String manifestHash=hash(manifest);
        version(i,set,"DEPENDENCY_ACCESS_SET",manifestHash);version(i,intent.artifactId(),"ARTIFACT",object.sha256());
        var access=meta(i,intent,now,false);access.put("access_set_id",set);access.put("manifest_sha256",manifestHash);access.put("content_sha256",manifestHash);insert("dependency_access_set",access);
        var m=meta(i,intent,now,true);m.put("schema_manifest_sha256",manifests.manifestSha256());m.put("access_set_id",set);m.put("manifest_json",json.canonical(json.tree(manifest)));m.put("manifest_sha256",manifestHash);m.put("content_sha256",manifestHash);m.put("member_count",manifest.members().size());insert("dependency_access_manifest",m);
        for(var member:content.members()){var row=meta(i,intent,now,false);row.remove("project_id");row.put("access_set_id",set);row.put("upstream_version_id",member.upstreamVersionId());row.put("rights_revision",member.rightsRevision());insert("dependency_access_member",row);}
        insert("artifact",artifact(i,intent,object,set,now));insert("artifact_content_binding",binding(i,intent,original.input().taskId(),set,manifestHash,now));
        for(String root:content.roots())insert("lineage_edge",Map.of("tenant_id",i.tenant(),"consumer_version_id",intent.artifactId(),"upstream_version_id",root,"relation_kind","RUNTIME_CONTENT_INPUT","actor_id",i.actor(),"created_at",now));
        var receipt=receipt(original,intent,object,manifest,content.roots(),saved.sha256(),now);String canonical=json.canonical(receipt),digest=hash(receipt);
        var record=meta(i,intent,now,true);record.put("run_id",i.run());record.put("write_id",intent.writeIntent().writeId());record.put("artifact_id",intent.artifactId());record.put("object_sha256",object.sha256());record.put("object_version",object.objectVersion());record.put("receipt_json",canonical);record.put("receipt_sha256",digest);insert("runtime_registered_object",record);
        var audit=meta(i,intent,now,true);audit.put("audit_id",UUID.randomUUID().toString());audit.put("subject_id",i.actor());audit.put("action","runtime.professional.registered");audit.put("resource_ref",canonical);audit.put("result","SUCCESS");audit.put("request_id",intent.writeIntent().writeId());audit.put("trace_id",i.run());insert("audit_event",audit);
        insert("outbox_event",Map.of("tenant_id",i.tenant(),"event_id",intent.artifactId(),"aggregate_id",intent.writeIntent().writeId(),"aggregate_revision",1,"event_type","runtime.professional.registered","payload",canonical,"occurred_at",now));
        return verify(original,attempt,physical,content,one(rows(original,attempt)));
    }
    private Registered verify(Original original,TaskProfessionalObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical,
            TaskProfessionalContentAuthority.Verified content,Map<String,Object> stored)throws Exception {
        requireTransfer(attempt,physical);original.require();var a=original.admission();var i=a.formalProof().identity();var intent=attempt.intent();var object=physical.reference();
        var created=time(stored,"created_at");if(created.isAfter(now()))bad();var r=schema.parse(str(stored,"receipt_json"));
        if(!hash(r).equals(str(stored,"receipt_sha256")))bad();var manifest=r.required("content_manifest");String set=text(manifest,"access_set_id"),manifestHash=hash(manifest);
        WireValues.uuid(set);var expectedManifest=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",i.tenant(),i.project(),set,content.members());if(!FormalTaskNodeAuthority.sameEvidence(json.tree(expectedManifest),manifest))bad();
        var saved=governance.requireStored(a,original.input(),original.authority,intent,original.provenance());
        if(!FormalTaskNodeAuthority.sameEvidence(receipt(original,intent,object,expectedManifest,content.roots(),saved.sha256(),created),r))bad();
        var record=meta(i,intent,created,true);record.put("run_id",i.run());record.put("write_id",intent.writeIntent().writeId());record.put("artifact_id",intent.artifactId());record.put("object_sha256",object.sha256());record.put("object_version",object.objectVersion());same(record,stored);
        var ar=one(db.queryForList("SELECT * FROM artifact WHERE tenant_id=? AND artifact_id=? LIMIT 2",i.tenant(),intent.artifactId()));same(artifact(i,intent,object,set,created),ar);StoredObjectReferences.decode(str(ar,"object_ref"),object);
        same(binding(i,intent,original.input().taskId(),set,manifestHash,created),one(db.queryForList("SELECT * FROM artifact_content_binding WHERE tenant_id=? AND artifact_id=? LIMIT 2",i.tenant(),intent.artifactId())));
        var access=meta(i,intent,created,false);access.put("access_set_id",set);access.put("manifest_sha256",manifestHash);access.put("content_sha256",manifestHash);same(access,one(db.queryForList("SELECT * FROM dependency_access_set WHERE tenant_id=? AND access_set_id=? LIMIT 2",i.tenant(),set)));
        var m=meta(i,intent,created,true);m.put("schema_manifest_sha256",manifests.manifestSha256());m.put("access_set_id",set);m.put("manifest_sha256",manifestHash);m.put("content_sha256",manifestHash);m.put("member_count",content.members().size());
        var actualManifest=one(db.queryForList("SELECT * FROM dependency_access_manifest WHERE tenant_id=? AND access_set_id=? LIMIT 2",i.tenant(),set));same(m,actualManifest);if(!FormalTaskNodeAuthority.sameEvidence(manifest,schema.parse(str(actualManifest,"manifest_json"))))bad();
        var members=db.queryForList("SELECT * FROM dependency_access_member WHERE tenant_id=? AND access_set_id=? ORDER BY upstream_version_id",i.tenant(),set);if(members.size()!=content.members().size())bad();
        for(int n=0;n<members.size();n++){var expected=meta(i,intent,created,false);expected.remove("project_id");expected.put("access_set_id",set);expected.put("upstream_version_id",content.members().get(n).upstreamVersionId());expected.put("rights_revision",content.members().get(n).rightsRevision());same(expected,members.get(n));}
        var edges=db.queryForList("SELECT * FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? ORDER BY upstream_version_id",i.tenant(),intent.artifactId());if(edges.size()!=content.roots().size())bad();
        for(int n=0;n<edges.size();n++){var expected=new LinkedHashMap<String,Object>();expected.put("tenant_id",i.tenant());expected.put("consumer_version_id",intent.artifactId());expected.put("upstream_version_id",content.roots().get(n));expected.put("relation_kind","RUNTIME_CONTENT_INPUT");expected.put("actor_id",i.actor());expected.put("created_at",created);expected.put("decision_ref",null);same(expected,edges.get(n));}
        for(var v:List.of(Map.entry(intent.artifactId(),object.sha256()),Map.entry(set,manifestHash)))same(Map.of("tenant_id",i.tenant(),"owner_project_id",i.project(),"version_id",v.getKey(),"entity_kind",v.getKey().equals(set)?"DEPENDENCY_ACCESS_SET":"ARTIFACT","content_sha256",v.getValue()),one(db.queryForList("SELECT * FROM version_registry WHERE tenant_id=? AND version_id=? LIMIT 2",i.tenant(),v.getKey())));
        lineage.require(intent.artifactId(),content.members(),limits);
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND request_id=? AND action='runtime.professional.registered' LIMIT 2",i.tenant(),intent.writeIntent().writeId()));var expectedAudit=meta(i,intent,created,true);expectedAudit.put("subject_id",i.actor());expectedAudit.put("action","runtime.professional.registered");expectedAudit.put("result","SUCCESS");expectedAudit.put("reason_code",null);expectedAudit.put("request_id",intent.writeIntent().writeId());expectedAudit.put("trace_id",i.run());same(expectedAudit,audit);if(!FormalTaskNodeAuthority.sameEvidence(r,schema.parse(str(audit,"resource_ref"))))bad();
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",i.tenant(),intent.artifactId()));same(Map.of("tenant_id",i.tenant(),"event_id",intent.artifactId(),"aggregate_id",intent.writeIntent().writeId(),"aggregate_revision",1,"event_type","runtime.professional.registered","occurred_at",created),event);if(!FormalTaskNodeAuthority.sameEvidence(r,schema.parse(str(event,"payload"))))bad();
        if(original.produced!=null)bindClosed(original,intent,created,false);if(original.exact!=null)bindPreSend(original,intent,created,false);
        content.requireUnchanged();original.require();return new Registered(this,original,attempt,physical,r,str(stored,"receipt_sha256"));
    }
    private JsonNode receipt(Original original,TaskProfessionalObjectLedger.Intent intent,ObjectRef object,ArtifactRegistryProtocol.DependencyManifest manifest,
            List<String> roots,String governanceHash,LocalDateTime created)throws Exception {
        var i=original.admission().formalProof().identity();var r=json.object().put("schema_version","2.0.0").put("tenant_id",i.tenant()).put("project_id",i.project())
                .put("run_id",i.run()).put("step_id",i.step()).put("task_id",original.input().taskId()).put("principal_id",i.principal()).put("action_id",i.action())
                .put("lease_generation",i.generation()).put("worker_service_uri",i.workerService()).put("session_id",intent.sessionId())
                .put("write_id",intent.writeIntent().writeId()).put("write_key",intent.key()).put("artifact_id",intent.artifactId()).put("kind",intent.kind())
                .put("schema_ref",intent.schemaRef()).put("payload_schema_sha256",intent.schemaSha256()).put("governance_sha256",governanceHash)
                .put("input_artifact_id",original.input().inputId()).put("input_sha256",original.input().inputSha256()).put("input_receipt_sha256",original.input().receiptSha256())
                .put("producer_rule_id",original.authority.ruleId()).put("producer_sha256",original.authority.ruleSha256())
                .put("producer_decision_id",original.authority.decisionId()).put("producer_decision_sha256",original.authority.decisionSha256())
                .put("created_by",i.actor()).put("created_at",created.toString()).put("classification",intent.classification());
        r.set("object_ref",json.tree(object));r.set("content_manifest",json.tree(manifest));r.set("content_roots",json.tree(roots));r.set("provenance",original.provenance());return r;
    }
    /** Separate admission binding after a genuine 083 session exists. This never creates a session. */
    public void bindAdmitted(TaskNodeProducerAuthority.LeasedProduction authority,TaskProfessionalRuntimeService.Envelope envelope,
            Registered registered,TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted admitted)throws Exception {
        authority.requireHostEnvelopeConsume(envelope,registered);registered.requireUnchanged();session.requireUnchanged();admitted.requireUnchanged();
        if(!envelope.sessionId().equals(session.sessionId())||!session.sessionId().equals(admitted.sessionId())
                ||!envelope.runId().equals(session.runId())||!envelope.stepId().equals(session.stepId())||!envelope.actionId().equals(session.actionId())
                ||!envelope.leaseGeneration().equals(session.generation())||!envelope.workerService().equals(session.workerService())
                ||!envelope.inputId().equals(session.input().inputId())||!envelope.inputSha256().equals(session.input().inputSha256())
                ||!FormalTaskNodeAuthority.sameEvidence(session.binding(),admitted.binding())||!envelope.payloadSha256().equals(text(admitted.binding(),"step_envelope_sha256"))
                ||!envelope.deployment().sha256().equals(admitted.deploymentSha256()))bad();
        var provenance=json.object().put("phase","ADMITTED").put("registration_receipt_sha256",registered.receiptSha256())
                .put("supervision_receipt_id",admitted.receiptId()).put("supervision_sha256",admitted.sha256()).put("deployment_sha256",admitted.deploymentSha256());
        provenance.set("binding",admitted.binding());
        bind(new Original(authority,envelope,null),registered.attempt.intent(),admitted.receiptId(),admitted.sha256(),provenance,now(),true);
        session.requireUnchanged();admitted.requireUnchanged();registered.requireUnchanged();
    }
    private void bindPreSend(Original original,TaskProfessionalObjectLedger.Intent intent,LocalDateTime at,boolean create)throws Exception {
        var exact=original.exact;exact.requireUnchanged();var admitted=exact.admitted();admitted.requireUnchanged();var session=exact.session();session.requireUnchanged();
        if(!"TASK_EXACT_INPUT".equals(intent.kind())||!"OPEN".equals(session.state())||!session.sessionId().equals(admitted.sessionId())||!FormalTaskNodeAuthority.sameEvidence(session.binding(),admitted.binding())||!session.deployment().sha256().equals(admitted.deploymentSha256())||!intent.sessionId().equals(admitted.sessionId()))bad();
        bind(original,intent,admitted.receiptId(),admitted.sha256(),exact.provenance(),at,create);admitted.requireUnchanged();exact.requireUnchanged();
    }
    private void bindClosed(Original original,TaskProfessionalObjectLedger.Intent intent,LocalDateTime at,boolean create)throws Exception {
        var produced=original.produced;produced.requireUnchanged();var closed=produced.supervision();closed.requireUnchanged();var session=produced.session();
        if(!"CLOSED".equals(session.state())||!session.sessionId().equals(closed.sessionId())||!FormalTaskNodeAuthority.sameEvidence(session.binding(),closed.binding())
                ||!session.deployment().sha256().equals(closed.deploymentSha256())||!intent.sessionId().equals(closed.sessionId()))bad();
        bind(original,intent,closed.receiptId(),closed.sha256(),produced.provenance(),at,create);closed.requireUnchanged();
    }
    private void bind(Original original,TaskProfessionalObjectLedger.Intent intent,String supervision,String supervisionHash,JsonNode provenance,LocalDateTime at,boolean create)throws Exception {
        original.require();var i=original.admission().formalProof().identity();var found=db.queryForList("SELECT * FROM runtime_professional_object_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? LIMIT 2 FOR UPDATE",i.tenant(),i.project(),i.run(),intent.writeIntent().writeId());
        if(!found.isEmpty())at=time(one(found),"created_at");if(at.isAfter(now()))bad();
        var row=meta(i,intent,at,true);row.put("run_id",i.run());row.put("step_id",i.step());row.put("write_id",intent.writeIntent().writeId());row.put("session_id",intent.sessionId());row.put("lease_generation",Long.parseLong(i.generation()));
        row.put("producer_rule_id",original.authority.ruleId());row.put("producer_sha256",original.authority.ruleSha256());row.put("producer_decision_id",original.authority.decisionId());row.put("producer_decision_sha256",original.authority.decisionSha256());
        row.put("input_artifact_id",original.input().inputId());row.put("input_receipt_sha256",original.input().receiptSha256());row.put("supervision_receipt_id",supervision);row.put("supervision_sha256",supervisionHash);row.put("provenance_sha256",hash(provenance));
        if(found.isEmpty()){if(!create)bad();row.put("canonical_provenance",json.canonical(provenance));insert("runtime_professional_object_binding",row);}
        else {same(row,one(found));if(!FormalTaskNodeAuthority.sameEvidence(provenance,schema.parse(str(one(found),"canonical_provenance"))))bad();}
        var actual=one(db.queryForList("SELECT * FROM runtime_professional_object_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? LIMIT 2 FOR UPDATE",i.tenant(),i.project(),i.run(),intent.writeIntent().writeId()));
        row.remove("canonical_provenance");same(row,actual);if(!FormalTaskNodeAuthority.sameEvidence(provenance,schema.parse(str(actual,"canonical_provenance"))))bad();
    }
    static void requireTransfer(TaskProfessionalObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical) {
        if(attempt==null||physical==null||physical.originalAttempt()!=attempt)bad();var expected=attempt.writeIntent();var actual=physical.reference();
        if(actual==null||!expected.objectKey().equals(actual.objectKey())||!expected.sha256().equals(actual.sha256())||!expected.sha256().equals(physical.rawSha256())
                ||!expected.sizeBytes().equals(actual.sizeBytes())||!expected.mediaType().equals(actual.mediaType())||actual.objectVersion()==null||actual.objectVersion().isBlank())bad();
    }
    private final class Original {
        final TaskNodeProducerAuthority.LeasedProduction authority;final TaskProfessionalRuntimeService.Envelope envelope;final TaskResearchExecutionService.Produced produced;final TaskInvocationContext.Prepared exact;
        Original(TaskNodeProducerAuthority.LeasedProduction authority,TaskProfessionalRuntimeService.Envelope envelope,TaskResearchExecutionService.Produced produced){this(authority,envelope,produced,null);}
        Original(TaskNodeProducerAuthority.LeasedProduction authority,TaskProfessionalRuntimeService.Envelope envelope,TaskResearchExecutionService.Produced produced,TaskInvocationContext.Prepared exact){this.authority=Objects.requireNonNull(authority);this.envelope=envelope;this.produced=produced;this.exact=exact;if((envelope==null?0:1)+(produced==null?0:1)+(exact==null?0:1)!=1)bad();}
        void require()throws Exception{if(envelope!=null)authority.requireProduction(envelope);else if(exact!=null){authority.requireProduction(exact);exact.admitted().requireUnchanged();}else {if(!Set.of("EVIDENCE_BUNDLE","SESSION_EXPORT","STEP_RESULT").contains(produced.kind()))bad();authority.requireProduction(produced);produced.supervision().requireUnchanged();}}
        EnterpriseAccess.Admission admission(){return envelope!=null?envelope.admission():exact!=null?exact.admission():produced.session().admission();}
        TaskNodeInputAuthority.Verified input(){return envelope!=null?envelope.input():exact!=null?exact.input():produced.session().input();}
        JsonNode provenance()throws Exception {if(produced!=null)return produced.provenance();if(exact!=null)return exact.provenance();return json.object().put("phase","PREPARED_BYTES").put("session_preparation_id",envelope.sessionId()).put("payload_sha256",envelope.payloadSha256()).put("deployment_sha256",envelope.deployment().sha256());}
    }
    /** Historical immutable facts only. Not a current content/read/send grant, Registered
     * capability, transport attempt, or permission to recover/rewrite an object. */
    public OriginalExactInput readOriginalExactInput(String tenant,String artifactId)throws Exception {
        var reader=new TaskProfessionalOriginalExactInput(db,json,schema,manifests,governance);
        return new OriginalExactInput(reader,tenant,artifactId,reader.read(tenant,artifactId));
    }
    public static final class OriginalExactInput {
        private final TaskProfessionalOriginalExactInput reader;private final String tenant,artifact;
        private final TaskProfessionalOriginalExactInput.Snapshot original;
        private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();
        private final Map<Object,Object> resources=Map.copyOf(org.springframework.transaction.support.TransactionSynchronizationManager.getResourceMap());
        private OriginalExactInput(TaskProfessionalOriginalExactInput reader,String tenant,String artifact,TaskProfessionalOriginalExactInput.Snapshot original){this.reader=reader;this.tenant=tenant;this.artifact=artifact;this.original=original;if(resources.isEmpty())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
        private void active(){TaskProfessionalOriginalExactInput.active(tenant);if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);var actual=org.springframework.transaction.support.TransactionSynchronizationManager.getResourceMap();if(actual.size()!=resources.size()||resources.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
        public void requireUnchanged()throws Exception{active();var current=reader.read(tenant,artifact);if(!original.digest().equals(current.digest())||!original.reference().equals(current.reference())||!Arrays.equals(original.bytes(),current.bytes())||!FormalTaskNodeAuthority.sameEvidence(original.receipt(),current.receipt()))bad();active();}
        public byte[] bytes(){active();return original.bytes();}public ObjectRef reference(){active();return original.reference();}
        public JsonNode receipt(){active();return original.receipt();}public String receiptSha256(){active();return original.digest();}
        public String kind(){active();return "TASK_EXACT_INPUT";}public String pin(String field){active();return text(original.receipt(),field);}
    }
    /** Full original075 POST_CLOSE facts, backed by the independent original083/099 closure.
     * This cannot create a Registered, physical transfer, write or completion capability. */
    public OriginalProduced readOriginalProduced(EnterpriseAccess.Admission admission,String artifactId,String kind,
            TaskProfessionalSupervisor supervisor,TaskProfessionalRuntimeService.Deployment deployment)throws Exception {
        if(!Set.of("SESSION_EXPORT","EVIDENCE_BUNDLE","STEP_RESULT").contains(kind))bad();
        var reader=new TaskProfessionalOriginalExactInput(db,json,schema,manifests,governance,kind,admission,supervisor,deployment);
        String tenant=admission.context().tenant_id();return new OriginalProduced(reader,tenant,artifactId,kind,reader.read(tenant,artifactId));
    }
    public static final class OriginalProduced {
        private final TaskProfessionalOriginalExactInput reader;private final String tenant,artifact,kind;private final TaskProfessionalOriginalExactInput.Snapshot original;
        private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();
        private final Map<Object,Object> resources=Map.copyOf(org.springframework.transaction.support.TransactionSynchronizationManager.getResourceMap());
        private OriginalProduced(TaskProfessionalOriginalExactInput reader,String tenant,String artifact,String kind,TaskProfessionalOriginalExactInput.Snapshot original){this.reader=reader;this.tenant=tenant;this.artifact=artifact;this.kind=kind;this.original=original;if(resources.isEmpty())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
        private void active(){TaskProfessionalOriginalExactInput.active(tenant);if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);var actual=org.springframework.transaction.support.TransactionSynchronizationManager.getResourceMap();if(actual.size()!=resources.size()||resources.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
        public void requireUnchanged()throws Exception{active();var current=reader.read(tenant,artifact);if(!original.digest().equals(current.digest())||!original.reference().equals(current.reference())||!Arrays.equals(original.bytes(),current.bytes())||!FormalTaskNodeAuthority.sameEvidence(original.receipt(),current.receipt()))bad();active();}
        public byte[] bytes(){active();return original.bytes();}public ObjectRef reference(){active();return original.reference();}
        public JsonNode receipt(){active();return original.receipt();}public String receiptSha256(){active();return original.digest();}
        public String kind(){active();return kind;}public String artifactId(){active();return artifact;}
    }
    public static final class Registered {
        private final TaskProfessionalObjectRegistration owner;private final Original original;private final TaskProfessionalObjectLedger.Attempt attempt;
        private final RuntimeObjectTransport.VerifiedTransfer transfer;private final JsonNode receipt;private final String digest;
        private Registered(TaskProfessionalObjectRegistration owner,Original original,TaskProfessionalObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer transfer,JsonNode receipt,String digest){this.owner=owner;this.original=original;this.attempt=attempt;this.transfer=transfer;this.receipt=receipt.deepCopy();this.digest=digest;}
        public void requireUnchanged()throws Exception{var current=owner.read(original,attempt,transfer);if(!digest.equals(current.digest)||!FormalTaskNodeAuthority.sameEvidence(receipt,current.receipt))bad();}
        public String artifactId(){return attempt.intent().artifactId();}public ObjectRef reference(){return transfer.reference();}public String receiptSha256(){return digest;}public JsonNode receipt(){return receipt.deepCopy();}
        public String kind(){return attempt.intent().kind();}public String sessionId(){return attempt.intent().sessionId();}
        public String payloadSha256(){return reference().sha256();}public String actionId(){return pin("action_id");}public String inputId(){return pin("input_artifact_id");}
        public String runId(){return pin("run_id");}public String stepId(){return pin("step_id");}public String leaseGeneration(){return pin("lease_generation");}
        public String workerService(){return pin("worker_service_uri");}public String inputSha256(){return pin("input_sha256");}public String inputReceiptSha256(){return pin("input_receipt_sha256");}
        public String pin(String field){return text(receipt,field);}
    }
    private List<Map<String,Object>> rows(Original original,TaskProfessionalObjectLedger.Attempt attempt){var i=original.admission().formalProof().identity();return db.queryForList("SELECT * FROM runtime_registered_object WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? LIMIT 2 FOR UPDATE",i.tenant(),i.project(),i.run(),attempt.writeIntent().writeId());}
    private Map<String,Object> meta(FormalWorkerAccess.Identity i,TaskProfessionalObjectLedger.Intent intent,LocalDateTime at,boolean schemaFields){var row=metadata(i,intent,at);row.remove("run_id");if(!schemaFields){row.remove("schema_version");row.remove("schema_manifest_sha256");}return row;}
    private Map<String,Object> artifact(FormalWorkerAccess.Identity i,TaskProfessionalObjectLedger.Intent intent,ObjectRef object,String set,LocalDateTime at)throws java.io.IOException {var row=meta(i,intent,at,true);row.put("artifact_id",intent.artifactId());row.put("run_id",i.run());row.put("document_revision",null);row.put("artifact_kind",intent.kind());row.put("object_ref",StoredObjectReferences.encode(object));row.put("object_key",object.objectKey());row.put("object_version",object.objectVersion());row.put("sha256",object.sha256());row.put("size_bytes",object.sizeBytes());row.put("media_type",object.mediaType());row.put("dependency_access_set_id",set);row.put("content_sha256",object.sha256());return row;}
    private Map<String,Object> binding(FormalWorkerAccess.Identity i,TaskProfessionalObjectLedger.Intent intent,String task,String set,String hash,LocalDateTime at){var row=meta(i,intent,at,false);row.put("artifact_id",intent.artifactId());row.put("resource_kind","TASK");row.put("resource_id",task);row.put("schema_ref",intent.schemaRef());row.put("schema_document_sha256",intent.schemaSha256());row.put("dependency_access_set_id",set);row.put("dependency_manifest_sha256",hash);return row;}
    private void version(FormalWorkerAccess.Identity i,String id,String kind,String hash){insert("version_registry",Map.of("tenant_id",i.tenant(),"owner_project_id",i.project(),"version_id",id,"entity_kind",kind,"content_sha256",hash));}
    private void insert(String table,Map<String,Object> values){var columns=new ArrayList<>(values.keySet());if(db.update("INSERT INTO "+table+"("+String.join(",",columns)+") VALUES("+String.join(",",Collections.nCopies(columns.size(),"?"))+")",columns.stream().map(values::get).toArray())!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
}
