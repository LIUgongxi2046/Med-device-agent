package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.TaskStrictFieldTypedStore.*;

/** Original source context spans plus independent human approval. Labels alone never constitute source facts. */
public final class TaskStrictFieldContextService {
    static final String PRODUCE="task.strict_field_context.write", READ="task.strict_field_context.read";
    private final EnterpriseAccess access;private final TaskStrictFieldTypedStore store;private final StrictComparisonCodec codec;
    private final StrictComparisonAuthority authority;private final SourceReadAuthority sources;private final TaskFieldSourceAuthority processingObjects;
    private final SourceUploadTransport transport;private final TaskFieldTargetPolicy objects;private final StrictComparisonRuntimePolicy capacity;
    private final SourceProcessingResult processing;private final TaskStrictFieldTypedActors actors;
    static final String APPROVE="task.strict_field_context.approve";
    TaskStrictFieldContextService(EnterpriseAccess access,TaskStrictFieldTypedStore store,StrictComparisonCodec codec,StrictComparisonAuthority authority,
            SourceReadAuthority sources,TaskFieldSourceAuthority processingObjects,SourceUploadTransport transport,TaskFieldTargetPolicy objects,StrictComparisonRuntimePolicy capacity,TaskStrictFieldTypedActors actors) throws Exception {
        this.actors=actors;this.access=access;this.store=store;this.codec=codec;this.authority=authority;this.sources=sources;this.processingObjects=processingObjects;
        this.transport=transport;this.objects=objects;this.capacity=capacity;processing=new SourceProcessingResult();
    }
    public JsonNode produce(ApplicationSessionIdentity.Verified subject,String context,String project,String key,byte[] bytes) throws Exception {
        capacity.requireConfigured();objects.requireConfigured();if(transport==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var request=codec.bind(bytes,TaskStrictFieldTypedProtocol.ContextAuthor.class);var tree=codec.tree(request);if("2.0.0".equals(request.schemaVersion()))com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath().validate("strict-field-typed-v2",tree,"ContextAuthor");TaskFieldMappingService.key(key);
        var original=access.execute(subject,a->checked(()->{StrictComparisonAuthority.human(a,context);return require(a,project,request,PRODUCE);}));
        var old=access.execute(subject,a->checked(()->{StrictComparisonAuthority.human(a,context);same(original,require(a,project,request,PRODUCE));return store.replay(a,project,Kind.CONTEXT,key,tree);}));
        if(old!=null)return access.execute(subject,a->checked(()->{StrictComparisonAuthority.human(a,context);return requireCurrent(a,project,old.id(),old.digest()).receipt();}));
        S3ObjectReader.Admission guard=()->access.execute(subject,a->checked(()->{StrictComparisonAuthority.human(a,context);same(original,require(a,project,request,PRODUCE));return null;}));
        var physical=original.processing();
        transport.readDocument(subject,subjectTenant(original),objects.maximumOriginalBytes(),guard,(reader,lease)->{
            var live=TaskFieldSourceReader.guard(guard,lease);try(var input=reader.readVerified(physical.original(),live)){consume(input,objects.maximumOriginalBytes(),false,live);return null;}
        });
        byte[] parsed=transport.readDocument(subject,subjectTenant(original),objects.maximumParsedBytes(),guard,(reader,lease)->{
            var live=TaskFieldSourceReader.guard(guard,lease);try(var input=reader.readVerified(physical.parsed(),live)){return consume(input,objects.maximumParsedBytes(),true,live);}
        });
        var result=(ObjectNode)physical.result().deepCopy();result.remove(List.of("object_ref","confirmed_by_worker","confirmed_lease_generation"));
        var verified=processing.verify(physical.command(),result,parsed);var bundle=verified.bundle();
        if(!"COMPLETE".equals(bundle.required("status").asText())||!bundle.required("issues").isEmpty()||!physical.original().sha256().equals(bundle.required("original_sha256").asText())||!physical.processorManifest().equals(bundle.required("processor_manifest")))mismatch();
        JsonNode selected=null;for(var chunk:bundle.required("chunks"))if(request.evidence().chunkId().equals(chunk.required("chunk_id").asText())){if(selected!=null)mismatch();selected=chunk;}
        if(selected==null)mismatch();String raw=selected.required("raw_text").textValue();
        if(!request.evidence().quoteHash().equals(selected.required("quote_hash").asText())||!WireValues.sha256(raw.getBytes(StandardCharsets.UTF_8)).equals(request.evidence().quoteHash())||!codec.tree(request.evidence().locator()).equals(selected.required("locator")))mismatch();
        TaskStrictFieldTypedProtocol.requireContext(request.models(),raw);TaskStrictFieldTypedProtocol.requireContext(request.conditions(),raw);
        var body=(ObjectNode)codec.tree(Map.of("request",request,"chunk_text",raw,"original_object",physical.original(),"parsed_object",physical.parsed(),"processing_attempt",physical.attempt(),"processing_command",physical.command(),"processing_result",physical.result(),"processor_manifest",physical.processorManifest()));
        guard.requireCurrent();
        return access.execute(subject,a->checked(()->{
            StrictComparisonAuthority.human(a,context);var now=require(a,project,request,PRODUCE);same(original,now);
            var existing=store.replay(a,project,Kind.CONTEXT,key,tree);if(existing!=null)return requireCurrent(a,project,existing.id(),existing.digest()).receipt();
            var authority=codec.tree(Map.of("current",now.witness(),"author",actors.witness(a,project,PRODUCE)));var saved=store.append(a,project,Kind.CONTEXT,null,key,tree,body,authority);one(store.db.update("INSERT INTO task_strict_field_context_state(tenant_id,project_id,revision_id,revision,status,decision_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) SELECT tenant_id,project_id,revision_id,1,'DRAFT',NULL,schema_version,schema_manifest_sha256,created_by,created_at,classification FROM task_strict_field_context_revision WHERE tenant_id=? AND project_id=? AND revision_id=?",a.context().tenant_id(),project,saved.id()));same(now,require(a,project,request,PRODUCE));return saved.receipt();
        }));
    }
    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String project,String id) {
        return access.execute(subject,a->checked(()->{StrictComparisonAuthority.human(a,context);authority.project(a,project,READ);var row=store.read(a,project,Kind.CONTEXT,id);return requireCurrent(a,project,id,row.digest()).view();}));
    }
    public final class Verified {
        private final EnterpriseAccess.Admission admission;private final String project;private final Row row;private final TaskStrictFieldTypedProtocol.ContextAuthor request;private final JsonNode witness;private final StrictComparisonRunScope.Verified run;
        private Verified(EnterpriseAccess.Admission a,String project,Row row,TaskStrictFieldTypedProtocol.ContextAuthor request,JsonNode witness,StrictComparisonRunScope.Verified run){this.admission=a;this.project=project;this.row=row;this.request=request;this.witness=witness;this.run=run;}
        public TaskStrictFieldTypedProtocol.Context models(){return request.models();}public TaskStrictFieldTypedProtocol.Context conditions(){return request.conditions();}
        public com.ruoyi.medical.contracts.DatasetProtocol.EvidenceRef evidence(){return request.evidence();}public StrictComparisonProtocol.Scope scope(){return request.scope();}
        public String revisionId(){return row.id();}public String digest(){return row.digest();}public JsonNode receipt(){return row.receipt().deepCopy();}
        SourceReadProtocol.VerifiedSource sourceProof()throws Exception{requireApproved();return require(admission,project,request,READ,run).source();}
        String originalChunk(){return row.body().required("chunk_text").asText();}
        public JsonNode view(){return codec.tree(Map.of("request",request,"receipt",receipt(),"assessment","SOURCE_CONTEXT_REQUIRES_INDEPENDENT_APPROVAL"));}
        public void requireUnchanged()throws Exception{var fresh=current(admission,project,row.id(),row.digest(),run);if(!witness.equals(fresh.witness))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
        public void requireApproved()throws Exception{requireUnchanged();approved(admission,project,row);}
        PermissionProtocol.Basis decision()throws Exception{requireApproved();var state=state(admission,project,row.id());var decision=store.read(admission,project,Kind.CONTEXT_DECISION,text(state,"decision_id"));return new PermissionProtocol.Basis(decision.id(),decision.digest());}
    }
    Verified requireCurrent(EnterpriseAccess.Admission a,String project,String id,String digest) throws Exception {
        return current(a,project,id,digest,null);
    }
    Verified requireCurrentFixedRun(StrictComparisonRunScope.Verified run,String id,String digest) throws Exception {
        return current(run.admission(),run.project(),id,digest,run);
    }
    private Verified current(EnterpriseAccess.Admission a,String project,String id,String digest,StrictComparisonRunScope.Verified run) throws Exception {
        capacity.requireConfigured();objects.requireConfigured();var row=store.read(a,project,Kind.CONTEXT,id);if(!digest.equals(row.digest()))mismatch();
        var request=codec.bind(row.request(),TaskStrictFieldTypedProtocol.ContextAuthor.class);if("2.0.0".equals(request.schemaVersion()))com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath().validate("strict-field-typed-v2",row.request(),"ContextAuthor");if(!row.request().equals(row.body().required("request")))mismatch();
        var now=require(a,project,request,READ,run);var body=row.body();var physical=now.processing();
        if(!codec.tree(physical.original()).equals(body.required("original_object"))||!codec.tree(physical.parsed()).equals(body.required("parsed_object"))||!physical.command().equals(body.required("processing_command"))||!physical.result().equals(body.required("processing_result"))||!physical.processorManifest().equals(body.required("processor_manifest"))||!physical.attempt().equals(body.required("processing_attempt").asText()))mismatch();
        String raw=body.required("chunk_text").asText();if(!WireValues.sha256(raw.getBytes(StandardCharsets.UTF_8)).equals(request.evidence().quoteHash()))mismatch();
        TaskStrictFieldTypedProtocol.requireContext(request.models(),raw);TaskStrictFieldTypedProtocol.requireContext(request.conditions(),raw);
        if(!row.receipt().required("created_by").asText().equals(row.authority().required("author").required("actor").asText()))mismatch();
        actors.require(a,project,PRODUCE,row.authority().required("author"));
        return new Verified(a,project,row,request,now.witness(),run);
    }

