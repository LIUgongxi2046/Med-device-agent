package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.ruoyi.medical.persistence.TaskRunActivityJournal.*;
/** Historical OS termination only. Never a business Completion, export or send grant. */
final class TaskProcessTerminationFacts {
    private static final JsonMapper JSON=new JsonMapper();
    static Optional<JsonNode> read(JdbcTemplate db,TaskRunLifecycleAuthority.Proof proof,Map<String,Object> session)throws Exception {
        proof.require(proof.operation());String tenant=proof.reference().tenantId(),id=text(session,"session_id");
        var rows=db.queryForList("SELECT * FROM professional_runtime_supervision WHERE tenant_id=? AND session_id=? AND phase='TERMINATED' LIMIT 2 FOR UPDATE",tenant,id);if(rows.isEmpty())return Optional.empty();var row=one(rows);
        var start=JSON.readTree(text(session,"canonical_start"));same(hash(start),text(session,"start_sha256"));same(id,start.required("session_id").asText());
        var value=JSON.readTree(text(row,"canonical_evidence"));validate(value);same(hash(value),text(row,"evidence_sha256"));same(id,value.required("session_id").asText());same(start.required("binding"),value.required("binding"));same(start.required("deployment_sha256"),value.required("deployment_sha256"));
        for(String field:List.of("tenant_id","project_id","run_id","step_id","session_id","lease_generation","input_sha256","envelope_sha256","created_by","classification"))same(session.get(field),row.get(field));
        same(proof.admission().actorId(),text(row,"created_by"));same(proof.reference().runId(),text(row,"run_id"));
        var admitted=one(db.queryForList("SELECT * FROM professional_runtime_supervision WHERE tenant_id=? AND session_id=? AND phase='ADMITTED' LIMIT 2 FOR UPDATE",tenant,id));var admission=JSON.readTree(text(admitted,"canonical_evidence"));TaskProfessionalSupervisor.validateStoredAdmission(admission);
        same(hash(admission),text(admitted,"evidence_sha256"));same(text(admitted,"receipt_id"),value.required("admitted_receipt_id").asText());same(text(admitted,"evidence_sha256"),value.required("admitted_sha256").asText());same(admission.required("process_unit"),value.required("process_unit"));same(admission.required("supervisor_instance_id"),value.required("supervisor_instance_id"));
        String prefix=switch(start.required("node_kind").asText()){case "N02"->"task.professional.";case "N04"->"task.section_writer.professional.";case "N05"->"task.source_verifier.professional.";default->throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);};
        for(var evidence:List.of(admitted,row)){
            String phase=text(evidence,"phase").toLowerCase(Locale.ROOT),action=prefix+phase;
            var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND action=? AND result='SUCCESS' LIMIT 2",tenant,text(row,"project_id"),text(evidence,"audit_id"),proof.admission().actorId(),action));
            var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type=? LIMIT 2",tenant,text(evidence,"event_id"),id,action));
            same(JSON.readTree(text(evidence,"canonical_evidence")),JSON.readTree(text(audit,"resource_ref")));same(JSON.readTree(text(evidence,"canonical_evidence")),JSON.readTree(text(event,"payload")));
            for(String field:List.of("created_by","created_at","classification","schema_version","schema_manifest_sha256"))same(evidence.get(field),audit.get(field));
        }
        proof.require(proof.operation());return Optional.of(value);
    }
    static void validate(JsonNode value)throws Exception {
        ProfessionalRuntimeObservations.closed(value,Set.of("schema_version","receipt_id","phase","session_id","binding","deployment_revision","deployment_sha256","runtime_manifest_sha256","supervisor_instance_id","inspected_at","process_unit","admitted_receipt_id","admitted_sha256","exit"));
        same("TERMINATED",value.required("phase").asText());same("1.0.0",value.required("schema_version").asText());
        for(String key:List.of("receipt_id","session_id","deployment_revision","supervisor_instance_id","admitted_receipt_id"))WireValues.uuid(value.required(key).asText());
        for(String key:List.of("deployment_sha256","runtime_manifest_sha256","admitted_sha256"))WireValues.hash(value.required(key).asText());
        ProfessionalRuntimeObservations.jsonBinding(value.required("binding"));TaskProfessionalSupervisor.validateUnit(value.required("process_unit"));
        var exit=value.required("exit");ProfessionalRuntimeObservations.closed(exit,Set.of("kind","boot_id","process_unit_sha256","root_pid","code","status","unit_state","observed_exits"));same("OWNED_WAITID",exit.required("kind").asText());same("EMPTY",exit.required("unit_state").asText());same(value.required("process_unit").required("boot_id"),exit.required("boot_id"));same(hash(value.required("process_unit")),exit.required("process_unit_sha256").asText());
        if(!Set.of("EXITED","KILLED","DUMPED").contains(exit.required("code").asText())||!exit.required("status").isIntegralNumber()||exit.required("status").intValue()<0||exit.required("status").intValue()>255)bad();
        var expected=new HashMap<Long,Long>();for(var p:value.required("process_unit").required("processes"))if(expected.put(p.required("pid").longValue(),p.required("start_ticks").longValue())!=null)bad();
        if(!expected.containsKey(exit.required("root_pid").longValue()))bad();var observed=exit.required("observed_exits");if(!observed.isArray()||observed.size()!=expected.size())bad();
        for(var p:observed){ProfessionalRuntimeObservations.closed(p,Set.of("pid","start_ticks","kind"));same("PIDFD_EXIT",p.required("kind").asText());same(expected.remove(p.required("pid").longValue()),p.required("start_ticks").longValue());}if(!expected.isEmpty())bad();
    }
    private static String hash(JsonNode n)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(n));}
}
