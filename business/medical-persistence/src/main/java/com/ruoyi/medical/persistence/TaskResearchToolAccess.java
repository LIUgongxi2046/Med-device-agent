package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.SimulationFixtureMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Bounded AT-01/AT-02 reads. No browser session, dynamic source scope or model-supplied coverage. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="medical.professional-runtime.task-enabled",havingValue="true",matchIfMissing=false)
public final class TaskResearchToolAccess {
    private final FormalWorkerAccess workers;
    private final TaskNodeInputAuthority inputs;
    private final RetrievalService retrievals;
    private final SourceReadAuthority sources;
    private final SimulationFixtureMapper originals;
    private final SourceUploadTransport transport;
    private final RetrievalPolicy policy;
    private final TaskResearchSourceReader parsedSources;
    private static final int MAXIMUM_SOURCE_BYTES = 16 * 1024 * 1024;

    @org.springframework.beans.factory.annotation.Autowired
    public TaskResearchToolAccess(FormalWorkerAccess workers, TaskNodeInputAuthority inputs, RetrievalService retrievals,
            SourceReadAuthority sources, SimulationFixtureMapper originals, SourceUploadTransport transport, RetrievalPolicy policy,
            TaskResearchSourceReader parsedSources) {
        this.workers=workers; this.inputs=inputs; this.retrievals=retrievals; this.sources=sources;
        this.originals=originals; this.transport=transport; this.policy=policy; this.parsedSources=Objects.requireNonNull(parsedSources);
    }

    TaskResearchToolAccess(FormalWorkerAccess workers, TaskNodeInputAuthority inputs, RetrievalService retrievals,
            SourceReadAuthority sources, SimulationFixtureMapper originals, SourceUploadTransport transport, RetrievalPolicy policy) {
        this.workers=workers; this.inputs=inputs; this.retrievals=retrievals; this.sources=sources;
        this.originals=originals; this.transport=transport; this.policy=policy; this.parsedSources=null;
    }

    /** Opening this handle does not grant permissions. Every use re-enters the original live token action. */
    public Handle open(HttpServletRequest http, String token, RunTokenAuthority.ActionRequest action) throws Exception {
        outside();
        Fixed fixed=workers.executeAction(http,token,action,(a,t)->ThrowingSupplier.of(()->fixed(a,action)).get());
        return new Handle(http,token,action,fixed);
    }

    public JsonNode createRetrieval(Handle handle) throws Exception {
        handle.requireArguments("AT-01:create",handle.fixed.questionId());
        return execute(handle,a->ThrowingSupplier.of(()-> {
            requireSources(a,handle.fixed);
            if(handle.fixed.questionText().codePointCount(0,handle.fixed.questionText().length())>500
                    || handle.fixed.questionText().codePointCount(0,handle.fixed.questionText().length())<2)
                throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            var input=new RetrievalProtocol.SearchInput("1.0.0",handle.fixed.projectId(),handle.fixed.questionText(),"VERIFICATION");
            return retrievals.create(a,"0",handle.fixed.retrievalKey(),input,handle.fixed.identity(),handle.fixed);
        }).get());
    }

    public JsonNode readRetrieval(Handle handle,String retrievalId,RetrievalProtocol.PageQuery page) throws Exception {
        WireValues.uuid(retrievalId); Objects.requireNonNull(page);
        if(page.page()<1 || page.pageSize()<1 || page.pageSize()>100) deny();
        handle.requireArguments("AT-01:read",retrievalId+":"+page.page()+":"+page.pageSize());
        return execute(handle,a->ThrowingSupplier.of(()-> {
            requireSources(a,handle.fixed);
            return retrievals.read(a,"0",retrievalId,page,handle.fixed);
        }).get());
    }

