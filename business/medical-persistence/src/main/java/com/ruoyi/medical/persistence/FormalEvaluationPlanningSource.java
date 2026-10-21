package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.FormalEvaluationPlanPreparationLedger.*;

/** Current N01-only authority plus original090 physical GET. HistoricalRead is never upgraded or
 * passed to a compiler. A stored receipt is only an equality assertion against the original journals. */
public final class FormalEvaluationPlanningSource {
    private final EnterpriseAccess access;
    private final FormalEvaluationPlanPreparationAuthority current;
    private final FormalEvaluationPlanPreparationLedger ledger;
    private final FormalEvaluationPlanPreparationRegistration registrations;
    private final FormalEvaluationPlanPreparationSchema schema;
    private final ResourceReadAuthority resources;
    private final SourceUploadTransport transport;
    private final DependencyLineage lineage;
    private final SourceReadMapper sourceRows;
    private final SourceReadAuthority sources;
    private final PermissionSchema json;
    public FormalEvaluationPlanningSource(EnterpriseAccess access,FormalEvaluationPlanPreparationAuthority current,
            FormalEvaluationPlanPreparationLedger ledger,FormalEvaluationPlanPreparationRegistration registrations,
            FormalEvaluationPlanPreparationSchema schema,ResourceReadAuthority resources,SourceUploadTransport transport,
            DependencyLineage lineage,SourceReadMapper sourceRows,SourceReadAuthority sources,PermissionSchema json) {
        this.access=access;this.current=current;this.ledger=ledger;this.registrations=registrations;this.schema=schema;
        this.resources=resources;this.transport=transport;this.lineage=lineage;this.sourceRows=sourceRows;this.sources=sources;this.json=json;
    }
    Prepared prepare(ApplicationSessionIdentity.Verified subject,String context,FormalEvaluationNodeInputObjectSchema.Request request)throws Exception {
        DocumentUploadTransferService.requireOutsideTransaction(subject);
        var original=schema.request(schema.canonical(request.planRequest()).getBytes(StandardCharsets.UTF_8));
        var authority=current.prepare(subject,context,original,false);
        var proof=new Prepared(this,subject,context,request,original,authority);
        proof.snapshot=access.execute(subject,a->ThrowingSupplier.of(()->inspect(a,proof)).get());
        S3ObjectReader.Admission guard=()->access.execute(subject,a->ThrowingSupplier.of(()->{proof.requireCurrent(a);return null;}).get());
        proof.bytes=transport.readDocument(subject,proof.snapshot.current().tenant(),FormalEvaluationPlanPreparationSchema.MAXIMUM_BYTES,guard,(reader,lease)->{
            var reference=proof.reference();
            S3ObjectReader.TimedAdmission timed=new S3ObjectReader.TimedAdmission(){
                public java.time.Duration remainingTime(){return lease.remainingTime();}
                public void requireCurrent(){lease.requireCurrent();guard.requireCurrent();}
            };
            try(var actual=reader.readVerified(reference,timed)) {
                if(!reference.equals(actual.reference())||!reference.sha256().equals(actual.fullSha256()))bad();
                int size=Integer.parseInt(reference.sizeBytes());byte[] bytes=new byte[size];
                for(int at=0;at<size;){timed.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-at);byte[] part=actual.chunk(at,count);if(part.length!=count)bad();System.arraycopy(part,0,bytes,at,count);at+=count;}
                timed.requireCurrent();if(!Arrays.equals(bytes,proof.snapshot.current().bytes())||!reference.sha256().equals(WireValues.sha256(bytes)))bad();return bytes;
            }
        });
        access.execute(subject,a->ThrowingSupplier.of(()->{proof.requireCurrent(a);return null;}).get());return proof;
    }
    private record Snapshot(FormalEvaluationPlanPreparationAuthority.Checked current,Row row,
            FormalEvaluationPlanPreparationRegistration.Receipt receipt,JsonNode artifactRead,String stable) {}
    private Snapshot inspect(EnterpriseAccess.Admission a,Prepared p)throws Exception {
        var checked=p.current.current(a);if(checked.bytes()==null||checked.dependencies()==null)throw new TenantAccessException(ErrorCode.PLAN_UNCONFIRMED);
        var attempt=ledger.findOriginal(a,p.request.planOriginalKey(),p.original).orElseThrow(()->new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE));
        var row=ledger.requireStored(a,attempt);var receipt=registrations.readStored(a,attempt).orElseThrow(()->new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE));
        if(!checked.stable().equals(schema.canonical(row.intent().required("stable_provenance")))||!Arrays.equals(checked.bytes(),text(row.columns(),"canonical_payload").getBytes(StandardCharsets.UTF_8)))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        var expected=p.request.planReceipt();
        if(!receipt.operationId().equals(text(expected,"operation_id"))||!receipt.writeId().equals(text(expected,"write_id"))||!receipt.attemptId().equals(text(expected,"attempt_id"))
            ||!receipt.executionId().equals(text(expected,"execution_id"))||!receipt.artifact().artifactId().equals(text(expected,"artifact_id"))
            ||!receipt.artifact().sha256().equals(text(expected,"artifact_sha256"))||!receipt.objectVersion().equals(text(expected,"object_version"))||!receipt.receiptSha256().equals(text(expected,"receipt_sha256")))bad();
        var read=AgentPublicationWitness.tree(resources.require(a,new PermissionProtocol.Scope(checked.tenant(),checked.project(),"ARTIFACT",receipt.artifact().artifactId()),"artifact.read","EVALUATION_RESULTS"));
        String stable=schema.canonical(Map.of("current",schema.read(checked.stable()),"topology",checked.dependencies().topology(),"original_intent",row.intent(),"receipt",json.tree(receipt),"artifact_read",read));
        return new Snapshot(checked,row,receipt,read,stable);
    }
    static final class Prepared {
        private final FormalEvaluationPlanningSource owner;private final ApplicationSessionIdentity.Verified subject;private final String context;
        private final FormalEvaluationNodeInputObjectSchema.Request request;private final FormalEvaluationPlanPreparationSchema.Original original;
        private final FormalEvaluationPlanPreparationAuthority.Prepared current;private Snapshot snapshot;private byte[] bytes;
        private Prepared(FormalEvaluationPlanningSource owner,ApplicationSessionIdentity.Verified subject,String context,FormalEvaluationNodeInputObjectSchema.Request request,
                FormalEvaluationPlanPreparationSchema.Original original,FormalEvaluationPlanPreparationAuthority.Prepared current){this.owner=owner;this.subject=subject;this.context=context;this.request=request;this.original=original;this.current=current;}
        void requireCurrent(EnterpriseAccess.Admission a)throws Exception {
            if(!a.actorId().equals(subject.user().user_id())||!Objects.equals(a.subject().sessionId(),subject.sessionId())||!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
            if(snapshot==null||!snapshot.stable().equals(owner.inspect(a,this).stable()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            if(bytes!=null&&!Arrays.equals(bytes,snapshot.current().bytes()))bad();
        }
        InterfaceProtocol.ObjectRef reference(){var row=snapshot.row();return new InterfaceProtocol.ObjectRef(text(row.intent(),"object_key"),snapshot.receipt().objectVersion(),text(row.columns(),"payload_sha256"),text(row.intent(),"payload_size"),"application/json");}
        JsonNode planning(EnterpriseAccess.Admission a)throws Exception {
            requireCurrent(a);if(bytes==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            var c=snapshot.current();var preview=c.preview();var selected=preview.required("selected_template");
            var template=c.evidence().required("templates").required(text(selected,"version_id")).required("template").required("content");
            var result=owner.json.object();result.set("template",selected.deepCopy());result.set("template_content",template.deepCopy());
            result.put("profile_revision",text(preview,"profile_revision")).put("profile_sha256",text(preview,"profile_sha256"));
            result.set("profile_attributes",c.evidence().required("profile").required("attributes").deepCopy());
            result.set("plan_candidate",owner.schema.read(new String(bytes,StandardCharsets.UTF_8)));
            result.set("conditions",preview.required("conditions").deepCopy());result.set("blockers",preview.required("blockers").deepCopy());return result;
        }
        JsonNode evidence(EnterpriseAccess.Admission a)throws Exception {
            requireCurrent(a);var result=owner.json.object();result.set("receipt",request.planReceipt());result.set("object",owner.json.tree(reference()));
            result.put("original_authority_sha256",WireValues.sha256(snapshot.stable().getBytes(StandardCharsets.UTF_8)));result.set("planning",planning(a));return result;
        }
        /** All template/profile source ancestors require real model-send permission with the original
         * EVALUATION_RESULTS use and ROLE narrowing. Readability or a plan receipt never grants external processing. */
        List<String> sourceVersions(EnterpriseAccess.Admission a)throws Exception {
            requireCurrent(a);var p=planning(a);var versions=new TreeSet<String>();
            for(String root:List.of(text(p.required("template"),"version_id"),text(p,"profile_revision")))versions.addAll(owner.lineage.inspect(root,new DependencyLineage.Limits(512,2048)).versions().keySet());
            var result=new TreeSet<String>();for(var id:versions){var rows=owner.sourceRows.source(id);if(rows.size()>1)bad();if(!rows.isEmpty())result.add(id);}
            if(result.isEmpty())throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return List.copyOf(result);
        }
        JsonNode requireModelSend(EnterpriseAccess.Admission a,JsonNode role,String provider,String environment,String region,ScopeSnapshotStore.Stored scope)throws Exception {
            requireCurrent(a);var projectPermission=owner.json.tree(new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(a.context().tenant_id(),snapshot.current().project(),"PROJECT",snapshot.current().project()),"project.read","EVALUATION_RESULTS"));boolean projectAllowed=false;for(var permission:role.required("permissions"))if(permission.equals(projectPermission))projectAllowed=true;if(!projectAllowed)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);var expected=new TreeSet<>(sourceVersions(a));var actual=new TreeSet<String>();var proof=owner.json.object();
            for(var member:scope.manifest().members()){
                if(!actual.add(member.source_version())||!expected.contains(member.source_version()))bad();
                var checked=owner.sources.require(a,new SourceReadProtocol.ReadRequirement(member.source_version(),member.rights_revision(),snapshot.current().project(),scope.snapshot().snapshotId(),member.parse_version(),null,SourceReadProtocol.Use.MODEL_SEND,"EVALUATION_RESULTS",environment,region,provider));
                for(String action:List.of("source.read","source.external.process")){
                    var required=owner.json.tree(new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(a.context().tenant_id(),checked.source().ownerProjectId(),"SOURCE",checked.source().sourceId()),action,"EVALUATION_RESULTS"));
                    boolean allowed=false;for(var permission:role.required("permissions"))if(permission.equals(required))allowed=true;if(!allowed)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
                }proof.set(member.source_version(),AgentPublicationWitness.tree(checked));
            }
            if(!expected.equals(actual))bad();requireCurrent(a);return proof;
        }
        FormalEvaluationExecutionProtocol.Node node(){return snapshot.current().node();}
        String classification(){return snapshot.current().dependencies().classification();}
    }
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
