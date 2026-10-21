package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** TK-05 proposes immutable input; TK-06 creates only an unconfirmed revision.
 * Existing plan save/confirm is the sole route from that revision to READY. */
@Service
public final class TaskInputRevisionService {
    private static final String PURPOSE="TASK_INPUT_CONTINUATION";
    private final EnterpriseAccess access; private final ResourceReadAuthority resources;
    private final TaskService tasks; private final TaskCreateService creation; private final TaskInputSchema inputs;
    private final PermissionSchema json; private final DocumentReadService documents; private final JdbcTemplate db;
    private final JsonMapper reader=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final com.ruoyi.medical.identity.IdentityDirectoryPolicy identity;
    private final String manifest; private final Map<String,com.networknt.schema.JsonSchema> contracts;
    public TaskInputRevisionService(EnterpriseAccess access,ResourceReadAuthority resources,TaskService tasks,
            TaskCreateService creation,TaskInputSchema inputs,PermissionSchema json,DocumentReadService documents,com.ruoyi.medical.identity.IdentityDirectoryPolicy identity,
            @Qualifier("masterDataSource") DataSource data)throws Exception {
        this.access=access;this.resources=resources;this.tasks=tasks;this.creation=creation;this.inputs=inputs;this.json=json;this.documents=documents;this.identity=identity;
        db=new JdbcTemplate(data);db.setQueryTimeout(5);
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-input-continuation.schema.json"))){
            byte[] bytes=in.readAllBytes();manifest=WireValues.sha256(bytes);var root=reader.readTree(bytes);var schemas=new HashMap<String,com.networknt.schema.JsonSchema>();
            for(String name:List.of("Proposal","Decision","Receipt")){
                var schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012)
                    .getSchema(root.required("$defs").required(name),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
                schema.initializeValidators();schemas.put(name,schema);
            }contracts=Map.copyOf(schemas);
        }
    }
    static final class RevisionAuthority {
        private final TaskInputRevisionService owner; private final TaskProtocol.Head head; private final TaskProtocol.ValidatedCreate input;
        private final String operationId,key,reason; private final DocumentReadService.FixedRead signed;
        private RevisionAuthority(TaskInputRevisionService owner,TaskProtocol.Head head,TaskProtocol.ValidatedCreate input,
                String operationId,String key,String reason,DocumentReadService.FixedRead signed){
            this.owner=owner;this.head=head;this.input=input;this.operationId=operationId;this.key=key;this.reason=reason;this.signed=signed;
        }
        TaskProtocol.Head requireCurrent(EnterpriseAccess.Admission a)throws Exception {
            var current=owner.tasks.fixedTask(a,head.taskId()).head();if(!head.equals(current))fail(ErrorCode.SCOPE_STALE);
            if("SIGNED".equals(head.status())&&input.request().based_on_document_revision()==null)fail(ErrorCode.DOCUMENT_SIGNED);
            owner.quiescent(a,head);owner.grant(a,head,"plan.confirm");owner.grant(a,head,"task.create");
            if(signed!=null){owner.documents.requireCurrent(a,signed);owner.signed(a,head,input,signed);}
            else if(input.request().based_on_document_revision()!=null)fail(ErrorCode.CONTENT_MISMATCH);
            return head;
        }
        TaskProtocol.ValidatedCreate input(){return input;} String operationId(){return operationId;} String key(){return key;} String reason(){return reason;}
    }
    JsonNode propose(ApplicationSessionIdentity.Verified subject,String context,String key,String taskId,byte[] body){
        json.headers(context,key);WireValues.uuid(taskId);
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            current(a,context);var request=parse(body,"Proposal");var input=inputs.create(json.canonical(request.required("input")).getBytes(StandardCharsets.UTF_8));
            var head=tasks.fixedTask(a,taskId).head();grant(a,head,"task.create");
            var old=original(a,taskId,"TK-05",key,hash(request));if(old!=null)return old;
            same(head.projectId(),input.request().project_id());same(head.taskRevision(),request.required("basis_task_revision").asText());
            same(head.revision().toString(),request.required("expected_revision").asText());
            var impact=impact(a,head,input);String operation=UUID.randomUUID().toString(),proposal=UUID.randomUUID().toString();
            var receipt=receipt(operation,head,proposal,null,"PENDING",impact,input);
            save(a,head,"TK-05",key,request,input,proposal,null,receipt);return envelope(receipt);
        }).get());
    }
    JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String key,String taskId,byte[] body){
        json.headers(context,key);WireValues.uuid(taskId);
        var request=ThrowingSupplier.of(()->parse(body,"Decision")).get();
        var preliminary=access.execute(subject,a->ThrowingSupplier.of(()->{
            current(a,context);var head=tasks.fixedTask(a,taskId).head();grant(a,head,"plan.confirm");
            var old=original(a,taskId,"TK-06",key,hash(request));if(old!=null)return new Prepared(old,null,null);
            var proposal=proposal(a,head,request.required("proposal_id").asText());
            var input=inputs.create(text(proposal,"input_json").getBytes(StandardCharsets.UTF_8));
            String document=null;if(input.request().based_on_document_revision()!=null){
                var row=one(db.queryForList("SELECT document_id FROM document_revision_meta WHERE tenant_id=? AND project_id=? AND task_id=? AND document_revision=? LIMIT 2",a.context().tenant_id(),head.projectId(),taskId,input.request().based_on_document_revision()));document=text(row,"document_id");
            }
            return new Prepared(null,input,document);
        }).get());
        if(preliminary.original()!=null)return preliminary.original();
        var prepared=ThrowingSupplier.of(()->creation.prepareRevision(subject,context,preliminary.input())).get();
        var signed=preliminary.document()==null?null:ThrowingSupplier.of(()->documents.readFixed(subject,context,preliminary.document(),preliminary.input().request().based_on_document_revision())).get();
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            current(a,context);var head=tasks.fixedTask(a,taskId).head();grant(a,head,"plan.confirm");
            var old=original(a,taskId,"TK-06",key,hash(request));if(old!=null)return old;
            var proposal=proposal(a,head,request.required("proposal_id").asText());
            same(preliminary.input().request_sha256(),text(proposal,"input_sha256"));
            same(head.revision().toString(),request.required("expected_revision").asText());
            same(head.taskRevision(),text(proposal,"basis_task_revision"));
            var originalReceipt=reader.readTree(text(proposal,"receipt_json"));validate(originalReceipt,"Receipt");
            same(hash(originalReceipt.required("impact")),request.required("impact_confirmation_sha256").asText());
            same(originalReceipt.required("impact"),impact(a,head,preliminary.input()));
            String operation=UUID.randomUUID().toString();
            var authority=new RevisionAuthority(this,head,preliminary.input(),operation,key,request.required("reason").asText(),signed);
            authority.requireCurrent(a);begin(a,head,"TK-06",key,request,operation);
            var created=creation.materializeRevision(a,authority,prepared);
            // Mutable plan pointer only; original plans, decisions and signed content are untouched.
            db.update("DELETE FROM task_plan_head WHERE tenant_id=? AND project_id=? AND task_id=?",a.context().tenant_id(),head.projectId(),taskId);
            changed(db.update("UPDATE task_head SET current_revision=?,status=?,owner_id=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND project_id=? AND task_id=? AND current_revision=? AND revision=?",
                created.get("taskRevision"),created.get("taskStatus"),created.get("ownerId"),a.context().tenant_id(),head.projectId(),taskId,head.taskRevision(),head.revision()));
            var receipt=receipt(operation,head,text(proposal,"proposal_id"),(String)created.get("taskRevision"),(String)created.get("taskStatus"),originalReceipt.required("impact"),preliminary.input());
            ((com.fasterxml.jackson.databind.node.ObjectNode)receipt).put("resource_revision",head.revision().add(BigInteger.ONE).toString());
            finish(a,head,"TK-06",key,request,preliminary.input(),text(proposal,"proposal_id"),(String)created.get("taskRevision"),receipt);
            var saved=tasks.fixedTask(a,taskId);same(created.get("taskRevision"),saved.head().taskRevision());same(created.get("taskStatus"),saved.head().status());
            grant(a,saved.head(),"plan.confirm");return envelope(receipt);
        }).get());
    }
    private record Prepared(JsonNode original,TaskProtocol.ValidatedCreate input,String document){}
    JsonNode original(ApplicationSessionIdentity.Verified subject,String context,String key,String taskId,String operation){
        json.headers(context,key);WireValues.uuid(taskId);
        if(!Set.of("TK-05","TK-06").contains(operation))fail(ErrorCode.SCHEMA_INVALID);
        return access.execute(subject,a->ThrowingSupplier.of(()->{current(a,context);var head=tasks.fixedTask(a,taskId).head();grant(a,head,"TK-06".equals(operation)?"plan.confirm":"task.create");
            var value=original(a,taskId,operation,key,null);return value==null?json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),null,json.tree(null))):value;
        }).get());
    }
    private JsonNode original(EnterpriseAccess.Admission a,String task,String operation,String key,String expected)throws Exception {
        var rows=db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),a.actorId(),operation,key);
        if(rows.isEmpty())return null;var row=one(rows);same(task,text(row,"target_id"));if(expected!=null&&!expected.equals(text(row,"request_sha256")))fail(ErrorCode.IDEMPOTENCY_CONFLICT);
        same("COMPLETED",text(row,"state"));same(manifest,text(row,"schema_manifest_sha256"));
        var journal=one(db.queryForList("SELECT * FROM task_input_continuation WHERE tenant_id=? AND task_id=? AND operation_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),task,text(row,"operation_id")));
        var receipt=reader.readTree(text(journal,"receipt_json"));validate(receipt,"Receipt");same(hash(receipt),text(journal,"receipt_sha256"));
        same(text(row,"operation_id"),receipt.required("operation_id").asText());same(task,receipt.required("task_id").asText());same(manifest,text(journal,"schema_manifest_sha256"));
        var response=reader.readTree(text(row,"response_ref"));same(receipt,response.required("data"));
        var audit=one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND audit_id=? AND subject_id=? AND action=? AND result='SUCCESS' LIMIT 2",a.context().tenant_id(),text(row,"operation_id"),a.actorId(),operation));same(receipt,reader.readTree(text(audit,"resource_ref")));
        var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type=? LIMIT 2",a.context().tenant_id(),text(row,"operation_id"),task,operation));same(response,reader.readTree(text(event,"payload")));return response;
    }
    private Map<String,Object> proposal(EnterpriseAccess.Admission a,TaskProtocol.Head head,String id)throws Exception {
        WireValues.uuid(id);var row=one(db.queryForList("SELECT * FROM task_input_continuation WHERE tenant_id=? AND project_id=? AND task_id=? AND proposal_id=? AND operation_name='TK-05' LIMIT 2 FOR UPDATE",a.context().tenant_id(),head.projectId(),head.taskId(),id));
        same(manifest,text(row,"schema_manifest_sha256"));var receipt=reader.readTree(text(row,"receipt_json"));validate(receipt,"Receipt");same(hash(receipt),text(row,"receipt_sha256"));
        var input=inputs.create(text(row,"input_json").getBytes(StandardCharsets.UTF_8));same(input.request_sha256(),text(row,"input_sha256"));same(input.request_sha256(),receipt.required("input_sha256").asText());
        same(hash(receipt.required("impact")),receipt.required("impact_sha256").asText());return row;
    }
    private JsonNode impact(EnterpriseAccess.Admission a,TaskProtocol.Head head,TaskProtocol.ValidatedCreate input)throws Exception {
        same(head.projectId(),input.request().project_id());
        var node=json.object().put("schema_version","1.0.0").put("task_id",head.taskId()).put("basis_task_revision",head.taskRevision())
                .put("basis_task_sha256",head.contentHash()).put("proposed_input_sha256",input.request_sha256()).put("requires_plan_confirmation",true)
                .put("invalidates_execution_inputs",true).put("preserves_original_runs",true).put("preserves_signed_bytes",true);
        if(input.request().based_on_document_revision()==null)node.putNull("based_on_document_revision");else node.put("based_on_document_revision",input.request().based_on_document_revision());
        var runs=db.queryForList("SELECT run_id,task_revision,replay_manifest,budget_revision FROM task_run WHERE tenant_id=? AND project_id=? AND task_id=? ORDER BY attempt_no FOR UPDATE",a.context().tenant_id(),head.projectId(),head.taskId());
        var array=reader.createArrayNode();for(var run:runs)array.add(json.object().put("run_id",text(run,"run_id")).put("task_revision",text(run,"task_revision"))
                .put("replay_sha256",hash(reader.readTree(text(run,"replay_manifest")))).put("budget_revision",text(run,"budget_revision")));
        node.set("original_runs",array);return node;
    }
    private void quiescent(EnterpriseAccess.Admission a,TaskProtocol.Head head)throws Exception{
        String tenant=a.context().tenant_id();
        if(!db.queryForList("SELECT run_id FROM task_active_run WHERE tenant_id=? AND project_id=? AND task_id=? LIMIT 1 FOR UPDATE",tenant,head.projectId(),head.taskId()).isEmpty())fail(ErrorCode.REVISION_CONFLICT);
        if(!db.queryForList("SELECT run_id FROM task_run WHERE tenant_id=? AND project_id=? AND task_id=? AND (status NOT IN ('SUCCEEDED','FAILED','CANCELLED') OR ended_at IS NULL OR dispatch_closed_at IS NULL) LIMIT 1 FOR UPDATE",tenant,head.projectId(),head.taskId()).isEmpty())fail(ErrorCode.REVISION_CONFLICT);
        FormalInvocationCancellation.requireQuiescentTask(db,tenant,head.projectId(),head.taskId(),ErrorCode.CONDITION_UNKNOWN);
    }
    private void signed(EnterpriseAccess.Admission a,TaskProtocol.Head head,TaskProtocol.ValidatedCreate input,DocumentReadService.FixedRead proof)throws Exception {
        var h=proof.header();same(head.projectId(),h.projectId());same(head.taskId(),h.taskId());same(input.request().based_on_document_revision(),h.documentRevision());
        var signed=one(db.queryForList("SELECT s.signature_id,s.manifest_sha256,c.body_bytes,c.body_sha256,c.manifest_json,c.manifest_sha256 AS saved_manifest_sha256 FROM signature_record s JOIN document_signed_content c ON c.tenant_id=s.tenant_id AND c.project_id=s.project_id AND c.signature_id=s.signature_id AND c.document_revision=s.document_revision WHERE s.tenant_id=? AND s.project_id=? AND s.document_id=? AND s.document_revision=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),head.projectId(),h.documentId(),h.documentRevision()));
        same(WireValues.sha256((byte[])signed.get("body_bytes")),text(signed,"body_sha256"));same(h.bodySha256(),text(signed,"body_sha256"));
        same(WireValues.sha256(text(signed,"manifest_json").getBytes(StandardCharsets.UTF_8)),text(signed,"saved_manifest_sha256"));same(text(signed,"manifest_sha256"),text(signed,"saved_manifest_sha256"));
        if(!Arrays.equals(proof.bodyBytes(),(byte[])signed.get("body_bytes")))fail(ErrorCode.CONTENT_MISMATCH);
    }
    private JsonNode receipt(String operation,TaskProtocol.Head head,String proposal,String result,String status,JsonNode impact,TaskProtocol.ValidatedCreate input)throws Exception {
        var node=json.object().put("schema_version","1.0.0").put("operation_id",operation).put("task_id",head.taskId()).put("proposal_id",proposal)
                .put("basis_task_revision",head.taskRevision()).put("status",status).put("impact_sha256",hash(impact)).put("input_sha256",input.request_sha256()).put("resource_revision",head.revision().toString());
        if(result==null)node.putNull("resulting_task_revision");else node.put("resulting_task_revision",result);node.set("impact",impact);validate(node,"Receipt");return node;
    }
    private void save(EnterpriseAccess.Admission a,TaskProtocol.Head h,String op,String key,JsonNode request,TaskProtocol.ValidatedCreate input,String proposal,String result,JsonNode receipt)throws Exception {
        begin(a,h,op,key,request,receipt.required("operation_id").asText());finish(a,h,op,key,request,input,proposal,result,receipt);
    }
    private void begin(EnterpriseAccess.Admission a,TaskProtocol.Head h,String op,String key,JsonNode request,String id)throws Exception {
        changed(db.update("INSERT INTO operation_record(tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,target_id,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,'PROCESSING',?,'1.0.0',?,?,UTC_TIMESTAMP(6),?,1,UTC_TIMESTAMP(6))",a.context().tenant_id(),id,a.actorId(),op,key,hash(request),h.taskId(),manifest,a.actorId(),identity.classification()));
    }
    private void finish(EnterpriseAccess.Admission a,TaskProtocol.Head h,String op,String key,JsonNode request,TaskProtocol.ValidatedCreate input,String proposal,String result,JsonNode receipt)throws Exception {
        validate(receipt,"Receipt");String id=receipt.required("operation_id").asText(),canonical=json.canonical(receipt),response=json.canonical(envelope(receipt));
        changed(db.update("INSERT INTO task_input_continuation(tenant_id,project_id,task_id,operation_id,operation_name,proposal_id,basis_task_revision,resulting_task_revision,input_json,input_sha256,request_json,receipt_json,receipt_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)",a.context().tenant_id(),h.projectId(),h.taskId(),id,op,proposal,h.taskRevision(),result,input.canonical_request(),input.request_sha256(),json.canonical(request),canonical,hash(receipt),manifest,a.actorId(),identity.classification()));
        changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)",a.context().tenant_id(),h.projectId(),id,a.actorId(),op,canonical,id,id,manifest,a.actorId(),identity.classification()));
        changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6))",a.context().tenant_id(),id,h.taskId(),receipt.required("resource_revision").asText(),op,response));
        changed(db.update("UPDATE operation_record SET state='COMPLETED',http_status=200,response_ref=?,revision=2,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND operation_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? AND request_sha256=? AND state='PROCESSING' AND revision=1",response,a.context().tenant_id(),id,a.actorId(),op,key,hash(request)));
    }
    private JsonNode envelope(JsonNode receipt){return json.tree(new InterfaceProtocol.Envelope("1.0.0",receipt.required("operation_id").asText(),receipt.required("resource_revision").asText(),receipt));}
    private void grant(EnterpriseAccess.Admission a,TaskProtocol.Head h,String action)throws java.io.IOException{resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),h.projectId(),"TASK",h.taskId()),action,PURPOSE);}
    private JsonNode parse(byte[] body,String schema)throws Exception{var node=reader.readTree(body);validate(node,schema);return node;}
    private void validate(JsonNode node,String schema){if(node==null||!contracts.get(schema).validate(node).isEmpty())fail(ErrorCode.SCHEMA_INVALID);}
    private String hash(JsonNode node)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(node));}
    private static void current(EnterpriseAccess.Admission a,String context){if(!context.equals(a.context().context_revision()))fail(ErrorCode.CONTEXT_CHANGED);}
    private static String text(Map<String,Object> row,String key){var value=row.get(key);if(value==null)fail(ErrorCode.CONTENT_MISMATCH);return value.toString();}
    private static <T>T one(List<T> rows){if(rows.size()!=1)fail(ErrorCode.CONTENT_MISMATCH);return rows.get(0);}
    private static void changed(int n){if(n!=1)fail(ErrorCode.REVISION_CONFLICT);}
    private static void same(Object a,Object b){if(!Objects.equals(a,b))fail(ErrorCode.CONTENT_MISMATCH);}
    private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}
