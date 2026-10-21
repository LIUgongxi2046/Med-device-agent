package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.VersionResourceMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Registration of one real pre-run target, never an authority to manufacture future resources. */
public final class TaskFieldTargetRegistration {
    private final JdbcTemplate db;private final PermissionSchema json;private final TaskFieldTargetSchema schema;
    private final TaskFieldTargetLedger ledger;private final ArtifactRegistryMapper registry;
    private final DependencyManifestSchema manifests;private final DependencyLineage lineage;private final java.util.function.Supplier<DependencyLineage.Limits> limitSupplier;
    private final TaskFieldTargetContentAuthority contents;private final TaskService tasks;private final TaskPlanService plans;
    private final ResourceReadAuthority resources;private final IdentityDirectoryPolicy identity;
    private static final String PRODUCE="task.field_target.produce";
    private static final Set<String> ACTIONS=Set.of(PRODUCE,"task.field_target_input.read","task.field_target_input.write","task.field_target_input.approve");
    public TaskFieldTargetRegistration(DataSource data,PermissionSchema json,TaskFieldTargetSchema schema,
            TaskFieldTargetLedger ledger,ArtifactRegistryMapper registry,DependencyManifestSchema manifests,
            DependencyLineage lineage,DependencyLineage.Limits limits,VersionResourceMapper owners,
            TaskFieldSourceReceiptStore receipts,TaskService tasks,TaskPlanService plans,ResourceReadAuthority resources,
            IdentityDirectoryPolicy identity) {
        this(data,json,schema,ledger,registry,manifests,lineage,()->limits,owners,receipts,tasks,plans,resources,identity);
    }
    public TaskFieldTargetRegistration(DataSource data,PermissionSchema json,TaskFieldTargetSchema schema,
            TaskFieldTargetLedger ledger,ArtifactRegistryMapper registry,DependencyManifestSchema manifests,
            DependencyLineage lineage,java.util.function.Supplier<DependencyLineage.Limits> limitSupplier,VersionResourceMapper owners,
            TaskFieldSourceReceiptStore receipts,TaskService tasks,TaskPlanService plans,ResourceReadAuthority resources,
            IdentityDirectoryPolicy identity) {
        this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.schema=schema;this.ledger=ledger;
        this.registry=registry;this.manifests=manifests;this.lineage=lineage;this.limitSupplier=limitSupplier;
        this.contents=new TaskFieldTargetContentAuthority(receipts,lineage,limitSupplier,owners);
        this.tasks=tasks;this.plans=plans;this.resources=resources;this.identity=identity;
    }
    Verified register(TaskFieldTargetBasis.Verified basis,TaskFieldMappingService.Verified mapping,
            List<TaskFieldSourceReader.Read> reads,TaskFieldTargetLedger.Attempt attempt,
            RuntimeObjectTransport.VerifiedTransfer transfer,String context) throws Exception {
        basis.requireAction(PRODUCE);basis.requireUnchanged();mapping.requireUnchanged();requireTransfer(attempt,transfer);
        var row=ledger.require(basis,mapping,reads,attempt,context);var a=basis.admission();var i=row.intent();
        var content=contents.require(a,row.manifest(),text(i,"assignment_id"),PRODUCE);
        var existing=registrations(a,attempt.artifactId());
        if(!existing.isEmpty()) {
            var saved=requireRegistered(a,attempt.artifactId(),PRODUCE);
            if(!saved.reference().equals(transfer.reference()))bad();return saved;
        }
        String tenant=a.context().tenant_id(),project=text(i,"project_id"),task=text(i,"task_id"),actor=a.actorId();
        String artifact=attempt.artifactId(),set=id();var object=transfer.reference();var at=now();
        if(!actor.equals(text(i,"created_by"))||!basis.classification().equals(text(i,"classification"))
                ||!registry.artifact(artifact).isEmpty())bad();
        var dependency=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",tenant,project,set,content.members());
        String dependencyJson=json.canonical(json.tree(dependency)),dependencyHash=hash(dependencyJson);
        version(tenant,project,set,"DEPENDENCY_ACCESS_SET",dependencyHash);
        var access=metadata(i,at,false);access.put("project_id",project);access.put("access_set_id",set);
        access.put("manifest_sha256",dependencyHash);access.put("content_sha256",dependencyHash);insert("dependency_access_set",access);
        var dm=metadata(i,at,true);dm.put("schema_manifest_sha256",manifests.manifestSha256());dm.put("project_id",project);
        dm.put("access_set_id",set);dm.put("manifest_json",dependencyJson);dm.put("manifest_sha256",dependencyHash);
        dm.put("content_sha256",dependencyHash);dm.put("member_count",content.members().size());insert("dependency_access_manifest",dm);
        for(var member:content.members()) {var m=metadata(i,at,false);m.put("access_set_id",set);m.put("upstream_version_id",member.upstreamVersionId());m.put("rights_revision",member.rightsRevision());insert("dependency_access_member",m);}
        version(tenant,project,artifact,"ARTIFACT",object.sha256());
        var ar=metadata(i,at,true);ar.put("project_id",project);ar.put("artifact_id",artifact);ar.put("run_id",null);ar.put("document_revision",null);
        ar.put("artifact_kind",TaskFieldTargetProtocol.ARTIFACT_KIND);ar.put("object_ref",StoredObjectReferences.encode(object));
        ar.put("object_key",object.objectKey());ar.put("object_version",object.objectVersion());ar.put("sha256",object.sha256());
        ar.put("size_bytes",object.sizeBytes());ar.put("media_type",object.mediaType());ar.put("dependency_access_set_id",set);ar.put("content_sha256",object.sha256());insert("artifact",ar);
        var binding=metadata(i,at,false);binding.put("project_id",project);binding.put("artifact_id",artifact);binding.put("resource_kind","TASK");binding.put("resource_id",task);
        binding.put("schema_ref",TaskFieldTargetProtocol.SCHEMA_REF);binding.put("schema_document_sha256",schema.manifest());binding.put("dependency_access_set_id",set);binding.put("dependency_manifest_sha256",dependencyHash);insert("artifact_content_binding",binding);
        for(String root:content.roots())insert("lineage_edge",Map.of("tenant_id",tenant,"consumer_version_id",artifact,"upstream_version_id",root,"relation_kind","TASK_FIELD_TARGET_CONTENT","actor_id",actor,"created_at",at));
        var receipt=json.object();for(String field:List.of("schema_version","tenant_id","project_id","task_id","operation_id","request_sha256","schema_manifest_sha256","created_by","classification","write_id","task_revision","plan_revision","assignment_id","assignment_sha256"))receipt.set(field,i.required(field));
        receipt.put("audit_id",id());receipt.put("event_id",id());receipt.put("registration_id",id());receipt.put("created_at",at.toInstant(ZoneOffset.UTC).toString());
        receipt.put("artifact_id",artifact);receipt.put("artifact_kind",TaskFieldTargetProtocol.ARTIFACT_KIND);receipt.put("owner_kind","TASK");receipt.putNull("run_id");receipt.putNull("document_revision");
        receipt.put("intent_sha256",attempt.intentSha256());receipt.set("object_ref",json.tree(object));receipt.put("status","REGISTERED");
        var receiptIds=receipt.putArray("source_read_receipt_ids");row.manifest().required("entry_bindings").forEach(b->receiptIds.add(text(b,"source_read_receipt_id")));
        schema.validate("ProduceReceipt",receipt);String digest=schema.hash(receipt),canonical=schema.canonical(receipt);
        var saved=metadata(i,at,true);saved.put("project_id",project);saved.put("task_id",task);saved.put("write_id",attempt.writeId());
        saved.put("registration_id",text(receipt,"registration_id"));saved.put("artifact_id",artifact);saved.put("object_sha256",object.sha256());saved.put("object_version",object.objectVersion());
        saved.put("receipt_json",canonical);saved.put("receipt_sha256",digest);saved.put("mapping_revision",text(i,"assignment_id"));saved.put("mapping_sha256",text(i,"assignment_sha256"));insert("task_field_target_registration",saved);
        var audit=metadata(i,at,true);audit.put("project_id",project);audit.put("audit_id",text(receipt,"audit_id"));audit.put("subject_id",actor);
        audit.put("action","task.field_target.registered");audit.put("resource_ref",canonical);audit.put("result","SUCCESS");audit.put("request_id",attempt.writeId());audit.put("trace_id",task);insert("audit_event",audit);
        insert("outbox_event",Map.of("tenant_id",tenant,"event_id",text(receipt,"event_id"),"aggregate_id",attempt.writeId(),"aggregate_revision",3,"event_type","task.field_target.registered","payload",canonical,"occurred_at",at));
        var status=status(receipt);changed(db.update("UPDATE operation_record SET state='COMPLETED',revision=3,http_status=200,response_ref=?,updated_at=? WHERE tenant_id=? AND operation_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? AND request_sha256=? AND state='PROCESSING' AND revision=2",
                schema.canonical(status),at,tenant,attempt.operationId(),actor,TaskFieldTargetLedger.OPERATION,attempt.idempotencyKey(),attempt.requestSha256()));
        basis.requireUnchanged();mapping.requireUnchanged();return requireRegistered(a,artifact,PRODUCE);
    }
    public Verified requireRegistered(EnterpriseAccess.Admission a,String artifactId,String action) throws Exception {
        active(a);if(!ACTIONS.contains(action))denied();WireValues.uuid(artifactId);
        var registration=one(registrations(a,artifactId));var receipt=schema.read(text(registration,"receipt_json"));schema.validate("ProduceReceipt",receipt);
        var stored=ledger.verifyStored(a,text(registration,"task_id"),text(registration,"write_id"));var intentRow=stored.columns();
        var intent=schema.read(text(intentRow,"intent_json"));schema.validate("TargetIntent",intent);
        var manifest=intent.required("target_manifest");schema.validate("TargetManifest",manifest);
        var task=tasks.fixedTask(a,text(receipt,"task_id"));var head=task.head();
        var scope=new PermissionProtocol.Scope(head.tenantId(),head.projectId(),"TASK",head.taskId());
        resources.require(a,scope,"task.read","TASK_READING");
        var grant=resources.require(a,scope,action,PRODUCE.equals(action)?"STRICT_FIELD_PREPARATION":"TASK_FIELD_TARGET_INPUT");
        var plan=plans.readFixed(a,task,text(intent,"plan_revision"),true);
        if(!"CONFIRMED".equals(plan.status())||!head.taskRevision().equals(text(intent,"task_revision"))
                ||!Objects.equals(head.planRevision(),text(intent,"plan_revision"))||!plan.contentSha256().equals(text(manifest,"plan_sha256"))
                ||!task.input().requestHash().equals(text(manifest,"task_input_sha256"))
                ||!head.templateVersion().equals(text(manifest.required("target").required("identity"),"template_version"))
                ||!head.profileRevision().equals(text(manifest,"profile_revision"))||!head.snapshotId().equals(text(manifest,"scope_snapshot")))stale();
        if(PRODUCE.equals(action)) {
            plans.execution(a,task,plan);
            var current=one(db.queryForList("SELECT * FROM task_field_mapping_head WHERE tenant_id=? AND task_id=? LIMIT 2 FOR UPDATE",head.tenantId(),head.taskId()));
            if(!text(current,"mapping_revision").equals(text(intent,"assignment_id")))stale();
        }
        var content=contents.require(a,manifest,text(intent,"assignment_id"),action);
        verify(a,registration,intentRow,intent,receipt,content,head.projectId());
        var pins=json.object();for(String field:List.of("task_revision","plan_revision","template_version","template_sha256"))pins.set(field,manifest.required("target").required("identity").required(field));
        for(String field:List.of("task_input_sha256","plan_sha256","profile_revision","scope_snapshot"))pins.set(field,manifest.required(field));
        pins.put("project_id",head.projectId());
        return new Verified(a,action,artifactId,receipt,manifest,pins,text(registration,"receipt_sha256"),grant,content);
    }
    void preflight(TaskFieldTargetBasis.Verified basis,TaskFieldMappingService.Verified mapping,List<TaskFieldSourceReader.Read> reads) throws Exception {
        contents.preflight(basis,mapping,reads,registry);
    }
    void requireCurrentContent(EnterpriseAccess.Admission a,TaskFieldTargetLedger.Attempt attempt,String action) throws Exception {
        active(a);if(!ACTIONS.contains(action))denied();
        var row=ledger.verifyStored(a,attempt.taskId(),attempt.writeId());
        if(!row.manifest().equals(attempt.targetManifest())||!schema.hash(row.intent()).equals(attempt.intentSha256()))bad();
        var head=tasks.fixedTask(a,attempt.taskId()).head();
        resources.require(a,new PermissionProtocol.Scope(head.tenantId(),head.projectId(),"TASK",head.taskId()),action,
                PRODUCE.equals(action)?"STRICT_FIELD_PREPARATION":"TASK_FIELD_TARGET_INPUT");
        contents.require(a,row.manifest(),text(row.intent(),"assignment_id"),action);
    }
    public Optional<Verified> findRegistered(EnterpriseAccess.Admission a,String artifact,String action) throws Exception {
        active(a);if(!ACTIONS.contains(action))denied();WireValues.uuid(artifact);
        return registrations(a,artifact).isEmpty()?Optional.empty():Optional.of(requireRegistered(a,artifact,action));
    }
    private void verify(EnterpriseAccess.Admission a,Map<String,Object> r,Map<String,Object> saved,JsonNode intent,
            JsonNode receipt,TaskFieldTargetContentAuthority.Content content,String project) throws Exception {
        String tenant=a.context().tenant_id(),artifact=text(receipt,"artifact_id"),task=text(receipt,"task_id"),actor=text(intent,"created_by");
        var o=receipt.required("object_ref");var object=new ObjectRef(text(o,"object_key"),text(o,"object_version"),text(o,"sha256"),text(o,"size_bytes"),text(o,"media_type"));
        var at=LocalDateTime.ofInstant(java.time.Instant.parse(text(receipt,"created_at")),ZoneOffset.UTC);
        if(!tenant.equals(text(receipt,"tenant_id"))||!project.equals(text(receipt,"project_id"))
                ||!schema.hash(receipt).equals(text(r,"receipt_sha256"))||!schema.hash(intent).equals(text(saved,"intent_sha256"))
                ||!schema.hash(intent).equals(text(receipt,"intent_sha256"))||!schema.hash(intent.required("target_manifest")).equals(object.sha256())
                ||!schema.read(text(saved,"canonical_payload")).equals(intent.required("target_manifest"))
                ||!identity.classification().equals(text(receipt,"classification")))bad();
        for(String field:List.of("tenant_id","project_id","task_id","write_id","schema_version","schema_manifest_sha256","created_by","classification"))
            if(!text(r,field).equals(text(receipt,field))||!text(intent,field).equals(text(receipt,field)))bad();
        for(String field:List.of("operation_id","request_sha256","task_revision","plan_revision","assignment_id","assignment_sha256"))
            if(!intent.required(field).equals(receipt.required(field)))bad();
        if(!text(r,"registration_id").equals(text(receipt,"registration_id"))||!text(r,"artifact_id").equals(artifact)
                ||!text(r,"mapping_revision").equals(text(receipt,"assignment_id"))||!text(r,"mapping_sha256").equals(text(receipt,"assignment_sha256"))
                ||!text(r,"object_sha256").equals(object.sha256())||!text(r,"object_version").equals(object.objectVersion())
                ||!time(r,"created_at").equals(at)||!actor.equals(text(receipt,"created_by"))||at.isAfter(now())
                ||at.isBefore(time(saved,"created_at"))||!artifact.equals(text(intent,"planned_artifact_id"))
                ||!object.objectKey().equals(text(intent,"object_key"))||!object.sha256().equals(text(intent,"target_sha256"))
                ||!object.sizeBytes().equals(text(intent,"size_bytes"))||!object.mediaType().equals(text(intent,"media_type")))bad();
        var ids=json.object().putArray("ids");intent.required("target_manifest").required("entry_bindings").forEach(b->ids.add(text(b,"source_read_receipt_id")));
        if(!ids.equals(receipt.required("source_read_receipt_ids")))bad();
        for(var pin:intent.required("target_manifest").required("governance_pins")) {
            var version=one(registry.version(text(pin,"version_id")));
            if(!version.contentSha256().equals(text(pin,"content_sha256")))bad();
        }
        var ar=one(registry.artifact(artifact));var binding=one(registry.binding(artifact));
        StoredObjectReferences.decode(ar.storedObject(),object);
        if(!tenant.equals(ar.tenantId())||!project.equals(ar.projectId())||!artifact.equals(ar.artifactId())
                ||!TaskFieldTargetProtocol.ARTIFACT_KIND.equals(ar.artifactKind())||!object.equals(ar.reference())
                ||!object.sha256().equals(ar.contentSha256())||!"1.0.0".equals(ar.schemaVersion())
                ||!schema.manifest().equals(ar.schemaManifest())||!identity.classification().equals(ar.classification()))bad();
        var dm=one(registry.manifest(ar.accessSetId()));var dependency=manifests.verify(dm,registry.members(ar.accessSetId()));
        if(!tenant.equals(dependency.tenant_id())||!project.equals(dependency.project_id())||!ar.accessSetId().equals(dependency.access_set_id())
                ||!dependency.members().equals(content.members())||!tenant.equals(binding.tenantId())||!project.equals(binding.projectId())
                ||!artifact.equals(binding.artifactId())||!"TASK".equals(binding.resourceKind())||!task.equals(binding.resourceId())
                ||!TaskFieldTargetProtocol.SCHEMA_REF.equals(binding.schemaRef())||!schema.manifest().equals(binding.schemaDocumentSha256())
                ||!ar.accessSetId().equals(binding.accessSetId())||!dm.manifestSha256().equals(binding.dependencyManifestSha256()))bad();
        for(var entry:List.of(Map.entry(artifact,object.sha256()),Map.entry(ar.accessSetId(),dm.manifestSha256()))) {
            var version=one(registry.version(entry.getKey()));
            if(!project.equals(version.projectId())||!entry.getValue().equals(version.contentSha256())
                    ||!(entry.getKey().equals(artifact)?"ARTIFACT":"DEPENDENCY_ACCESS_SET").equals(version.entityKind()))bad();
        }
        // Fetch complete immutable identities first; metadata predicates must never hide extra rows.
        for(String table:List.of("artifact","artifact_content_binding","dependency_access_set","dependency_access_manifest")) {
            boolean isArtifact=table.startsWith("artifact");String idColumn=isArtifact?"artifact_id":"access_set_id";
            var actual=one(db.queryForList("SELECT * FROM "+table+" WHERE tenant_id=? AND "+idColumn+"=? LIMIT 2",tenant,isArtifact?artifact:ar.accessSetId()));
            metadataMatches(actual,receipt,at);
            if(!project.equals(text(actual,"project_id")))bad();
            if(table.equals("artifact")&&(actual.get("run_id")!=null||actual.get("document_revision")!=null))bad();
        }
        var members=db.queryForList("SELECT * FROM dependency_access_member WHERE tenant_id=? AND access_set_id=?",tenant,ar.accessSetId());
        if(members.size()!=content.members().size())bad();for(var member:members)metadataMatches(member,receipt,at);
        var edges=db.queryForList("SELECT * FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=?",tenant,artifact);
        var roots=new TreeSet<String>();for(var edge:edges) {
            if(!roots.add(text(edge,"upstream_version_id"))||!actor.equals(text(edge,"actor_id"))
                    ||!"TASK_FIELD_TARGET_CONTENT".equals(text(edge,"relation_kind"))||!at.equals(time(edge,"created_at")))bad();
        }
        if(!List.copyOf(roots).equals(content.roots()))bad();lineage.require(artifact,content.members(),limitSupplier.get());
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",tenant,text(receipt,"audit_id")));
        metadataMatches(audit,receipt,at);
        if(!project.equals(text(audit,"project_id"))||!actor.equals(text(audit,"subject_id"))||!"task.field_target.registered".equals(text(audit,"action"))
                ||!"SUCCESS".equals(text(audit,"result"))||audit.get("reason_code")!=null||!text(receipt,"write_id").equals(text(audit,"request_id"))
                ||!task.equals(text(audit,"trace_id"))||!schema.manifest().equals(text(audit,"schema_manifest_sha256"))
                ||!receipt.equals(schema.read(text(audit,"resource_ref"))))bad();
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",tenant,text(receipt,"event_id")));
        if(!text(receipt,"write_id").equals(text(event,"aggregate_id"))||!"3".equals(text(event,"aggregate_revision"))
                ||!"task.field_target.registered".equals(text(event,"event_type"))||!at.equals(time(event,"occurred_at"))||!receipt.equals(schema.read(text(event,"payload"))))bad();
        var op=one(db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? LIMIT 2",tenant,text(intent,"operation_id")));
        if(!actor.equals(text(op,"subject_id"))||!actor.equals(text(op,"created_by"))||!TaskFieldTargetLedger.OPERATION.equals(text(op,"operation_name"))
                ||!text(intent,"idempotency_key").equals(text(op,"idempotency_key"))||!text(intent,"request_sha256").equals(text(op,"request_sha256"))
                ||!"COMPLETED".equals(text(op,"state"))||!"3".equals(text(op,"revision"))||!"200".equals(text(op,"http_status"))
                ||!artifact.equals(text(op,"target_id"))||!schema.manifest().equals(text(op,"schema_manifest_sha256"))
                ||!at.equals(time(op,"updated_at"))||op.get("error_code")!=null||!status(receipt).equals(schema.read(text(op,"response_ref"))))bad();
    }
    public final class Verified {
        private final EnterpriseAccess.Admission admission;private final String action,artifact,digest;
        private final JsonNode receipt,manifest,pins;private final PermissionProtocol.GrantRow grant;
        private final TaskFieldTargetContentAuthority.Content content;
        private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();
        private final Map<Object,Object> transaction=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private Verified(EnterpriseAccess.Admission a,String action,String artifact,JsonNode receipt,JsonNode manifest,JsonNode pins,
                String digest,PermissionProtocol.GrantRow grant,TaskFieldTargetContentAuthority.Content content) {
            this.admission=a;this.action=action;this.artifact=artifact;this.receipt=receipt.deepCopy();this.manifest=manifest.deepCopy();
            this.pins=pins.deepCopy();this.digest=digest;this.grant=grant;this.content=content;live();
        }
        public void requireUnchanged() throws Exception {
            live();var current=requireRegistered(admission,artifact,action);
            if(!digest.equals(current.digest)||!receipt.equals(current.receipt)||!manifest.equals(current.manifest)
                    ||!pins.equals(current.pins)||!grant.equals(current.grant)||!content.equals(current.content))stale();
        }
        public JsonNode manifest(){live();return manifest.deepCopy();}public JsonNode receipt(){live();return receipt.deepCopy();}
        public JsonNode pins(){live();return pins.deepCopy();}public String receiptSha256(){live();return digest;}
        public String artifactId(){live();return artifact;}public String registrationId(){live();return text(receipt,"registration_id");}
        public String taskId(){live();return text(receipt,"task_id");}public String projectId(){live();return text(receipt,"project_id");}
        public String mappingRevision(){live();return text(receipt,"assignment_id");}public String mappingSha256(){live();return text(receipt,"assignment_sha256");}
        public String targetSha256(){live();return text(receipt.required("object_ref"),"sha256");}
        public String targetSchemaSha256(){live();return schema.manifest();}
        public List<String> occurrenceIds(){live();var result=new ArrayList<String>();manifest.required("target").required("entries").forEach(e->result.add(text(e,"field_id")));return List.copyOf(result);}
        public ObjectRef reference(){live();var o=receipt.required("object_ref");return new ObjectRef(text(o,"object_key"),text(o,"object_version"),text(o,"sha256"),text(o,"size_bytes"),text(o,"media_type"));}
        private void live(){active(admission);if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent())denied();
            var current=TransactionSynchronizationManager.getResourceMap();if(current.size()!=transaction.size()||transaction.entrySet().stream().anyMatch(e->current.get(e.getKey())!=e.getValue()))denied();}
    }
    static void requireTransfer(TaskFieldTargetLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer transfer) {
        if(attempt==null||transfer==null||transfer.originalAttempt()!=attempt)bad();var expected=attempt.writeIntent();var actual=transfer.reference();
        if(actual==null||!expected.objectKey().equals(actual.objectKey())||!expected.sha256().equals(actual.sha256())
                ||!expected.sha256().equals(transfer.rawSha256())||!expected.sizeBytes().equals(actual.sizeBytes())
                ||!expected.mediaType().equals(actual.mediaType())||actual.objectVersion()==null||actual.objectVersion().isBlank())bad();
    }
    JsonNode status(JsonNode receipt) throws Exception {
        var result=json.object();for(String field:List.of("schema_version","tenant_id","project_id","task_id","assignment_id","assignment_sha256","write_id","artifact_id"))result.set(field,receipt.required(field));
        result.put("state","REGISTERED");result.set("receipt",receipt);result.putNull("reason");return schema.validate("Status",result);
    }
    private List<Map<String,Object>> registrations(EnterpriseAccess.Admission a,String artifact) {
        return db.queryForList("SELECT * FROM task_field_target_registration WHERE tenant_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),artifact);
    }
    private void metadataMatches(Map<String,Object> actual,JsonNode receipt,LocalDateTime at) {
        if(!text(receipt,"tenant_id").equals(text(actual,"tenant_id"))||!text(receipt,"created_by").equals(text(actual,"created_by"))
                ||!text(receipt,"classification").equals(text(actual,"classification"))||!at.equals(time(actual,"created_at")))bad();
        if(actual.containsKey("schema_version")&&!"1.0.0".equals(text(actual,"schema_version")))bad();
    }
    private Map<String,Object> metadata(JsonNode i,LocalDateTime at,boolean typed) {
        var result=new LinkedHashMap<String,Object>();for(String field:List.of("tenant_id","created_by","classification"))result.put(field,text(i,field));
        result.put("created_at",at);if(typed){result.put("schema_version","1.0.0");result.put("schema_manifest_sha256",schema.manifest());}return result;
    }
    private void version(String tenant,String project,String id,String kind,String hash){insert("version_registry",Map.of("tenant_id",tenant,"owner_project_id",project,"version_id",id,"entity_kind",kind,"content_sha256",hash));}
    private void insert(String table,Map<String,Object> values){var columns=new ArrayList<>(values.keySet());changed(db.update("INSERT INTO "+table+"("+String.join(",",columns)+") VALUES("+String.join(",",Collections.nCopies(columns.size(),"?"))+")",columns.stream().map(values::get).toArray()));}
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
    private static LocalDateTime time(Map<String,Object> row,String field){var value=row.get(field);if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime();if(value instanceof LocalDateTime t)return t;bad();return null;}
    private static String text(JsonNode n,String f){var v=n.required(f);if(!v.isTextual())bad();return v.textValue();}
    private static String text(Map<String,Object> n,String f){var v=n.get(f);if(v==null)bad();return v.toString();}
    private static String hash(String s)throws Exception{return WireValues.sha256(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static String id(){return UUID.randomUUID().toString();}private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}
    private static void changed(int n){if(n!=1)bad();}
    private static void active(EnterpriseAccess.Admission a){if(a==null||a.simulationWorker()||a.formalWorker()||a.controlWorker()||a.recoveryWorker()||!TransactionSynchronizationManager.isActualTransactionActive())denied();TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.TaskFieldTargetRegistration");}
    private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
