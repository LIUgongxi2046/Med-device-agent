package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.ruoyi.medical.persistence.FormalEvaluationPlanPreparationLedger.*;

/** Read-only090 authority. Cannot be passed to execution admission, compiler or model-send gates. */
public final class FormalEvaluationPlanPreparationContentAuthority {
    private final FormalEvaluationPlanPreparationReadService reads;
    private final FormalEvaluationPlanPreparationSchema schema;
    private final FormalEvaluationPlanPreparationContentSchema content;
    private final PermissionSchema json;
    public FormalEvaluationPlanPreparationContentAuthority(FormalEvaluationPlanPreparationReadService reads,FormalEvaluationPlanPreparationSchema schema,
            FormalEvaluationPlanPreparationContentSchema content,PermissionSchema json){this.reads=reads;this.schema=schema;this.content=content;this.json=json;}

    HistoricalRead require(ApplicationSessionIdentity.Verified subject,String context,byte[] request)throws Exception {
        DocumentUploadTransferService.requireOutsideTransaction(subject);
        var value=content.request(request);byte[] recovery=schema.canonical(value.required("recovery")).getBytes(StandardCharsets.UTF_8);
        var expected=value.required("expected_receipt");
        var snapshot=reads.contentSnapshot(subject,context,recovery,expected);requireOriginalProjection(snapshot);
        return new HistoricalRead(this,subject,context,recovery,expected,snapshot);
    }
    private void requireOriginalProjection(FormalEvaluationPlanPreparationReadService.Historical snapshot)throws Exception {
        var root=snapshot.witness().required("root");var original=schema.read(text(root,"canonical_admission"));
        var row=snapshot.row();var preview=schema.validate("Preview",row.intent().required("original_evidence").required("preview"));
        if(!preview.required("source_snapshot").equals(original.required("source_snapshot"))
                ||!preview.required("source_snapshot").equals(row.intent().required("original_evidence").required("source_snapshot"))
                ||!preview.required("profile_revision").equals(original.required("profile_revision"))
                ||preview.required("coverage").required("expected_cells").longValue()!=root.required("expected_cells").longValue()
                ||preview.required("coverage").required("expected_nodes").longValue()!=root.required("expected_nodes").longValue())bad();
        var samples=new ArrayList<JsonNode>();for(var sample:original.required("definition").required("request").required("samples"))
            if(sample.required("sample_id").asText().equals(text(row.columns(),"sample_id")))samples.add(sample);
        if(samples.size()!=1||!samples.get(0).required("document_type").equals(preview.required("document_type")))bad();
    }
    /** Private-constructor historical capability. Original serialized hashes/receipts are assertions, never constructors. */
    static final class HistoricalRead {
        private final FormalEvaluationPlanPreparationContentAuthority owner;
        private final ApplicationSessionIdentity.Verified subject;
        private final String context,session,actor;
        private final byte[] recovery;
        private final JsonNode expected;
        private final FormalEvaluationPlanPreparationReadService.Historical snapshot;
        private HistoricalRead(FormalEvaluationPlanPreparationContentAuthority owner,ApplicationSessionIdentity.Verified subject,String context,
                byte[] recovery,JsonNode expected,FormalEvaluationPlanPreparationReadService.Historical snapshot){
            this.owner=owner;this.subject=subject;this.context=context;this.recovery=recovery.clone();this.expected=expected.deepCopy();this.snapshot=snapshot;
            session=subject.sessionId();actor=subject.user().user_id();
        }
        void requireCurrent()throws Exception {current(subject,context);}
        void current(ApplicationSessionIdentity.Verified caller,String requestedContext)throws Exception {
            DocumentUploadTransferService.requireOutsideTransaction(caller);
            if(caller!=subject||!context.equals(requestedContext)||!Objects.equals(session,caller.sessionId())||!Objects.equals(actor,caller.user().user_id()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
            var fresh=owner.reads.contentSnapshot(subject,context,recovery,expected);
            if(!snapshot.witness().equals(fresh.witness())||!snapshot.receipt().equals(fresh.receipt()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        }
        ApplicationSessionIdentity.Verified subject(){return subject;}
        String tenant(){return snapshot.tenant();}
        InterfaceProtocol.ObjectRef reference(){
            var row=snapshot.row();return new InterfaceProtocol.ObjectRef(text(row.intent(),"object_key"),snapshot.receipt().objectVersion(),
                text(row.columns(),"payload_sha256"),text(row.intent(),"payload_size"),"application/json");
        }
        JsonNode consume(byte[] actual)throws Exception {
            requireCurrent();var row=snapshot.row();var ref=reference();
            byte[] immutable=text(row.columns(),"canonical_payload").getBytes(StandardCharsets.UTF_8);
            if(actual==null||!Arrays.equals(immutable,actual)||!ref.sha256().equals(WireValues.sha256(actual))||!Integer.toString(actual.length).equals(ref.sizeBytes()))bad();
            SchemaCatalogue.fromClasspath().bindPublished(FormalEvaluationPlanPreparationSchema.PAYLOAD_REF,text(row.intent(),"payload_schema_sha256"),actual);
            var plan=owner.schema.validate("PlanCandidate",owner.schema.read(new String(actual,StandardCharsets.UTF_8)));
            var preview=owner.schema.validate("Preview",row.intent().required("original_evidence").required("preview"));
            if(!plan.equals(preview.required("plan_candidate"))||!"SELECTED".equals(text(preview,"selection_state"))
                    ||!text(row.columns(),"execution_id").equals(text(preview,"execution_id"))||!text(row.columns(),"evaluation_id").equals(text(preview,"evaluation_id"))
                    ||!text(row.columns(),"sample_id").equals(text(preview,"sample_id"))||number(row.columns(),"repeat_index")!=preview.required("repeat_index").longValue()
                    ||!row.intent().required("context_binding").equals(preview.required("context_binding"))
                    ||!row.intent().required("node").required("cell").required("candidate").equals(preview.required("candidate"))
                    ||!row.intent().required("node").required("cell").required("policy").equals(preview.required("execution_policy"))
                    ||!plan.required("template_version").equals(preview.required("selected_template").required("version_id"))
                    ||!plan.required("profile_revision").equals(preview.required("profile_revision")))bad();
            var safe=(com.fasterxml.jackson.databind.node.ObjectNode)preview.deepCopy();
            safe.putArray("action_summary"); // Historical write decisions are not current authority.
            var result=owner.json.object().put("schema_version","1.0.0").put("runnable",false).put("professional_completion",false);
            result.set("receipt",expected.deepCopy());result.set("plan_candidate",plan);result.set("original_preview",safe);
            owner.content.validate("Content",result);requireCurrent();return result;
        }
    }
}
