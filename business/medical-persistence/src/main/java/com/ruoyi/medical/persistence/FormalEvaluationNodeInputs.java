package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.FormalEvaluationProtocol;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.FormalEvaluationExactInputObjectLedger.*;

/** Real original EVAL input consumption. The sample body and expected answer are never treated
 * as a compiled prompt. N01 consumes only independently approved 088/physically registered 089
 * exact input, with the original compiler, current ancestry and fresh bounded object GET. */
public final class FormalEvaluationNodeInputs {
    private final FormalEvaluationStageInputPreparation stages;private final FormalEvaluationStageInputObjects stageObjects;private final RuntimeObjectTransport stageTransport;
    private final FormalEvaluationJobs jobs;private final FormalEvaluationExactInputObjectLedger ledger;
    private final FormalEvaluationExactInputObjectRegistration registrations;private final FormalEvaluationContextStore contexts;
    private final FormalEvaluationExactInputObjectDependencies dependencies;private final AgentExecutionPolicyAuthority policies;
    private final FormalEvaluationNodeInputObjectLedger planningLedger;private final FormalEvaluationNodeInputObjectRegistration planningRegistrations;private final FormalEvaluationNodeInputObjectDependencies planningDependencies;
    private final FormalEvaluationContextReads contextReads;private final SourceUploadTransport transport;private final PermissionSchema json;
    private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public FormalEvaluationNodeInputs(FormalEvaluationJobs jobs,FormalEvaluationExactInputObjectLedger ledger,
            FormalEvaluationExactInputObjectRegistration registrations,FormalEvaluationContextStore contexts,
            FormalEvaluationExactInputObjectDependencies dependencies,AgentExecutionPolicyAuthority policies,SourceUploadTransport transport,PermissionSchema json,FormalEvaluationContextReads contextReads,FormalEvaluationNodeInputObjectLedger planningLedger,FormalEvaluationNodeInputObjectRegistration planningRegistrations,FormalEvaluationNodeInputObjectDependencies planningDependencies){
        this(jobs,ledger,registrations,contexts,dependencies,policies,transport,json,contextReads,planningLedger,planningRegistrations,planningDependencies,null,null,null);
    }
    public FormalEvaluationNodeInputs(FormalEvaluationJobs jobs,FormalEvaluationExactInputObjectLedger ledger,
            FormalEvaluationExactInputObjectRegistration registrations,FormalEvaluationContextStore contexts,
            FormalEvaluationExactInputObjectDependencies dependencies,AgentExecutionPolicyAuthority policies,SourceUploadTransport transport,PermissionSchema json,FormalEvaluationContextReads contextReads,FormalEvaluationNodeInputObjectLedger planningLedger,FormalEvaluationNodeInputObjectRegistration planningRegistrations,FormalEvaluationNodeInputObjectDependencies planningDependencies,FormalEvaluationStageInputPreparation stages,FormalEvaluationStageInputObjects stageObjects,RuntimeObjectTransport stageTransport){
        this.stages=stages;this.stageObjects=stageObjects;this.stageTransport=stageTransport;
        this.planningLedger=planningLedger;this.planningRegistrations=planningRegistrations;this.planningDependencies=planningDependencies;this.contextReads=contextReads;this.jobs=jobs;this.ledger=ledger;this.registrations=registrations;this.contexts=contexts;this.dependencies=dependencies;this.policies=policies;this.transport=transport;this.json=json;
    }
    private record Stored(Map<String,Object> columns,JsonNode intent){}
    private record Receipt(FormalEvaluationExecutionProtocol.Artifact artifact,String receiptSha256){String objectVersion(){return artifact.objectVersion();}}
    private record Snapshot(FormalEvaluationJobs.Job job,FormalEvaluationJobs.NodeRow node,Stored original,Receipt receipt,String canonical){}
    public Verified read(FormalEvaluationControlAccess.Reads reads,FormalEvaluationProtocol.Step step)throws Exception {
        reads.requireActive();var actualContext=contextReads.read(reads,step.stepId());if(!"N01".equals(step.nodeKind()))return stage(reads,step,actualContext);var before=reads.execute(a->ThrowingSupplier.of(()->snapshot(a,reads.runId(),step)).get());
        var original=before.original();var receipt=before.receipt();var ref=new InterfaceProtocol.ObjectRef(text(original.intent(),"object_key"),receipt.objectVersion(),receipt.artifact().sha256(),text(original.intent(),"payload_size"),"application/json");
        S3ObjectReader.Admission guard=()->reads.execute(a->ThrowingSupplier.of(()->{same(before.canonical(),snapshot(a,reads.runId(),step).canonical());return null;}).get());
        byte[] bytes=transport.readEvaluationDocument(reads,BuiltinEvaluationDocumentContextCompiler.MAXIMUM_BYTES,guard,(reader,lease)->{
            var timed=new S3ObjectReader.TimedAdmission(){public java.time.Duration remainingTime(){return lease.remainingTime();}public void requireCurrent(){lease.requireCurrent();guard.requireCurrent();}};
            try(var physical=reader.readVerified(ref,timed)){
                same(ref,physical.reference());same(ref.sha256(),physical.fullSha256());byte[] result=new byte[Integer.parseInt(ref.sizeBytes())];
                for(int offset=0;offset<result.length;){timed.requireCurrent();int size=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,result.length-offset);byte[] chunk=physical.chunk(offset,size);if(chunk.length!=size)bad();System.arraycopy(chunk,0,result,offset,size);offset+=size;}
                same(ref.sha256(),WireValues.sha256(result));if(!Arrays.equals(result,text(original.columns(),"canonical_payload").getBytes(StandardCharsets.UTF_8)))bad();timed.requireCurrent();return result;
            }
        });
        var result=new Verified(this,reads,step,before,bytes,actualContext);reads.execute(a->ThrowingSupplier.of(()->{result.requireCurrent(a);return null;}).get());return result;
    }
    /** Recovery consumes registered original bytes. It never recompiles, tokenizes or transfers an input. */
    public Verified readOriginal(FormalEvaluationControlAccess.Reads reads,FormalEvaluationProtocol.Step step)throws Exception {
        if("N01".equals(step.nodeKind()))return read(reads,step);
        if(stages==null||stageObjects==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var context=contextReads.read(reads,step.stepId());
        var original=reads.execute(a->ThrowingSupplier.of(()->{var o=stageObjects.readOriginal(a,reads.runId(),step.stepId(),"STAGE_INPUT");stages.requireOriginal(a,reads.runId(),step,o.evidence());return o;}).get());
        var node=reads.execute(a->ThrowingSupplier.of(()->{var job=jobs.original(a,reads.runId());var nodes=job.nodes().stream().filter(n->n.stepId().equals(step.stepId())&&n.nodeSha256().equals(step.nodeSha256())).toList();if(nodes.size()!=1)bad();return nodes.get(0);}).get());
        S3ObjectReader.Admission guard=()->reads.execute(a->ThrowingSupplier.of(()->{context.requireCurrent(a);same(original,stageObjects.readOriginal(a,reads.runId(),step.stepId(),"STAGE_INPUT"));stages.requireOriginal(a,reads.runId(),step,original.evidence());return null;}).get());
        byte[] bytes=transport.readEvaluationDocument(reads,BuiltinEvaluationDocumentContextCompiler.MAXIMUM_BYTES,guard,(reader,lease)->{
            var timed=new S3ObjectReader.TimedAdmission(){public java.time.Duration remainingTime(){return lease.remainingTime();}public void requireCurrent(){lease.requireCurrent();guard.requireCurrent();}};
            try(var physical=reader.readVerified(original.reference(),timed)){
                long bounded=Long.parseLong(original.reference().sizeBytes());if(bounded<1||bounded>BuiltinEvaluationDocumentContextCompiler.MAXIMUM_BYTES)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);byte[] result=new byte[(int)bounded];
                for(int offset=0;offset<result.length;){timed.requireCurrent();int size=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,result.length-offset);byte[] chunk=physical.chunk(offset,size);if(chunk.length!=size)bad();System.arraycopy(chunk,0,result,offset,size);offset+=size;}
                same(original.artifact().sha256(),WireValues.sha256(result));if(!Arrays.equals(result,original.payload().getBytes(StandardCharsets.UTF_8)))bad();timed.requireCurrent();return result;
            }
        });
        return new Verified(this,reads,step,original,context,node,bytes);
    }
    private Verified stage(FormalEvaluationControlAccess.Reads reads,FormalEvaluationProtocol.Step step,FormalEvaluationContextReads.Verified context)throws Exception {
        if(stages==null||stageObjects==null||stageTransport==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var prepared=stages.prepare(reads,step,context);
        var attempt=reads.execute(a->ThrowingSupplier.of(()->{var intent=stageObjects.prepare(a,prepared);return stageObjects.attempt(a,prepared,intent);}).get());
        var physical=stageTransport.transferVerified(attempt,()->reads.execute(a->ThrowingSupplier.of(()->{stageObjects.requireTransferCurrent(a,prepared,attempt);return null;}).get()));
        var registered=reads.execute(a->ThrowingSupplier.of(()->stageObjects.register(a,prepared,attempt,physical)).get());
        var original=reads.execute(a->ThrowingSupplier.of(()->stageObjects.readOriginal(a,reads.runId(),step.stepId(),"STAGE_INPUT")).get());
        same(registered.artifact(),original.artifact());same(prepared.evidence(),original.evidence());
        return new Verified(this,reads,step,prepared,original,context);
    }
    private Snapshot snapshot(EnterpriseAccess.Admission a,String run,FormalEvaluationProtocol.Step step)throws Exception {
        FormalEvaluationExecutionAdmission.current(a);var job=jobs.original(a,run);var matches=job.nodes().stream().filter(n->n.stepId().equals(step.stepId())).toList();if(matches.size()!=1)bad();var node=matches.get(0);
        same(step.nodeKind(),node.node().nodeKind());same(step.nodeSha256(),node.nodeSha256());
        if(!"N01".equals(node.node().nodeKind()))throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var cell=node.node().cell();var admission=parser.readTree(job.canonicalAdmission());var b=admission.required("context_binding");
        var binding=new PermissionProtocol.Basis(text(b,"version_id"),text(b,"content_sha256"));
        var approved=contexts.requireApproved(a,binding,cell.candidate(),cell.policy());var configured=approved.node(node.node().nodeKind());
        boolean planning=BuiltinEvaluationPlanningContextCompiler.INPUT.equals(configured.inputSchema().schemaRef());Stored row;Receipt receipt;
        if(planning){
            var attempt=planningLedger.originalForNode(a,job.executionId(),cell.sampleId(),cell.repeatIndex(),node.node().nodeKind());
            var actual=planningLedger.requireOriginalStored(a,attempt);var registered=planningRegistrations.readStored(a,attempt).orElseThrow(()->new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE));
            row=new Stored(actual.columns(),actual.intent());receipt=new Receipt(new FormalEvaluationExecutionProtocol.Artifact(registered.artifact().artifactId(),registered.artifact().sha256(),registered.objectVersion(),BuiltinEvaluationPlanningContextCompiler.OUTPUT),registered.receiptSha256());
        }else{
            same(BuiltinEvaluationDocumentContextCompiler.INPUT,configured.inputSchema().schemaRef());
            var actual=ledger.original(a,job.executionId(),cell.sampleId(),cell.repeatIndex(),node.node().nodeKind());
            var registered=registrations.readOriginal(a,actual).orElseThrow(()->new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE));
            row=new Stored(actual.columns(),actual.intent());receipt=new Receipt(new FormalEvaluationExecutionProtocol.Artifact(registered.artifact().artifactId(),registered.artifact().sha256(),registered.objectVersion(),BuiltinEvaluationDocumentContextCompiler.OUTPUT),registered.receiptSha256());
        }
        same(b,row.intent().required("context_binding"));same(json.tree(node.node()),row.intent().required("node"));same(job.projectId(),text(row.columns(),"project_id"));same(job.actorId(),text(row.columns(),"created_by"));
        var evidence=row.intent().required("original_evidence");same(json.tree(node.node().role()),evidence.required("role"));same(json.tree(configured.prompt()),evidence.required("prompt"));same(json.tree(configured.contextAssembler()),evidence.required("assembler"));
        same(planning?BuiltinEvaluationPlanningContextCompiler.IMPLEMENTATION:BuiltinEvaluationDocumentContextCompiler.IMPLEMENTATION,text(evidence,"implementation_id"));same("1.0.0",text(evidence,"implementation_version"));
        String implementation=planning?new BuiltinEvaluationPlanningContextCompiler(SchemaCatalogue.fromClasspath(),json).implementationClassSha256():new BuiltinEvaluationDocumentContextCompiler(SchemaCatalogue.fromClasspath(),json).implementationClassSha256();same(implementation,text(evidence,"implementation_class_sha256"));
        var policy=policies.requireByVersion(a,cell.policy().version_id(),cell.policy().content_sha256(),job.projectId(),job.classification(),"RUN_CREATION");
        var stable=row.intent().required("stable_provenance");JsonNode authorizations;
        if(planning){var current=planningDependencies.require(a,job.projectId(),job.classification(),node.node().cell().input(),policy.businessPurpose(),evidence);same(json.tree(current.roots()),stable.required("roots"));same(json.tree(current.members()),stable.required("members"));same(current.classification(),text(stable,"classification"));same(current.topology(),stable.required("topology"));authorizations=current.authorizations();}
        else{var current=dependencies.requireRuntime(a,job.projectId(),job.classification(),node.node(),policy.businessPurpose(),evidence);same(json.tree(current.roots()),stable.required("roots"));same(json.tree(current.members()),stable.required("members"));same(current.classification(),text(stable,"classification"));authorizations=current.authorizations();}
        same(receipt.artifact().sha256(),text(evidence,"exact_input_sha256"));same(receipt.artifact().sha256(),text(row.columns(),"payload_sha256"));
        var tokens=evidence.required("tokenization");same(receipt.artifact().sha256(),text(tokens,"input_sha256"));same(node.node().tokenizer().content_sha256(),text(tokens,"executable_sha256"));
        approved.requireUnchanged();policy.requireUnchanged();
        return new Snapshot(job,node,row,receipt,json.canonical(json.tree(Map.of("job",json.tree(job),"original",AgentPublicationWitness.tree(row.columns()),"receipt",json.tree(receipt),"dependencies",authorizations,"policy",policy.authorizations()))));
    }
    /** Current original compiler/dependency checks, without a live worker lease or impersonation. */
    public void requireOriginal(EnterpriseAccess.Admission a,String run,FormalEvaluationProtocol.Step step,FormalEvaluationExecutionProtocol.Artifact artifact,JsonNode compiler)throws Exception {
        if(!"N01".equals(step.nodeKind())){if(stages==null||stageObjects==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);var original=stageObjects.readOriginal(a,run,step.stepId(),"STAGE_INPUT");same(original.artifact(),artifact);same(original.evidence(),compiler);stages.requireOriginal(a,run,step,compiler);return;}
        var original=snapshot(a,run,step);same(original.receipt().artifact(),artifact);same(original.original().intent().required("original_evidence"),compiler);
    }
    public static final class Verified {
        private FormalEvaluationJobs.NodeRow recoveryNode;private final FormalEvaluationNodeInputs owner;private final FormalEvaluationControlAccess.Reads reads;private final FormalEvaluationProtocol.Step step;private final Snapshot original;private final FormalEvaluationStageInputPreparation.Prepared stage;private final FormalEvaluationStageInputObjects.Original stageOriginal;private final byte[] bytes;private final FormalEvaluationContextReads.Verified context;
        private Verified(FormalEvaluationNodeInputs owner,FormalEvaluationControlAccess.Reads reads,FormalEvaluationProtocol.Step step,Snapshot original,byte[] bytes,FormalEvaluationContextReads.Verified context){this.stage=null;this.stageOriginal=null;this.context=context;this.owner=owner;this.reads=reads;this.step=step;this.original=original;this.bytes=bytes.clone();}
        private Verified(FormalEvaluationNodeInputs owner,FormalEvaluationControlAccess.Reads reads,FormalEvaluationProtocol.Step step,FormalEvaluationStageInputPreparation.Prepared stage,FormalEvaluationStageInputObjects.Original original,FormalEvaluationContextReads.Verified context){this.owner=owner;this.reads=reads;this.step=step;this.original=null;this.stage=stage;this.stageOriginal=original;this.context=context;this.bytes=stage.bytes();}
        private Verified(FormalEvaluationNodeInputs owner,FormalEvaluationControlAccess.Reads reads,FormalEvaluationProtocol.Step step,FormalEvaluationStageInputObjects.Original original,FormalEvaluationContextReads.Verified context,FormalEvaluationJobs.NodeRow node,byte[] bytes){this.owner=owner;this.reads=reads;this.step=step;this.original=null;this.stage=null;this.stageOriginal=original;this.context=context;this.recoveryNode=node;this.bytes=bytes.clone();}
        public void requireCurrent(EnterpriseAccess.Admission a)throws Exception{reads.requireActive();if(stageOriginal!=null){context.requireCurrent(a);if(stage!=null)stage.requireCurrent(a);else owner.stages.requireOriginal(a,reads.runId(),step,stageOriginal.evidence());var current=owner.stageObjects.readOriginal(a,reads.runId(),step.stepId(),"STAGE_INPUT");same(stageOriginal,current);same(stageOriginal.artifact().sha256(),WireValues.sha256(bytes));return;}context.requireCurrent(a);same(original.job().runId(),reads.runId());same(original.canonical(),owner.snapshot(a,reads.runId(),step).canonical());same(sha256(),WireValues.sha256(bytes));}
        public FormalEvaluationContextReads.Verified context(){reads.requireActive();return context;}
        public byte[] bytes(){reads.requireActive();return bytes.clone();}public String sha256(){reads.requireActive();return stageOriginal==null?original.receipt().artifact().sha256():stageOriginal.artifact().sha256();}
        public FormalEvaluationExecutionProtocol.Artifact artifact(){reads.requireActive();return stageOriginal==null?original.receipt().artifact():stageOriginal.artifact();}
        public JsonNode evidence(){reads.requireActive();return stageOriginal==null?original.original().intent().required("original_evidence").deepCopy():stageOriginal.evidence().deepCopy();}
        FormalEvaluationJobs.NodeRow node(){reads.requireActive();return stageOriginal==null?original.node():(stage==null?recoveryNode:stage.node());}
        FormalEvaluationControlAccess.Reads reads(){reads.requireActive();return reads;}
    }
    private static void same(Object x,Object y){if(!Objects.equals(x,y))bad();}
}
