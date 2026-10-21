package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Immutable pre-RN01 proposed-target intent and physical-attempt ledger. The reserved artifact
 * identity is not a registered artifact. Only private current producer/mapping/read proofs can
 * enter this boundary. No caller-supplied recovery flag, target JSON or object reference is authority. */
public final class TaskFieldTargetLedger {
    static final String OPERATION = "task.field_target.produce";
    private final JdbcTemplate db;
    private final TaskFieldTargetSchema schema;
    private final PermissionSchema json;
    private final TaskFieldSourceReader reader;
    private final TaskService tasks;
    private final java.util.function.IntSupplier maximumTargetBytes;

    public TaskFieldTargetLedger(DataSource data, TaskFieldTargetSchema schema, PermissionSchema json,
            TaskFieldSourceReader reader,TaskService tasks,TaskFieldTargetPolicy policy) { this(new JdbcTemplate(data),schema,json,reader,tasks,()->{policy.requireConfigured();return policy.maximumTargetBytes();}); }
    TaskFieldTargetLedger(JdbcTemplate db, TaskFieldTargetSchema schema, PermissionSchema json,
            TaskFieldSourceReader reader) { this(db,schema,json,reader,null); }
    TaskFieldTargetLedger(JdbcTemplate db, TaskFieldTargetSchema schema, PermissionSchema json,
            TaskFieldSourceReader reader,TaskService tasks) {this(db,schema,json,reader,tasks,()->8_388_608);}
    TaskFieldTargetLedger(JdbcTemplate db,TaskFieldTargetSchema schema,PermissionSchema json,
            TaskFieldSourceReader reader,TaskService tasks,java.util.function.IntSupplier maximumTargetBytes){
        this.maximumTargetBytes=Objects.requireNonNull(maximumTargetBytes);this.tasks=tasks;this.db=Objects.requireNonNull(db); this.schema=Objects.requireNonNull(schema);
        this.json=Objects.requireNonNull(json); this.reader=Objects.requireNonNull(reader); db.setQueryTimeout(5);
    }

    record Row(Map<String,Object> columns, JsonNode intent, String payload) {
        Row {
            var copy=new LinkedHashMap<String,Object>();
            columns.forEach((key,value)->copy.put(key,value instanceof java.sql.Timestamp t?t.toLocalDateTime():value));
            columns=Collections.unmodifiableMap(copy); intent=intent.deepCopy();
        }
        @Override public JsonNode intent(){return intent.deepCopy();}
        JsonNode manifest(){return intent.required("target_manifest").deepCopy();}
    }