    /** Original binary bytes only. This is not parsed text, quote evidence, or question-completion proof. */
    public SourceRead readSource(Handle handle,String sourceVersion) throws Exception {
        WireValues.uuid(sourceVersion); handle.requireArguments("AT-02:source",sourceVersion); outside();
        SourceState initial=execute(handle,a->ThrowingSupplier.of(()->sourceState(a,handle.fixed,sourceVersion)).get());
        S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()-> {
            var fresh=execute(handle,a->ThrowingSupplier.of(()->sourceState(a,handle.fixed,sourceVersion)).get());
            if(!initial.equals(fresh)) stale(); return null;
        }).get();
        byte[] bytes=transport.readTaskDocument(handle.fixed.tenantId(),handle.fixed.actorId(),MAXIMUM_SOURCE_BYTES,guard,(reader,current)-> {
            try(var object=reader.readVerified(initial.original().reference(),current)) {
                if(!initial.original().reference().equals(object.reference())) mismatch();
                int size=initial.original().sizeBytes().intValueExact();
                if(size<1 || size>MAXIMUM_SOURCE_BYTES) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
                byte[] value=new byte[size];
                for(int start=0;start<size;) {
                    current.requireCurrent(); int length=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-start);
                    System.arraycopy(object.chunk(start,length),0,value,start,length);start+=length;current.requireCurrent();
                }
                if(!WireValues.sha256(value).equals(initial.original().sha256()) || !object.fullSha256().equals(initial.original().sha256())) mismatch();
                current.requireCurrent(); return value;
            }
        });
        guard.requireCurrent(); return new SourceRead(sourceVersion,initial.original().sha256(),initial.original().mediaType(),bytes,guard);
    }

    /** Formal AT-02: exact parsed evidence with a typed durable source-read receipt. */
    public TaskResearchSourceReader.Read readSource(Handle handle,com.ruoyi.medical.contracts.DatasetProtocol.EvidenceRef evidence,String cursor) throws Exception {
        if(parsedSources==null) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        handle.requireArguments("AT-02:read",TaskResearchSourceReader.requestSha256(evidence,cursor));
        return parsedSources.read(this,handle,evidence,cursor);
    }

    /** EvidenceBundle completion must call this; an arbitrary audit/content receipt UUID is not accepted. */
    public void requireSourceReadReceipts(Handle handle,List<String> receiptIds,
            List<com.ruoyi.medical.contracts.DatasetProtocol.EvidenceRef> selectedRefs) throws Exception {
        if(parsedSources==null) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        parsedSources.requireReceipts(this,handle,receiptIds,selectedRefs);
    }

    Fixed fixed(Handle handle){if(handle==null || handle.owner!=this) deny();return handle.fixed;}
    TaskNodeInputAuthority.Verified inputProof(Handle handle,EnterpriseAccess.Admission admission) throws Exception {
        fixed(handle);return inputs.require(admission,handle.action.runId(),handle.action.stepId());
    }
    JsonNode sourceReceiptIdentity(Handle handle,EnterpriseAccess.Admission admission) throws Exception {
        var input=inputs.require(admission,handle.action.runId(),handle.action.stepId());
        return receiptIdentity(input,admission);
    }
    static JsonNode receiptIdentity(TaskNodeInputAuthority.Verified input,EnterpriseAccess.Admission admission) throws Exception {
        var worker=admission.formalProof().identity();
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var result=mapper.createObjectNode();
        result.put("input_id",input.inputId()).put("input_sha256",input.inputSha256()).put("input_receipt_sha256",input.receiptSha256());
        result.set("node_input",input.manifest()); result.put("actor_id",admission.actorId());
        result.put("worker_service",worker.workerService()).put("lease_generation",worker.generation());
        input.requireUnchanged();return result;
    }
    RunTokenAuthority.ActionRequest action(Handle handle){fixed(handle);return handle.action;}
    String tokenSha256(Handle handle){fixed(handle);return org.apache.commons.codec.digest.DigestUtils.sha256Hex(handle.token);}
    String actionId(Handle handle){fixed(handle);return handle.action.actionId();}

    private SourceState sourceState(EnterpriseAccess.Admission a,Fixed fixed,String sourceVersion) throws Exception {
        var selected=fixed.source(sourceVersion); var proof=requireSource(a,fixed,selected);
        var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("sourceVersion",sourceVersion);
        var rows=originals.original(values);if(rows.size()!=1) mismatch();var original=rows.get(0);
        if(!fixed.tenantId().equals(original.tenantId()) || !sourceVersion.equals(original.sourceVersion())
                || !proof.source().originalSha256().equals(original.sha256())
                || !original.objectKey().startsWith("tenants/"+fixed.tenantId()+"/") || original.objectKey().contains("..")) mismatch();
        if(original.sizeBytes().signum()<=0 || original.sizeBytes().compareTo(java.math.BigInteger.valueOf(MAXIMUM_SOURCE_BYTES))>0)
            throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        return new SourceState(original,WireValues.sha256(ContentReadAuthorization.canonicalBytes(proof)));
    }

    private void requireSources(EnterpriseAccess.Admission a,Fixed fixed) throws Exception {
        for(var source:fixed.sources()) requireSource(a,fixed,source);
    }
    SourceReadProtocol.VerifiedSource requireSource(EnterpriseAccess.Admission a,Fixed fixed,TaskNodeInputProtocol.SourceSelection pin) throws Exception {
        var proof=sources.require(a,new SourceReadProtocol.ReadRequirement(pin.sourceVersion(),pin.rightsRevision(),fixed.projectId(),
                fixed.snapshotId(),pin.parseVersion(),null,SourceReadProtocol.Use.FORMAL,pin.purpose(),policy.environment(),policy.region(),null));
        var selection=proof.selection();
        if(selection==null || !pin.indexVersion().equals(selection.indexVersion()) || !pin.approvalRevision().equals(selection.approvalRevision())
                || !pin.bindingRevision().equals(selection.bindingRevision())) mismatch();
        return proof;
    }

    <T>T execute(Handle handle,Function<EnterpriseAccess.Admission,T> operation) throws Exception {
        Objects.requireNonNull(handle); if(handle.owner!=this) deny(); outside();
        return workers.executeAction(handle.http,handle.token,handle.action,(a,t)->ThrowingSupplier.of(()-> {
            Fixed current=fixed(a,handle.action);if(!handle.fixed.equals(current)) stale();
            T result=operation.apply(a);
            if(!current.equals(fixed(a,handle.action))) stale();return result;
        }).get());
    }
    private Fixed fixed(EnterpriseAccess.Admission a,RunTokenAuthority.ActionRequest action) throws Exception {
        if(!a.formalWorker() || !"TASK".equals(a.formalProof().identity().executionKind())) deny();
        var input=inputs.require(a,action.runId(),action.stepId());
        if(!action.inputSha256().equals(input.inputSha256())) mismatch();
        String identity=WireValues.sha256(ContentReadAuthorization.canonicalBytes(Map.of(
                "tenant",input.tenantId(),"project",input.projectId(),"run",input.runId(),"step",input.stepId(),
                "input",input.inputId(),"receipt",input.receiptSha256(),"input_sha256",input.inputSha256(),
                "graph",input.graphId(),"graph_sha256",input.graphSha256())));
        input.requireUnchanged();
        return new Fixed(input.tenantId(),input.projectId(),a.actorId(),input.snapshotId(),input.questionId(),input.questionText(),
                input.fixedSources(),identity);
    }

    /** The dispatcher must bind this digest into its real action row before issuing the action token. */
    public static String argumentsSha256(String operation,String target) throws Exception {
        if(!Set.of("AT-01:create","AT-01:read","AT-02:source","AT-02:read").contains(operation) || target==null || target.isBlank()) deny();
        return WireValues.sha256(ContentReadAuthorization.canonicalBytes(Map.of("operation",operation,"target",target)));
    }
    public final class Handle {
        private final TaskResearchToolAccess owner=TaskResearchToolAccess.this;
        private final HttpServletRequest http; private final String token; private final RunTokenAuthority.ActionRequest action; private final Fixed fixed;
        private Handle(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,Fixed fixed){this.http=http;this.token=token;this.action=action;this.fixed=fixed;}
        private void requireArguments(String operation,String target) throws Exception {
            if(!argumentsSha256(operation,target).equals(action.argumentsSha256())) mismatch();
        }
    }
    public static final class SourceRead {
        private final String sourceVersion,sha256,mediaType; private final byte[] bytes; private final S3ObjectReader.Admission current;
        private SourceRead(String version,String hash,String type,byte[] bytes,S3ObjectReader.Admission current){sourceVersion=version;sha256=hash;mediaType=type;this.bytes=bytes.clone();this.current=current;}
        public String sourceVersion(){return sourceVersion;} public String sha256(){return sha256;} public String mediaType(){return mediaType;}
        public byte[] bytes(){current.requireCurrent();return bytes.clone();}
        public void requireCurrent(){current.requireCurrent();}
    }
    record Fixed(String tenantId,String projectId,String actorId,String snapshotId,String questionId,String questionText,
            List<TaskNodeInputProtocol.SourceSelection> sources,String identity) {
        Fixed { sources=List.copyOf(sources); if(questionId==null || questionText==null || questionText.isBlank()) deny();
            var ids=new HashSet<String>();for(var source:sources) if(!ids.add(source.sourceVersion())) mismatch(); }
        String retrievalKey(){return "task-research-"+identity;}
        TaskNodeInputProtocol.SourceSelection source(String version){return sources.stream().filter(s->s.sourceVersion().equals(version)).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.ACCESS_DENIED));}
        void require(RetrievalProtocol.Candidate value){require(value.sourceVersion(),value.parseVersion(),value.indexVersion(),value.approvalRevision(),value.rightsRevision());}
        void require(RetrievalProtocol.Hit value){require(value.sourceVersion(),value.parseVersion(),value.indexVersion(),value.approvalRevision(),value.rightsRevision());}
        private void require(String version,String parse,String index,String approval,String rights){var pin=source(version);
            if(!pin.parseVersion().equals(parse)||!pin.indexVersion().equals(index)||!pin.approvalRevision().equals(approval)||!pin.rightsRevision().equals(rights)) mismatch();}
    }
    private record SourceState(SimulationInvocationSources.Original original,String authoritySha256) { }
    private static void outside(){if(TransactionSynchronizationManager.isActualTransactionActive()) deny();}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}
