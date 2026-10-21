package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.persistence.mapper.SourceExactMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.StrictFieldComparisonProtocol.*;

/** Actual full original/parsed reads under the exact live N03 TOOL action, never user impersonation. */
public final class TaskStrictFieldSourceReader {
    private final JdbcTemplate db;private final TaskStrictFieldSchema schema;private final PermissionSchema json;
    private final SourceReadAuthority sources;private final SourceExactMapper exacts;private final SourceExactSchema exact;
    private final TaskFieldSourceAuthority processing;private final TaskFieldSourceReader parsedVerifier;private final SourceUploadTransport transport;
    private final int maximumBytes;private final StrictComparisonRunScope scopes;private final StrictDecimalEvidenceService decimals;private final TaskStrictFieldContextService contexts;private final TaskStrictFieldTextEvidence texts;
    public TaskStrictFieldSourceReader(DataSource data,TaskStrictFieldSchema schema,PermissionSchema json,SourceReadAuthority sources,
            SourceExactMapper exacts,SourceExactSchema exact,TaskFieldSourceAuthority processing,TaskFieldSourceReader parsedVerifier,
            SourceUploadTransport transport,int maximumBytes,StrictComparisonRunScope scopes,StrictDecimalEvidenceService decimals,TaskStrictFieldContextService contexts,TaskStrictFieldTextEvidence texts){this.texts=texts;this.scopes=scopes;this.decimals=decimals;this.contexts=contexts;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.json=json;this.sources=sources;
        this.exacts=exacts;this.exact=exact;this.processing=processing;this.parsedVerifier=parsedVerifier;this.transport=transport;
        if(maximumBytes<1||maximumBytes>16*1024*1024)throw new IllegalArgumentException("N03_READ_CAPACITY");this.maximumBytes=maximumBytes;}
    private record MethodState(JsonNode identity,PermissionProtocol.Basis context,com.ruoyi.medical.contracts.DatasetProtocol.EvidenceRef evidence,String raw,TaskFieldSourceAuthority.Processing physical,JsonNode witness){}
    private MethodState methodState(TaskStrictFieldNode.Current c,PermissionProtocol.Basis pin)throws Exception{
        c.requireUnchanged();var input=c.input().input();var context=contexts.requireCurrentFixedRun(scopes.require(c.input().production()),pin.version_id(),pin.content_sha256());context.requireApproved();var evidence=context.evidence();var selected=input.fixedSources().stream().filter(x->x.sourceVersion().equals(evidence.sourceVersion())).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.ACCESS_DENIED));var policy=c.input().production().replay().content().required("execution_policy");
        var source=sources.require(c.admission(),new SourceReadProtocol.ReadRequirement(selected.sourceVersion(),selected.rightsRevision(),input.projectId(),input.snapshotId(),selected.parseVersion(),null,SourceReadProtocol.Use.FORMAL,selected.purpose(),text(policy,"environment"),text(policy,"region"),null));
        if(!selected.parseVersion().equals(evidence.parseVersion())||!selected.approvalRevision().equals(evidence.approvalRevision()))bad();var physical=processing.processing(input.tenantId(),selected.sourceVersion(),selected.parseVersion(),source.source().originalSha256());return new MethodState(c.identity(),pin,evidence,context.originalChunk(),physical,json.tree(List.of(context.receipt(),context.decision(),TaskFieldAuthorityWitness.tree(source))));
    }
    JsonNode readMethod(TaskStrictFieldNode node,TaskStrictFieldNode.Handle handle,PermissionProtocol.Basis context)throws Exception{
        var initial=node.execute(handle,c->methodState(c,context));
        var prior=node.execute(handle,c->{var rows=db.queryForList("SELECT receipt_json FROM task_strict_field_method_read WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? AND lease_generation=? AND context_revision=? LIMIT 2 FOR UPDATE",c.input().input().tenantId(),c.input().input().runId(),c.input().input().stepId(),c.actionId(),c.generation(),context.version_id());if(rows.isEmpty())return null;var receipt=schema.read(str(one(rows),"receipt_json"));requireMethodReceipt(c,context,receipt);return receipt;});if(prior!=null)return prior;
        S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{node.execute(handle,c->{if(!initial.equals(methodState(c,context)))bad();return null;});return null;}).get();
        consume(initial.physical().original(),node.execute(handle,c->c.input().input().tenantId()),node.execute(handle,c->c.actorId()),false,guard);byte[] bytes=consume(initial.physical().parsed(),node.execute(handle,c->c.input().input().tenantId()),node.execute(handle,c->c.actorId()),true,guard);
        var physical=initial.physical();var rawResult=physical.result().deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)rawResult).remove(List.of("object_ref","confirmed_by_worker","confirmed_lease_generation"));var bundle=new SourceProcessingResult().verify(physical.command(),rawResult,bytes).bundle();if(!"COMPLETE".equals(bundle.required("status").asText())||!bundle.required("issues").isEmpty()||!physical.original().sha256().equals(bundle.required("original_sha256").asText())||!physical.processorManifest().equals(bundle.required("processor_manifest")))bad();var chunks=new ArrayList<JsonNode>();for(var chunk:bundle.required("chunks"))if(initial.evidence().chunkId().equals(chunk.required("chunk_id").asText()))chunks.add(chunk);var chunk=one(chunks);if(!initial.raw().equals(chunk.required("raw_text").asText())||!initial.evidence().quoteHash().equals(WireValues.sha256(initial.raw().getBytes(StandardCharsets.UTF_8)))||!json.tree(initial.evidence().locator()).equals(chunk.required("locator")))bad();guard.requireCurrent();
        return node.execute(handle,c->{if(!initial.equals(methodState(c,context)))bad();String id=UUID.randomUUID().toString(),audit=UUID.randomUUID().toString();var at=db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class);var receipt=json.object().put("schema_version","1.0.0").put("kind","STRICT_FIELD_METHOD_READ").put("receipt_id",id).put("audit_id",audit);receipt.set("state",json.tree(initial));String canonical=schema.canonical(receipt);
            changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,'task.strict_field.method.read',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",c.input().input().tenantId(),c.input().input().projectId(),audit,c.actorId(),canonical,id,c.input().input().runId(),schema.manifest(),c.actorId(),at,c.classification()));
            changed(db.update("INSERT INTO task_strict_field_method_read(tenant_id,project_id,run_id,step_id,action_id,lease_generation,context_revision,context_sha256,receipt_id,receipt_json,receipt_sha256,audit_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",c.input().input().tenantId(),c.input().input().projectId(),c.input().input().runId(),c.input().input().stepId(),c.actionId(),c.generation(),context.version_id(),context.content_sha256(),id,canonical,schema.hash(receipt),audit,schema.manifest(),c.actorId(),at,c.classification()));requireMethodReceipt(c,context,receipt);return receipt;});
    }
    void requireMethodReceipt(TaskStrictFieldNode.Current c,PermissionProtocol.Basis context,JsonNode receipt)throws Exception{
        if(!json.tree(methodState(c,context)).equals(receipt.required("state")))bad();verifyMethodOriginal(c.admission(),receipt,c.identity());
    }
    void verifyMethodOriginal(EnterpriseAccess.Admission a,JsonNode receipt,JsonNode identity)throws Exception{
        String id=receipt.required("receipt_id").asText();var row=one(db.queryForList("SELECT * FROM task_strict_field_method_read WHERE tenant_id=? AND receipt_id=? FOR UPDATE",a.context().tenant_id(),id));if(!receipt.equals(schema.read(str(row,"receipt_json")))||!schema.hash(receipt).equals(str(row,"receipt_sha256"))||!identity.equals(receipt.required("state").required("identity"))||!schema.manifest().equals(str(row,"schema_manifest_sha256"))||!"1.0.0".equals(str(row,"schema_version")))bad();var action=identity.required("action");for(String key:List.of("run_id","step_id","action_id"))if(!action.required(key).asText().equals(str(row,key)))bad();if(!action.required("generation").asText().equals(str(row,"lease_generation"))||!identity.required("actor_id").asText().equals(str(row,"created_by"))||!receipt.required("state").required("context").required("version_id").asText().equals(str(row,"context_revision"))||!receipt.required("state").required("context").required("content_sha256").asText().equals(str(row,"context_sha256")))bad();var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? FOR UPDATE",a.context().tenant_id(),str(row,"audit_id")));for(String key:List.of("tenant_id","project_id","created_by","created_at","classification","schema_version","schema_manifest_sha256"))if(!Objects.equals(row.get(key),audit.get(key)))bad();if(!str(row,"created_by").equals(str(audit,"subject_id"))||!id.equals(str(audit,"request_id"))||!str(row,"run_id").equals(str(audit,"trace_id"))||!"task.strict_field.method.read".equals(str(audit,"action"))||!"SUCCESS".equals(str(audit,"result"))||audit.get("reason_code")!=null||!receipt.equals(schema.read(str(audit,"resource_ref"))))bad();
    }
    void readTarget(TaskStrictFieldNode node,TaskStrictFieldNode.Handle handle)throws Exception {
        var input=node.execute(handle,c->c.input().input());
        S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{node.execute(handle,c->{if(!input.equals(c.input().input()))bad();return null;});return null;}).get();
        byte[] bytes=consume(input.targetObject(),input.tenantId(),node.execute(handle,c->c.actorId()),true,guard);
        if(!Arrays.equals(ContentReadAuthorization.canonicalBytes(input.targetManifest()),bytes))bad();guard.requireCurrent();
    }
    Read read(TaskStrictFieldNode node,TaskStrictFieldNode.Handle handle,String entryId)throws Exception {
        Read original=node.execute(handle,c->{var rows=db.queryForList("SELECT receipt_id FROM task_strict_field_source_read WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? AND lease_generation=? AND entry_id=? LIMIT 2 FOR UPDATE",c.input().input().tenantId(),c.input().input().runId(),c.input().input().stepId(),c.actionId(),c.generation(),entryId);return rows.isEmpty()?null:requireReceipt(c,str(one(rows),"receipt_id"));});
        if(original!=null)return original;
        State initial=node.execute(handle,c->state(c,entryId));
        if(initial.field()==null&&initial.observation()==null)return new Read(entryId,null,null,initial); // explicit unsupported, never a read receipt
        S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{node.execute(handle,c->{same(initial,state(c,entryId));return null;});return null;}).get();
        consume(initial.physical().original(),initial.tenant(),initial.actor(),false,guard);
        byte[] parsed=consume(initial.physical().parsed(),initial.tenant(),initial.actor(),true,guard);
        if(initial.field()!=null)parsedVerifier.verifyParsed(initial.physical(),initial.field(),parsed);else verifyNumeric(initial,parsed);guard.requireCurrent();
        Read read=node.execute(handle,c->{same(initial,state(c,entryId));String id=UUID.randomUUID().toString();
            var body=json.object().put("schema_version","1.0.0").put("kind","TASK_STRICT_FIELD_SOURCE_READ").put("receipt_id",id).put("entry_id",entryId);
            body.set("identity",c.identity());body.put("state_sha256",initial.fingerprint());body.set("source",json.tree(initial.source()));body.set("field",json.tree(initial.field()));body.set("observation",json.tree(initial.observation()));
            body.set("original_object",json.tree(initial.physical().original()));body.set("parsed_object",json.tree(initial.physical().parsed()));
            String digest=schema.hash(body),audit=UUID.randomUUID().toString();var now=db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class);
            changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,'task.strict_field.source.read',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",
                initial.tenant(),c.input().input().projectId(),audit,c.actorId(),schema.canonical(body),id,c.input().input().runId(),schema.manifest(),c.actorId(),now,c.classification()));
            changed(db.update("INSERT INTO task_strict_field_source_read(tenant_id,project_id,run_id,step_id,input_id,input_sha256,action_id,lease_generation,entry_id,receipt_id,receipt_json,receipt_sha256,audit_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",
                initial.tenant(),c.input().input().projectId(),c.input().input().runId(),c.input().input().stepId(),c.input().inputId(),c.input().inputSha256(),c.actionId(),c.generation(),entryId,id,schema.canonical(body),digest,audit,schema.manifest(),c.actorId(),now,c.classification()));
            var result=new Read(entryId,id,digest,initial);requireCurrent(c,result);return result;});guard.requireCurrent();return read;
    }
    void requireCurrent(TaskStrictFieldNode.Current c,Read read)throws Exception {
        c.requireUnchanged();same(read.state,state(c,read.entryId));if(read.receiptId==null){if(read.state.field()!=null||read.state.observation()!=null)bad();return;}
        var row=one(db.queryForList("SELECT * FROM task_strict_field_source_read WHERE tenant_id=? AND receipt_id=? LIMIT 2 FOR UPDATE",c.input().input().tenantId(),read.receiptId));
        var body=schema.read(str(row,"receipt_json"));if(!read.receiptSha256.equals(str(row,"receipt_sha256"))||!read.receiptSha256.equals(schema.hash(body))
                ||!c.identity().equals(body.required("identity"))||!read.state.fingerprint().equals(text(body,"state_sha256"))||!read.entryId.equals(text(body,"entry_id"))
                ||!json.tree(read.state.observation()).equals(body.required("observation"))||!json.tree(read.state.field()).equals(body.required("field"))||!json.tree(read.state.source()).equals(body.required("source")))bad();
        for(var p:Map.of("run_id",c.input().input().runId(),"step_id",c.input().input().stepId(),"input_id",c.input().inputId(),"input_sha256",c.input().inputSha256(),"action_id",c.actionId(),"lease_generation",c.generation(),"created_by",c.actorId()).entrySet())if(!p.getValue().equals(str(row,p.getKey())))bad();
        var audit=one(db.queryForList("SELECT action,resource_ref,result,subject_id,request_id FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",c.input().input().tenantId(),str(row,"audit_id")));
        if(!"task.strict_field.source.read".equals(str(audit,"action"))||!"SUCCESS".equals(str(audit,"result"))||!c.actorId().equals(str(audit,"subject_id"))||!read.receiptId.equals(str(audit,"request_id"))||!body.equals(schema.read(str(audit,"resource_ref"))))bad();
    }
    Read requireMissing(TaskStrictFieldNode.Current c,String entry)throws Exception {var state=state(c,entry);if(state.field()!=null||state.observation()!=null)bad();return new Read(entry,null,null,state);}
    Read requireReceipt(TaskStrictFieldNode.Current c,String receipt)throws Exception {
        var row=one(db.queryForList("SELECT entry_id,receipt_sha256 FROM task_strict_field_source_read WHERE tenant_id=? AND receipt_id=? LIMIT 2 FOR UPDATE",c.input().input().tenantId(),receipt));
        var read=new Read(str(row,"entry_id"),receipt,str(row,"receipt_sha256"),state(c,str(row,"entry_id")));requireCurrent(c,read);return read;
    }
    private State state(TaskStrictFieldNode.Current c,String entryId)throws Exception {
        c.requireUnchanged();var input=c.input().input();var target=schema.target(input);var entry=target.entries().stream().filter(e->e.entryId().equals(entryId)).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.ACCESS_DENIED));
        if(TaskStrictFieldTypedProtocol.typed(input.targetManifest().path("schema_ref").asText()))return typedState(c,entry);
        if(entry.provenance()==null||entry.provenance().source()==null)return new State(input.tenantId(),c.actorId(),c.identity(),null,null,null,schema.hash(List.of(c.identity(),entry)));
        SourceIdentity identity=entry.provenance().source();if(!"SOURCE_EXACT_REVISION".equals(identity.kind())||identity.sourceExact()==null)
            return new State(input.tenantId(),c.actorId(),c.identity(),identity,null,null,schema.hash(List.of(c.identity(),entry)));
        var evidence=Objects.requireNonNull(identity.evidenceRef());var pin=input.fixedSources().stream().filter(p->p.sourceVersion().equals(evidence.sourceVersion())).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.ACCESS_DENIED));
        var execution=c.input().production().replay().content().required("execution_policy");
        var source=sources.require(c.admission(),new SourceReadProtocol.ReadRequirement(pin.sourceVersion(),pin.rightsRevision(),input.projectId(),input.snapshotId(),pin.parseVersion(),null,SourceReadProtocol.Use.FORMAL,pin.purpose(),text(execution,"environment"),text(execution,"region"),null));
        if(!evidence.tenantId().equals(input.tenantId())||!evidence.projectId().equals(input.projectId())||!evidence.sourceId().equals(source.source().sourceId())
                ||!evidence.parseVersion().equals(pin.parseVersion())||!evidence.approvalRevision().equals(pin.approvalRevision())||source.selection()==null
                ||!pin.bindingRevision().equals(source.selection().bindingRevision())||!pin.indexVersion().equals(source.selection().indexVersion()))bad();
        var args=new HashMap<String,Object>();args.put("sourceVersion",pin.sourceVersion());args.put("fieldRevision",identity.sourceExact().fieldRevision());args.put("projectId",input.projectId());args.put("userId",c.actorId());args.put("fieldLimit",10001);
        var row=one(exacts.revision(args));var manifest=exact.parse(str(row,"manifest"));exact.require("Manifest",manifest);var published=exact.storedInput(manifest.required("request"));
        if(!identity.sourceExact().fieldManifestSha256().equals(schema.hash(manifest))||!identity.sourceExact().fieldManifestSha256().equals(str(row,"manifest_sha256"))
                ||!identity.sourceExact().fieldManifestSha256().equals(str(row,"registered_sha256"))||!"SOURCE_EXACT_REVISION".equals(str(row,"entity_kind"))
                ||!source.source().originalSha256().equals(text(manifest,"original_sha256"))||!pin.parseVersion().equals(published.parse_version())
                ||!pin.indexVersion().equals(published.index_version())||!pin.approvalRevision().equals(published.approval_revision())
                ||!pin.bindingRevision().equals(published.binding_revision())||!pin.rightsRevision().equals(published.rights_revision())||!input.projectId().equals(published.project_id()))bad();
        int ordinal=identity.sourceExact().ordinal();if(ordinal<0||ordinal>=published.fields().size()||published.fields().size()>10000)bad();var field=published.fields().get(ordinal);exact.checkField(field);
        if(!identity.sourceExact().fieldKind().equals(field.field_kind())||!evidence.chunkId().equals(field.chunk_id())||!evidence.quoteHash().equals(field.quote_hash()))bad();
        var actualFields=exacts.fields(args);if(actualFields.size()!=published.fields().size()||!json.tree(field).equals(exact.parse(str(actualFields.get(ordinal),"field_manifest"))))bad();
        var physical=processing.processing(input.tenantId(),pin.sourceVersion(),pin.parseVersion(),source.source().originalSha256());
        var selected=input.policies().stream().filter(x->x.entryId().equals(entryId)).findFirst().orElseThrow();if(selected.sourceContext()==null)throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
        var context=contexts.requireCurrentFixedRun(scopes.require(c.input().production()),selected.sourceContext().version_id(),selected.sourceContext().content_sha256());context.requireApproved();if(!evidence.equals(context.evidence()))bad();
        var value=new ArtifactProtocol.FieldValue(ArtifactProtocol.ValueKind.TEXT,field.raw_value(),null,null,null,List.of(),null);
        var provenance=new Provenance(identity,List.of(context.revisionId()),field.start_codepoint(),field.end_codepoint(),WireValues.sha256(field.raw_value().getBytes(StandardCharsets.UTF_8)));
        var observation=new SourceObservation(context.models().ids(),context.conditions().ids(),contextState(context.models()),contextState(context.conditions()),Applicability.APPLICABLE,value,provenance);
        return new State(input.tenantId(),c.actorId(),c.identity(),identity,field,physical,schema.hash(List.of(c.identity(),identity,json.tree(field),TaskFieldAuthorityWitness.tree(source),json.tree(physical),context.receipt(),context.decision())),observation);
    }
    private byte[] consume(ObjectRef reference,String tenant,String actor,boolean keep,S3ObjectReader.Admission current)throws Exception {
        long size=WireValues.revision(reference.sizeBytes()).longValueExact();if(size<1||size>maximumBytes)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        return transport.readTaskDocument(tenant,actor,maximumBytes,current,(reader,guard)->{try(var object=reader.readVerified(reference,guard)){
            if(!reference.equals(object.reference()))bad();var digest=java.security.MessageDigest.getInstance("SHA-256");byte[] bytes=keep?new byte[(int)size]:null;
            for(int offset=0;offset<size;){guard.requireCurrent();int length=(int)Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);byte[] chunk=object.chunk(offset,length);if(chunk.length!=length)bad();digest.update(chunk);if(keep)System.arraycopy(chunk,0,bytes,offset,length);offset+=length;guard.requireCurrent();}
            String hash=HexFormat.of().formatHex(digest.digest());if(!hash.equals(reference.sha256())||!hash.equals(object.fullSha256()))bad();guard.requireCurrent();return bytes;
        }});
    }
    private record State(String tenant,String actor,JsonNode identity,SourceIdentity source,SourceExactProtocol.Field field,TaskFieldSourceAuthority.Processing physical,String fingerprint,SourceObservation observation){
        State(String tenant,String actor,JsonNode identity,SourceIdentity source,SourceExactProtocol.Field field,TaskFieldSourceAuthority.Processing physical,String fingerprint){this(tenant,actor,identity,source,field,physical,fingerprint,null);}
    }
    private State typedState(TaskStrictFieldNode.Current c,TargetEntry entry)throws Exception{
        var input=c.input().input();var matches=new ArrayList<JsonNode>();for(var e:input.targetManifest().required("bindings"))if(entry.fieldId().equals(e.required("field_id").asText()))matches.add(e);var binding=one(matches);var context=json.basis(binding.required("observed_context"));var run=scopes.require(c.input().production());
        var scope=contexts.requireCurrentFixedRun(run,context.version_id(),context.content_sha256());scope.requireApproved();
        ArtifactProtocol.FieldValue observedValue;Provenance provenance;SourceExactProtocol.Field exactField=null;JsonNode valueReceipt;
        if(!binding.required("observed_decimal").isNull()){
            var decimal=json.basis(binding.required("observed_decimal"));var value=decimals.requireCurrentFixedRun(run,decimal.version_id(),decimal.content_sha256());if(!value.evidence().equals(scope.evidence())||!value.scope().equals(scope.scope()))bad();observedValue=value.value();provenance=value.provenance();valueReceipt=value.receipt();value.requireUnchanged();
        }else{var ref=binding.required("observed_exact");var reference=new SourceExactReference(text(ref,"field_revision"),text(ref,"field_manifest_sha256"),ref.required("ordinal").intValue(),text(ref,"field_kind"));var value=texts.require(c.admission(),input.projectId(),scope,reference);observedValue=value.value();provenance=value.provenance();exactField=value.field();valueReceipt=json.tree(value.provenance());value.requireUnchanged();}
        var observation=new SourceObservation(scope.models().ids(),scope.conditions().ids(),contextState(scope.models()),contextState(scope.conditions()),Applicability.APPLICABLE,observedValue,provenance);
        var evidence=scope.evidence();var pin=input.fixedSources().stream().filter(p->p.sourceVersion().equals(evidence.sourceVersion())).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.ACCESS_DENIED));var execution=c.input().production().replay().content().required("execution_policy");
        var source=sources.require(c.admission(),new SourceReadProtocol.ReadRequirement(pin.sourceVersion(),pin.rightsRevision(),input.projectId(),input.snapshotId(),pin.parseVersion(),null,SourceReadProtocol.Use.FORMAL,pin.purpose(),text(execution,"environment"),text(execution,"region"),null));
        if(!input.tenantId().equals(evidence.tenantId())||!input.projectId().equals(evidence.projectId())||!source.source().sourceId().equals(evidence.sourceId())||!pin.parseVersion().equals(evidence.parseVersion())||!pin.approvalRevision().equals(evidence.approvalRevision()))bad();
        var physical=processing.processing(input.tenantId(),pin.sourceVersion(),pin.parseVersion(),source.source().originalSha256());scope.requireApproved();
        return new State(input.tenantId(),c.actorId(),c.identity(),provenance.source(),exactField,physical,schema.hash(List.of(c.identity(),observation,valueReceipt,scope.receipt(),scope.decision(),TaskFieldAuthorityWitness.tree(source),json.tree(physical))),observation);
    }
    private static ContextState contextState(TaskStrictFieldTypedProtocol.Context context){return context.mode()==TaskStrictFieldTypedProtocol.Mode.GLOBAL?ContextState.KNOWN_GLOBAL:ContextState.KNOWN_SCOPED;}
    private void verifyNumeric(State state,byte[] bytes)throws Exception{
        var physical=state.physical();var result=physical.result().deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)result).remove(List.of("object_ref","confirmed_by_worker","confirmed_lease_generation"));var bundle=new SourceProcessingResult().verify(physical.command(),result,bytes).bundle();
        if(!"COMPLETE".equals(bundle.required("status").asText())||!bundle.required("issues").isEmpty()||!physical.original().sha256().equals(bundle.required("original_sha256").asText())||!physical.processorManifest().equals(bundle.required("processor_manifest")))bad();
        var evidence=state.source().evidenceRef();var chunks=new ArrayList<JsonNode>();for(var chunk:bundle.required("chunks"))if(evidence.chunkId().equals(chunk.required("chunk_id").asText()))chunks.add(chunk);var chunk=one(chunks);String raw=chunk.required("raw_text").asText();
        if(!evidence.quoteHash().equals(WireValues.sha256(raw.getBytes(StandardCharsets.UTF_8)))||!evidence.quoteHash().equals(chunk.required("quote_hash").asText())||!json.tree(evidence.locator()).equals(chunk.required("locator")))bad();var proof=state.observation().provenance();
        if(!state.observation().value().rawText().equals(TaskStrictFieldTypedProtocol.span(raw,new StrictComparisonProtocol.Span(proof.startCodepoint(),proof.endCodepoint()))))bad();
    }
    public static final class Read {
        private final String entryId,receiptId,receiptSha256;private final State state;
        private Read(String entryId,String receiptId,String receiptSha256,State state){this.entryId=entryId;this.receiptId=receiptId;this.receiptSha256=receiptSha256;this.state=state;}
        public String entryId(){return entryId;}public String receiptId(){return receiptId;}public String receiptSha256(){return receiptSha256;}SourceIdentity source(){return state.source();}
        SourceObservation observation(StrictComparisonPolicyDefinition.Definition policy)throws Exception{
            if(receiptId==null)return null;
            if(state.observation()!=null){var observation=state.observation();var p=observation.provenance();var receipts=new ArrayList<>(p.readReceiptIds());receipts.add(receiptId);return new SourceObservation(observation.modelIds(),observation.conditionIds(),observation.modelContext(),observation.conditionContext(),observation.applicability(),observation.value(),new Provenance(p.source(),receipts,p.startCodepoint(),p.endCodepoint(),p.rawTextSha256()));}
            if(state.field()==null)return null;
            var field=state.field();var value=new ArtifactProtocol.FieldValue(ArtifactProtocol.ValueKind.TEXT,field.raw_value(),null,null,null,List.of(),null);
            var provenance=new Provenance(state.source(),List.of(receiptId),field.start_codepoint(),field.end_codepoint(),WireValues.sha256(field.raw_value().getBytes(StandardCharsets.UTF_8)));
            // Lexical SourceExact proves text, not model/condition scope. Policy declarations
            // cannot supply missing source facts, even for a GLOBAL comparison.
            return new SourceObservation(List.of(),List.of(),ContextState.UNRESOLVED,ContextState.UNRESOLVED,
                    Applicability.UNKNOWN,value,provenance);
        }
    }
    private static void same(State a,State b){if(!a.fingerprint().equals(b.fingerprint()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String text(JsonNode n,String key){return n.required(key).textValue();}
    private static String str(Map<String,Object> n,String key){var v=n.get(key);if(v==null)bad();return v.toString();}
    private static void changed(int n){if(n!=1)bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
