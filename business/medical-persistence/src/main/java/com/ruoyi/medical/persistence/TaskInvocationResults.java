package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Original076 result/capture/accounting verification, shared by current consumers and
 * predecessor/pre-commit integrity checks. No live node/input/session authority dependency. */
public final class TaskInvocationResults {
    private final JdbcTemplate db;private final TaskInvocationRecords records;private final FormalInvocationSettlement settlement;
    private final FormalInvocationObjectRegistration objects;private final FormalProviderCaptureEvidence captures;
    private final FormalInvocationSchema schema;private final SchemaCatalogue published;
    public TaskInvocationResults(DataSource data,TaskInvocationRecords records,FormalInvocationSettlement settlement,
            FormalInvocationObjectRegistration objects,FormalProviderCaptureEvidence captures,FormalInvocationSchema schema)throws Exception {
        this(new JdbcTemplate(data),records,settlement,objects,captures,schema);
    }
    TaskInvocationResults(JdbcTemplate db,TaskInvocationRecords records,FormalInvocationSettlement settlement,
            FormalInvocationObjectRegistration objects,FormalProviderCaptureEvidence captures,FormalInvocationSchema schema)throws Exception {
        this.db=db;db.setQueryTimeout(5);this.records=records;this.settlement=settlement;this.objects=objects;this.captures=captures;this.schema=schema;published=SchemaCatalogue.fromClasspath();
    }
    /** Ordinals are accepted only from an authenticated observed request or an original
     * registered context. Neither this proof nor its output grants new execution. */
    OrderedHistory orderedBefore(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted supervisor,TaskProfessionalSupervisor.ModelRequest request)throws Exception {
        request.requireUnchanged(session.admission());
        if(!session.sessionId().equals(request.sessionId())||!FormalTaskNodeAuthority.sameEvidence(session.binding(),request.binding()))bad();
        return ordered(session,supervisor,request.ordinal());
    }
    OrderedHistory orderedBefore(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted supervisor,TaskInvocationOriginals.Stored context)throws Exception {
        requireSession(context,session);return ordered(session,supervisor,context.modelRequestOrdinal());
    }
    private OrderedHistory ordered(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted supervisor,long ordinal)throws Exception {
        return new OrderedHistory(session,supervisor,ordinal,orderedData(session,supervisor,ordinal));
    }
    public final class OrderedHistory {
        private final TaskProfessionalRuntimeService.Session session;private final TaskProfessionalSupervisor.Admitted supervisor;
        private final long ordinal;private final JsonNode original;
        private OrderedHistory(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted supervisor,long ordinal,JsonNode original){this.session=session;this.supervisor=supervisor;this.ordinal=ordinal;this.original=original.deepCopy();}
        public void requireUnchanged()throws Exception {if(!FormalTaskNodeAuthority.sameEvidence(original,orderedData(session,supervisor,ordinal)))bad();}
        void requireFor(TaskProfessionalRuntimeService.Session current)throws Exception {
            current.requireUnchanged();requireUnchanged();
            if(session.admission()!=current.admission()||!session.sessionId().equals(current.sessionId())||!FormalTaskNodeAuthority.sameEvidence(session.binding(),current.binding()))bad();
        }
        JsonNode modelResults()throws Exception{requireUnchanged();return original.required("model_results").deepCopy();}
        JsonNode modelPins()throws Exception{requireUnchanged();return original.required("model_pins").deepCopy();}
        JsonNode toolCallPins(String id)throws Exception{
            requireUnchanged();JsonNode found=null;var results=original.required("model_results");
            for(int index=0;index<results.size();index++)for(var block:results.get(index).required("blocks"))if("tool-call".equals(block.path("type").asText())&&id.equals(block.path("id").asText())){
                if(found!=null)bad();var result=results.get(index);
                var pin=(com.fasterxml.jackson.databind.node.ObjectNode)original.required("model_pins").get(index).deepCopy();
                pin.put("context_manifest_id",text(result,"context_manifest_id")).put("tool_call_id",id).put("tool_name",text(block,"name"))
                    .put("arguments_sha256",schema.hash(schema.parse(text(block,"arguments")))).put("session_id",session.sessionId())
                    .put("supervision_receipt_id",supervisor.receiptId()).put("supervision_sha256",supervisor.sha256());found=pin;
            }
            if(found==null)bad();return found;
        }
    }
    private JsonNode orderedData(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted supervisor,long ordinal)throws Exception {
        session.requireUnchanged();supervisor.requireUnchanged();
        if(!"OPEN".equals(session.state())||!session.sessionId().equals(supervisor.sessionId())||!FormalTaskNodeAuthority.sameEvidence(session.binding(),supervisor.binding()))bad();
        if(ordinal<1||ordinal>TaskInvocationHistoryData.MAXIMUM_ROUNDS+1)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var input=session.input();
        var ids=db.query("SELECT i.invocation_id FROM invocation i JOIN context_manifest c ON c.tenant_id=i.tenant_id AND c.context_manifest_id=i.context_manifest_id WHERE i.tenant_id=? AND i.project_id=? AND i.run_id=? AND i.step_id=? AND i.lease_generation=? AND i.created_by=? AND (JSON_EXTRACT(c.source_manifest,'$.dependencies.model_request.ordinal') IS NULL OR CAST(JSON_UNQUOTE(JSON_EXTRACT(c.source_manifest,'$.dependencies.model_request.ordinal')) AS UNSIGNED)<?) ORDER BY i.invocation_id LIMIT 1025",
            (r,n)->r.getString(1),input.tenantId(),input.projectId(),session.runId(),session.stepId(),session.generation(),session.admission().actorId(),ordinal);
        if(ids.size()!=ordinal-1||new HashSet<>(ids).size()!=ids.size())bad();
        var ordered=new TreeMap<Long,Result>();var contexts=new HashSet<String>();var calls=new HashSet<String>();
        for(String id:ids){
            var binding=records.byInvocation(input.tenantId(),id);requireSession(binding.original(),session);
            if(!binding.node().supervisor_receipt_id().equals(supervisor.receiptId())||!binding.node().supervisor_receipt_sha256().equals(supervisor.sha256()))bad();
            long index=binding.original().modelRequestOrdinal();
            if(index<1||index>=ordinal||!contexts.add(binding.context()))bad();
            var result=verifiedResult(binding,id);
            if(!id.equals(text(result.output(),"invocation_id"))||!binding.context().equals(text(result.output(),"context_manifest_id"))
                ||!binding.model().equals(text(result.output(),"model_config_revision"))||!"tool-calls".equals(text(result.output().required("reason"),"kind"))
                ||ordered.put(index,result)!=null)bad();
            int tools=0;for(var block:result.output().required("blocks")){
                if("text".equals(text(block,"type")))continue;
                if(!"tool-call".equals(text(block,"type"))||!calls.add(text(block,"id")))bad();tools++;
            }
            if(tools==0||calls.size()>TaskInvocationHistoryData.MAXIMUM_TOOL_CALLS)bad();
        }
        var data=TaskInvocationHistoryData.empty();long expected=1;
        for(var entry:ordered.entrySet()){
            if(entry.getKey()!=expected++)bad();var result=entry.getValue();
            ((ArrayNode)data.required("model_results")).add(result.output());
            ((ArrayNode)data.required("model_pins")).addObject().put("invocation_id",result.id()).put("accounting_sha256",result.accounting())
                .put("result_registration_sha256",result.result()).put("capture_registration_sha256",result.capture());
        }
        session.requireUnchanged();supervisor.requireUnchanged();return data;
    }
    static void requireSession(TaskInvocationOriginals.Stored stored,TaskProfessionalRuntimeService.Session session){
        var node=stored.request().node();var input=session.input();
        if(!node.tenant_id().equals(input.tenantId())||!node.project_id().equals(input.projectId())||!node.task_id().equals(input.taskId())||!stored.actorId().equals(session.admission().actorId())
            ||!node.principal_id().equals(session.admission().formalProof().identity().principal())
            ||!node.session_id().equals(session.sessionId())||!node.run_id().equals(session.runId())||!node.step_id().equals(session.stepId())
            ||!node.host_action_id().equals(session.actionId())||!node.lease_generation().equals(session.generation())||!node.worker_service_uri().equals(session.workerService())
            ||!node.node_input_artifact_id().equals(input.inputId())||!node.node_input_sha256().equals(input.inputSha256())||!node.node_input_receipt_sha256().equals(input.receiptSha256()))bad();
    }
    /** Factual original076 lineage only. No current execution or content-read capability. */
    public JsonNode historicalTerminalPins(TaskResearchCompletionAuthority.Input input,List<String> invocationIds)throws Exception {return historicalTerminal(input,invocationIds).canonicalPins();}
    public HistoricalTerminal historicalTerminal(TaskResearchCompletionAuthority.Input input,List<String> invocationIds)throws Exception {
        return new HistoricalTerminal(input,List.copyOf(invocationIds),historical(input,invocationIds));
    }
    /** Data-integrity proof only. Reading/disclosing output still requires a separate current gate. */
    public final class HistoricalTerminal {
        private final TaskResearchCompletionAuthority.Input input;private final List<String> ids;private final History fixed;
        private HistoricalTerminal(TaskResearchCompletionAuthority.Input input,List<String> ids,History fixed){this.input=input;this.ids=ids;this.fixed=fixed;}
        public void requireUnchanged()throws Exception {var now=historical(input,ids);if(!FormalTaskNodeAuthority.sameEvidence(fixed.pins,now.pins)||!FormalTaskNodeAuthority.sameEvidence(fixed.output,now.output))bad();}
        public JsonNode canonicalPins()throws Exception {requireUnchanged();return fixed.pins.deepCopy();}
        public JsonNode output()throws Exception {requireUnchanged();return fixed.output.deepCopy();}
    }
    private record History(JsonNode pins,JsonNode output){}
    private History historical(TaskResearchCompletionAuthority.Input input,List<String> invocationIds)throws Exception {
        input.requireUnchanged();if(invocationIds.isEmpty()||invocationIds.size()>1024||new HashSet<>(invocationIds).size()!=invocationIds.size())bad();
        var actual=db.query("SELECT invocation_id FROM invocation WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? AND created_by=? ORDER BY invocation_id LIMIT 1025",(r,n)->r.getString(1),input.tenantId(),input.projectId(),input.runId(),input.stepId(),input.generation(),input.actorId());
        if(!new HashSet<>(actual).equals(new HashSet<>(invocationIds))||actual.size()!=invocationIds.size())bad();var ordered=new TreeMap<Long,Result>();
        for(String invocation:invocationIds){var binding=records.byInvocation(input.tenantId(),invocation);var node=binding.node();
            if(!input.sessionId().equals(node.session_id())||!input.runId().equals(node.run_id())||!input.stepId().equals(node.step_id())||!input.inputId().equals(node.node_input_artifact_id())||!input.inputSha256().equals(node.node_input_sha256())||!input.receiptSha256().equals(node.node_input_receipt_sha256())||!input.workerService().equals(node.worker_service_uri())||!Long.toString(input.generation()).equals(node.lease_generation())||!input.actorId().equals(binding.actor()))bad();
            if(ordered.put(binding.original().modelRequestOrdinal(),verifiedResult(binding,invocation))!=null)bad();
        }
        long ordinal=1;for(long value:ordered.keySet())if(value!=ordinal++)bad();var values=List.copyOf(ordered.values());
        if(!"stop".equals(text(values.get(values.size()-1).output().required("reason"),"kind"))||values.subList(0,values.size()-1).stream().anyMatch(v->!"tool-calls".equals(v.output().required("reason").required("kind").textValue())))bad();
        var pins=new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode();for(var value:values)pins.addObject().put("invocation_id",value.id()).put("accounting_sha256",value.accounting()).put("result_registration_sha256",value.result()).put("capture_registration_sha256",value.capture());input.requireUnchanged();return new History(pins,values.get(values.size()-1).output());
    }
    Result verifiedResult(TaskInvocationRecords.Binding binding,String invocation)throws Exception {return verifiedResult(binding,invocation,true);}
    Result verifiedAnyResult(TaskInvocationRecords.Binding binding,String invocation)throws Exception {return verifiedResult(binding,invocation,false);}
    private Result verifiedResult(TaskInvocationRecords.Binding binding,String invocation,boolean requireSuccess)throws Exception {
        return verifiedSettlement(binding,invocation,binding.original().price(),requireSuccess);
    }
    /** Original facts only: callers must independently verify the typed original binding. */
    Result verifiedSettlement(FormalInvocationBinding binding,String invocation,FormalInvocationPricing.Schedule price,boolean requireSuccess)throws Exception {
        var account=settlement.read(binding.tenant(),invocation).orElseThrow(()->new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
        var receipt=account.receipt();if(!account.settled()||!receipt.required("provider_terminal").asBoolean(false)||(!Set.of("SUCCEEDED","FAILED","CANCELLED").contains(text(receipt,"status"))||(requireSuccess&&!"SUCCEEDED".equals(text(receipt,"status")))))unavailable();
        var result=objects.registered(binding.tenant(),invocation,"MODEL_RESULT").orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
        var capture=objects.registered(binding.tenant(),invocation,"PROVIDER_CAPTURE").orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
        var send=dispatchEvidence(binding,invocation);FormalInvocationDispatch.verifyEvidence(db,schema,binding.tenant(),invocation,send);
        var payload=result.canonicalPayload();published.bindPublished(published.schemaRef("model-stream","ModelResult"),published.schemaDigest("model-stream"),ContentReadAuthorization.canonicalBytes(payload));
        String actualStatus=text(receipt,"status");boolean successful=FormalInvocationSettlement.successfulResult(true,text(payload.required("reason"),"kind"));if(("SUCCEEDED".equals(actualStatus)&&!successful)||("FAILED".equals(actualStatus)&&successful))bad(); // CANCELLED is the actual original cancellation/accounting outcome, not provider-stopped evidence.
        var observation=captures.inspect(invocation,text(receipt,"attempt_id"),send,payload,ContentReadAuthorization.canonicalBytes(capture.canonicalPayload()));
        var determination=FormalInvocationAccounting.determine(price,observation.usage(),observation.calls(),observation.terminal());
        if(!determination.maySettleAll()||!observation.terminal()||determination.amount().orElseThrow().compareTo(new BigDecimal(text(receipt,"known_amount")))!=0
                ||!determination.tokens().orElseThrow().toString().equals(text(receipt,"known_tokens"))||!determination.calls().orElseThrow().toString().equals(text(receipt,"known_calls")))bad();
        return new Result(invocation,payload,account.receiptSha256(),result.objectReceiptSha256(),capture.objectReceiptSha256());
    }
    record Result(String id,JsonNode output,String accounting,String result,String capture){}
    private JsonNode dispatchEvidence(FormalInvocationBinding b,String invocation)throws Exception {
        return schema.parse(one(db.query("SELECT JSON_OBJECT('project_id',i.project_id,'created_by',i.created_by,'classification',i.classification,'actual_send_manifest',i.actual_send_manifest,'actual_send_sha256',i.actual_send_sha256,'input_sha256',i.input_sha256,'attempt_id',a.attempt_id,'request_sha256',a.request_sha256,'service_uri',a.service_uri,'certificate_sha256',a.certificate_sha256,'trust_revision',a.trust_revision,'lease_generation',CAST(a.lease_generation AS CHAR)) FROM invocation i JOIN formal_invocation_attempt a ON a.tenant_id=i.tenant_id AND a.invocation_id=i.invocation_id WHERE i.tenant_id=? AND i.invocation_id=? AND i.run_id=? AND i.step_id=? AND i.action_id=? AND i.created_by=? AND i.dispatch_generation=1 AND a.dispatch_generation=1 AND a.lease_generation=i.lease_generation AND a.input_sha256=i.input_sha256 LIMIT 2",
                (r,n)->r.getString(1),b.tenant(),invocation,b.run(),b.step(),b.action(),b.actor())));
    }
    private static String text(JsonNode n,String key){var value=n.required(key);if(!value.isTextual())bad();return value.textValue();}
    private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void unavailable(){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
}
