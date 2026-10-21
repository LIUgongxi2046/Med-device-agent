package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.ContentReadMapper;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** Formal, exact-chunk source reader. Receipts are versioned source-read audit records, not artifact content receipts. */
@Component
public final class TaskResearchSourceReader {
    static final String ACTION="task.source.read.receipt";
    static final String KIND="TASK_SOURCE_READ_RECEIPT";
    static final int MAXIMUM_OBJECT_BYTES=16*1024*1024, MAXIMUM_TEXT_BYTES=256*1024;
    private final TaskFieldSourceAuthority processing;
    private final SourceUploadTransport transport;
    private final ContentReadMapper receipts;
    private final PermissionSchema json;
    private final SourceProcessingResult parser;
    private final TaskSourceReadReceiptSchema schema;
    private final SourceReadAuthority sourceAuthority;
    private final RetrievalPolicy policy;
    private final org.springframework.jdbc.core.JdbcTemplate db;

    @org.springframework.beans.factory.annotation.Autowired
    public TaskResearchSourceReader(TaskFieldSourceAuthority processing,SourceUploadTransport transport,
            ContentReadMapper receipts,PermissionSchema json,SourceReadAuthority sourceAuthority,RetrievalPolicy policy,
            @org.springframework.beans.factory.annotation.Qualifier("masterDataSource") javax.sql.DataSource data) throws Exception {
        this(processing,transport,receipts,json,sourceAuthority,policy,new org.springframework.jdbc.core.JdbcTemplate(data));
    }
    TaskResearchSourceReader(TaskFieldSourceAuthority processing,SourceUploadTransport transport,
            ContentReadMapper receipts,PermissionSchema json,SourceReadAuthority sourceAuthority,RetrievalPolicy policy,
            org.springframework.jdbc.core.JdbcTemplate db) throws Exception {
        this.processing=processing;this.transport=transport;this.receipts=receipts;this.json=json;
        parser=new SourceProcessingResult();schema=new TaskSourceReadReceiptSchema(json);this.sourceAuthority=sourceAuthority;this.policy=policy;this.db=db;
    }

