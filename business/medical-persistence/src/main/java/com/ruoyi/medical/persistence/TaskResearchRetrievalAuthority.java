package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.RetrievalMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Revalidates original fixed-question KN-14 results. A model-proposed retrieval UUID is not a receipt. */
public final class TaskResearchRetrievalAuthority {
    private final RetrievalService service;private final RetrievalMapper mapper;private final RetrievalSchema schema;private final PermissionSchema json;
    public TaskResearchRetrievalAuthority(RetrievalService service,RetrievalMapper mapper,RetrievalSchema schema,PermissionSchema json){this.service=service;this.mapper=mapper;this.schema=schema;this.json=json;}
    public Verified require(TaskProfessionalRuntimeService.Session session,List<String> ids)throws Exception {
        return require(session,ids,false);
    }
    public Verified require(TaskProfessionalRuntimeService.Session session,List<String> ids,boolean v2)throws Exception {return require(new LiveScope(session),ids,v2);}
    public Verified requireOriginal(TaskResearchCompletionAuthority.Input input,List<String> ids)throws Exception {return requireOriginal(input,ids,false);}
    public Verified requireOriginal(TaskResearchCompletionAuthority.Input input,List<String> ids,boolean v2)throws Exception {return require(new OriginalScope(input),ids,v2);}
    private Verified require(Scope scope,List<String> ids,boolean v2)throws Exception {
        if(ids==null||ids.size()>1||new HashSet<>(ids).size()!=ids.size())bad(); // one exact question/idempotency key
        return new Verified(scope,List.copyOf(ids),v2,verify(scope,ids,v2));
    }
    private JsonNode verify(Scope scope,List<String> ids,boolean v2)throws Exception {
        scope.requireUnchanged();var a=scope.admission();var input=new TaskNodeInputSchema().read(ContentReadAuthorization.canonicalBytes(scope.manifest()));var pins=json.object().arrayNode();var evidence=json.object().arrayNode();
        String identity=WireValues.sha256(ContentReadAuthorization.canonicalBytes(Map.of("tenant",input.tenantId(),"project",input.projectId(),"run",input.runId(),"step",input.stepId(),"input",scope.inputId(),"receipt",scope.receiptSha256(),"input_sha256",scope.inputSha256(),"graph",input.graphId(),"graph_sha256",input.graphSha256())));
        var fixed=new TaskResearchToolAccess.Fixed(input.tenantId(),input.projectId(),a.actorId(),input.snapshotId(),input.questionId(),input.questionText(),input.fixedSources(),identity);
        var request=new RetrievalProtocol.SearchInput("1.0.0",input.projectId(),input.questionText(),"VERIFICATION");
        String requestSha=WireValues.sha256(ContentReadAuthorization.canonicalBytes(Map.of("operation","TASK_AT01_RETRIEVAL","input_receipt_identity",identity,"body",request)));
        for(String id:ids){WireValues.uuid(id);var args=new HashMap<String,Object>();args.put("userId",a.actorId());args.put("retrievalId",id);args.put("key",fixed.retrievalKey());
            var row=one(mapper.query(args));var operation=one(mapper.operation(args));var receipt=schema.operationReceipt(operation.responseRef());
            if(!id.equals(row.retrievalId())||!id.equals(operation.targetId())||!requestSha.equals(operation.requestSha256())||!"COMPLETED".equals(operation.state())||!Integer.valueOf(201).equals(operation.httpStatus())||!"1.0.0".equals(operation.schemaVersion())||!schema.manifestSha256().equals(operation.schemaManifestSha256())
                ||!operation.operationId().equals(receipt.required("data").required("operation_id").textValue())||!id.equals(receipt.required("data").required("resource_id").textValue())||!input.tenantId().equals(row.tenantId())||!input.projectId().equals(row.projectId())||!a.actorId().equals(row.subjectId())||!a.actorId().equals(row.createdBy())||!input.questionText().equals(row.queryText())||!WireValues.sha256(input.questionText().getBytes(StandardCharsets.UTF_8)).equals(row.querySha256())||row.resultCount().signum()<0||row.resultCount().compareTo(java.math.BigInteger.valueOf(1000))>0)bad();
            var selection=json.object().arrayNode();int count=row.resultCount().intValueExact();
            for(int page=1;page<=Math.max(1,(count+99)/100);page++){
                // This existing path checks current source rights, real index/parse/approval and original fixed key.
                var view=service.read(a,a.context().context_revision(),id,new RetrievalProtocol.PageQuery(page,100),fixed).required("data");
                for(var hit:view.required("items")){var pin=json.object();for(String field:List.of("source_version","parse_version","index_version","chunk_id","approval_revision","quote_hash"))pin.set(field,hit.required(field));selection.add(pin);var ref=json.object().put("schema_version",v2?"2.0.0":"1.0.0").put("tenant_id",input.tenantId()).put("project_id",input.projectId());for(String field:List.of("source_id","source_version","parse_version","chunk_id","locator_kind","locator","quote_hash","approval_revision"))ref.set(field,hit.required(field));SourceEvidenceBindings.bind(SchemaCatalogue.fromClasspath(),ref).read();evidence.add(ref);}
            }
            if(selection.size()!=count||!WireValues.sha256(ContentReadAuthorization.canonicalBytes(selection)).equals(row.selectionSha256()))bad();
            pins.addObject().put("retrieval_id",id).put("operation_id",operation.operationId()).put("request_sha256",requestSha).put("selection_sha256",row.selectionSha256()).put("operation_receipt_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(receipt)));
        }
        scope.requireUnchanged();var result=json.object();result.set("pins",pins);result.set("evidence_refs",evidence);return result;
    }
    public final class Verified {
        private final Scope scope;private final boolean v2;private final List<String> ids;private final JsonNode pins;
        private Verified(Scope scope,List<String> ids,boolean v2,JsonNode pins){this.scope=scope;this.v2=v2;this.ids=ids;this.pins=pins.deepCopy();}
        public void requireUnchanged()throws Exception {if(!FormalTaskNodeAuthority.sameEvidence(pins,verify(scope,ids,v2)))bad();}
        public JsonNode canonicalPins()throws Exception{requireUnchanged();return pins.required("pins").deepCopy();}
        public JsonNode evidenceRefs()throws Exception{requireUnchanged();return pins.required("evidence_refs").deepCopy();}
    }
    private sealed interface Scope permits LiveScope,OriginalScope {
        void requireUnchanged()throws Exception;EnterpriseAccess.Admission admission();JsonNode manifest();String inputId();String inputSha256();String receiptSha256();
    }
    private record LiveScope(TaskProfessionalRuntimeService.Session session) implements Scope {
        public void requireUnchanged()throws Exception{session.requireUnchanged();session.input().requireUnchanged();}
        public EnterpriseAccess.Admission admission(){return session.admission();}public JsonNode manifest(){return session.input().manifest();}
        public String inputId(){return session.input().inputId();}public String inputSha256(){return session.input().inputSha256();}public String receiptSha256(){return session.input().receiptSha256();}
    }
    private record OriginalScope(TaskResearchCompletionAuthority.Input input) implements Scope {
        public void requireUnchanged()throws Exception{input.requireUnchanged();}public EnterpriseAccess.Admission admission(){return input.admission();}public JsonNode manifest(){return input.manifest();}
        public String inputId(){return input.inputId();}public String inputSha256(){return input.inputSha256();}public String receiptSha256(){return input.receiptSha256();}
    }
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