    /** Constructor inaccessible outside this ledger. Initial I/O authority becomes usable only
     * after the intent, entries, journal and singleton attempt transaction commits successfully. */
    public static final class Attempt implements RuntimeObjectAttempt {
        private final TaskFieldTargetLedger owner;
        private final Row row;
        private final String attemptId;
        private final boolean prior;
        private final Thread thread=Thread.currentThread();
        private final AtomicBoolean claimed=new AtomicBoolean();
        private volatile boolean committed;
        private Attempt(TaskFieldTargetLedger owner,Row row,String attemptId,boolean prior) {
            this.owner=owner;this.row=row;this.attemptId=attemptId;this.prior=prior;
            if(!TransactionSynchronizationManager.isActualTransactionActive()
                    || !TransactionSynchronizationManager.isSynchronizationActive()) denied();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){committed=true;}
                @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)committed=false;}
            });
        }
        public String tenantId(){return text(row.columns(),"tenant_id");}
        public String actorId(){return text(row.columns(),"created_by");}
        public String taskId(){return text(row.columns(),"task_id");}
        public String writeId(){return text(row.columns(),"write_id");}
        public String plannedArtifactId(){return text(row.columns(),"planned_artifact_id");}
        public String artifactId(){return plannedArtifactId();}
        public String operationId(){return text(row.columns(),"operation_id");}
        public String auditId(){return text(row.columns(),"audit_id");}
        public String eventId(){return text(row.columns(),"event_id");}
        public String idempotencyKey(){return text(row.columns(),"idempotency_key");}
        public String requestSha256(){return text(row.columns(),"request_sha256");}
        public String intentSha256(){return text(row.columns(),"intent_sha256");}
        public String attemptId(){return attemptId;}
        public JsonNode targetManifest(){return row.manifest();}
        public JsonNode intent(){return row.intent();}
        public String expectedObjectKey(){return objectKey(tenantId(),taskId(),writeId(),plannedArtifactId());}
        public S3ObjectWriter.WriteIntent writeIntent(){return new S3ObjectWriter.WriteIntent(writeId(),
                text(row.columns(),"object_key"),text(row.columns(),"target_sha256"),
                Integer.toString(bytes().length),"application/json");}
        public byte[] bytes(){return row.payload().getBytes(StandardCharsets.UTF_8);}
        public boolean priorAttempt(){return prior;}
        public TransferMode claimTransferMode(){
            if(!committed || thread!=Thread.currentThread() || TransactionSynchronizationManager.isActualTransactionActive()) denied();
            return prior || !claimed.compareAndSet(false,true)?TransferMode.RECOVER_ONLY:TransferMode.INITIAL_WRITE;
        }
    }

    Attempt prepare(TaskFieldTargetBasis.Verified basis,TaskFieldMappingService.Verified mapping,
            List<TaskFieldSourceReader.Read> reads,JsonNode produce,String key,String context) throws Exception {
        var checked=check(basis,mapping,reads,produce,key,context);
        var a=basis.admission();
        // Serialize original keys for this actor before selecting/inserting immutable intent.
        one(db.queryForList("SELECT user_id FROM tenant_member WHERE tenant_id=? AND user_id=? FOR UPDATE",a.context().tenant_id(),a.actorId()));
        var originals=rows(a,key); Row row;
        if(originals.isEmpty()) {
            if(!operations(a,key).isEmpty()) bad();
            if(!db.queryForList("SELECT write_id FROM task_field_target_intent WHERE tenant_id=? AND created_by=? AND task_id=? AND mapping_revision=? LIMIT 2",
                    a.context().tenant_id(),a.actorId(),basis.task().head().taskId(),mapping.mappingRevision()).isEmpty()) conflict();
            row=create(basis,mapping,checked,key,context);
        } else {
            row=verify(a,one(originals));
            if(!requestHash(a,context,checked.request()).equals(text(row.columns(),"request_sha256"))) conflict();
        }
        requireProof(basis,mapping,checked,row,context);
        var result=attempt(a,row,originals.isEmpty());
        basis.requireUnchanged(); mapping.requireUnchanged();
        return result;
    }

    /** Recovery never inserts an intent, occurrence, attempt, or replacement object identity. */
    Attempt recover(TaskFieldTargetBasis.Verified basis,TaskFieldMappingService.Verified mapping,
            List<TaskFieldSourceReader.Read> reads,JsonNode produce,String key,String context) throws Exception {
        var checked=check(basis,mapping,reads,produce,key,context); var a=basis.admission();
        var row=verify(a,one(rows(a,key)));
        if(!requestHash(a,context,checked.request()).equals(text(row.columns(),"request_sha256"))) conflict();
        requireProof(basis,mapping,checked,row,context);
        var result=attempt(a,row,false); basis.requireUnchanged();mapping.requireUnchanged();return result;
    }

    Row require(TaskFieldTargetBasis.Verified basis,TaskFieldMappingService.Verified mapping,
            List<TaskFieldSourceReader.Read> reads,Attempt original,String context) throws Exception {
        if(original==null || original.owner!=this || !original.committed || original.thread!=Thread.currentThread()) denied();
        var checked=check(basis,mapping,reads,original.intent().required("original_request"),original.idempotencyKey(),context);
        var a=basis.admission();
        if(!a.actorId().equals(original.actorId()) || !a.context().tenant_id().equals(original.tenantId())) denied();
        var row=verify(a,one(rows(a,original.idempotencyKey())));
        if(!row.intent().equals(original.row.intent()) || !row.payload().equals(original.row.payload())) bad();
        requireProof(basis,mapping,checked,row,context);
        if(!attemptRecord(a,row,false).equals(original.attemptId())) bad();
        basis.requireUnchanged();mapping.requireUnchanged();return row;
    }

    /** Read-only original metadata. A request-shaped JSON value never creates transfer authority. */
    Optional<Row> findOriginal(EnterpriseAccess.Admission a,String taskId,String key) throws Exception {
        readTask(a,taskId);json.headers(a.context().context_revision(),key);
        var records=rows(a,key);if(records.isEmpty())return Optional.empty();
        var row=verify(a,one(records));if(!taskId.equals(text(row.columns(),"task_id")))denied();
        attemptRecord(a,row,false);return Optional.of(row);
    }

    /** Selects one exact mapping identity; the caller must establish whether that mapping is current.
     * This is metadata only, including after successful registration, and cannot mint a new Attempt. */
    Optional<Row> findForMapping(EnterpriseAccess.Admission a,String taskId,String mappingRevision,String mappingHash) throws Exception {
        readTask(a,taskId);WireValues.uuid(mappingRevision);WireValues.hash(mappingHash);
        var records=db.queryForList("SELECT * FROM task_field_target_intent WHERE tenant_id=? AND created_by=? AND task_id=? AND mapping_revision=? LIMIT 2 FOR UPDATE",
                a.context().tenant_id(),a.actorId(),taskId,mappingRevision);
        if(records.isEmpty())return Optional.empty();var row=verify(a,one(records));
        if(!mappingHash.equals(text(row.columns(),"mapping_sha256")))bad();
        attemptRecord(a,row,false);return Optional.of(row);
    }
    /** Historical integrity only. The original actor is read from the exact immutable database row,
     * never substituted into the caller admission. Input read/author/approval authority is separate. */
    Row verifyStored(EnterpriseAccess.Admission a,String taskId,String writeId) throws Exception {
        readTask(a,taskId);WireValues.uuid(writeId);
        var records=db.queryForList("SELECT * FROM task_field_target_intent WHERE tenant_id=? AND write_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),writeId);
        var row=verifySaved(a,one(records),false);
        if(!taskId.equals(text(row.columns(),"task_id")))denied();
        attemptRecord(a,row,false);return row;
    }

    private void readTask(EnterpriseAccess.Admission a,String taskId) throws Exception {
        current(a);WireValues.uuid(taskId);if(tasks==null)denied();var head=tasks.fixedTask(a,taskId).head();
        if(!taskId.equals(head.taskId()) || !a.context().tenant_id().equals(head.tenantId()))denied();
    }

    private record Checked(JsonNode request,Map<String,TaskFieldSourceReader.Read> reads) { }
    private Checked check(TaskFieldTargetBasis.Verified basis,TaskFieldMappingService.Verified mapping,
            List<TaskFieldSourceReader.Read> reads,JsonNode produce,String key,String context) throws Exception {
        Objects.requireNonNull(basis); Objects.requireNonNull(mapping); Objects.requireNonNull(reads);
        basis.requireAction(TaskFieldTargetProtocol.TARGET_PRODUCE); basis.requireUnchanged(); mapping.requireUnchanged();
        var a=basis.admission();current(a);json.headers(context,key);
        if(!context.equals(a.context().context_revision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        var request=schema.validate("Produce",produce);var manifest=schema.validate("MappingManifest",mapping.manifest());
        basis.requirePins(manifest);
        for(String pin:List.of("project_id","task_revision","plan_revision")) same(request,pin,manifest,pin);
        if(!text(request,"assignment_id").equals(mapping.mappingRevision())
                || !text(request,"assignment_sha256").equals(mapping.mappingSha256())
                || !text(manifest,"assignment_id").equals(mapping.mappingRevision())
                || !schema.hash(manifest).equals(mapping.mappingSha256())) bad();
        if(!text(request,"expected_revision").equals(mapping.revision())) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        var expected=eligible(manifest);var actual=new LinkedHashMap<String,TaskFieldSourceReader.Read>();
        for(var read:reads) {
            if(read==null) denied();var r=read.request();
            if(!basis.task().head().taskId().equals(r.taskId()) || !mapping.mappingRevision().equals(r.mappingRevision())
                    || !mapping.mappingSha256().equals(r.mappingSha256()) || !expected.containsKey(r.entryKey())
                    || actual.put(r.entryKey(),read)!=null) bad();
            reader.requireCurrent(a,context,read);
            var receipt=schema.validate("SourceReadReceipt",read.manifest());
            if(!schema.hash(receipt).equals(read.receiptSha256()) || !read.receiptId().equals(text(receipt,"receipt_id"))) bad();
            for(String pin:List.of("tenant_id","project_id","task_id","task_revision","plan_revision")) same(receipt,pin,manifest,pin);
            same(receipt,"snapshot_id",manifest,"scope_snapshot");
            if(!a.actorId().equals(text(receipt,"actor_id")) || !mapping.mappingRevision().equals(text(receipt,"mapping_revision"))
                    || !mapping.mappingSha256().equals(text(receipt,"mapping_sha256")) || !r.entryKey().equals(text(receipt,"entry_key"))
                    || !expected.get(r.entryKey()).required("source_exact").equals(receipt.required("source_exact"))) bad();
        }
        if(!actual.keySet().equals(expected.keySet())) bad();
        return new Checked(request,Collections.unmodifiableMap(actual));
    }

    private LinkedHashMap<String,JsonNode> eligible(JsonNode mapping) {
        var inventory=new HashMap<String,JsonNode>();
        for(var item:mapping.required("inventory"))if(inventory.put(inventoryKey(item),item)!=null)bad();
        var result=new LinkedHashMap<String,JsonNode>();
        for(var entry:mapping.required("mappings")) {
            var field=inventory.get(inventoryKey(entry));if(field==null)bad();
            if("MAPPED".equals(text(field,"state")) && result.put(text(entry,"entry_key"),entry)!=null)bad();
        }
        return result;
    }

    private static String inventoryKey(JsonNode item) {
        return text(item,"field_definition_id")+"/"+(item.required("section_id").isNull()?"":text(item,"section_id"))+"/"+text(item,"node_id");
    }

    private Row create(TaskFieldTargetBasis.Verified basis,TaskFieldMappingService.Verified mapping,Checked checked,
            String key,String context) throws Exception {
        var a=basis.admission();var head=basis.task().head();var at=now();
        String write=id(),artifact=id(),operation=id(),audit=id(),event=id();
        var manifest=manifest(basis,mapping,checked.reads(),null);
        String payload=schema.canonical(manifest),hash=WireValues.sha256(payload.getBytes(StandardCharsets.UTF_8));
        int bound=maximumTargetBytes.getAsInt();if(bound<1||bound>8_388_608)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        if(payload.getBytes(StandardCharsets.UTF_8).length>bound)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        var intent=json.object().put("schema_version","1.0.0").put("tenant_id",head.tenantId()).put("project_id",head.projectId())
                .put("task_id",head.taskId()).put("operation_id",operation).put("audit_id",audit).put("event_id",event)
                .put("request_sha256",requestHash(a,context,checked.request())).put("schema_manifest_sha256",schema.manifest())
                .put("created_by",a.actorId()).put("created_at",instant(at)).put("classification",basis.classification())
                .put("write_id",write).put("planned_artifact_id",artifact).put("assignment_id",mapping.mappingRevision())
                .put("assignment_sha256",mapping.mappingSha256()).put("task_revision",head.taskRevision())
                .put("plan_revision",head.planRevision()).put("object_key",objectKey(head.tenantId(),head.taskId(),write,artifact))
                .put("target_sha256",hash).put("size_bytes",Integer.toString(payload.getBytes(StandardCharsets.UTF_8).length))
                .put("media_type","application/json").put("context_revision",context).put("idempotency_key",key)
                .put("expected_revision",text(checked.request(),"expected_revision")).put("authority_grant",basis.grant().grantId())
                .put("authority_revision",basis.grant().revision().toString()).put("auth_epoch",a.watermarks().authEpoch().toString())
                .put("governance_epoch",a.watermarks().governanceEpoch().toString()).put("authorizations_sha256",basis.authorizationsSha256())
                .put("reason",text(checked.request(),"reason"));
        intent.set("original_request",checked.request());intent.set("target_manifest",manifest);schema.validate("TargetIntent",intent);
        var c=metadata(head.tenantId(),head.projectId(),head.taskId(),a.actorId(),basis.classification(),at);
        for(String f:List.of("task_revision","plan_revision","write_id","planned_artifact_id","object_key","target_sha256","request_sha256",
                "idempotency_key","operation_id","audit_id","event_id","reason","authority_grant")) c.put(f,text(intent,f));
        for(String f:List.of("size_bytes","expected_revision","authority_revision","auth_epoch","governance_epoch"))c.put(f,new java.math.BigInteger(text(intent,f)));
        c.put("template_version",head.templateVersion());c.put("mapping_revision",mapping.mappingRevision());c.put("mapping_sha256",mapping.mappingSha256());
        c.put("canonical_payload",payload);c.put("intent_json",schema.canonical(intent));c.put("intent_sha256",schema.hash(intent));
        var row=new Row(c,intent,payload);
        changed(db.update("INSERT INTO operation_record (tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,http_status,response_ref,target_id,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES (?,?,?,?,?,?,'PROCESSING',202,NULL,?,'1.0.0',?,?,?,?,1,?)",
                head.tenantId(),operation,a.actorId(),OPERATION,key,c.get("request_sha256"),artifact,schema.manifest(),a.actorId(),at,basis.classification(),at));
        journal(row,"task.field_target.prepared",audit,event,1,anchor(row),at);
        insert("task_field_target_intent",c);saveEntries(row);
        return verify(a,one(rows(a,key)));
    }

    private JsonNode manifest(TaskFieldTargetBasis.Verified basis,TaskFieldMappingService.Verified mapping,
            Map<String,TaskFieldSourceReader.Read> reads,JsonNode original) throws Exception {
        var mapped=mapping.manifest();var head=basis.task().head();
        var result=json.object().put("schema_version","1.0.0").put("artifact_kind",TaskFieldTargetProtocol.ARTIFACT_KIND)
                .put("owner_kind","TASK").putNull("run_id").putNull("document_revision");
        for(String f:List.of("task_input_sha256","plan_sha256","profile_revision","scope_snapshot","inventory","template_nodes",
                "repeat_definitions","attachment_requirements","condition_results"))result.set(f,mapped.required(f));
        var target=result.putObject("target").put("schema_version","1.0.0").put("assessment","PROPOSED");
        var identity=target.putObject("identity");
        for(String f:List.of("tenant_id","project_id","task_id","task_revision","plan_revision","template_version","template_sha256","assignment_id"))identity.set(f,mapped.required(f));
        identity.put("assignment_sha256",mapping.mappingSha256());
        var sections=target.putArray("sections");
        for(var section:basis.plan().content().sections()) {
            var node=sections.addObject().put("section_id",section.sectionId());node.set("requirement_node_ids",json.tree(section.requirementNodeIds()));
        }
        var entries=target.putArray("entries");var bindings=result.putArray("entry_bindings");
        var originals=new HashMap<String,JsonNode>();
        if(original!=null)for(var binding:original.required("entry_bindings"))if(originals.put(text(binding,"entry_key"),binding)!=null)bad();
        var lineage=new TreeMap<String,String>();var governance=new TreeMap<String,String>();
        pin(governance,head.tenantId(),head.taskRevision(),head.registryHash());
        pin(governance,head.tenantId(),head.planRevision(),text(mapped,"plan_sha256"));
        pin(governance,head.tenantId(),mapping.mappingRevision(),mapping.mappingSha256());
        pin(governance,head.tenantId(),head.templateVersion(),text(mapped,"template_sha256"));
        pin(governance,head.tenantId(),head.profileRevision(),null);pin(governance,head.tenantId(),head.snapshotId(),null);
        pin(governance,head.tenantId(),head.agentReleaseId(),null);
        for(var item:eligible(mapped).entrySet()) {
            String key=item.getKey();var assignment=item.getValue();var read=reads.get(key);if(read==null)bad();
            var receipt=read.manifest();var exact=assignment.required("source_exact");var field=read.field();
            var old=originals.remove(key);if(original!=null && old==null)bad();
            String entry=old==null?id():text(old,"entry_id"),occurrence=old==null?id():text(old,"field_id");
            if(occurrence.equals(text(assignment,"field_definition_id")))bad();
            var value=entries.addObject().put("entry_id",entry).put("field_id",occurrence);
            for(String f:List.of("field_definition_id","section_id","node_id","row_id","model_ids","condition_ids","model_context","condition_context","applicability"))value.set(f,assignment.required(f));
            var candidate=value.putObject("candidate_value").put("value_kind","TEXT").put("raw_text",field.raw_value())
                    .putNull("decimal_value").putNull("precision").putNull("unit").putNull("object_ref");candidate.putArray("selected_ids");
            var provenance=value.putObject("provenance").put("start_codepoint",field.start_codepoint()).put("end_codepoint",field.end_codepoint())
                    .put("raw_text_sha256",WireValues.sha256(field.raw_value().getBytes(StandardCharsets.UTF_8)));
            provenance.putArray("read_receipt_ids").add(read.receiptId());
            var source=provenance.putObject("source").put("kind","SOURCE_EXACT_REVISION").putNull("parsed_decimal");
            source.set("evidence_ref",evidence(basis,read));
            source.putObject("source_exact").put("field_revision",text(exact,"field_revision"))
                    .put("field_manifest_sha256",text(exact,"manifest_sha256")).put("ordinal",exact.required("ordinal").intValue()).put("field_kind",text(exact,"field_kind"));
            bindings.addObject().put("entry_key",key).put("entry_id",entry).put("field_id",occurrence).put("source_read_receipt_id",read.receiptId());
            var actual=one(basis.execution().sources().stream().filter(s->s.source().sourceVersion().equals(text(receipt,"source_version"))).toList());
            pin(lineage,head.tenantId(),text(receipt,"source_version"),actual.source().registeredSha256());
            pin(lineage,head.tenantId(),text(receipt,"parse_version"),text(receipt.required("parsed_read").required("object_ref"),"sha256"));
            pin(lineage,head.tenantId(),text(exact,"field_revision"),text(exact,"manifest_sha256"));
        }
        if(!originals.isEmpty() || lineage.keySet().stream().anyMatch(governance::containsKey))bad();
        var sources=result.putArray("source_lineage");lineage.forEach((id,hash)->sources.addObject().put("version_id",id).put("content_sha256",hash));
        var pins=result.putArray("governance_pins");governance.forEach((id,hash)->pins.addObject().put("version_id",id).put("content_sha256",hash));
        return schema.validate("TargetManifest",result);
    }

    private JsonNode evidence(TaskFieldTargetBasis.Verified basis,TaskFieldSourceReader.Read read) throws Exception {
        var receipt=read.manifest();var field=read.field();var head=basis.task().head();
        var source=one(basis.execution().sources().stream().filter(s->s.source().sourceVersion().equals(text(receipt,"source_version"))).toList());
        var chunk=one(db.queryForList("SELECT locator,quote_hash,raw_text FROM source_chunk WHERE tenant_id=? AND source_version=? AND parse_version=? AND chunk_id=? LIMIT 2",
                head.tenantId(),text(receipt,"source_version"),text(receipt,"parse_version"),field.chunk_id()));
        var locator=schema.read(text(chunk,"locator"));String raw=text(chunk,"raw_text");
        if(!field.locator_sha256().equals(schema.hash(locator)) || !field.quote_hash().equals(text(chunk,"quote_hash"))
                || !field.quote_hash().equals(WireValues.sha256(raw.getBytes(StandardCharsets.UTF_8))))bad();
        int length=raw.codePointCount(0,raw.length());
        if(field.start_codepoint()<0 || field.end_codepoint()>length || field.end_codepoint()<=field.start_codepoint()
                || !field.raw_value().equals(raw.substring(raw.offsetByCodePoints(0,field.start_codepoint()),raw.offsetByCodePoints(0,field.end_codepoint()))))bad();
        var result=json.object().put("schema_version","1.0.0").put("tenant_id",head.tenantId()).put("project_id",head.projectId())
                .put("source_id",source.source().sourceId()).put("source_version",text(receipt,"source_version"))
                .put("parse_version",text(receipt,"parse_version")).put("chunk_id",field.chunk_id()).put("locator_kind",text(locator,"kind"))
                .put("quote_hash",field.quote_hash()).put("approval_revision",text(receipt,"approval_revision"));
        result.set("locator",locator);return result;
    }

    private void pin(Map<String,String> pins,String tenant,String version,String expected) {
        WireValues.uuid(version);
        var row=one(db.queryForList("SELECT version_id,content_sha256 FROM version_registry WHERE tenant_id=? AND version_id=? LIMIT 2",tenant,version));
        if(!version.equals(text(row,"version_id")))bad();String hash=text(row,"content_sha256");WireValues.hash(hash);
        if(expected!=null && !expected.equals(hash))bad();var prior=pins.putIfAbsent(version,hash);if(prior!=null && !prior.equals(hash))bad();
    }

    private void saveEntries(Row row) throws Exception {
        var entries=new HashMap<String,JsonNode>();row.manifest().required("target").required("entries").forEach(e->entries.put(text(e,"entry_id"),e));
        for(var binding:row.manifest().required("entry_bindings"))insert("task_field_target_entry",entryColumns(row,binding,entries.get(text(binding,"entry_id"))));
    }
    private Map<String,Object> entryColumns(Row row,JsonNode binding,JsonNode entry) throws Exception {
        var c=row.columns();var values=metadata(text(c,"tenant_id"),text(c,"project_id"),text(c,"task_id"),text(c,"created_by"),text(c,"classification"),time(c,"created_at"));
        for(String f:List.of("write_id","mapping_revision","mapping_sha256","template_version","plan_revision"))values.put(f,c.get(f));
        for(String f:List.of("entry_key","entry_id","field_id","source_read_receipt_id"))values.put(f,text(binding,f));
        for(String f:List.of("field_definition_id","section_id","node_id"))values.put(f,text(entry,f));
        values.put("row_id",entry.required("row_id").isNull()?null:text(entry,"row_id"));
        values.put("entry_json",schema.canonical(entry));values.put("entry_sha256",schema.hash(entry));return values;
    }
    private void verifyEntries(Row row) throws Exception {
        var c=row.columns();var records=db.queryForList("SELECT * FROM task_field_target_entry WHERE tenant_id=? AND write_id=?",
                c.get("tenant_id"),c.get("write_id"));
        var byId=new HashMap<String,Map<String,Object>>();for(var actual:records)if(byId.put(text(actual,"entry_id"),actual)!=null)bad();
        var entries=new HashMap<String,JsonNode>();row.manifest().required("target").required("entries").forEach(e->entries.put(text(e,"entry_id"),e));
        for(var binding:row.manifest().required("entry_bindings")) {
            var actual=byId.remove(text(binding,"entry_id"));if(actual==null)bad();
            var expected=entryColumns(row,binding,entries.get(text(binding,"entry_id")));
            for(var column:expected.entrySet()) {
                Object value=actual.get(column.getKey());
                if("entry_json".equals(column.getKey()))value=schema.canonical(schema.read(text(actual,"entry_json")));
                if(value instanceof java.sql.Timestamp t)value=t.toLocalDateTime();
                if(!Objects.equals(column.getValue(),value))bad();
            }
        }
        if(!byId.isEmpty())bad();
    }

    private Attempt attempt(EnterpriseAccess.Admission a,Row row,boolean create) throws Exception {
        var existing=attempts(row);boolean prior=!existing.isEmpty();
        String id=attemptRecord(a,row,create);return new Attempt(this,row,id,prior);
    }
    private String attemptRecord(EnterpriseAccess.Admission a,Row row,boolean create) throws Exception {
        var c=row.columns();var records=attempts(row);var op=one(operations(text(c,"tenant_id"),text(c,"created_by"),text(c,"idempotency_key")));
        if(records.isEmpty()) {
            if(!create || number(op,"revision")!=1 || !"PROCESSING".equals(op.get("state")))bad();
            String attempt=id(),audit=id(),event=id();var at=now();
            var record=metadata(text(c,"tenant_id"),text(c,"project_id"),text(c,"task_id"),text(c,"created_by"),text(c,"classification"),at);
            for(String f:List.of("write_id","intent_sha256","operation_id"))record.put(f,c.get(f));
            record.put("attempt_id",attempt);record.put("audit_id",audit);record.put("event_id",event);
            journal(row,"task.field_target.attempted",audit,event,2,attemptAnchor(row,attempt,at),at);
            insert("task_field_target_attempt",record);
            changed(db.update("UPDATE operation_record SET revision=2,updated_at=? WHERE tenant_id=? AND operation_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? AND state='PROCESSING' AND revision=1",
                    at,c.get("tenant_id"),c.get("operation_id"),c.get("created_by"),OPERATION,c.get("idempotency_key")));
            return attemptRecord(a,row,false);
        }
        var record=one(records);verifyMetadata(row,record);var at=time(record,"created_at");
        for(String f:List.of("task_id","write_id","intent_sha256","operation_id"))if(!c.get(f).equals(record.get(f)))bad();
        if(at.isBefore(time(c,"created_at")) || at.isAfter(now()) || number(op,"revision")<2
                || time(op,"updated_at").isBefore(at) || "PROCESSING".equals(op.get("state"))&&!at.equals(time(op,"updated_at")))bad();
        requireJournal(row,"task.field_target.attempted",text(record,"audit_id"),text(record,"event_id"),2,attemptAnchor(row,text(record,"attempt_id"),at),at);
        return text(record,"attempt_id");
    }
    private List<Map<String,Object>> attempts(Row row) {
        return db.queryForList("SELECT * FROM task_field_target_attempt WHERE tenant_id=? AND write_id=? LIMIT 2 FOR UPDATE",
                row.columns().get("tenant_id"),row.columns().get("write_id"));
    }

    private Row verify(EnterpriseAccess.Admission a,Map<String,Object> c) throws Exception {return verifySaved(a,c,true);}
    private Row verifySaved(EnterpriseAccess.Admission a,Map<String,Object> c,boolean requireOriginalActor) throws Exception {
        current(a);var intent=schema.validate("TargetIntent",schema.read(text(c,"intent_json")));
        for(String pin:List.of("project_id","task_revision","plan_revision","assignment_id","assignment_sha256","expected_revision","reason"))same(intent,pin,intent.required("original_request"),pin);
        String payload=schema.canonical(schema.read(text(c,"canonical_payload")));var row=new Row(c,intent,payload);c=row.columns();
        if(!a.context().tenant_id().equals(c.get("tenant_id")) || requireOriginalActor&&!a.actorId().equals(c.get("created_by")))denied();
        if(!schema.hash(intent).equals(c.get("intent_sha256")) || !payload.equals(schema.canonical(intent.required("target_manifest")))
                || !WireValues.sha256(payload.getBytes(StandardCharsets.UTF_8)).equals(c.get("target_sha256"))
                || payload.getBytes(StandardCharsets.UTF_8).length!=number(c,"size_bytes"))bad();
        for(String f:List.of("tenant_id","project_id","task_id","task_revision","plan_revision","write_id","planned_artifact_id","object_key",
                "target_sha256","request_sha256","idempotency_key","operation_id","audit_id","event_id","reason","authority_grant",
                "schema_version","schema_manifest_sha256","created_by","classification"))if(!text(c,f).equals(text(intent,f)))bad();
        for(String f:List.of("size_bytes","expected_revision","authority_revision","auth_epoch","governance_epoch"))if(!numberString(c,f).equals(text(intent,f)))bad();
        if(!text(c,"mapping_revision").equals(text(intent,"assignment_id")) || !text(c,"mapping_sha256").equals(text(intent,"assignment_sha256"))
                || !text(c,"template_version").equals(text(row.manifest().required("target").required("identity"),"template_version"))
                || !"1.0.0".equals(c.get("schema_version")) || !schema.manifest().equals(c.get("schema_manifest_sha256"))
                || !instant(time(c,"created_at")).equals(text(intent,"created_at")) || time(c,"created_at").isAfter(now())
                || !objectKey(text(c,"tenant_id"),text(c,"task_id"),text(c,"write_id"),text(c,"planned_artifact_id")).equals(text(c,"object_key"))
                || !requestHash(text(c,"tenant_id"),text(c,"created_by"),text(intent,"context_revision"),intent.required("original_request")).equals(c.get("request_sha256")))bad();
        var op=one(operations(text(c,"tenant_id"),text(c,"created_by"),text(c,"idempotency_key")));verifyMetadata(row,op);
        if(!c.get("operation_id").equals(op.get("operation_id")) || !c.get("created_by").equals(op.get("subject_id"))
                || !OPERATION.equals(op.get("operation_name")) || !c.get("request_sha256").equals(op.get("request_sha256"))
                || !c.get("planned_artifact_id").equals(op.get("target_id")) || !time(c,"created_at").equals(time(op,"created_at"))
                || time(op,"updated_at").isBefore(time(c,"created_at")) || time(op,"updated_at").isAfter(now()) || op.get("error_code")!=null)bad();
        if("PROCESSING".equals(op.get("state"))) {
            if(!Set.of(1L,2L).contains(number(op,"revision")) || number(op,"http_status")!=202 || op.get("response_ref")!=null
                    || number(op,"revision")==1&&!time(op,"updated_at").equals(time(c,"created_at")))bad();
        } else if(!"COMPLETED".equals(op.get("state")) || number(op,"revision")!=3 || number(op,"http_status")!=200 || op.get("response_ref")==null)bad();
        requireJournal(row,"task.field_target.prepared",text(c,"audit_id"),text(c,"event_id"),1,anchor(row),time(c,"created_at"));
        verifyEntries(row);return row;
    }

    private void requireProof(TaskFieldTargetBasis.Verified basis,TaskFieldMappingService.Verified mapping,Checked checked,Row row,String context) throws Exception {
        var intent=row.intent();var a=basis.admission();
        if(!text(intent,"context_revision").equals(context) || !text(intent,"authorizations_sha256").equals(basis.authorizationsSha256())
                || !text(intent,"authority_grant").equals(basis.grant().grantId()) || !text(intent,"authority_revision").equals(basis.grant().revision().toString())
                || !text(intent,"auth_epoch").equals(a.watermarks().authEpoch().toString()) || !text(intent,"governance_epoch").equals(a.watermarks().governanceEpoch().toString())
                || !text(intent,"classification").equals(basis.classification()) || !checked.request().equals(intent.required("original_request"))
                || !row.manifest().equals(manifest(basis,mapping,checked.reads(),row.manifest())))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
    }

    private String requestHash(EnterpriseAccess.Admission a,String context,JsonNode request) throws Exception {
        return requestHash(a.context().tenant_id(),a.actorId(),context,request);
    }
    private String requestHash(String tenant,String actor,String context,JsonNode request) throws Exception {
        return schema.hash(Map.of("tenant_id",tenant,"actor_id",actor,"context_revision",context,"operation",OPERATION,"request",request));
    }
    private List<Map<String,Object>> rows(EnterpriseAccess.Admission a,String key) {
        return db.queryForList("SELECT * FROM task_field_target_intent WHERE tenant_id=? AND created_by=? AND idempotency_key=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),a.actorId(),key);
    }
    private List<Map<String,Object>> operations(EnterpriseAccess.Admission a,String key) {
        return operations(a.context().tenant_id(),a.actorId(),key);
    }
    private List<Map<String,Object>> operations(String tenant,String actor,String key) {
        return db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? LIMIT 2 FOR UPDATE",tenant,actor,OPERATION,key);
    }
    private String anchor(Row row) throws Exception {
        return schema.canonical(Map.of("schema_version","1.0.0","kind","TASK_FIELD_TARGET_PREPARED","operation_id",row.columns().get("operation_id"),
                "write_id",row.columns().get("write_id"),"intent_sha256",row.columns().get("intent_sha256"),"target_sha256",row.columns().get("target_sha256")));
    }
    private String attemptAnchor(Row row,String attempt,LocalDateTime at) throws Exception {
        var node=(ObjectNode)schema.read(anchor(row));node.put("kind","TASK_FIELD_TARGET_ATTEMPTED").put("attempt_id",attempt).put("attempted_at",instant(at));return schema.canonical(node);
    }
    private void journal(Row row,String action,String audit,String event,int revision,String body,LocalDateTime at) {
        var c=row.columns();
        changed(db.update("INSERT INTO audit_event (tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",
                c.get("tenant_id"),c.get("project_id"),audit,c.get("created_by"),action,body,c.get("operation_id"),c.get("write_id"),schema.manifest(),c.get("created_by"),at,c.get("classification")));
        changed(db.update("INSERT INTO outbox_event (tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES (?,?,?,?,?,?,?)",
                c.get("tenant_id"),event,c.get("write_id"),revision,action,body,at));
    }
    private void requireJournal(Row row,String action,String audit,String event,int revision,String body,LocalDateTime at) throws Exception {
        var c=row.columns();var a=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",c.get("tenant_id"),audit));
        var e=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",c.get("tenant_id"),event));verifyMetadata(row,a);
        if(!c.get("project_id").equals(a.get("project_id")) || !c.get("created_by").equals(a.get("subject_id")) || !action.equals(a.get("action"))
                || !"SUCCESS".equals(a.get("result")) || a.get("reason_code")!=null || !c.get("operation_id").equals(a.get("request_id"))
                || !c.get("write_id").equals(a.get("trace_id")) || !at.equals(time(a,"created_at")) || !body.equals(schema.canonical(schema.read(text(a,"resource_ref")))))bad();
        if(!c.get("tenant_id").equals(e.get("tenant_id")) || !c.get("write_id").equals(e.get("aggregate_id")) || number(e,"aggregate_revision")!=revision
                || !action.equals(e.get("event_type")) || !at.equals(time(e,"occurred_at")) || !body.equals(schema.canonical(schema.read(text(e,"payload")))))bad();
    }
    private void verifyMetadata(Row row,Map<String,Object> actual) {
        for(String f:List.of("tenant_id","created_by","classification","schema_version","schema_manifest_sha256"))if(!row.columns().get(f).equals(actual.get(f)))bad();
        if(actual.containsKey("project_id") && !row.columns().get("project_id").equals(actual.get("project_id")))bad();
    }
    private Map<String,Object> metadata(String tenant,String project,String task,String actor,String classification,LocalDateTime at) {
        var m=new LinkedHashMap<String,Object>();m.put("tenant_id",tenant);m.put("project_id",project);m.put("task_id",task);m.put("created_by",actor);
        m.put("created_at",at);m.put("classification",classification);m.put("schema_version","1.0.0");m.put("schema_manifest_sha256",schema.manifest());return m;
    }
    void insert(String table,Map<String,Object> values) {
        if(!Set.of("task_field_target_intent","task_field_target_entry","task_field_target_attempt").contains(table))denied();
        var keys=new ArrayList<>(values.keySet());Collections.sort(keys);
        changed(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(values::get).toArray()));
    }
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
    static String objectKey(String tenant,String task,String write,String artifact) {
        for(String value:List.of(tenant,task,write,artifact))WireValues.uuid(value);return "task-field-target/"+tenant+"/"+task+"/"+write+"/"+artifact;
    }
    private static void current(EnterpriseAccess.Admission a) {
        var scope=TenantScope.requireCurrent();
        if(!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isSynchronizationActive()
                || !scope.tenantId().equals(a.context().tenant_id()) || !scope.subjectId().equals(a.actorId())
                || a.simulationWorker() || a.formalWorker() || a.controlWorker() || a.recoveryWorker())denied();
    }
    private static void same(JsonNode a,String ak,JsonNode b,String bk){if(!a.required(ak).equals(b.required(bk)))bad();}
    private static String text(JsonNode value,String key){var n=value.required(key);if(!n.isTextual())bad();return n.textValue();}
    private static String text(Map<String,Object> value,String key){if(value.get(key) instanceof String s)return s;bad();return null;}
    private static String numberString(Map<String,Object> value,String key){if(value.get(key) instanceof Number n)return new java.math.BigDecimal(n.toString()).toBigIntegerExact().toString();bad();return null;}
    private static long number(Map<String,Object> value,String key){return new java.math.BigInteger(numberString(value,key)).longValueExact();}
    private static LocalDateTime time(Map<String,Object> value,String key){var v=value.get(key);if(v instanceof LocalDateTime t)return t;if(v instanceof java.sql.Timestamp t)return t.toLocalDateTime();bad();return null;}
    private static String instant(LocalDateTime at){return at.toInstant(ZoneOffset.UTC).toString();}
    private static String id(){return UUID.randomUUID().toString();}
    private static <T>T one(List<T> values){if(values.size()!=1 || values.get(0)==null)bad();return values.get(0);}
    private static void changed(int rows){if(rows!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
    private static void conflict(){throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
