package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Independent supervisor receipts. Worker requests can identify a session, never attest it.
 * Snapshot inside a formal transaction, authenticate/read outside it, then record against a
 * fresh current original. No network or process launch occurs in a database transaction. */
public final class TaskProfessionalSupervisor {
    private static final Set<String> COMMON=Set.of("schema_version","receipt_id","phase","session_id","binding","deployment_revision","deployment_sha256","runtime_manifest_sha256","supervisor_instance_id","inspected_at","process_unit");
    private static final JsonMapper JSON=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JdbcTemplate db;private final PermissionSchema json;private final ServiceCertificateIdentity identities;
    private final ServiceCertificateIdentity.TrustSource trust;private final Transport transport;
    public TaskProfessionalSupervisor(DataSource data,PermissionSchema json,ServiceCertificateIdentity identities,ServiceCertificateIdentity.TrustSource trust,com.ruoyi.medical.identity.ServiceClientTlsStore.ContextSource tls){this(new JdbcTemplate(data),json,identities,trust,new HttpsTransport(tls));}
    TaskProfessionalSupervisor(JdbcTemplate db,PermissionSchema json,ServiceCertificateIdentity identities,ServiceCertificateIdentity.TrustSource trust,Transport transport){this.db=db;db.setQueryTimeout(5);this.json=json;this.identities=identities;this.trust=trust;this.transport=transport;}

