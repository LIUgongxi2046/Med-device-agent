package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Private physical predecessor read capability, never an SDK claim or a copied registration.
 * Original completion/075 facts, consuming 097/lease/098, fixed lineage and current rights
 * are independent checks. MODEL_SEND is checked again under the actual MODEL_CALL action. */
public final class TaskProfessionalPredecessorEvidence {
    private final TaskNodeInputAuthority inputs;private final TaskResearchCompletionAuthority completions;
    private final TaskProfessionalObjectRegistration objects;private final TaskProfessionalContentAuthority contents;
    private final TaskNodeProducerAuthority producers;private final TaskProfessionalSupervisor supervisor;
    private final TaskProfessionalRuntimeService.Deployment deployment;private final SourceUploadTransport transport;
    private final SourceReadAuthority sources;private final TaskProfessionalSchema schema;private final PermissionSchema json;private final JdbcTemplate db;
    private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public TaskProfessionalPredecessorEvidence(DataSource data,TaskNodeInputAuthority inputs,TaskResearchCompletionAuthority completions,
            TaskProfessionalObjectRegistration objects,TaskProfessionalContentAuthority contents,TaskNodeProducerAuthority producers,
            TaskProfessionalSupervisor supervisor,TaskProfessionalRuntimeService.Deployment deployment,SourceUploadTransport transport,
            SourceReadAuthority sources,TaskProfessionalSchema schema,PermissionSchema json) {
        this(new JdbcTemplate(data),inputs,completions,objects,contents,producers,supervisor,deployment,transport,sources,schema,json);
    }
    TaskProfessionalPredecessorEvidence(JdbcTemplate db,TaskNodeInputAuthority inputs,TaskResearchCompletionAuthority completions,
            TaskProfessionalObjectRegistration objects,TaskProfessionalContentAuthority contents,TaskNodeProducerAuthority producers,
            TaskProfessionalSupervisor supervisor,TaskProfessionalRuntimeService.Deployment deployment,SourceUploadTransport transport,
            SourceReadAuthority sources,TaskProfessionalSchema schema,PermissionSchema json) {
        this.inputs=inputs;this.completions=completions;this.objects=objects;this.contents=contents;this.producers=producers;this.supervisor=supervisor;
        this.deployment=deployment;this.transport=transport;this.sources=sources;this.schema=schema;this.json=json;this.db=db;db.setQueryTimeout(5);
    }
    private record Snapshot(String tenant,String actor,String inputId,String inputHash,String inputReceipt,JsonNode predecessor,
            InterfaceProtocol.ObjectRef object,JsonNode pins,JsonNode bundle) { }
    public Reads readWorker(TaskProfessionalHostAccess hosts,TaskProfessionalHostAccess.Handle handle)throws Exception {
        if(TransactionSynchronizationManager.isActualTransactionActive())deny();
        JsonNode expected=hosts.execute(handle,a->ThrowingSupplier.<JsonNode>of(()->input(a).manifest().required("predecessors").deepCopy()).get());
        if(!expected.isArray()||expected.size()>512)bad();var snapshots=new ArrayList<Snapshot>();var bytes=new ArrayList<byte[]>();long total=0;
        for(var pin:expected) {
            Snapshot original=hosts.execute(handle,a->ThrowingSupplier.<Snapshot>of(()->state(a,input(a),pin,true)).get());
            total+=Long.parseLong(original.object().sizeBytes());if(total>deployment.maximumBytes())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{hosts.execute(handle,a->ThrowingSupplier.of(()->{if(!original.equals(state(a,input(a),pin,true)))stale();return null;}).get());return null;}).get();
            byte[] physical=transport.readTaskDocument(original.tenant(),original.actor(),deployment.maximumBytes(),guard,(reader,current)->{
                try(var object=reader.readVerified(original.object(),current)) {
                    if(!original.object().equals(object.reference()))bad();int size=Integer.parseInt(original.object().sizeBytes());byte[] result=new byte[size];
                    for(int start=0;start<size;) {current.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-start);System.arraycopy(object.chunk(start,count),0,result,start,count);start+=count;current.requireCurrent();}
                    if(!original.object().sha256().equals(object.fullSha256())||!original.object().sha256().equals(WireValues.sha256(result)))bad();current.requireCurrent();return result;
                }
            });
            // Only physically read canonical bytes enter the prompt. SQL payload alone never suffices.
            if(!Arrays.equals(physical,ContentReadAuthorization.canonicalBytes(original.bundle()))||!schema.read("EVIDENCE_BUNDLE",physical,text(original.pins(),"schema_ref"),text(original.pins(),"schema_sha256")).equals(original.bundle()))bad();
            guard.requireCurrent();snapshots.add(original);bytes.add(physical.clone());
        }
        var result=new Reads(this,List.copyOf(snapshots),List.copyOf(bytes),expected);
        hosts.execute(handle,a->ThrowingSupplier.of(()->{result.requireCurrent(a);return null;}).get());return result;
    }
    private TaskNodeInputAuthority.Verified input(EnterpriseAccess.Admission a)throws Exception {
        TaskProfessionalRuntimeService.current(a);var i=a.formalProof().identity();return inputs.require(a,i.run(),i.step());
    }
    private Snapshot state(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,JsonNode pin,boolean host)throws Exception {
        TaskProfessionalRuntimeService.current(a);input.requireUnchanged();TaskProfessionalPromptV2.predecessor(pin);
        var matches=new ArrayList<JsonNode>();for(var allowed:input.manifest().required("predecessors"))if(allowed.equals(pin))matches.add(allowed);if(matches.size()!=1)deny();
        var completion=completions.require(a,input.runId(),text(pin,"step_id"));completion.requireUnchanged();
        var actual=new TaskNodeInputProtocol.Predecessor(text(pin,"step_id"),completion.resultArtifactId(),completion.resultSha256(),completion.resultObjectVersion(),completion.resultReceiptSha256(),completion.evidenceArtifactId(),completion.evidenceSha256(),completion.evidenceObjectVersion(),completion.evidenceReceiptSha256(),completion.completionReceiptSha256());
        if(!json.tree(actual).equals(pin))bad();
        var original=objects.readOriginalProduced(a,text(pin,"evidence_artifact_id"),"EVIDENCE_BUNDLE",supervisor,deployment);original.requireUnchanged();
        var receipt=original.receipt();var object=original.reference();
        requireOriginal(input.manifest(),a.actorId(),pin,receipt,object,original.receiptSha256());
        if(Long.parseLong(object.sizeBytes())<1||Long.parseLong(object.sizeBytes())>deployment.maximumBytes())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        if(host)producers.requireLeased(a,input).requireHostPredecessorConsume(input,object.sizeBytes());
        var fixed=contents.require(input);fixed.requireUnchanged();
        requireFixedContent(receipt,json.tree(fixed.roots()),json.tree(fixed.members()));
        var bundle=schema.read("EVIDENCE_BUNDLE",original.bytes(),text(receipt,"schema_ref"),text(receipt,"payload_schema_sha256"));requireFixedReferences(bundle,input.manifest());
        var pins=json.object();pins.set("predecessor",pin.deepCopy());pins.set("object_ref",json.tree(object));
        pins.put("schema_ref",text(receipt,"schema_ref")).put("schema_sha256",text(receipt,"payload_schema_sha256"));
        pins.set("content_roots",receipt.required("content_roots").deepCopy());pins.set("content_members",receipt.required("content_manifest").required("members").deepCopy());
        completion.requireUnchanged();original.requireUnchanged();input.requireUnchanged();fixed.requireUnchanged();
        return new Snapshot(input.tenantId(),a.actorId(),input.inputId(),input.inputSha256(),input.receiptSha256(),pin.deepCopy(),object,pins,bundle);
    }
    /** Pure comparisons for negative boundary tests. They do not return a read capability. */
    static void requireOriginal(JsonNode input,String actor,JsonNode pin,JsonNode receipt,InterfaceProtocol.ObjectRef object,String receiptHash) {
        for(String key:List.of("tenant_id","project_id","task_id","run_id"))if(!input.required(key).equals(receipt.required(key)))bad();
        if(!actor.equals(text(receipt,"created_by"))||!"EVIDENCE_BUNDLE".equals(text(receipt,"kind"))||!text(pin,"step_id").equals(text(receipt,"step_id"))
                ||!text(pin,"evidence_artifact_id").equals(text(receipt,"artifact_id"))||!text(pin,"evidence_sha256").equals(object.sha256())
                ||!text(pin,"evidence_object_version").equals(object.objectVersion())||!text(pin,"evidence_receipt_sha256").equals(receiptHash)
                ||!"application/json".equals(object.mediaType())||!object.objectKey().startsWith("tenants/"+text(input,"tenant_id")+"/")||object.objectKey().contains(".."))bad();
    }
    static void requireFixedContent(JsonNode receipt,JsonNode roots,JsonNode members) {
        if(!roots.equals(receipt.required("content_roots"))||!members.equals(receipt.required("content_manifest").required("members")))deny();
    }
    static void requireFixedReferences(JsonNode bundle,JsonNode input) {
        var refs=new ArrayList<JsonNode>();for(String key:List.of("selected_refs","conflict_refs"))for(var ref:bundle.required(key))refs.add(ref);
        for(var question:bundle.required("question_coverage"))for(var link:question.required("evidence_links"))refs.add(link.required("evidence_ref"));
        for(var ref:refs) {
            if(!input.required("tenant_id").equals(ref.required("tenant_id"))||!input.required("project_id").equals(ref.required("project_id")))deny();
            int matches=0;for(var fixed:input.required("fixed_sources"))if(fixed.required("source_version").equals(ref.required("source_version"))
                    &&fixed.required("parse_version").equals(ref.required("parse_version"))&&fixed.required("approval_revision").equals(ref.required("approval_revision")))matches++;
            if(matches!=1)deny();
        }
    }
    /** Current original pins are rechecked even after preparation. Caller pins grant nothing. */
    JsonNode requireCurrentPins(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,JsonNode pins,String purpose,String provider)throws Exception {
        if(!pins.isArray()||pins.size()!=input.manifest().required("predecessors").size())bad();
        var transmitted=json.object().arrayNode();
        for(int n=0;n<pins.size();n++) {
            var expected=state(a,input,input.manifest().required("predecessors").get(n),false);
            if(!expected.pins().equals(pins.get(n)))stale();transmitted.add(expected.pins().deepCopy());
        }
        if(provider!=null) {
            var i=a.formalProof().identity();var rows=db.query("SELECT replay_manifest FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND execution_kind='TASK' AND status='RUNNING' AND ended_at IS NULL AND dispatch_closed_at IS NULL AND superseded_by_run_id IS NULL LIMIT 2 FOR UPDATE",(r,n)->r.getString(1),input.tenantId(),input.projectId(),input.runId());
            if(rows.size()!=1)deny();var replay=parser.readTree(rows.get(0));if(!WireValues.sha256(ContentReadAuthorization.canonicalBytes(replay)).equals(text(input.manifest(),"replay_sha256")))bad();
            var policy=replay.required("execution_policy");if(!text(policy,"business_purpose").equals(purpose))deny();
            var authorizations=json.object().arrayNode();
            for(var fixed:input.fixedSources()) {
                if(!fixed.purpose().equals(purpose))deny();
                var current=sources.require(a,new SourceReadProtocol.ReadRequirement(fixed.sourceVersion(),fixed.rightsRevision(),i.project(),input.snapshotId(),fixed.parseVersion(),null,SourceReadProtocol.Use.MODEL_SEND,purpose,text(policy,"environment"),text(policy,"region"),provider));
                if(current.selection()==null||!fixed.indexVersion().equals(current.selection().indexVersion())||!fixed.approvalRevision().equals(current.selection().approvalRevision())||!fixed.bindingRevision().equals(current.selection().bindingRevision()))bad();
                authorizations.add(AgentPublicationWitness.tree(current));
            }
            input.requireUnchanged();var sent=json.object();sent.set("predecessors",transmitted);sent.set("source_authorizations",authorizations);return sent;
        }
        input.requireUnchanged();return transmitted;
    }
    public static final class Reads {
        private final TaskProfessionalPredecessorEvidence owner;private final List<Snapshot> snapshots;private final List<byte[]> bytes;private final JsonNode expected;
        private Reads(TaskProfessionalPredecessorEvidence owner,List<Snapshot> snapshots,List<byte[]> bytes,JsonNode expected){this.owner=owner;this.snapshots=snapshots;this.bytes=bytes;this.expected=expected.deepCopy();}
        void requireCurrent(EnterpriseAccess.Admission a)throws Exception {
            var input=owner.input(a);if(!expected.equals(input.manifest().required("predecessors")))stale();
            for(int n=0;n<snapshots.size();n++) {var previous=snapshots.get(n);if(!previous.equals(owner.state(a,input,expected.get(n),true))||!WireValues.sha256(bytes.get(n)).equals(previous.object().sha256()))stale();}
        }
        JsonNode document(EnterpriseAccess.Admission a)throws Exception {
            requireCurrent(a);var result=owner.json.object().arrayNode();for(int n=0;n<snapshots.size();n++){var item=result.addObject();item.set("predecessor",snapshots.get(n).predecessor().deepCopy());item.set("evidence_bundle",owner.schema.read("EVIDENCE_BUNDLE",bytes.get(n),text(snapshots.get(n).pins(),"schema_ref"),text(snapshots.get(n).pins(),"schema_sha256")));}return result;
        }
        JsonNode canonicalPins(){var pins=owner.json.object().arrayNode();for(var snapshot:snapshots)pins.add(snapshot.pins().deepCopy());return pins;}
    }
    private static String text(JsonNode n,String f){return TaskProfessionalPromptV2.text(n,f);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}