    Read read(TaskResearchToolAccess tools,TaskResearchToolAccess.Handle handle,DatasetProtocol.EvidenceRef evidence,String cursor) throws Exception {
        requestSha256(evidence,cursor);
        if(cursor!=null) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT); // no invented continuation
        State initial=state(tools,handle,evidence);
        S3ObjectReader.Admission current=()->ThrowingSupplier.of(()->{same(initial,state(tools,handle,evidence));return null;}).get();
        // Original bytes establish the processor's actual immutable input, never substring text.
        consume(initial.physical().original(),initial.tenant(),initial.actor(),false,current);
        byte[] parsed=consume(initial.physical().parsed(),initial.tenant(),initial.actor(),true,current);
        String text=literal(initial.physical(),evidence,parsed);
        current.requireCurrent();
        String receiptId=tools.execute(handle,a->ThrowingSupplier.of(()-> {
            same(initial,state(tools,handle,a,evidence));
            String id=UUID.randomUUID().toString();var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("auditId",id);
            var now=Objects.requireNonNull(receipts.databaseTime(values));
            var body=body(initial,evidence,tools.actionId(handle),id,text,now.toInstant(ZoneOffset.UTC).toString());
            body.set("action_request",json.tree(tools.action(handle)));body.put("token_sha256",tools.tokenSha256(handle));
            var document=schema.seal(body);String canonical=json.canonical(document);
            var audit=new ContentReadProtocol.AuditRow(initial.tenant(),tools.fixed(handle).projectId(),id,a.actorId(),ACTION,canonical,
                    "SUCCESS",null,tools.actionId(handle),body.required("identity").required("node_input").required("run_id").textValue(),
                    evidence.schemaVersion(),schema.manifest(evidence.schemaVersion()),a.actorId(),now,initial.classification());
            values.put("audit",audit);if(receipts.saveAudit(values)!=1) mismatch();
            var saved=one(receipts.audit(values));requireAudit(saved,document,initial,tools.fixed(handle).projectId());
            same(initial,state(tools,handle,a,evidence));return id;
        }).get());
        current.requireCurrent();
        return new Read(evidence,text,receiptId,scope(evidence),current);
    }

    /** The completion consumer verifies every selected reference, but never converts reads into question coverage. */
    public void requireReceipts(TaskResearchToolAccess tools,TaskResearchToolAccess.Handle handle,List<String> receiptIds,
            List<DatasetProtocol.EvidenceRef> selectedRefs) throws Exception {
        tools.execute(handle,a->ThrowingSupplier.of(()-> {
            requireReceipts(a,tools.inputProof(handle,a),receiptIds,selectedRefs).requireUnchanged();return null;
        }).get());
    }

    public VerifiedReceipts requireReceipts(EnterpriseAccess.Admission admission,TaskNodeInputAuthority.Verified input,
            List<String> receiptIds,List<DatasetProtocol.EvidenceRef> selectedRefs) throws Exception {
        input.requireUnchanged();
        return requireReceipts(admission,new NodeInput(input.manifest(),input.inputId(),input.receiptSha256(),input.inputSha256(),admission.formalProof().identity().generation(),admission.formalProof().identity().workerService(),null,input::requireUnchanged),receiptIds,selectedRefs);
    }
    public VerifiedReceipts requireReceipts(EnterpriseAccess.Admission admission,TaskResearchCompletionAuthority.Input input,
            List<String> receiptIds,List<DatasetProtocol.EvidenceRef> selectedRefs) throws Exception {
        input.requireUnchanged();
        return requireReceipts(admission,new NodeInput(input.manifest(),input.inputId(),input.receiptSha256(),input.inputSha256(),Long.toString(input.generation()),input.workerService(),input.sessionId(),input::requireUnchanged),receiptIds,selectedRefs);
    }
    private VerifiedReceipts requireReceipts(EnterpriseAccess.Admission admission,NodeInput input,List<String> receiptIds,List<DatasetProtocol.EvidenceRef> selectedRefs) throws Exception {
        if(!admission.formalWorker()&&!admission.controlWorker())deny();
        return new VerifiedReceipts(admission,input,List.copyOf(receiptIds),List.copyOf(selectedRefs),verifyCurrent(admission,input,receiptIds,selectedRefs));
    }
    private JsonNode verifyCurrent(EnterpriseAccess.Admission a,NodeInput input,List<String> ids,List<DatasetProtocol.EvidenceRef> refs) throws Exception {
        input.requireUnchanged();
        if(ids.size()>512||refs.size()>512||new HashSet<>(ids).size()!=ids.size()||new HashSet<>(refs).size()!=refs.size())deny();
        var matched=new HashSet<DatasetProtocol.EvidenceRef>();var pins=json.object().putArray("receipts");
        for(String id:ids) {
            WireValues.uuid(id);var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("auditId",id);
            var row=one(receipts.audit(values));var document=schema.read(row.resource());var ref=schema.evidence(document.required("body").required("evidence_ref"));
            if(!refs.contains(ref))deny();var current=state(a,input,ref,document.required("body").required("identity"));requireAudit(row,document,current,input.projectId());
            requireOriginalAction(a,input,row,document.required("body"));matched.add(ref);
            pins.addObject().put("read_receipt_id",id).put("receipt_sha256",document.required("receipt_sha256").textValue());
        }
        if(!matched.equals(new HashSet<>(refs)))deny();input.requireUnchanged();return pins;
    }
    private void requireOriginalAction(EnterpriseAccess.Admission a,NodeInput input,ContentReadProtocol.AuditRow row,JsonNode body) throws Exception {
        var request=body.required("action_request");var identity=body.required("identity");
        String expectedArguments=TaskResearchToolAccess.argumentsSha256("AT-02:read",requestSha256(schema.evidence(body.required("evidence_ref")),null));
        if(!expectedArguments.equals(request.required("arguments_sha256").textValue())||!input.inputSha256().equals(request.required("input_sha256").textValue())
                ||!input.runId().equals(request.required("run_id").textValue())||!input.stepId().equals(request.required("step_id").textValue())
                ||!body.required("action_id").equals(request.required("action_id"))||!identity.required("lease_generation").textValue().equals(request.required("generation").asText()))mismatch();
        var rows=db.query("""
            SELECT b.action_id FROM step_action_binding b JOIN principal_token t
              ON t.tenant_id=b.tenant_id AND t.project_key=b.project_key AND t.run_id=b.run_id AND t.step_id=b.step_id AND t.lease_generation=b.lease_generation
            WHERE b.tenant_id=? AND b.project_id=? AND b.run_id=? AND b.step_id=? AND b.action_id=? AND b.input_sha256=?
              AND b.kind=? AND b.arguments_sha256=? AND b.tool_version=? AND b.lease_generation=? AND b.created_by=?
              AND t.token_sha256=? AND t.service_uri=? AND t.created_by=? AND t.issued_at<=? AND t.expires_at>?
              AND (t.revoked_at IS NULL OR t.revoked_at>?) AND JSON_CONTAINS(t.action_scope,JSON_QUOTE(?))
            LIMIT 2 FOR UPDATE
            """,(r,n)->r.getString(1),input.tenantId(),input.projectId(),input.runId(),input.stepId(),row.requestId(),input.inputSha256(),
            request.required("kind").textValue(),expectedArguments,request.required("tool_version").textValue(),identity.required("lease_generation").textValue(),a.actorId(),
            body.required("token_sha256").textValue(),identity.required("worker_service").textValue(),a.actorId(),row.createdAt(),row.createdAt(),row.createdAt(),request.required("action").textValue());
        if(rows.size()!=1||!row.requestId().equals(rows.get(0)))deny();
    }
    public final class VerifiedReceipts {
        private final EnterpriseAccess.Admission admission;private final NodeInput input;
        private final List<String> ids;private final List<DatasetProtocol.EvidenceRef> refs;private final JsonNode pins;
        private VerifiedReceipts(EnterpriseAccess.Admission a,NodeInput input,List<String> ids,List<DatasetProtocol.EvidenceRef> refs,JsonNode pins){this.admission=a;this.input=input;this.ids=ids;this.refs=refs;this.pins=pins.deepCopy();}
        public JsonNode canonicalPins() throws Exception {requireUnchanged();return pins.deepCopy();}
        public void requireUnchanged() throws Exception {if(admission.formalWorker())admission.formalProof().requireActive();else admission.controlProof().requireActive();if(!pins.equals(verifyCurrent(admission,input,ids,refs)))mismatch();}
    }

    private State state(TaskResearchToolAccess tools,TaskResearchToolAccess.Handle handle,DatasetProtocol.EvidenceRef evidence) throws Exception {
        return tools.execute(handle,a->ThrowingSupplier.of(()->state(tools,handle,a,evidence)).get());
    }
    private State state(TaskResearchToolAccess tools,TaskResearchToolAccess.Handle handle,EnterpriseAccess.Admission a,
            DatasetProtocol.EvidenceRef evidence) throws Exception {
        var fixed=tools.fixed(handle);var pin=fixed.source(evidence.sourceVersion());
        var source=tools.requireSource(a,fixed,pin);
        var identity=(ObjectNode)tools.sourceReceiptIdentity(handle,a);
        identity.put("session_id",requireSession(a,identity,true));
        return state(a,fixed.tenantId(),fixed.projectId(),pin,identity,evidence,source);
    }
    private State state(EnterpriseAccess.Admission a,NodeInput input,DatasetProtocol.EvidenceRef evidence,JsonNode originalIdentity) throws Exception {
        input.requireUnchanged();
        if(!input.generation().equals(originalIdentity.required("lease_generation").textValue())||!input.workerService().equals(originalIdentity.required("worker_service").textValue())
                ||input.sessionId()!=null&&!input.sessionId().equals(originalIdentity.required("session_id").textValue()))mismatch();
        var pin=input.fixedSources().stream().filter(p->p.sourceVersion().equals(evidence.sourceVersion())).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.ACCESS_DENIED));
        var source=sourceAuthority.require(a,new SourceReadProtocol.ReadRequirement(pin.sourceVersion(),pin.rightsRevision(),input.projectId(),input.snapshotId(),pin.parseVersion(),null,
                SourceReadProtocol.Use.FORMAL,pin.purpose(),policy.environment(),policy.region(),null));
        var selection=source.selection();if(selection==null||!pin.indexVersion().equals(selection.indexVersion())||!pin.bindingRevision().equals(selection.bindingRevision())||!pin.approvalRevision().equals(selection.approvalRevision()))mismatch();
        var identity=json.object().put("input_id",input.inputId()).put("input_sha256",input.inputSha256()).put("input_receipt_sha256",input.receiptSha256()).put("actor_id",a.actorId());
        identity.set("node_input",input.manifest());identity.set("worker_service",originalIdentity.required("worker_service"));identity.set("lease_generation",originalIdentity.required("lease_generation"));identity.set("session_id",originalIdentity.required("session_id"));
        requireSession(a,identity,false);
        return state(a,input.tenantId(),input.projectId(),pin,identity,evidence,source);
    }
    private State state(EnterpriseAccess.Admission a,String tenant,String project,TaskNodeInputProtocol.SourceSelection pin,
            JsonNode identity,DatasetProtocol.EvidenceRef evidence,SourceReadProtocol.VerifiedSource source) throws Exception {
        schema.evidence(json.tree(evidence));
        if(!tenant.equals(evidence.tenantId())||!project.equals(evidence.projectId())||!pin.parseVersion().equals(evidence.parseVersion())
                ||!pin.approvalRevision().equals(evidence.approvalRevision())||!source.source().sourceId().equals(evidence.sourceId()))mismatch();
        var physical=processing.processing(tenant,pin.sourceVersion(),pin.parseVersion(),source.source().originalSha256());
        if(source.source().ownerProjectId()!=null&&!source.source().ownerProjectId().equals(physical.projectId()))mismatch();
        String sourceAuth=WireValues.sha256(ContentReadAuthorization.canonicalBytes(source));
        String provenance=WireValues.sha256(ContentReadAuthorization.canonicalBytes(physical));
        return new State(tenant,a.actorId(),source.source().ownerProjectId(),source.source().classification(),identity,sourceAuth,provenance,physical);
    }

    private String requireSession(EnterpriseAccess.Admission a,JsonNode identity,boolean active) {
        var node=identity.required("node_input");
        var found=db.query("""
            SELECT session_id FROM professional_runtime_session
            WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? AND service_uri=?
              AND input_sha256=? AND created_by=? AND (?=FALSE OR admission_state='OPEN')
              AND admission_state IN ('OPEN','CLOSED') LIMIT 2 FOR UPDATE
            """,(r,n)->r.getString(1),node.required("tenant_id").textValue(),node.required("project_id").textValue(),
            node.required("run_id").textValue(),node.required("step_id").textValue(),identity.required("lease_generation").textValue(),
            identity.required("worker_service").textValue(),identity.required("input_sha256").textValue(),a.actorId(),active);
        if(found.size()!=1)deny();WireValues.uuid(found.get(0));
        if(identity.has("session_id")&&!identity.required("session_id").textValue().equals(found.get(0)))deny();return found.get(0);
    }
    @FunctionalInterface private interface Current {void require()throws Exception;}
    private record NodeInput(JsonNode manifest,String inputId,String receiptSha256,String inputSha256,String generation,String workerService,String sessionId,Current current) {
        void requireUnchanged()throws Exception{current.require();}
        String tenantId(){return manifest.required("tenant_id").textValue();}String projectId(){return manifest.required("project_id").textValue();}
        String runId(){return manifest.required("run_id").textValue();}String stepId(){return manifest.required("step_id").textValue();}
        String snapshotId(){return manifest.required("snapshot_id").textValue();}
        List<TaskNodeInputProtocol.SourceSelection> fixedSources(){var values=new ArrayList<TaskNodeInputProtocol.SourceSelection>();for(var p:manifest.required("fixed_sources"))values.add(new TaskNodeInputProtocol.SourceSelection(
            p.required("source_version").textValue(),p.required("parse_version").textValue(),p.required("index_version").textValue(),p.required("approval_revision").textValue(),p.required("binding_revision").textValue(),p.required("rights_revision").textValue(),p.required("purpose").textValue()));return List.copyOf(values);}
    }

    private byte[] consume(InterfaceProtocol.ObjectRef reference,String tenant,String actor,boolean keep,S3ObjectReader.Admission current) throws Exception {
        long size=WireValues.revision(reference.sizeBytes()).longValueExact();
        if(size<1 || size>MAXIMUM_OBJECT_BYTES) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        return transport.readTaskDocument(tenant,actor,MAXIMUM_OBJECT_BYTES,current,(reader,guard)-> {
            try(var object=reader.readVerified(reference,guard)) {
                if(!reference.equals(object.reference())) mismatch();
                var digest=java.security.MessageDigest.getInstance("SHA-256");byte[] bytes=keep?new byte[(int)size]:null;
                for(int start=0;start<size;) {
                    guard.requireCurrent();int length=(int)Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-start);
                    byte[] chunk=object.chunk(start,length);if(chunk.length!=length) mismatch();
                    digest.update(chunk);if(keep)System.arraycopy(chunk,0,bytes,start,length);start+=length;guard.requireCurrent();
                }
                String hash=java.util.HexFormat.of().formatHex(digest.digest());
                if(!reference.sha256().equals(hash)||!object.fullSha256().equals(hash)) mismatch();guard.requireCurrent();return bytes;
            }
        });
    }

    String literal(TaskFieldSourceAuthority.Processing expected,DatasetProtocol.EvidenceRef evidence,byte[] bytes) throws Exception {
        return reviewLiteral(expected,evidence,bytes).orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
    }
    /** A genuine complete parsed bundle can prove a missing or mismatched requested locator.
     * Only that deterministic outcome is retained for N05. Corrupt bytes, provenance,
     * processor failures and resource limits still throw; they are never semantic findings. */
    java.util.Optional<String> reviewLiteral(TaskFieldSourceAuthority.Processing expected,DatasetProtocol.EvidenceRef evidence,byte[] bytes) throws Exception {
        if("2.0.0".equals(evidence.schemaVersion()))return reviewLiteralV2(expected,json.tree(evidence),bytes);
        if(!expected.parsed().sha256().equals(WireValues.sha256(bytes)) || !expected.parsed().sizeBytes().equals(Integer.toString(bytes.length))) mismatch();
        var receipt=(ObjectNode)expected.result().deepCopy();
        if(!json.tree(expected.parsed()).equals(receipt.required("object_ref"))) mismatch();
        receipt.remove(List.of("object_ref","confirmed_by_worker","confirmed_lease_generation"));
        var bundle=parser.verify(expected.command(),receipt,bytes).bundle();
        if(!"COMPLETE".equals(bundle.required("status").textValue()) || !bundle.required("issues").isEmpty()
                || !expected.attempt().equals(bundle.required("attempt_id").textValue())
                || !expected.original().sha256().equals(bundle.required("original_sha256").textValue())
                || !expected.processorManifest().equals(bundle.required("processor_manifest"))) mismatch();
        JsonNode selected=null;for(var chunk:bundle.required("chunks")) if(evidence.chunkId().equals(chunk.required("chunk_id").textValue())) {
            if(selected!=null) mismatch();selected=chunk;
        }
        if(selected==null || !evidence.quoteHash().equals(selected.required("quote_hash").textValue())
                || !TaskSourceIdentity.same(json.tree(evidence.locator()),selected.required("locator"))
                || !evidence.locatorKind().name().equals(selected.required("locator").required("kind").textValue())) return java.util.Optional.empty();
        String text=selected.required("raw_text").textValue();byte[] literal=text.getBytes(StandardCharsets.UTF_8);
        if(literal.length<1 || literal.length>MAXIMUM_TEXT_BYTES) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        if(!WireValues.sha256(literal).equals(evidence.quoteHash())) mismatch();return java.util.Optional.of(text);
    }

    /** Additive v2 formal reader: same original/bundle proof, explicit versioned evidence. */
    java.util.Optional<String> reviewLiteralV2(TaskFieldSourceAuthority.Processing expected,JsonNode evidence,byte[] bytes) throws Exception {
        SourceEvidenceV2.validate(SchemaCatalogue.fromClasspath(),evidence);
        if(!expected.parsed().sha256().equals(WireValues.sha256(bytes)) || !expected.parsed().sizeBytes().equals(Integer.toString(bytes.length))) mismatch();
        var receipt=(ObjectNode)expected.result().deepCopy();
        if(!json.tree(expected.parsed()).equals(receipt.required("object_ref"))) mismatch();
        receipt.remove(List.of("object_ref","confirmed_by_worker","confirmed_lease_generation"));
        var bundle=parser.verify(expected.command(),receipt,bytes).bundle();
        if(!"COMPLETE".equals(bundle.required("status").textValue()) || !bundle.required("issues").isEmpty()
                || !expected.attempt().equals(bundle.required("attempt_id").textValue())
                || !expected.original().sha256().equals(bundle.required("original_sha256").textValue())
                || !expected.processorManifest().equals(bundle.required("processor_manifest"))
                || !evidence.required("source_version").equals(bundle.required("source_version"))
                || !evidence.required("parse_version").equals(bundle.required("parse_version"))) mismatch();
        JsonNode selected=null;
        for(var chunk:bundle.required("chunks")) if(evidence.required("chunk_id").equals(chunk.required("chunk_id"))) {
            if(selected!=null) mismatch(); selected=chunk;
        }
        if(selected==null || !evidence.required("quote_hash").equals(selected.required("quote_hash"))
                || !TaskSourceIdentity.same(evidence.required("locator"),selected.required("locator"))) return java.util.Optional.empty();
        String text=selected.required("raw_text").textValue();byte[] literal=text.getBytes(StandardCharsets.UTF_8);
        if(literal.length<1 || literal.length>MAXIMUM_TEXT_BYTES) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        if(!WireValues.sha256(literal).equals(evidence.required("quote_hash").textValue())) mismatch();
        return java.util.Optional.of(text);
    }

    private ObjectNode body(State state,DatasetProtocol.EvidenceRef evidence,String action,String id,String text,String time) throws Exception {
        var body=json.object().put("schema_version",evidence.schemaVersion()).put("kind",KIND).put("receipt_id",id).put("action_id",action);
        var request=json.object();request.set("evidence_ref",json.tree(evidence));request.putNull("cursor");body.set("request",request);
        body.set("identity",state.identity());body.set("evidence_ref",json.tree(evidence));
        if(state.ownerProject()==null)body.putNull("source_owner_project_id");else body.put("source_owner_project_id",state.ownerProject());
        body.put("processing_project_id",state.physical().projectId());body.put("processing_attempt_id",state.physical().attempt());
        body.set("original_object",json.tree(state.physical().original()));body.set("parsed_object",json.tree(state.physical().parsed()));
        body.set("original_read",fullRead(state.physical().original(),"ORIGINAL_BINARY"));body.set("parsed_read",fullRead(state.physical().parsed(),"PARSED_BUNDLE"));
        body.put("processing_command_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(state.physical().command())));
        body.put("processing_result_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(state.physical().result())));
        body.put("processor_manifest_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(state.physical().processorManifest())));
        body.put("source_authorization_sha256",state.sourceAuthorization()).put("provenance_sha256",state.provenance());
        var range=json.object().put("representation","PARSED_CHUNK_UTF8").put("start_byte","0")
                .put("end_byte",Integer.toString(text.getBytes(StandardCharsets.UTF_8).length)).put("content_sha256",evidence.quoteHash()).put("complete",true);
        body.set("delivered_range",range);body.put("scope_sha256",scope(evidence));body.put("created_at",time).put("classification",state.classification());
        return body;
    }

    private ObjectNode fullRead(InterfaceProtocol.ObjectRef object,String representation) {
        return json.object().put("representation",representation).put("start_byte","0").put("end_byte",object.sizeBytes())
                .put("content_sha256",object.sha256()).put("complete",true);
    }

    private void requireAudit(ContentReadProtocol.AuditRow row,JsonNode document,State current,String project) throws Exception {
        schema.verify(document);var body=document.required("body");
        if(!current.tenant().equals(row.tenantId()) || !project.equals(row.projectId()) || !current.actor().equals(row.subjectId())
                || !current.actor().equals(row.createdBy()) || !ACTION.equals(row.action()) || !"SUCCESS".equals(row.result()) || row.reasonCode()!=null
                || !body.required("schema_version").asText().equals(row.schemaVersion()) || !schema.manifest(row.schemaVersion()).equals(row.schemaManifest())
                || !body.required("receipt_id").textValue().equals(row.auditId()) || !body.required("action_id").textValue().equals(row.requestId())
                || !body.required("identity").required("node_input").required("run_id").textValue().equals(row.traceId())
                || !body.required("created_at").textValue().equals(row.createdAt().toInstant(ZoneOffset.UTC).toString())
                || !current.classification().equals(row.classification()) || !body.required("classification").textValue().equals(row.classification())
                || !current.identity().equals(body.required("identity")) || !current.sourceAuthorization().equals(body.required("source_authorization_sha256").textValue())
                || !current.provenance().equals(body.required("provenance_sha256").textValue())
                || !json.tree(current.physical().original()).equals(body.required("original_object"))
                || !json.tree(current.physical().parsed()).equals(body.required("parsed_object"))) mismatch();
        if(!json.canonical(document).equals(row.resource()) || !current.physical().projectId().equals(body.required("processing_project_id").textValue())
                || !current.physical().attempt().equals(body.required("processing_attempt_id").textValue())
                || !Objects.equals(current.ownerProject(),body.required("source_owner_project_id").isNull()?null:body.required("source_owner_project_id").textValue())
                || !WireValues.sha256(ContentReadAuthorization.canonicalBytes(current.physical().command())).equals(body.required("processing_command_sha256").textValue())
                || !WireValues.sha256(ContentReadAuthorization.canonicalBytes(current.physical().result())).equals(body.required("processing_result_sha256").textValue())
                || !WireValues.sha256(ContentReadAuthorization.canonicalBytes(current.physical().processorManifest())).equals(body.required("processor_manifest_sha256").textValue())) mismatch();
    }

    public static String requestSha256(DatasetProtocol.EvidenceRef evidence,String cursor) throws Exception {
        Objects.requireNonNull(evidence);var request=new LinkedHashMap<String,Object>();request.put("evidence_ref",evidence);request.put("cursor",cursor);
        return TaskSourceIdentity.sha256(request);
    }
    private String scope(DatasetProtocol.EvidenceRef evidence) throws Exception{return TaskSourceIdentity.sha256(evidence);}
    public static final class Read {
        private final DatasetProtocol.EvidenceRef evidence;private final String content,receipt,scope;private final S3ObjectReader.Admission current;
        private Read(DatasetProtocol.EvidenceRef evidence,String content,String receipt,String scope,S3ObjectReader.Admission current){this.evidence=evidence;this.content=content;this.receipt=receipt;this.scope=scope;this.current=current;}
        public DatasetProtocol.EvidenceRef evidenceRef(){current.requireCurrent();return evidence;}
        public String content(){current.requireCurrent();return content;}
        public String readReceiptId(){current.requireCurrent();return receipt;}
        public String requestedScopeSha256(){current.requireCurrent();return scope;}
        public String deliveredScopeSha256(){current.requireCurrent();return scope;}
        /** Only the requested chunk, never all questions or the full source. */
        public boolean complete(){current.requireCurrent();return true;}
    }
    private record State(String tenant,String actor,String ownerProject,String classification,JsonNode identity,
            String sourceAuthorization,String provenance,TaskFieldSourceAuthority.Processing physical) { }
    private static void same(State expected,State actual){if(!expected.equals(actual))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
    private static <T>T one(List<T> rows){if(rows.size()!=1)mismatch();return rows.get(0);}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