    /** Private detached immutable request is not admission or authorization to run. */
    public static final class Request {
        private final TaskProfessionalSupervisor owner;private final String phase,session,tenant,project,actor,classification;
        private final JsonNode binding,deployment;private final String deploymentHash;
        private Request(TaskProfessionalSupervisor owner,String phase,String session,JsonNode binding,TaskProfessionalRuntimeService.Deployment deployment,String project,String actor,String classification){this.owner=owner;this.phase=phase;this.session=session;this.binding=binding.deepCopy();this.deployment=deployment.value();deploymentHash=deployment.sha256();this.tenant=text(binding,"tenant_id");this.project=project;this.actor=actor;this.classification=classification;}
    }
    /** Only an authenticated, bounded independent fetch can create this one-use transfer proof. */
    public static final class Inspection {
        private final TaskProfessionalSupervisor owner;private final Request request;private final JsonNode evidence;
        private final byte[] export;private final SSLSession tls;private boolean consumed;
        private Inspection(TaskProfessionalSupervisor owner,Request request,JsonNode evidence,byte[] export,SSLSession tls){this.owner=owner;this.request=request;this.evidence=evidence.deepCopy();this.export=export==null?null:export.clone();this.tls=tls;}
    }
    public Request snapshotAdmission(TaskProfessionalRuntimeService.Envelope e)throws Exception {
        e.requireProductionReady();var i=e.admission().formalProof().identity();var b=json.object().put("tenant_id",i.tenant()).put("run_id",i.run()).put("step_id",i.step()).put("action_id",i.action()).put("lease_generation",Long.parseLong(i.generation())).put("input_sha256",e.inputSha256()).put("step_envelope_sha256",e.payloadSha256());
        return new Request(this,"ADMITTED",e.sessionId(),b,e.deployment(),i.project(),i.actor(),classification(i.tenant(),i.run()));
    }
    public Request snapshotClosure(TaskProfessionalRuntimeService.Session s)throws Exception {
        s.requireUnchanged();var i=s.admission().formalProof().identity();return new Request(this,"CLOSED",s.sessionId(),s.binding(),s.deployment(),i.project(),i.actor(),classification(i.tenant(),i.run()));
    }
    Request snapshotTermination(TaskRunTerminationFacts.PendingClosure original,TaskProfessionalRuntimeService.Deployment deployment)throws Exception {
        original.requireUnchanged();var start=original.start();same("N02",text(start,"node_kind"));
        same("TASK",text(start,"purpose"));same(deployment.sha256(),text(start,"deployment_sha256"));
        return new Request(this,"TERMINATED",original.session(),start.required("binding"),deployment,original.project(),original.actor(),original.classification());
    }
    void recordTermination(TaskRunTerminationFacts.PendingClosure original,TaskProfessionalRuntimeService.Deployment deployment,Inspection inspection)throws Exception {
        var request=snapshotTermination(original,deployment);accept(request,inspection);
        var admitted=stored(request,"ADMITTED");same(text(inspection.evidence,"admitted_receipt_id"),str(admitted,"receipt_id"));
        same(text(inspection.evidence,"admitted_sha256"),str(admitted,"evidence_sha256"));
        // A genuine independent owned waitid receipt closes admission. Worker
        // abort booleans and cancellation acknowledgements never reach record().
        original.requireUnchanged();
        db.update("UPDATE professional_runtime_session SET admission_state='CLOSED',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND session_id=? AND admission_state='OPEN'",request.tenant,request.session);
        record(request,inspection.evidence);
    }
    Request snapshotPending(TaskRunTerminationFacts.PendingLaunch original,TaskProfessionalRuntimeService.Deployment deployment,String phase)throws Exception {
        original.requireUnchanged();same("N02",original.kind());if(!Set.of("ADMITTED","TERMINATED").contains(phase))deny();same(deployment.sha256(),original.deploymentHash());
        return new Request(this,phase,original.session(),original.binding(),deployment,original.project(),original.actor(),original.classification());
    }
    void recordPending(TaskRunTerminationFacts.PendingLaunch original,TaskProfessionalRuntimeService.Deployment deployment,Inspection admission,Inspection termination)throws Exception {
        var admittedRequest=snapshotPending(original,deployment,"ADMITTED");var closedRequest=snapshotPending(original,deployment,"TERMINATED");accept(admittedRequest,admission);accept(closedRequest,termination);
        same(text(admission.evidence,"receipt_id"),text(termination.evidence,"admitted_receipt_id"));same(hash(admission.evidence),text(termination.evidence,"admitted_sha256"));same(admission.evidence.required("process_unit"),termination.evidence.required("process_unit"));
        original.requireUnchanged();record(admittedRequest,admission.evidence);record(closedRequest,termination.evidence);
    }
    Optional<JsonNode> originalPendingTermination(TaskRunTerminationFacts.PendingLaunch original,TaskProfessionalRuntimeService.Deployment deployment)throws Exception {
        var request=snapshotPending(original,deployment,"TERMINATED");if(db.queryForList("SELECT receipt_id FROM professional_runtime_supervision WHERE tenant_id=? AND session_id=? AND phase='TERMINATED' LIMIT 1 FOR UPDATE",request.tenant,request.session).isEmpty())return Optional.empty();
        var terminated=stored(request,"TERMINATED");var admitted=stored(request,"ADMITTED");var value=JSON.readTree(str(terminated,"canonical_evidence"));same(str(admitted,"receipt_id"),text(value,"admitted_receipt_id"));same(str(admitted,"evidence_sha256"),text(value,"admitted_sha256"));same(JSON.readTree(str(admitted,"canonical_evidence")).required("process_unit"),value.required("process_unit"));original.requireUnchanged();return Optional.of(value);
    }
    Inspection inspectPendingAdmission(Request request)throws Exception {
        if(request==null||request.owner!=this||!"ADMITTED".equals(request.phase)||TransactionSynchronizationManager.isActualTransactionActive())deny();trusted(request);
        var fetched=transport.get(endpoint(request,"/internal/v1/supervision/"+request.session+"/admission-original"),maximum(request));verifyPeer(request,fetched.session());var evidence=JSON.readTree(fetched.bytes());validate(request,evidence);return new Inspection(this,request,evidence,null,fetched.session());
    }
    public Inspection inspect(Request request)throws Exception {
        if(request==null||request.owner!=this||TransactionSynchronizationManager.isActualTransactionActive())deny();trusted(request);
        String path="/internal/v1/supervision/"+request.session+"/"+request.phase;
        var fetched="TERMINATED".equals(request.phase)?transport.post(endpoint(request,"/internal/v1/supervision/"+request.session+"/terminate"),ContentReadAuthorization.canonicalBytes(Map.of("binding",request.binding)),maximum(request)):transport.get(endpoint(request,path),maximum(request));verifyPeer(request,fetched.session());
        var evidence=JSON.readTree(fetched.bytes());validate(request,evidence);byte[] export=null;
        if("CLOSED".equals(request.phase)){
            var actual=transport.get(endpoint(request,"/internal/v1/supervision/"+request.session+"/export"),maximum(request));verifyPeer(request,actual.session());
            export=actual.bytes();var pin=evidence.required("session_export");same(text(pin,"sha256"),WireValues.sha256(export));same(number(pin,"size_bytes"),(long)export.length);
            // It is actual independently reread bounded bytes, not the worker's file/hash list.
            if(export.length==0)bad();
        }
        verifyPeer(request,fetched.session());return new Inspection(this,request,evidence,export,fetched.session());
    }
    public Admitted admit(TaskProfessionalRuntimeService.Envelope envelope,Inspection inspection)throws Exception {
        var request=snapshotAdmission(envelope);accept(request,inspection);var row=record(request,inspection.evidence);return new Admitted(this,envelope,null,request,row);
    }
    public Closed close(TaskProfessionalRuntimeService.Session session,Inspection inspection)throws Exception {
        var request=snapshotClosure(session);accept(request,inspection);var admitted=stored(request,"ADMITTED");
        same(text(inspection.evidence,"admitted_receipt_id"),str(admitted,"receipt_id"));same(text(inspection.evidence,"admitted_sha256"),str(admitted,"evidence_sha256"));
        var row=record(request,inspection.evidence);return new Closed(this,session,request,row,inspection.export);
    }
    public Admitted requireAdmitted(TaskProfessionalRuntimeService.Envelope envelope)throws Exception {var request=snapshotAdmission(envelope);return new Admitted(this,envelope,null,request,stored(request,"ADMITTED"));}
    public Admitted requireAdmitted(TaskProfessionalRuntimeService.Session session)throws Exception {session.requireUnchanged();var request=snapshotClosure(session);return new Admitted(this,null,session,request,stored(request,"ADMITTED"));}
    /** CLOSED bytes must always be independently reread outside the transaction. An old row alone
     * can never manufacture a session-export content proof after restart. */
    public Closed requireClosed(TaskProfessionalRuntimeService.Session session,Inspection reread)throws Exception {
        var request=snapshotClosure(session);accept(request,reread);var row=stored(request,"CLOSED");same(str(row,"evidence_sha256"),hash(reread.evidence));return new Closed(this,session,request,row,reread.export);
    }
    public static final class Admitted {
        private final TaskProfessionalSupervisor owner;private final TaskProfessionalRuntimeService.Envelope envelope;private final TaskProfessionalRuntimeService.Session session;private final Request request;private final Map<String,Object> row;private final String fingerprint;
        private Admitted(TaskProfessionalSupervisor owner,TaskProfessionalRuntimeService.Envelope envelope,TaskProfessionalRuntimeService.Session session,Request request,Map<String,Object> row)throws Exception{this.owner=owner;this.envelope=envelope;this.session=session;this.request=request;this.row=Map.copyOf(row);fingerprint=hash(AgentPublicationWitness.tree(row));}
        public void requireUnchanged()throws Exception{if(envelope!=null)envelope.requireUnchanged();else session.requireUnchanged();same(fingerprint,hash(AgentPublicationWitness.tree(owner.stored(request,"ADMITTED"))));}
        public String receiptId(){active();return str(row,"receipt_id");}public String sha256(){active();return str(row,"evidence_sha256");}public String sessionId(){active();return request.session;}public JsonNode binding(){active();return request.binding.deepCopy();}public String deploymentSha256(){active();return request.deploymentHash;}
        private void active(){if(envelope!=null)TaskProfessionalRuntimeService.current(envelope.admission());else TaskProfessionalRuntimeService.current(session.admission());}
    }
    public static final class Closed {
        private final TaskProfessionalSupervisor owner;private final TaskProfessionalRuntimeService.Session session;private final Request request;private final Map<String,Object> row;private final String fingerprint;private final byte[] export;
        private Closed(TaskProfessionalSupervisor owner,TaskProfessionalRuntimeService.Session session,Request request,Map<String,Object> row,byte[] export)throws Exception{this.owner=owner;this.session=session;this.request=request;this.row=Map.copyOf(row);fingerprint=hash(AgentPublicationWitness.tree(row));if(export==null)deny();this.export=export.clone();}
        public void requireUnchanged()throws Exception{session.requireUnchanged();same(fingerprint,hash(AgentPublicationWitness.tree(owner.stored(request,"CLOSED"))));var pin=JSON.readTree(str(row,"canonical_evidence")).required("session_export");same(WireValues.sha256(export),text(pin,"sha256"));same((long)export.length,number(pin,"size_bytes"));}
        public void requireSuccessfulExit()throws Exception{requireUnchanged();successfulExit(JSON.readTree(str(row,"canonical_evidence")));}
        public byte[] sessionExport()throws Exception{requireUnchanged();return export.clone();}public String receiptId(){active();return str(row,"receipt_id");}public String sha256(){active();return str(row,"evidence_sha256");}public String sessionId(){active();return request.session;}public JsonNode binding(){active();return request.binding.deepCopy();}public String deploymentSha256(){active();return request.deploymentHash;}
        private void active(){TaskProfessionalRuntimeService.current(session.admission());}
    }
    /** Historical closure proves the original predecessor, not a live predecessor lease,
     * content-read permission, current process, or newly fetched export bytes. */
    public OriginalClosure requireClosedOriginal(EnterpriseAccess.Admission admission,JsonNode binding,String session,String receiptId,String sha256,TaskProfessionalRuntimeService.Deployment deployment)throws Exception {
        originalActive(admission);ProfessionalRuntimeObservations.jsonBinding(binding);WireValues.uuid(session);WireValues.uuid(receiptId);WireValues.hash(sha256);same(admission.context().tenant_id(),text(binding,"tenant_id"));
        var original=one(db.queryForList("SELECT * FROM professional_runtime_session WHERE tenant_id=? AND session_id=? AND run_id=? AND step_id=? AND action_id=? AND lease_generation=? AND input_sha256=? AND envelope_sha256=? AND created_by=? AND admission_state='CLOSED' LIMIT 2 FOR UPDATE",admission.context().tenant_id(),session,text(binding,"run_id"),text(binding,"step_id"),text(binding,"action_id"),number(binding,"lease_generation"),text(binding,"input_sha256"),text(binding,"step_envelope_sha256"),admission.actorId()));
        var start=JSON.readTree(str(original,"canonical_start"));same(hash(start),str(original,"start_sha256"));same(binding,start.required("binding"));same(session,text(start,"session_id"));same("TASK",text(start,"purpose"));same("N02",text(start,"node_kind"));same(deployment.workerService(),str(original,"service_uri"));same(text(deployment.value(),"runtime_manifest_sha256"),text(start,"runtime_manifest_sha256"));
        var request=new Request(this,"CLOSED",session,binding,deployment,str(original,"project_id"),admission.actorId(),classification(admission.context().tenant_id(),text(binding,"run_id")));var row=stored(request,"CLOSED");same(receiptId,str(row,"receipt_id"));same(sha256,str(row,"evidence_sha256"));var admitted=stored(request,"ADMITTED");var closed=JSON.readTree(str(row,"canonical_evidence"));same(str(admitted,"receipt_id"),text(closed,"admitted_receipt_id"));same(str(admitted,"evidence_sha256"),text(closed,"admitted_sha256"));
        return new OriginalClosure(this,admission,binding,session,receiptId,sha256,deployment,hash(Map.of("session",AgentPublicationWitness.tree(original),"admitted",AgentPublicationWitness.tree(admitted),"closed",AgentPublicationWitness.tree(row))),closed);
    }
    public static final class OriginalClosure {
        private final TaskProfessionalSupervisor owner;private final EnterpriseAccess.Admission admission;private final JsonNode binding,evidence;private final String session,receipt,digest,fingerprint;private final TaskProfessionalRuntimeService.Deployment deployment;
        private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();private final Map<Object,Object> resources=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private OriginalClosure(TaskProfessionalSupervisor owner,EnterpriseAccess.Admission admission,JsonNode binding,String session,String receipt,String digest,TaskProfessionalRuntimeService.Deployment deployment,String fingerprint,JsonNode evidence){this.owner=owner;this.admission=admission;this.binding=binding.deepCopy();this.session=session;this.receipt=receipt;this.digest=digest;this.deployment=deployment;this.fingerprint=fingerprint;this.evidence=evidence.deepCopy();if(resources.isEmpty())deny();}
        private void active(){originalActive(admission);if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent())deny();var current=TransactionSynchronizationManager.getResourceMap();if(current.size()!=resources.size()||resources.entrySet().stream().anyMatch(e->current.get(e.getKey())!=e.getValue()))deny();}
        public void requireUnchanged()throws Exception{active();same(fingerprint,owner.requireClosedOriginal(admission,binding,session,receipt,digest,deployment).fingerprint);}
        public void requireSuccessfulExit()throws Exception{requireUnchanged();successfulExit(evidence);}
        public String receiptId(){active();return receipt;}public String sha256(){active();return digest;}public String sessionId(){active();return session;}public JsonNode binding(){active();return binding.deepCopy();}public String deploymentSha256(){active();return deployment.sha256();}
    }
    private static void successfulExit(JsonNode evidence){var exit=evidence.required("exit");same("OWNED_WAITID",text(exit,"kind"));same("EXITED",text(exit,"code"));same(0L,number(exit,"status"));}
    private static void originalActive(EnterpriseAccess.Admission a){TaskNodeProducerService.requireAdmission(a);if(a.formalWorker())a.formalProof().requireActive();else if(a.controlWorker())a.controlProof().requireActive();else deny();}
    public static final class ModelSnapshot {
        private final TaskProfessionalSupervisor owner;private final Request session;private final String requestId,admittedId,admittedHash;
        private ModelSnapshot(TaskProfessionalSupervisor owner,Request session,String requestId,String admittedId,String admittedHash){this.owner=owner;this.session=session;this.requestId=requestId;this.admittedId=admittedId;this.admittedHash=admittedHash;}
    }
    public static final class ModelInspection {
        private final TaskProfessionalSupervisor owner;private final ModelSnapshot snapshot;private final JsonNode evidence;private final byte[] bytes;private final SSLSession tls;
        private ModelInspection(TaskProfessionalSupervisor owner,ModelSnapshot snapshot,JsonNode evidence,byte[] bytes,SSLSession tls){this.owner=owner;this.snapshot=snapshot;this.evidence=evidence.deepCopy();this.bytes=bytes.clone();this.tls=tls;}
    }
    public ModelSnapshot snapshotModelRequest(TaskProfessionalRuntimeService.Session session,String requestId)throws Exception {
        WireValues.uuid(requestId);session.requireUnchanged();var request=snapshotClosure(session);var admitted=stored(request,"ADMITTED");return new ModelSnapshot(this,request,requestId,str(admitted,"receipt_id"),str(admitted,"evidence_sha256"));
    }
    public ModelInspection inspectModelRequest(ModelSnapshot snapshot)throws Exception {
        if(snapshot==null||snapshot.owner!=this||TransactionSynchronizationManager.isActualTransactionActive())deny();var request=snapshot.session;
        var current=transport.get(endpoint(request,"/internal/v1/supervision/"+request.session+"/ADMITTED"),maximum(request));verifyPeer(request,current.session());same(snapshot.admittedHash,hash(JSON.readTree(current.bytes())));
        var fetched=transport.get(endpoint(request,"/internal/v1/supervision/"+request.session+"/request-"+snapshot.requestId),maximum(request));verifyPeer(request,fetched.session());var evidence=JSON.readTree(fetched.bytes());
        ProfessionalRuntimeObservations.closed(evidence,Set.of("schema_version","receipt_id","request_id","ordinal","session_id","binding","deployment_sha256","supervision_receipt_id","supervision_sha256","method","route","raw_body_sha256","raw_size_bytes","projection_schema_ref","projection_schema_version","projection_schema_sha256","body_sha256","body_base64","peer_pid","peer_start_ticks","peer_exe_sha256"));
        same("1.0.0",text(evidence,"schema_version"));WireValues.uuid(text(evidence,"receipt_id"));same(snapshot.requestId,text(evidence,"request_id"));same(request.session,text(evidence,"session_id"));same(request.binding,evidence.required("binding"));same(request.deploymentHash,text(evidence,"deployment_sha256"));same(snapshot.admittedId,text(evidence,"supervision_receipt_id"));same(snapshot.admittedHash,text(evidence,"supervision_sha256"));same("POST",text(evidence,"method"));same("/v1/model-contexts",text(evidence,"route"));if(number(evidence,"ordinal")<1||number(evidence,"ordinal")>10000)bad();
        var processes=JSON.readTree(current.bytes()).required("process_unit").required("processes");boolean found=false;for(var process:processes)if(number(process,"pid")==number(evidence,"peer_pid")&&number(process,"start_ticks")==number(evidence,"peer_start_ticks")&&text(process,"exe_sha256").equals(text(evidence,"peer_exe_sha256")))found=true;if(!found)deny();
        byte[] bytes=Base64.getDecoder().decode(text(evidence,"body_base64"));if(bytes.length<1||bytes.length>maximum(request))bad();same(text(evidence,"body_sha256"),WireValues.sha256(bytes));same(text(evidence,"raw_body_sha256"),WireValues.sha256(bytes));same(number(evidence,"raw_size_bytes"),(long)bytes.length);same("urn:medical:task-invocation-context:1.0.0#/$defs/SdkProjection",text(evidence,"projection_schema_ref"));same("1.0.0",text(evidence,"projection_schema_version"));same("30378eaad8c98486077b47d995a4da5cd218d57eab20d86d69ddabda1139a01b",text(evidence,"projection_schema_sha256"));validateProjection(bytes);if(!Arrays.equals(bytes,ContentReadAuthorization.canonicalBytes(JSON.readTree(bytes))))bad();return new ModelInspection(this,snapshot,evidence,bytes,fetched.session());
    }
    public ModelRequest requireModelRequest(TaskProfessionalRuntimeService.Session session,ModelInspection inspection)throws Exception {
        if(inspection==null||inspection.owner!=this)deny();session.requireUnchanged();var current=snapshotClosure(session);sameRequest(current,inspection.snapshot.session);verifyPeer(current,inspection.tls);var admitted=stored(current,"ADMITTED");same(inspection.snapshot.admittedId,str(admitted,"receipt_id"));same(inspection.snapshot.admittedHash,str(admitted,"evidence_sha256"));
        return new ModelRequest(this,session.admission(),current,inspection.evidence,inspection.bytes);
    }
    /** Observed SDK projection only. This does not authorize a model, source or data
     * transmission; the invocation factory must still prove every original permission. */
    public static final class ModelRequest {
        private final TaskProfessionalSupervisor owner;private final EnterpriseAccess.Admission admission;private final Request session;private final JsonNode evidence;private final byte[] bytes;private final String digest;
        private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();private final Map<Object,Object> resources=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private ModelRequest(TaskProfessionalSupervisor owner,EnterpriseAccess.Admission admission,Request session,JsonNode evidence,byte[] bytes)throws Exception{this.owner=owner;this.admission=admission;this.session=session;this.evidence=evidence.deepCopy();this.bytes=bytes.clone();digest=hash(evidence);if(resources.isEmpty())deny();}
        public void requireUnchanged()throws Exception{requireUnchanged(admission);}
        public void requireUnchanged(EnterpriseAccess.Admission current)throws Exception{
            TaskProfessionalRuntimeService.current(current);var i=current.formalProof().identity();same(session.tenant,i.tenant());same(session.project,i.project());same(session.actor,i.actor());same(text(session.binding,"run_id"),i.run());same(text(session.binding,"step_id"),i.step());same(number(session.binding,"lease_generation"),Long.parseLong(i.generation()));same(text(session.deployment,"worker_service_uri"),i.workerService());
            same(text(session.binding,"input_sha256"),one(owner.db.query("SELECT input_sha256 FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? AND lease_generation=? LIMIT 2 FOR UPDATE",(r,n)->r.getString(1),i.tenant(),i.run(),i.step(),i.generation())));
            // Same segment lifetime is required. A fresh segment obtains a fresh
            // independently authenticated inspection; changing only the action is OK.
            if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent())deny();var actual=TransactionSynchronizationManager.getResourceMap();if(actual.size()!=resources.size()||resources.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))deny();
            var row=one(owner.db.queryForList("SELECT canonical_start,start_sha256 FROM professional_runtime_session WHERE tenant_id=? AND session_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? AND input_sha256=? AND envelope_sha256=? AND service_uri=? AND created_by=? AND admission_state='OPEN' LIMIT 2 FOR UPDATE",session.tenant,session.session,session.project,i.run(),i.step(),i.generation(),text(session.binding,"input_sha256"),text(session.binding,"step_envelope_sha256"),i.workerService(),i.actor()));var start=JSON.readTree(str(row,"canonical_start"));same(hash(start),str(row,"start_sha256"));same(session.binding,start.required("binding"));same(session.session,text(start,"session_id"));
            var admitted=owner.stored(session,"ADMITTED");same(str(admitted,"receipt_id"),text(evidence,"supervision_receipt_id"));same(str(admitted,"evidence_sha256"),text(evidence,"supervision_sha256"));same(digest,hash(evidence));same(WireValues.sha256(bytes),text(evidence,"body_sha256"));
        }
        private void active(){TaskProfessionalRuntimeService.current(admission);if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent())deny();}
        public JsonNode canonicalReceipt()throws Exception{requireUnchanged();return evidence.deepCopy();}public byte[] bytes()throws Exception{requireUnchanged();return bytes.clone();}public String requestId(){active();return text(evidence,"request_id");}public long ordinal(){active();return number(evidence,"ordinal");}public String sessionId(){active();return session.session;}public JsonNode binding(){active();return session.binding.deepCopy();}public String receiptId(){active();return text(evidence,"receipt_id");}public String sha256(){active();return digest;}
    }
    static void validateProjection(byte[] bytes)throws Exception {
        // Jackson also accepts UTF-16/32 byte streams. The wire contract does not:
        // decode strictly, then require the very same UTF-8 JCS bytes, not a rewrite.
        String utf8=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        var value=JSON.readTree(utf8);if(!Arrays.equals(bytes,ContentReadAuthorization.canonicalBytes(value)))bad();
        byte[] schemaBytes;try(var stream=Objects.requireNonNull(TaskProfessionalSupervisor.class.getResourceAsStream("/task-invocation-context.schema.json"))){schemaBytes=stream.readAllBytes();}
        same("30378eaad8c98486077b47d995a4da5cd218d57eab20d86d69ddabda1139a01b",WireValues.sha256(schemaBytes));
        var schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(JSON.readTree(schemaBytes),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        var projection=schema.getSubSchema(new com.networknt.schema.JsonNodePath(com.networknt.schema.PathType.JSON_POINTER).append("$defs").append("SdkProjection"));projection.initializeValidators();if(!projection.validate(value).isEmpty())bad();
    }
    private void accept(Request current,Inspection inspection)throws Exception {
        if(inspection==null||inspection.owner!=this||inspection.consumed)deny();sameRequest(current,inspection.request);verifyPeer(current,inspection.tls);validate(current,inspection.evidence);inspection.consumed=true;
    }
    private static void sameRequest(Request a,Request b){same(a.phase,b.phase);same(a.session,b.session);same(a.tenant,b.tenant);same(a.project,b.project);same(a.actor,b.actor);same(a.classification,b.classification);same(a.binding,b.binding);same(a.deployment,b.deployment);same(a.deploymentHash,b.deploymentHash);}
    private void verifyPeer(Request request,SSLSession session)throws Exception {
        var peer=identities.requireServer(session);same(text(request.deployment,"supervisor_service_uri"),peer.serviceUri());same(text(request.deployment,"supervisor_certificate_sha256"),peer.certificateSha256());same(text(request.deployment,"supervisor_trust_revision"),peer.trustRevision());
        if(peer.serviceUri().equals(text(request.deployment,"worker_service_uri")))deny();trusted(request);
    }
    private void trusted(Request request)throws Exception {
        var current=trust.read();same(text(request.deployment,"supervisor_trust_revision"),current.revision());var certificates=current.registeredCertificates().get(text(request.deployment,"supervisor_service_uri"));if(certificates==null||!certificates.contains(text(request.deployment,"supervisor_certificate_sha256")))deny();
    }
    static void validate(Request request,JsonNode n)throws Exception {
        var fields=new HashSet<>(COMMON);if(request.phase.equals("ADMITTED"))fields.add("gate");else {fields.addAll(Set.of("admitted_receipt_id","admitted_sha256","exit"));if(!request.phase.equals("TERMINATED"))fields.add("session_export");}ProfessionalRuntimeObservations.closed(n,fields);
        same("1.0.0",text(n,"schema_version"));same(request.phase,text(n,"phase"));same(request.session,text(n,"session_id"));same(request.binding,n.required("binding"));ProfessionalRuntimeObservations.jsonBinding(n.required("binding"));WireValues.uuid(text(n,"receipt_id"));WireValues.uuid(text(n,"supervisor_instance_id"));
        same(request.deploymentHash,text(n,"deployment_sha256"));same(text(request.deployment,"deployment_revision"),text(n,"deployment_revision"));same(text(request.deployment,"runtime_manifest_sha256"),text(n,"runtime_manifest_sha256"));
        Instant at=Instant.parse(text(n,"inspected_at"));if(at.isAfter(Instant.now().plusSeconds(5)))bad();validateUnit(n.required("process_unit"));
        if(request.phase.equals("ADMITTED")){validateStoredAdmission(n);}
        else {
            WireValues.uuid(text(n,"admitted_receipt_id"));WireValues.hash(text(n,"admitted_sha256"));var exit=n.required("exit");ProfessionalRuntimeObservations.closed(exit,Set.of("kind","boot_id","process_unit_sha256","root_pid","code","status","unit_state","observed_exits"));same("OWNED_WAITID",text(exit,"kind"));WireValues.uuid(text(exit,"boot_id"));same(text(n.required("process_unit"),"boot_id"),text(exit,"boot_id"));same(hash(n.required("process_unit")),text(exit,"process_unit_sha256"));same("EMPTY",text(exit,"unit_state"));if(!Set.of("EXITED","KILLED","DUMPED").contains(text(exit,"code"))||number(exit,"status")<0||number(exit,"status")>255)bad();
            var observed=exit.required("observed_exits");if(!observed.isArray())bad();var expectedProcesses=new HashMap<Long,Long>();for(var process:n.required("process_unit").required("processes"))expectedProcesses.put(number(process,"pid"),number(process,"start_ticks"));
            if(!expectedProcesses.containsKey(number(exit,"root_pid"))||observed.size()!=expectedProcesses.size())bad();var seen=new HashSet<Long>();for(var item:observed){ProfessionalRuntimeObservations.closed(item,Set.of("pid","start_ticks","kind"));same("PIDFD_EXIT",text(item,"kind"));long pid=number(item,"pid");if(!seen.add(pid)||!Objects.equals(expectedProcesses.get(pid),number(item,"start_ticks")))bad();}
            if(!request.phase.equals("TERMINATED")){var pin=n.required("session_export");ProfessionalRuntimeObservations.closed(pin,Set.of("sha256","size_bytes"));WireValues.hash(text(pin,"sha256"));if(number(pin,"size_bytes")<1||number(pin,"size_bytes")>maximum(request))bad();}
        }
    }
    /** Historical shape/pin integrity only. The caller must authenticate the original
     *099/083 journals separately; this creates no live admission or send authority. */
    static void validateOriginalAdmission(JsonNode binding,JsonNode evidence,JsonNode deployment,String deploymentHash,String session)throws Exception {
        ProfessionalRuntimeObservations.jsonBinding(binding);WireValues.uuid(session);
        var pinned=new TaskProfessionalRuntimeService.Deployment(deployment,deploymentHash);
        validate(new Request(null,"ADMITTED",session,binding,pinned,"historical-integrity-only","historical-integrity-only","historical-integrity-only"),evidence);
    }
    /** Historical CLOSED shape and original pinned deployment integrity only. The
     * caller must authenticate original session/083/audit/outbox and current consumption separately. */
    static void validateOriginalClosure(JsonNode binding,JsonNode evidence,JsonNode deployment,String deploymentHash,String session)throws Exception {
        ProfessionalRuntimeObservations.jsonBinding(binding);WireValues.uuid(session);
        var pinned=new TaskProfessionalRuntimeService.Deployment(deployment,deploymentHash);
        validate(new Request(null,"CLOSED",session,binding,pinned,"historical-integrity-only","historical-integrity-only","historical-integrity-only"),evidence);
    }
    /** Closed stored-evidence shape only, with no current deployment/trust claim.
     * Original099 authentication,083 linkage and audit/outbox remain caller duties. */
    static void validateStoredAdmission(JsonNode evidence){
        var fields=new HashSet<>(COMMON);fields.add("gate");ProfessionalRuntimeObservations.closed(evidence,fields);
        same("1.0.0",text(evidence,"schema_version"));same("ADMITTED",text(evidence,"phase"));same("SDK_READY_LOCKED",text(evidence,"gate"));
        for(String field:List.of("receipt_id","session_id","deployment_revision","supervisor_instance_id"))WireValues.uuid(text(evidence,field));
        for(String field:List.of("deployment_sha256","runtime_manifest_sha256"))WireValues.hash(text(evidence,field));
        ProfessionalRuntimeObservations.jsonBinding(evidence.required("binding"));
        if(Instant.parse(text(evidence,"inspected_at")).isAfter(Instant.now().plusSeconds(5)))bad();validateUnit(evidence.required("process_unit"));
    }
    /** Exact low-level evidence is generated by the pinned Linux supervisor, not the host. */
    static void validateUnit(JsonNode unit){
        if(!unit.isObject()||!unit.has("boot_id")||!unit.has("cgroup_path")||!unit.has("cgroup_inode")||!unit.has("processes")||!unit.has("runtime_files")||!unit.has("profile_files"))bad();WireValues.uuid(text(unit,"boot_id"));if(!text(unit,"cgroup_path").startsWith("/sys/fs/cgroup/")||number(unit,"cgroup_inode")<1)bad();
        var processes=unit.required("processes");if(!processes.isArray()||processes.size()<2||processes.size()>256)bad();Set<Long> pids=new HashSet<>();Set<String> executables=new HashSet<>();
        for(var process:processes){long pid=number(process,"pid");if(pid<1||number(process,"start_ticks")<1||!pids.add(pid))bad();WireValues.hash(text(process,"exe_sha256"));executables.add(text(process,"exe_sha256"));if(number(process,"uid")<1||number(process,"gid")<1||number(process,"no_new_privs")!=1||number(process,"seccomp")!=2||!"0000000000000000".equals(text(process,"cap_eff"))||text(process,"lsm_profile").isBlank()||!text(process,"lsm_profile").endsWith(" (enforce)"))bad();var namespaces=process.required("namespaces");ProfessionalRuntimeObservations.closed(namespaces,Set.of("mnt","pid","net","user","ipc","uts","cgroup"));for(var ns:namespaces)if(!ns.isIntegralNumber()||!ns.canConvertToLong()||ns.longValue()<1)bad();}
        // An inspected launcher alone cannot attest a subsequently exec'd runtime.
        if(executables.size()<2)bad();for(String kind:List.of("runtime_files","profile_files")){var files=unit.required(kind);if(!files.isArray()||files.isEmpty()||files.size()>256)bad();Set<String> paths=new HashSet<>();for(var file:files){if(!text(file,"path").startsWith("/")||!paths.add(text(file,"path")))bad();WireValues.hash(text(file,"sha256"));}}
    }
    private Map<String,Object> record(Request request,JsonNode evidence)throws Exception {
        var previous=db.queryForList("SELECT * FROM professional_runtime_supervision WHERE tenant_id=? AND session_id=? AND phase=? LIMIT 2 FOR UPDATE",request.tenant,request.session,request.phase);
        if(!previous.isEmpty()){var row=stored(request,request.phase);same(hash(evidence),str(row,"evidence_sha256"));return row;}
        var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));String audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString(),receipt=text(evidence,"receipt_id"),body=json.canonical(evidence),digest=hash(evidence),action="task.professional."+request.phase.toLowerCase(Locale.ROOT);
        changed(db.update("INSERT INTO audit_event (tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",request.tenant,request.project,audit,request.actor,action,body,request.session,receipt,json.manifestSha256(),request.actor,now,request.classification));
        changed(db.update("INSERT INTO outbox_event (tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES (?,?,?,?,?,?,?)",request.tenant,event,request.session,request.phase.equals("ADMITTED")?1:2,action,body,now));
        changed(db.update("INSERT INTO professional_runtime_supervision (tenant_id,project_id,run_id,step_id,lease_generation,session_id,receipt_id,phase,supervisor_service_uri,certificate_sha256,trust_revision,deployment_revision,deployment_sha256,input_sha256,envelope_sha256,canonical_evidence,evidence_sha256,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",request.tenant,request.project,text(request.binding,"run_id"),text(request.binding,"step_id"),number(request.binding,"lease_generation"),request.session,receipt,request.phase,text(request.deployment,"supervisor_service_uri"),text(request.deployment,"supervisor_certificate_sha256"),text(request.deployment,"supervisor_trust_revision"),text(request.deployment,"deployment_revision"),request.deploymentHash,text(request.binding,"input_sha256"),text(request.binding,"step_envelope_sha256"),body,digest,audit,event,json.manifestSha256(),request.actor,now,request.classification));return stored(request,request.phase);
    }
    private Map<String,Object> stored(Request request,String phase)throws Exception {
        if(!TransactionSynchronizationManager.isActualTransactionActive())deny();trusted(request);var row=one(db.queryForList("SELECT * FROM professional_runtime_supervision WHERE tenant_id=? AND session_id=? AND phase=? LIMIT 2 FOR UPDATE",request.tenant,request.session,phase));
        for(var entry:Map.of("tenant_id",request.tenant,"project_id",request.project,"run_id",text(request.binding,"run_id"),"step_id",text(request.binding,"step_id"),"session_id",request.session,"phase",phase,"created_by",request.actor,"classification",request.classification,"schema_version","1.0.0","schema_manifest_sha256",json.manifestSha256()).entrySet())same(entry.getValue(),str(row,entry.getKey()));
        same(number(request.binding,"lease_generation"),((Number)row.get("lease_generation")).longValue());same(request.deploymentHash,str(row,"deployment_sha256"));same(text(request.deployment,"deployment_revision"),str(row,"deployment_revision"));same(text(request.deployment,"supervisor_service_uri"),str(row,"supervisor_service_uri"));same(text(request.deployment,"supervisor_certificate_sha256"),str(row,"certificate_sha256"));same(text(request.deployment,"supervisor_trust_revision"),str(row,"trust_revision"));same(text(request.binding,"input_sha256"),str(row,"input_sha256"));same(text(request.binding,"step_envelope_sha256"),str(row,"envelope_sha256"));
        var evidence=JSON.readTree(str(row,"canonical_evidence"));same(hash(evidence),str(row,"evidence_sha256"));same(json.canonical(evidence),str(row,"canonical_evidence"));same(text(evidence,"receipt_id"),str(row,"receipt_id"));
        var phaseRequest=new Request(this,phase,request.session,request.binding,new TaskProfessionalRuntimeService.Deployment(request.deployment,request.deploymentHash),request.project,request.actor,request.classification);validate(phaseRequest,evidence);
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",request.tenant,str(row,"audit_id")));var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",request.tenant,str(row,"event_id")));String action="task.professional."+phase.toLowerCase(Locale.ROOT);
        for(String field:List.of("tenant_id","project_id","created_by","classification","schema_version","schema_manifest_sha256","created_at"))same(row.get(field),audit.get(field));same(request.actor,str(audit,"subject_id"));same(action,str(audit,"action"));same("SUCCESS",str(audit,"result"));if(audit.get("reason_code")!=null)bad();same(request.session,str(audit,"request_id"));same(str(row,"receipt_id"),str(audit,"trace_id"));same(str(row,"canonical_evidence"),json.canonical(JSON.readTree(str(audit,"resource_ref"))));same(request.tenant,str(event,"tenant_id"));same(request.session,str(event,"aggregate_id"));same((long)(phase.equals("ADMITTED")?1:2),((Number)event.get("aggregate_revision")).longValue());same(action,str(event,"event_type"));same(row.get("created_at"),event.get("occurred_at"));same(str(row,"canonical_evidence"),json.canonical(JSON.readTree(str(event,"payload"))));return row;
    }
    private String classification(String tenant,String run){return one(db.query("SELECT classification FROM task_run WHERE tenant_id=? AND run_id=? LIMIT 2",(r,n)->r.getString(1),tenant,run));}
    private static URI endpoint(Request r,String path){URI origin=URI.create(text(r.deployment,"supervisor_endpoint"));if(!"https".equals(origin.getScheme())||origin.getHost()==null||origin.getUserInfo()!=null||origin.getRawQuery()!=null||origin.getRawFragment()!=null||!Set.of("","/").contains(origin.getPath()))deny();return URI.create(origin.toString().replaceAll("/$","")+path);}
    private static int maximum(Request r){return r.deployment.required("maximum_object_bytes").intValue();}
    record Fetch(byte[] bytes,SSLSession session){Fetch{bytes=bytes.clone();}@Override public byte[] bytes(){return bytes.clone();}}
    interface Transport {Fetch get(URI uri,int maximum)throws Exception;default Fetch post(URI uri,byte[] body,int maximum)throws Exception{throw new java.io.IOException("TERMINATION_TRANSPORT_UNAVAILABLE");}}
    private static final class HttpsTransport implements Transport {
        private final com.ruoyi.medical.identity.ServiceClientTlsStore.ContextSource source;
        HttpsTransport(com.ruoyi.medical.identity.ServiceClientTlsStore.ContextSource tls){this.source=Objects.requireNonNull(tls);}
        public Fetch get(URI uri,int maximum)throws Exception{return send(uri,null,maximum);}
        public Fetch post(URI uri,byte[] body,int maximum)throws Exception{return send(uri,body,maximum);}
        private Fetch send(URI uri,byte[] body,int maximum)throws Exception{
            // New context/cache for every request: pooled or resumed sessions cannot
            // bypass current peer pinning, full chain validation and signed CRLs.
            SSLContext tls=source.create();
            var parameters=new javax.net.ssl.SSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");parameters.setProtocols(new String[]{"TLSv1.3"});
            var client=HttpClient.newBuilder().sslContext(tls).sslParameters(parameters).followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
            var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Accept","application/json");if(body==null)builder.GET();else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body));
            var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var stream=response.body()){if((response.statusCode()==404||response.statusCode()==409)&&(uri.getPath().endsWith("/CLOSED")||uri.getPath().endsWith("/terminate")))throw new java.io.IOException("ORIGINAL_PROCESS_CLOSURE_PENDING");if(response.statusCode()!=200)deny();byte[] bytes=stream.readNBytes(maximum+1);if(bytes.length==0||bytes.length>maximum)bad();return new Fetch(bytes,response.sslSession().orElseThrow());}
        }
    }
    private static String text(JsonNode n,String key){if(!n.required(key).isTextual())bad();return n.required(key).textValue();}private static long number(JsonNode n,String key){var v=n.required(key);if(!v.isIntegralNumber()||!v.canConvertToLong())bad();return v.longValue();}private static String str(Map<String,Object> row,String key){if(!(row.get(key) instanceof String))bad();return (String)row.get(key);}private static String hash(Object value)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}
    private static <T>T one(List<T> rows){if(rows.size()!=1)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return rows.get(0);}private static void changed(int count){if(count!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}private static void same(Object a,Object b){if(a instanceof JsonNode left&&b instanceof JsonNode right){if(!FormalTaskNodeAuthority.sameEvidence(left,right))bad();}else if(!Objects.equals(a,b))bad();}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
