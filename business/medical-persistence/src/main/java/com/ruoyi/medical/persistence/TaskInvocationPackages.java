package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Actual registered immutable package GET under the existing exact TASK permission intersection.
 * Neither an artifact UUID, a host role nor a compiled prompt is a read grant. */
public final class TaskInvocationPackages {
    private static final String PURPOSE="AGENT_PUBLICATION";
    private final ArtifactRegistryMapper artifacts;private final SourceReadMapper sources;private final DependencyLineage lineage;
    private final ArtifactSourceAuthority authority;private final AgentPublicationDependencies governance;
    private final EvaluationReferences profiles;private final EvaluationDependencyPolicy policy;private final SourceUploadTransport transport;
    public TaskInvocationPackages(ArtifactRegistryMapper artifacts,SourceReadMapper sources,DependencyLineage lineage,
            ArtifactSourceAuthority authority,AgentPublicationDependencies governance,EvaluationReferences profiles,
            EvaluationDependencyPolicy policy,SourceUploadTransport transport){
        this.artifacts=artifacts;this.sources=sources;this.lineage=lineage;this.authority=authority;
        this.governance=governance;this.profiles=profiles;this.policy=policy;this.transport=transport;
    }
    private record State(String tenant,String actor,String project,PermissionProtocol.Basis artifact,
            InterfaceProtocol.ObjectRef object,String schemaRef,String schemaSha256,String digest,List<ArtifactRegistryProtocol.MemberRow> contentMembers) { }
    public final class Read {
        private final State original;private final byte[] bytes;
        private Read(State original,byte[] bytes){this.original=original;this.bytes=bytes.clone();}
        public byte[] bytes(){return bytes.clone();}public PermissionProtocol.Basis artifact(){return original.artifact();}
        public List<ArtifactRegistryProtocol.MemberRow> contentMembers(){return original.contentMembers();}
        public String schemaRef(){return original.schemaRef();}public String schemaSha256(){return original.schemaSha256();}
        public JsonNode canonicalPins()throws Exception{return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of(
            "artifact",original.artifact(),"object_ref",original.object(),"schema_ref",original.schemaRef(),"schema_sha256",original.schemaSha256(),"authorization_sha256",original.digest(),"content_members",original.contentMembers()));}
        public void requireCurrent(EnterpriseAccess.Admission a)throws Exception {
            if(!original.equals(state(a,original.project(),original.artifact(),SourceReadProtocol.Use.REVIEW,PURPOSE,null)))stale();
            if(!original.artifact().content_sha256().equals(WireValues.sha256(bytes)))bad();
        }
        /** Separate current transmission check. The original REVIEW read never implies MODEL_SEND. */
        public JsonNode requireModelSend(EnterpriseAccess.Admission a,String purpose,String provider)throws Exception {
            requireCurrent(a);var sent=state(a,original.project(),original.artifact(),SourceReadProtocol.Use.MODEL_SEND,purpose,provider);
            return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("artifact",sent.artifact(),"object_ref",sent.object(),"authorization_sha256",sent.digest()));
        }
        /** TASK callers supply the actual fixed input; no snapshot or rights are inferred. */
        public JsonNode requireModelSend(EnterpriseAccess.Admission a,String purpose,String provider,TaskNodeInputAuthority.Verified input)throws Exception {
            Objects.requireNonNull(input);requireCurrent(a);var sent=state(a,original.project(),original.artifact(),SourceReadProtocol.Use.MODEL_SEND,purpose,provider,TaskInvocationSources.of(input));
            return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("artifact",sent.artifact(),"object_ref",sent.object(),"authorization_sha256",sent.digest()));
        }
        public JsonNode requireModelSend(EnterpriseAccess.Admission a,String purpose,String provider,TaskSectionWriterInputAuthority.Verified input)throws Exception {
            Objects.requireNonNull(input);requireCurrent(a);var sent=state(a,original.project(),original.artifact(),SourceReadProtocol.Use.MODEL_SEND,purpose,provider,TaskInvocationSources.of(input));
            return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("artifact",sent.artifact(),"object_ref",sent.object(),"authorization_sha256",sent.digest()));
        }
        public JsonNode requireModelSend(EnterpriseAccess.Admission a,String purpose,String provider,TaskSourceVerifierInputAuthority.Verified input)throws Exception {
            Objects.requireNonNull(input);requireCurrent(a);var sent=state(a,original.project(),original.artifact(),SourceReadProtocol.Use.MODEL_SEND,purpose,provider,TaskInvocationSources.of(input));
            return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("artifact",sent.artifact(),"object_ref",sent.object(),"authorization_sha256",sent.digest()));
        }
        public JsonNode requireModelSend(EnterpriseAccess.Admission a,String purpose,String provider,DocumentReviewRuntimeAuthority.Verified input)throws Exception {
            Objects.requireNonNull(input);requireCurrent(a);var sent=state(a,original.project(),original.artifact(),SourceReadProtocol.Use.MODEL_SEND,purpose,provider,TaskInvocationSources.of(input));
            return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("artifact",sent.artifact(),"object_ref",sent.object(),"authorization_sha256",sent.digest()));
        }
    }
    public Read readWorker(TaskProfessionalHostAccess hosts,TaskProfessionalHostAccess.Handle handle,String project,
            PermissionProtocol.Basis artifact)throws Exception {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        State original=hosts.execute(handle,a->ThrowingSupplier.of(()->state(a,project,artifact,SourceReadProtocol.Use.REVIEW,PURPOSE,null)).get());
        S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{hosts.execute(handle,a->ThrowingSupplier.of(()->{
            if(!original.equals(state(a,project,artifact,SourceReadProtocol.Use.REVIEW,PURPOSE,null)))stale();return null;
        }).get());return null;}).get();
        byte[] bytes=transport.readTaskDocument(original.tenant(),original.actor(),TaskInvocationContextSchema.MAXIMUM_BYTES,guard,(reader,current)->{
            try(var object=reader.readVerified(original.object(),current)){
                if(!original.object().equals(object.reference())||!original.artifact().content_sha256().equals(object.fullSha256()))bad();
                int size=Integer.parseInt(original.object().sizeBytes());byte[] result=new byte[size];
                for(int offset=0;offset<size;){current.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);System.arraycopy(object.chunk(offset,count),0,result,offset,count);offset+=count;current.requireCurrent();}
                return result;
            }
        });
        if(!artifact.content_sha256().equals(WireValues.sha256(bytes)))bad();guard.requireCurrent();return new Read(original,bytes);
    }
    public Read readWorker(TaskSectionWriterHostAccess hosts,TaskSectionWriterHostAccess.Handle handle,String project,
            PermissionProtocol.Basis artifact)throws Exception {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        State original=hosts.execute(handle,a->ThrowingSupplier.of(()->state(a,project,artifact,SourceReadProtocol.Use.REVIEW,PURPOSE,null)).get());
        S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{hosts.execute(handle,a->ThrowingSupplier.of(()->{
            if(!original.equals(state(a,project,artifact,SourceReadProtocol.Use.REVIEW,PURPOSE,null)))stale();return null;
        }).get());return null;}).get();
        byte[] bytes=transport.readTaskDocument(original.tenant(),original.actor(),TaskInvocationContextSchema.MAXIMUM_BYTES,guard,(reader,current)->{
            try(var object=reader.readVerified(original.object(),current)){
                if(!original.object().equals(object.reference())||!original.artifact().content_sha256().equals(object.fullSha256()))bad();
                int size=Integer.parseInt(original.object().sizeBytes());byte[] result=new byte[size];
                for(int offset=0;offset<size;){current.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);System.arraycopy(object.chunk(offset,count),0,result,offset,count);offset+=count;current.requireCurrent();}
                return result;
            }
        });
        if(!artifact.content_sha256().equals(WireValues.sha256(bytes)))bad();guard.requireCurrent();return new Read(original,bytes);
    }
    public Read readWorker(TaskSourceVerifierHostAccess hosts,TaskSourceVerifierHostAccess.Handle handle,String project,
            PermissionProtocol.Basis artifact)throws Exception {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        State original=hosts.execute(handle,a->ThrowingSupplier.of(()->state(a,project,artifact,SourceReadProtocol.Use.REVIEW,PURPOSE,null)).get());
        S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{hosts.execute(handle,a->ThrowingSupplier.of(()->{
            if(!original.equals(state(a,project,artifact,SourceReadProtocol.Use.REVIEW,PURPOSE,null)))stale();return null;
        }).get());return null;}).get();
        byte[] bytes=transport.readTaskDocument(original.tenant(),original.actor(),TaskInvocationContextSchema.MAXIMUM_BYTES,guard,(reader,current)->{
            try(var object=reader.readVerified(original.object(),current)){
                if(!original.object().equals(object.reference())||!original.artifact().content_sha256().equals(object.fullSha256()))bad();
                int size=Integer.parseInt(original.object().sizeBytes());byte[] result=new byte[size];
                for(int offset=0;offset<size;){current.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);System.arraycopy(object.chunk(offset,count),0,result,offset,count);offset+=count;current.requireCurrent();}
                return result;
            }
        });
        if(!artifact.content_sha256().equals(WireValues.sha256(bytes)))bad();guard.requireCurrent();return new Read(original,bytes);
    }
    public Read readWorker(DocumentReviewHostAccess hosts,DocumentReviewHostAccess.Handle handle,String project,
            PermissionProtocol.Basis artifact)throws Exception {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        State original=hosts.execute(handle,a->ThrowingSupplier.of(()->state(a,project,artifact,SourceReadProtocol.Use.REVIEW,PURPOSE,null)).get());
        S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{hosts.execute(handle,a->ThrowingSupplier.of(()->{
            if(!original.equals(state(a,project,artifact,SourceReadProtocol.Use.REVIEW,PURPOSE,null)))stale();return null;
        }).get());return null;}).get();
        byte[] bytes=transport.readTaskDocument(original.tenant(),original.actor(),TaskInvocationContextSchema.MAXIMUM_BYTES,guard,(reader,current)->{
            try(var object=reader.readVerified(original.object(),current)){
                if(!original.object().equals(object.reference())||!original.artifact().content_sha256().equals(object.fullSha256()))bad();
                int size=Integer.parseInt(original.object().sizeBytes());byte[] result=new byte[size];
                for(int offset=0;offset<size;){current.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);System.arraycopy(object.chunk(offset,count),0,result,offset,count);offset+=count;current.requireCurrent();}
                return result;
            }
        });
        if(!artifact.content_sha256().equals(WireValues.sha256(bytes)))bad();guard.requireCurrent();return new Read(original,bytes);
    }
    /** Current authorization for an originally persisted physical read. It does not read
     * bytes, mint a Read, or make a caller-supplied pin into physical evidence. */
    JsonNode requireCurrentPins(EnterpriseAccess.Admission a,String project,JsonNode pins,String purpose,String provider)throws Exception {
        requireReviewedPins(a,project,pins);var b=pins.required("artifact");
        var basis=new PermissionProtocol.Basis(b.required("version_id").textValue(),b.required("content_sha256").textValue());
        var transmitted=state(a,project,basis,SourceReadProtocol.Use.MODEL_SEND,purpose,provider);
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("artifact",transmitted.artifact(),"object_ref",transmitted.object(),"authorization_sha256",transmitted.digest()));
    }
    /** Compatibility fix for real TASK source-backed V1 and V2 packages. The legacy method
     * above remains unchanged; only a private current input can supply formal source pins. */
    JsonNode requireCurrentPins(EnterpriseAccess.Admission a,String project,JsonNode pins,String purpose,String provider,TaskNodeInputAuthority.Verified input)throws Exception {
        Objects.requireNonNull(input);requireReviewedPins(a,project,pins);var b=pins.required("artifact");
        var basis=new PermissionProtocol.Basis(b.required("version_id").textValue(),b.required("content_sha256").textValue());
        var transmitted=state(a,project,basis,SourceReadProtocol.Use.MODEL_SEND,purpose,provider,TaskInvocationSources.of(input));
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("artifact",transmitted.artifact(),"object_ref",transmitted.object(),"authorization_sha256",transmitted.digest()));
    }
    JsonNode requireCurrentPins(EnterpriseAccess.Admission a,String project,JsonNode pins,String purpose,String provider,TaskSectionWriterInputAuthority.Verified input)throws Exception {
        Objects.requireNonNull(input);requireReviewedPins(a,project,pins);var b=pins.required("artifact");
        var basis=new PermissionProtocol.Basis(b.required("version_id").textValue(),b.required("content_sha256").textValue());
        var transmitted=state(a,project,basis,SourceReadProtocol.Use.MODEL_SEND,purpose,provider,TaskInvocationSources.of(input));
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("artifact",transmitted.artifact(),"object_ref",transmitted.object(),"authorization_sha256",transmitted.digest()));
    }
    JsonNode requireCurrentPins(EnterpriseAccess.Admission a,String project,JsonNode pins,String purpose,String provider,TaskSourceVerifierInputAuthority.Verified input)throws Exception {
        Objects.requireNonNull(input);requireReviewedPins(a,project,pins);var b=pins.required("artifact");
        var basis=new PermissionProtocol.Basis(b.required("version_id").textValue(),b.required("content_sha256").textValue());
        var transmitted=state(a,project,basis,SourceReadProtocol.Use.MODEL_SEND,purpose,provider,TaskInvocationSources.of(input));
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("artifact",transmitted.artifact(),"object_ref",transmitted.object(),"authorization_sha256",transmitted.digest()));
    }    JsonNode requireCurrentPins(EnterpriseAccess.Admission a,String project,JsonNode pins,String purpose,String provider,DocumentReviewRuntimeAuthority.Verified input)throws Exception {
        Objects.requireNonNull(input);requireReviewedPins(a,project,pins);var b=pins.required("artifact");
        var basis=new PermissionProtocol.Basis(b.required("version_id").textValue(),b.required("content_sha256").textValue());
        var transmitted=state(a,project,basis,SourceReadProtocol.Use.MODEL_SEND,purpose,provider,TaskInvocationSources.of(input));
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("artifact",transmitted.artifact(),"object_ref",transmitted.object(),"authorization_sha256",transmitted.digest()));
    }
    static SourceReadProtocol.ReadRequirement modelRequirement(SourceReadProtocol.SourceRow source,TaskNodeInputAuthority.Verified input,String purpose,String environment,String region,String provider)throws Exception {return modelRequirement(source,TaskInvocationSources.of(input),purpose,environment,region,provider);}
    static void requireModelSelection(TaskNodeInputAuthority.Verified input,SourceReadProtocol.VerifiedSource current,String purpose)throws Exception {requireModelSelection(TaskInvocationSources.of(input),current,purpose);}
    void requireReviewedPins(EnterpriseAccess.Admission a,String project,JsonNode pins)throws Exception {
        var b=pins.required("artifact");var basis=new PermissionProtocol.Basis(b.required("version_id").textValue(),b.required("content_sha256").textValue());
        var original=state(a,project,basis,SourceReadProtocol.Use.REVIEW,PURPOSE,null);
        var expected=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("artifact",original.artifact(),"object_ref",original.object(),"schema_ref",original.schemaRef(),"schema_sha256",original.schemaSha256(),"authorization_sha256",original.digest(),"content_members",original.contentMembers()));
        if(!expected.equals(pins))stale();
    }
    void requireReviewedDocumentReviewControlPins(EnterpriseAccess.Admission a,String project,JsonNode pins)throws Exception {
        if(!a.documentReviewWorker())deny();a.documentReviewProof().requireSupervisor();
        var b=pins.required("artifact");var basis=new PermissionProtocol.Basis(b.required("version_id").textValue(),b.required("content_sha256").textValue());
        var original=state(a,project,basis,SourceReadProtocol.Use.REVIEW,PURPOSE,null,null,true);
        var expected=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("artifact",original.artifact(),"object_ref",original.object(),"schema_ref",original.schemaRef(),"schema_sha256",original.schemaSha256(),"authorization_sha256",original.digest(),"content_members",original.contentMembers()));
        if(!expected.equals(pins))stale();
    }
    private State state(EnterpriseAccess.Admission a,String project,PermissionProtocol.Basis artifact,SourceReadProtocol.Use use,String purpose,String provider)throws Exception {return state(a,project,artifact,use,purpose,provider,null);}
    private State state(EnterpriseAccess.Admission a,String project,PermissionProtocol.Basis artifact,SourceReadProtocol.Use use,String purpose,String provider,TaskInvocationSources input)throws Exception {
        return state(a,project,artifact,use,purpose,provider,input,false);
    }
    private State state(EnterpriseAccess.Admission a,String project,PermissionProtocol.Basis artifact,SourceReadProtocol.Use use,String purpose,String provider,TaskInvocationSources input,boolean supervisor)throws Exception {
        String tenant;
        if(supervisor){if(!a.documentReviewWorker()||input!=null||use!=SourceReadProtocol.Use.REVIEW||!PURPOSE.equals(purpose)||provider!=null)deny();a.documentReviewProof().requireSupervisor();tenant=a.context().tenant_id();}
        else {TaskProfessionalRuntimeService.current(a);var identity=a.formalProof().identity();if(!Set.of("TASK","CHECK_DOCUMENT").contains(identity.executionKind())||!project.equals(identity.project()))deny();tenant=identity.tenant();}
        policy.requireConfigured();
        if(input!=null){var identity=a.formalProof().identity();input.requireUnchanged();if((input instanceof TaskInvocationSources.DocumentReview)!= "CHECK_DOCUMENT".equals(identity.executionKind()))deny();if(use!=SourceReadProtocol.Use.MODEL_SEND||!identity.tenant().equals(input.tenantId())||!project.equals(input.projectId())||!identity.run().equals(input.runId())||!identity.step().equals(input.stepId()))deny();}
        var declared=governance.require(a,project,List.of(artifact),PURPOSE);var row=one(artifacts.artifact(artifact.version_id()));var binding=one(artifacts.binding(artifact.version_id()));
        if(!tenant.equals(row.tenantId())||!artifact.content_sha256().equals(row.sha256())||!row.sha256().equals(row.contentSha256())
                ||row.projectId()!=null&&!project.equals(row.projectId()))bad();
        var reference=StoredObjectReferences.decode(row.storedObject(),row.reference());
        if(!reference.objectKey().startsWith("tenants/"+tenant+"/")||reference.objectKey().contains("..")
                ||row.sizeBytes().signum()<=0||row.sizeBytes().compareTo(BigInteger.valueOf(TaskInvocationContextSchema.MAXIMUM_BYTES))>0)bad();
        var graph=lineage.inspect(artifact.version_id(),policy.limits());var required=new TreeMap<String,ArtifactRegistryProtocol.ReadRequirement>();var leaves=new ArrayList<SourceReadProtocol.ReadRequirement>();
        for(var version:graph.versions().values()){
            if(version.projectId()!=null&&!project.equals(version.projectId()))deny();
            var source=sources.source(version.id());if(source.size()>1)bad();
            if(!source.isEmpty()){var s=source.get(0);leaves.add(input==null?new SourceReadProtocol.ReadRequirement(s.sourceVersion(),s.rightsRevision(),project,null,null,null,use,purpose,policy.environment(),policy.region(),provider):modelRequirement(s,input,purpose,policy.environment(),policy.region(),provider));}
            if("ARTIFACT".equals(version.entityKind())){
                var b=one(artifacts.binding(version.id()));var p=profiles.profile(b.schemaRef(),PURPOSE,b.resourceKind());
                required.put(version.id(),new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(tenant,b.projectId(),p.resourceKind(),b.resourceId()),p.action(),purpose,b.schemaRef(),b.schemaDocumentSha256(),p.artifactSchemaManifest(),policy.limits()));
            }
        }
        var root=required.remove(artifact.version_id());if(root==null)bad();leaves.sort(Comparator.comparing(SourceReadProtocol.ReadRequirement::sourceVersion));
        var checked=authority.require(a,artifact.version_id(),root,leaves,new VersionResourceProtocol.ReadContext(use,null),required);
        if(input!=null){for(var leaf:leaves)requireModelSelection(input,checked.sources().get(leaf.sourceVersion()),purpose);input.requireUnchanged();}
        String digest=WireValues.sha256(ContentReadAuthorization.canonicalBytes(Map.of("tenant",tenant,"actor",a.actorId(),"project",project,"artifact",row,"binding",binding,"permissions",checked,"governance",declared.authorizations(),"purpose",purpose,"use",use)));
        return new State(tenant,a.actorId(),project,artifact,reference,binding.schemaRef(),binding.schemaDocumentSha256(),digest,List.copyOf(checked.registration().manifest().members()));
    }
    static SourceReadProtocol.ReadRequirement modelRequirement(SourceReadProtocol.SourceRow source,TaskInvocationSources input,String purpose,String environment,String region,String provider)throws Exception {
        input.requireUnchanged();var fixed=fixed(input,source.sourceVersion(),purpose);
        if(!input.tenantId().equals(source.tenantId())||!fixed.rightsRevision().equals(source.rightsRevision())||source.ownerProjectId()!=null&&!input.projectId().equals(source.ownerProjectId()))deny();
        return new SourceReadProtocol.ReadRequirement(fixed.sourceVersion(),fixed.rightsRevision(),input.projectId(),input.snapshotId(),fixed.parseVersion(),null,SourceReadProtocol.Use.MODEL_SEND,purpose,environment,region,provider);
    }
    static void requireModelSelection(TaskInvocationSources input,SourceReadProtocol.VerifiedSource current,String purpose)throws Exception {
        input.requireUnchanged();if(current==null||current.source()==null||current.rights()==null||current.selection()==null)deny();
        var fixed=fixed(input,current.source().sourceVersion(),purpose);var selected=current.selection();
        if(!input.tenantId().equals(current.source().tenantId())||!fixed.rightsRevision().equals(current.source().rightsRevision())||!fixed.rightsRevision().equals(current.rights().rightsRevision())
                ||!input.tenantId().equals(selected.tenantId())||!input.projectId().equals(selected.projectId())||!input.snapshotId().equals(selected.snapshotId())||!fixed.sourceVersion().equals(selected.sourceVersion())
                ||!fixed.parseVersion().equals(selected.parseVersion())||!fixed.indexVersion().equals(selected.indexVersion())||!fixed.approvalRevision().equals(selected.approvalRevision())||!fixed.bindingRevision().equals(selected.bindingRevision()))deny();
    }
    private static TaskNodeInputProtocol.SourceSelection fixed(TaskInvocationSources input,String source,String purpose){var rows=input.fixedSources().stream().filter(f->f.sourceVersion().equals(source)).toList();if(rows.size()!=1||!rows.get(0).purpose().equals(purpose))deny();return rows.get(0);}
    private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}
