package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.FormalEvaluationNodeInputObjectLedger.*;

/** Historical status/content only. No current execution, model eligibility, budget, tokenizer,
 * registration grant or PUT is required. This class produces no executable authority. */
public final class FormalEvaluationNodeInputReadService {
    private final EnterpriseAccess access;private final FormalEvaluationNodeInputObjectSchema schema;
    private final FormalEvaluationNodeInputObjectLedger ledger;private final FormalEvaluationNodeInputObjectRegistration registrations;
    private final FormalEvaluationPlanPreparationReadService plans;private final FormalEvaluationPlanPreparationSchema planSchema;
    private final FormalEvaluationNodeInputObjectDependencies dependencies;private final ResourceReadAuthority resources;
    private final ObjectProvider<SourceUploadTransport> transport;private final PermissionSchema json;
    public FormalEvaluationNodeInputReadService(EnterpriseAccess access,FormalEvaluationNodeInputObjectSchema schema,
            FormalEvaluationNodeInputObjectLedger ledger,FormalEvaluationNodeInputObjectRegistration registrations,
            FormalEvaluationPlanPreparationReadService plans,FormalEvaluationPlanPreparationSchema planSchema,
            FormalEvaluationNodeInputObjectDependencies dependencies,ResourceReadAuthority resources,ObjectProvider<SourceUploadTransport> transport,PermissionSchema json) {
        this.access=access;this.schema=schema;this.ledger=ledger;this.registrations=registrations;this.plans=plans;this.planSchema=planSchema;
        this.dependencies=dependencies;this.resources=resources;this.transport=transport;this.json=json;
    }
    private record Query(String key,FormalEvaluationNodeInputObjectSchema.Original original,byte[] planRecovery) {}
    private Query query(byte[] bytes)throws Exception {
        var recovery=schema.recovery(bytes);var original=schema.request(schema.canonical(recovery.originalRequest()).getBytes(StandardCharsets.UTF_8));
        var request=original.request();var plan=json.object().put("schema_version","1.0.0").put("original_key",request.planOriginalKey());plan.set("original_request",json.tree(request.planRequest()));
        return new Query(recovery.originalKey(),original,planSchema.canonical(plan).getBytes(StandardCharsets.UTF_8));
    }
    private record Snapshot(String tenant,Row row,FormalEvaluationNodeInputObjectRegistration.Receipt receipt,JsonNode status,String witness) {}
    public JsonNode status(ApplicationSessionIdentity.Verified subject,String context,byte[] bytes)throws Exception {
        DocumentUploadTransferService.requireOutsideTransaction(subject);return snapshot(subject,context,query(bytes)).status().deepCopy();
    }
    public JsonNode content(ApplicationSessionIdentity.Verified subject,String context,byte[] bytes)throws Exception {
        DocumentUploadTransferService.requireOutsideTransaction(subject);var query=query(bytes);var first=snapshot(subject,context,query);
        if(first.receipt()==null)throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);
        var actual=transport.getIfUnique();if(actual==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        S3ObjectReader.Admission current=()->ThrowingSupplier.of(()->{if(!first.witness().equals(snapshot(subject,context,query).witness()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);return null;}).get();
        var row=first.row();var ref=new InterfaceProtocol.ObjectRef(text(row.intent(),"object_key"),first.receipt().objectVersion(),text(row.columns(),"payload_sha256"),text(row.intent(),"payload_size"),"application/json");
        return actual.readDocument(subject,first.tenant(),BuiltinEvaluationPlanningContextCompiler.MAXIMUM_BYTES,current,(reader,lease)->{
            S3ObjectReader.TimedAdmission timed=new S3ObjectReader.TimedAdmission(){public java.time.Duration remainingTime(){return lease.remainingTime();}public void requireCurrent(){lease.requireCurrent();current.requireCurrent();}};
            try(var read=reader.readVerified(ref,timed)){
                if(!ref.equals(read.reference())||!ref.sha256().equals(read.fullSha256()))bad();int size=Integer.parseInt(ref.sizeBytes());byte[] value=new byte[size];
                for(int at=0;at<size;){timed.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-at);byte[] chunk=read.chunk(at,count);if(chunk.length!=count)bad();System.arraycopy(chunk,0,value,at,count);at+=count;}
                if(!Arrays.equals(value,text(row.columns(),"canonical_payload").getBytes(StandardCharsets.UTF_8))||!ref.sha256().equals(WireValues.sha256(value)))bad();
                SchemaCatalogue.fromClasspath().bindPublished(BuiltinEvaluationPlanningContextCompiler.OUTPUT,text(row.intent(),"payload_schema_sha256"),value);
                var result=json.object().put("schema_version","1.0.0").put("runnable",false).put("professional_completion",false);
                result.set("receipt",first.status().required("receipt").deepCopy());result.set("exact_input",schema.read(new String(value,StandardCharsets.UTF_8)));
                result.set("planning",row.intent().required("original_evidence").required("planning_original").required("planning").deepCopy());timed.requireCurrent();return result;
            }
        });
    }
    private Snapshot snapshot(ApplicationSessionIdentity.Verified subject,String context,Query query)throws Exception {
        var plan=plans.contentSnapshot(subject,context,query.planRecovery(),query.original().request().planReceipt());
        var result=access.execute(subject,a->ThrowingSupplier.of(()->inspect(a,context,query,plan)).get());
        var recheck=plans.contentSnapshot(subject,context,query.planRecovery(),query.original().request().planReceipt());
        if(!plan.witness().equals(recheck.witness())||!plan.receipt().equals(recheck.receipt()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);return result;
    }
    private Snapshot inspect(EnterpriseAccess.Admission a,String context,Query query,FormalEvaluationPlanPreparationReadService.Historical plan)throws Exception {
        FormalEvaluationExecutionAdmission.current(a);if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        var found=ledger.findOriginal(a,query.key(),query.original());var status=json.object().put("schema_version","1.0.0").put("content_available",false).put("runnable",false).put("professional_completion",false);
        if(found.isEmpty()){status.put("state","NOT_FOUND").putNull("receipt");return new Snapshot(a.context().tenant_id(),null,null,status,schema.canonical(plan.witness()));}
        var row=ledger.requireStored(a,found.get());var columns=row.columns();var evidence=row.intent().required("original_evidence");
        same(row.intent().required("node"),plan.row().intent().required("node"));same(row.intent().required("context_binding"),plan.row().intent().required("context_binding"));
        for(String field:List.of("execution_id","evaluation_id","project_id","sample_id","repeat_index","node_kind"))same(columns.get(field),plan.row().columns().get(field));
        same(evidence.required("planning_original").required("receipt"),query.original().request().planReceipt());
        var preview=plan.row().intent().required("original_evidence").required("preview");var planning=evidence.required("planning_original").required("planning");
        same(planning.required("plan_candidate"),preview.required("plan_candidate"));same(planning.required("template"),preview.required("selected_template"));
        same(planning.required("profile_revision"),preview.required("profile_revision"));same(planning.required("profile_sha256"),preview.required("profile_sha256"));
        same(planning.required("conditions"),preview.required("conditions"));same(planning.required("blockers"),preview.required("blockers"));
        var planEvidence=plan.row().intent().required("original_evidence");
        same(planning.required("template_content"),planEvidence.required("templates").required(text(planning.required("template"),"version_id")).required("template").required("content"));
        same(planning.required("profile_attributes"),planEvidence.required("profile").required("attributes"));
        // Bind compiler packages to the original independently approved088 context, not public evidence alone.
        var definition=plan.witness().required("context").required("canonical_binding");var contextDefinition=planSchema.read(definition.asText()).required("context_definition");
        var nodes=contextDefinition.required("node_bindings");if(nodes.size()!=1)bad();var node=nodes.get(0);
        same(evidence.required("role"),node.required("role"));same(evidence.required("prompt"),node.required("prompt"));same(evidence.required("assembler"),node.required("context_assembler"));
        same(BuiltinEvaluationPlanningContextCompiler.INPUT,text(node.required("input_schema"),"schema_ref"));
        String purpose=text(plan.row().intent().required("original_evidence"),"source_business_purpose");
        var current=dependencies.require(a,text(columns,"project_id"),text(plan.witness().required("root"),"classification"),
            new PermissionProtocol.Basis(text(columns,"source_input_version"),text(columns,"source_input_sha256")),purpose,evidence);
        var stable=row.intent().required("stable_provenance");same(json.tree(current.roots()),stable.required("roots"));same(json.tree(current.members()),stable.required("members"));same(current.classification(),text(stable,"classification"));same(current.topology(),stable.required("topology"));
        var receipt=registrations.readStored(a,found.get());JsonNode read=json.tree(null);
        if(receipt.isEmpty())status.put("state","PENDING").putNull("receipt");else{
            read=AgentPublicationWitness.tree(resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),text(columns,"project_id"),"ARTIFACT",receipt.get().artifact().artifactId()),"artifact.read","EVALUATION_RESULTS"));
            status.put("state","PREPARED");status.set("receipt",identity(receipt.get()));
        }
        var again=dependencies.require(a,text(columns,"project_id"),text(plan.witness().required("root"),"classification"),new PermissionProtocol.Basis(text(columns,"source_input_version"),text(columns,"source_input_sha256")),purpose,evidence);
        if(!current.equals(again))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        return new Snapshot(a.context().tenant_id(),row,receipt.orElse(null),status,schema.canonical(Map.of("plan",plan.witness(),"row",row.columns(),"provenance",current,"artifact_read",read,"context",json.tree(a.context()))));
    }
    JsonNode identity(FormalEvaluationNodeInputObjectRegistration.Receipt receipt){var result=json.object().put("schema_version","1.0.0").put("state","PREPARED").put("runnable",false).put("professional_completion",false);
        result.put("operation_id",receipt.operationId()).put("write_id",receipt.writeId()).put("attempt_id",receipt.attemptId()).put("execution_id",receipt.executionId()).put("artifact_id",receipt.artifact().artifactId()).put("artifact_sha256",receipt.artifact().sha256()).put("object_version",receipt.objectVersion()).put("receipt_sha256",receipt.receiptSha256());return result;}
    private static void same(Object x,Object y){if(!Objects.equals(x,y))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
