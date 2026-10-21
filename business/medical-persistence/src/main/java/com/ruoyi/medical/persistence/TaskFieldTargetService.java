package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.util.function.ThrowingSupplier;

/** Authenticated pre-run physical preparation. No workflow or future output authority is minted.
 * Every physical segment reacquires current task, mapping and original-source receipt authority.
 * Recovery retains the first immutable request and object identity, and never retries a PUT. */
public final class TaskFieldTargetService {
    private final EnterpriseAccess access;
    private final TaskFieldTargetBasis basis;
    private final TaskFieldMappingService mappings;
    private final TaskFieldSourceReader sources;
    private final TaskFieldTargetLedger ledger;
    private final RuntimeObjectTransport transport;
    private final TaskFieldTargetRegistration registration;
    private final TaskFieldTargetSchema schema;
    private final TaskFieldTargetPolicy policy;
    public TaskFieldTargetService(EnterpriseAccess access,TaskFieldTargetBasis basis,TaskFieldMappingService mappings,
            TaskFieldSourceReader sources,TaskFieldTargetLedger ledger,RuntimeObjectTransport transport,
            TaskFieldTargetRegistration registration,TaskFieldTargetSchema schema,TaskFieldTargetPolicy policy){
        this.access=access;this.basis=basis;this.mappings=mappings;this.sources=sources;this.ledger=ledger;
        this.transport=transport;this.registration=registration;this.schema=schema;this.policy=policy;
    }
    public JsonNode produce(ApplicationSessionIdentity.Verified subject,String context,String taskId,String key,byte[] bytes)throws Exception{
        WireValues.uuid(taskId);var request=schema.request("Produce",bytes);
        var original=access.execute(subject,a->checked(()->{context(a,context);return ledger.findOriginal(a,taskId,key);}));
        if(original.isPresent()&&!request.equals(original.get().intent().required("original_request")))
            throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        if(original.isPresent()){
            var saved=registered(subject,context,taskId,original.get());if(saved!=null)return saved;
        }
        return materialize(subject,context,taskId,key,request,original.isPresent());
    }
    public JsonNode recover(ApplicationSessionIdentity.Verified subject,String context,String taskId,byte[] bytes)throws Exception{
        WireValues.uuid(taskId);var recovery=schema.request("Recovery",bytes);String key=recovery.required("original_idempotency_key").asText();
        var original=access.execute(subject,a->checked(()->{context(a,context);return ledger.findOriginal(a,taskId,key);}));
        if(original.isEmpty())return null;
        var request=original.get().intent().required("original_request");
        if(!request.required("project_id").equals(recovery.required("project_id")))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var saved=registered(subject,context,taskId,original.get());if(saved!=null)return saved;
        return materialize(subject,context,taskId,key,request,true);
    }
    private JsonNode registered(ApplicationSessionIdentity.Verified subject,String context,String task,TaskFieldTargetLedger.Row original)throws Exception{
        return access.execute(subject,a->checked(()->{
            context(a,context);var found=registration.findRegistered(a,original.columns().get("planned_artifact_id").toString(),TaskFieldTargetProtocol.TARGET_PRODUCE);
            if(found.isEmpty())return null;var proof=found.get();
            if(!task.equals(proof.taskId()))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
            var result=status(a,task,original.intent().required("original_request"),original.columns().get("write_id").toString(),proof);
            proof.requireUnchanged();return result;
        }));
    }
    private JsonNode status(EnterpriseAccess.Admission a,String taskId,JsonNode request,String writeId,TaskFieldTargetRegistration.Verified registered)throws Exception{
        var result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
            .put("schema_version","1.0.0").put("tenant_id",a.context().tenant_id())
            .put("project_id",text(request,"project_id")).put("task_id",taskId).put("state","REGISTERED")
            .put("assignment_id",text(request,"assignment_id")).put("assignment_sha256",text(request,"assignment_sha256"))
            .put("write_id",writeId).put("artifact_id",registered.artifactId());
        result.set("receipt",registered.receipt());result.putNull("reason");schema.validate("Status",result);return result;
    }
    private JsonNode materialize(ApplicationSessionIdentity.Verified subject,String context,String taskId,String key,JsonNode request,boolean recovery)throws Exception{
        // A missing deployed object writer or bounds is detected before any receipt/intent write.
        policy.requireConfigured();policy.lineageLimits();if(transport==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var mapping=access.execute(subject,a->checked(()->{
            var proof=current(a,context,taskId,request);return mappings.requireCurrent(proof,text(request,"assignment_id"),text(request,"assignment_sha256")).manifest();
        }));
        if(TaskFieldTargetPreparationService.hasUnresolved(mapping.required("inventory"))){
            var waiting=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                .put("schema_version","1.0.0").put("tenant_id",mapping.required("tenant_id").asText())
                .put("project_id",text(request,"project_id")).put("task_id",taskId).put("state","WAITING_INPUT")
                .put("assignment_id",text(request,"assignment_id")).put("assignment_sha256",text(request,"assignment_sha256"))
                .putNull("write_id").putNull("artifact_id").putNull("receipt")
                .put("reason","Complete the current inventory's missing mapping, source, context or supported field requirements before production.");
            schema.validate("Status",waiting);return waiting;
        }
        List<TaskFieldSourceReader.Read> read=new ArrayList<>();
        for(var entry:mapping.required("mappings")){
            // Only mapped, fully applicable literal assignments can become target occurrences.
            // Ledger independently verifies exact coverage against current inventory.
            if(entry.required("source_exact").isNull())continue;
            boolean selected=false;
            for(var item:mapping.required("inventory"))if("MAPPED".equals(text(item,"state")))
                for(var id:item.required("entry_keys"))if(id.asText().equals(text(entry,"entry_key")))selected=true;
            if(selected)read.add(sources.read(subject,context,new TaskFieldSourceAuthority.Request(taskId,text(request,"assignment_id"),text(request,"assignment_sha256"),text(entry,"entry_key"))));
        }
        var reads=List.copyOf(read);
        var attempt=access.execute(subject,a->checked(()->{
            var proof=current(a,context,taskId,request);var assigned=mappings.requireCurrent(proof,text(request,"assignment_id"),text(request,"assignment_sha256"));
            registration.preflight(proof,assigned,reads);
            return recovery?ledger.recover(proof,assigned,reads,request,key,context):ledger.prepare(proof,assigned,reads,request,key,context);
        }));
        var transferred=transport.transferVerified(attempt,()->access.execute(subject,a->checked(()->{
            var proof=current(a,context,taskId,request);var assigned=mappings.requireCurrent(proof,text(request,"assignment_id"),text(request,"assignment_sha256"));
            ledger.require(proof,assigned,reads,attempt,context);registration.requireCurrentContent(a,attempt,TaskFieldTargetProtocol.TARGET_PRODUCE);for(var value:reads)sources.requireCurrent(a,context,value);
            proof.requireUnchanged();assigned.requireUnchanged();return null;
        })));
        return access.execute(subject,a->checked(()->{
            var proof=current(a,context,taskId,request);var assigned=mappings.requireCurrent(proof,text(request,"assignment_id"),text(request,"assignment_sha256"));
            var registered=registration.register(proof,assigned,reads,attempt,transferred,context);
            var result=status(a,taskId,request,attempt.writeId(),registered);registered.requireUnchanged();return result;
        }));
    }
    private TaskFieldTargetBasis.Verified current(EnterpriseAccess.Admission a,String context,String taskId,JsonNode request)throws Exception{
        context(a,context);var proof=basis.require(a,taskId,TaskFieldTargetProtocol.TARGET_PRODUCE);
        for(String field:List.of("project_id","task_revision","plan_revision"))if(!Objects.equals(proof.pins().get(field),request.get(field)))
            throw new TenantAccessException(ErrorCode.RESULT_VERSION_MISMATCH);return proof;
    }
    private static String text(JsonNode n,String f){return n.required(f).asText();}
    private static void context(EnterpriseAccess.Admission a,String context){if(!Objects.equals(context,a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
    private static <T>T checked(ThrowingSupplier<T> operation){return operation.get();}
}