    public JsonNode baseline(ApplicationSessionIdentity.Verified subject,String context,String project,String id){return access.execute(subject,a->checked(()->{
        StrictComparisonAuthority.human(a,context);authority.project(a,project,READ);var row=store.read(a,project,Kind.CONTEXT,id);var current=requireCurrent(a,project,id,row.digest());
        var baseline=baseline(a,project,row,current);return codec.tree(Map.of("baseline",baseline,"baseline_sha256",codec.hash(baseline)));
    }));}
    public JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String project,String id,String key,byte[] bytes)throws Exception{
        var request=codec.bind(bytes,TaskStrictFieldTypedProtocol.Decide.class);var envelope=codec.tree(Map.of("revision_id",id,"decision",request));
        return access.execute(subject,a->checked(()->{StrictComparisonAuthority.human(a,context);var approver=actors.witness(a,project,APPROVE);var row=store.read(a,project,Kind.CONTEXT,id);if(!request.schemaVersion().equals(row.request().path("schema_version").asText()))mismatch();
            if(a.actorId().equals(row.receipt().required("created_by").asText()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
            var previous=store.replay(a,project,Kind.CONTEXT_DECISION,key,envelope);if(previous!=null){if(!id.equals(previous.parentId()))mismatch();return previous.receipt();}
            var live=requireCurrent(a,project,id,row.digest());var baseline=baseline(a,project,row,live);var state=state(a,project,id);
            if(!request.expectedRevision().equals(text(state,"revision")))throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
            if(!request.contentSha256().equals(row.digest())||!request.baselineSha256().equals(codec.hash(baseline)))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            String status=switch(request.decision()){case APPROVE->"APPROVED";case REJECT->"REJECTED";case DISABLE->"DISABLED";};
            if(status.equals(text(state,"status"))||"DRAFT".equals(text(state,"status"))&&"DISABLED".equals(status))throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
            String revision=PermissionSchema.next(request.expectedRevision());var body=codec.tree(Map.of("revision_id",id,"content_sha256",row.digest(),"revision",revision,"status",status,"baseline",baseline,"decision",request,"approver",approver));
            var saved=store.append(a,project,Kind.CONTEXT_DECISION,id,key,envelope,body,baseline);
            one(store.db.update("UPDATE task_strict_field_context_state SET revision=?,status=?,decision_id=? WHERE tenant_id=? AND project_id=? AND revision_id=? AND revision=?",revision,status,saved.id(),a.context().tenant_id(),project,id,request.expectedRevision()));
            live.requireUnchanged();actors.require(a,project,APPROVE,approver);store.advance(a);return saved.receipt();
        }));
    }
    private JsonNode baseline(EnterpriseAccess.Admission a,String project,Row row,Verified live)throws Exception{return codec.tree(Map.of("revision_id",row.id(),"content_sha256",row.digest(),"state",state(a,project,row.id()),"source_authority",live.witness,"author",row.authority().required("author")));}
    private Map<String,Object> state(EnterpriseAccess.Admission a,String project,String id){var row=single(store.db.queryForList("SELECT * FROM task_strict_field_context_state WHERE tenant_id=? AND project_id=? AND revision_id=? FOR UPDATE",a.context().tenant_id(),project,id));var original=single(store.db.queryForList("SELECT * FROM task_strict_field_context_revision WHERE tenant_id=? AND project_id=? AND revision_id=? FOR UPDATE",a.context().tenant_id(),project,id));for(String key:List.of("tenant_id","project_id","revision_id","schema_version","schema_manifest_sha256","created_by","created_at","classification"))if(!Objects.equals(row.get(key),original.get(key)))mismatch();var result=new HashMap<String,Object>();for(String key:List.of("revision","status","decision_id"))result.put(key,row.get(key));return result;}
    private void approved(EnterpriseAccess.Admission a,String project,Row row)throws Exception{
        var state=state(a,project,row.id());if(!"APPROVED".equals(text(state,"status"))||state.get("decision_id")==null)throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
        var decision=store.read(a,project,Kind.CONTEXT_DECISION,text(state,"decision_id"));var body=decision.body();var supplied=codec.bind(body.required("decision"),TaskStrictFieldTypedProtocol.Decide.class);
        if(!row.id().equals(decision.parentId())||!row.id().equals(body.required("revision_id").asText())||!row.digest().equals(body.required("content_sha256").asText())||!row.digest().equals(supplied.contentSha256())||!"APPROVED".equals(body.required("status").asText())||supplied.decision()!=StrictComparisonProtocol.Decision.APPROVE
            ||!text(state,"revision").equals(body.required("revision").asText())||!text(state,"revision").equals(PermissionSchema.next(supplied.expectedRevision()))||!codec.hash(body.required("baseline")).equals(supplied.baselineSha256())
            ||row.receipt().required("created_by").equals(decision.receipt().required("created_by"))||!decision.receipt().required("created_by").asText().equals(body.required("approver").required("actor").asText()))mismatch();
        actors.require(a,project,PRODUCE,row.authority().required("author"));actors.require(a,project,APPROVE,body.required("approver"));
    }
    private record Current(StrictComparisonAuthority.Current task,SourceReadProtocol.VerifiedSource source,TaskFieldSourceAuthority.Processing processing,JsonNode witness) { }
    private Current require(EnterpriseAccess.Admission a,String project,TaskStrictFieldTypedProtocol.ContextAuthor request,String action) throws Exception {
        return require(a,project,request,action,null);
    }
    private Current require(EnterpriseAccess.Admission a,String project,TaskStrictFieldTypedProtocol.ContextAuthor request,String action,StrictComparisonRunScope.Verified run) throws Exception {
        var current=run==null?authority.require(a,project,request.scope(),action):authority.requireRun(run,request.scope(),action);var evidence=request.evidence();com.ruoyi.medical.contracts.SourceEvidenceBindings.bind(com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath(),codec.tree(evidence)).read();if(!request.schemaVersion().equals(evidence.schemaVersion()))mismatch();
        if(!a.context().tenant_id().equals(evidence.tenantId())||!project.equals(evidence.projectId()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var requirement=new SourceReadProtocol.ReadRequirement(evidence.sourceVersion(),request.rightsRevision(),project,request.scope().scopeSnapshot(),evidence.parseVersion(),null,SourceReadProtocol.Use.FORMAL,request.purpose(),objects.environment(),objects.region(),null);
        var source=sources.require(a,requirement);
        if(!source.source().sourceId().equals(evidence.sourceId())||!source.selection().approvalRevision().equals(evidence.approvalRevision())||evidence.locatorKind()==null||!evidence.locatorKind().name().equals(evidence.locator().kind()))mismatch();
        var scoped=current.sources().stream().filter(s->s.source().sourceVersion().equals(evidence.sourceVersion())).toList();
        if(scoped.size()!=1||!source.equals(scoped.get(0)))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        var physical=processingObjects.processing(a.context().tenant_id(),evidence.sourceVersion(),evidence.parseVersion(),source.source().originalSha256());
        var witness=codec.tree(Map.of("task",current.witness(),"source",TaskFieldAuthorityWitness.tree(source),"processing",physical,"parser",implementation()));
        current.requireUnchanged();return new Current(current,source,physical,witness);
    }
    private static String subjectTenant(Current current){return current.source().source().tenantId();}
    private static byte[] consume(S3ObjectReader.VerifiedObject input,int maximum,boolean keep,S3ObjectReader.Admission guard) throws Exception {
        int size=WireValues.revision(input.reference().sizeBytes()).intValueExact();if(size<1||size>maximum)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var hash=java.security.MessageDigest.getInstance("SHA-256");byte[] result=keep?new byte[size]:null;
        for(int offset=0;offset<size;){guard.requireCurrent();int length=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);byte[] part=input.chunk(offset,length);hash.update(part);if(keep)System.arraycopy(part,0,result,offset,length);offset+=length;guard.requireCurrent();}
        String digest=java.util.HexFormat.of().formatHex(hash.digest());if(!digest.equals(input.fullSha256())||!digest.equals(input.reference().sha256()))mismatch();return result;
    }
    static StrictComparisonPolicyDefinition.Version implementation() throws Exception {return StrictComparisonCodec.implementation(TaskStrictFieldTypedProtocol.class,"STRICT_FIELD_CONTEXT_SPANS_V1");}
    private static void same(Current a,Current b){if(!a.witness().equals(b.witness()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
    private static <T>T checked(ThrowingSupplier<T> action){return action.get();}
}
