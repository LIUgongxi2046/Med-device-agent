package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Always present: removing a sidecar from a descendant cannot hide its real immutable base chain. */
@Component
public final class TaskPlanDraftOriginLocator {
    private final JdbcTemplate db;private final TaskPlanDraftSchema schema;private final TemplateDependencyPolicy limits;
    public TaskPlanDraftOriginLocator(DataSource data,TemplateDependencyPolicy limits)throws Exception{db=new JdbcTemplate(data);db.setQueryTimeout(5);schema=new TaskPlanDraftSchema();this.limits=limits;}
    Optional<Found> locate(EnterpriseAccess.Admission a,TaskService.VerifiedTask task,TaskPlanWriteProtocol.View draft)throws Exception {
        if(!a.context().tenant_id().equals(task.head().tenantId())||!draft.tenantId().equals(task.head().tenantId())||!draft.projectId().equals(task.head().projectId())||!draft.taskId().equals(task.head().taskId()))bad();
        String id=draft.planRevision(),expected=draft.contentSha256();var chain=new ArrayList<String>();var seen=new HashSet<String>();Found found=null;
        while(id!=null){if(!seen.add(id)||seen.size()>limits.maximumVersions())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            var row=one(db.queryForList("SELECT c.plan_revision,c.base_version,c.base_version_sha256,c.content_sha256,c.content_json,p.content_sha256 plan_hash,v.content_sha256 registry_hash FROM plan_content_revision c JOIN plan_revision p ON p.tenant_id=c.tenant_id AND p.project_id=c.project_id AND p.task_id=c.task_id AND p.plan_revision=c.plan_revision JOIN version_registry v ON v.tenant_id=c.tenant_id AND v.version_id=c.plan_revision WHERE c.tenant_id=? AND c.project_id=? AND c.task_id=? AND c.plan_revision=? LIMIT 2",draft.tenantId(),draft.projectId(),draft.taskId(),id));
            if(!expected.equals(row.get("content_sha256"))||!expected.equals(row.get("plan_hash"))||!expected.equals(row.get("registry_hash"))||!expected.equals(schema.hash(schema.read(str(row,"content_json")))))bad();chain.add(id+":"+expected);
            var origins=db.queryForList("SELECT * FROM task_plan_draft_origin WHERE tenant_id=? AND project_id=? AND task_id=? AND draft_revision=? LIMIT 2",draft.tenantId(),draft.projectId(),draft.taskId(),id);if(origins.size()>1)bad();
            if(!origins.isEmpty()){
                var origin=origins.get(0);var receipt=schema.validate("Origin",schema.read(str(origin,"receipt_json")));String hash=schema.hash(receipt);
                if(!hash.equals(origin.get("receipt_sha256"))||!schema.canonical(receipt).equals(origin.get("receipt_json"))||!id.equals(origin.get("draft_revision"))||!expected.equals(origin.get("draft_sha256"))||!"1.0.0".equals(origin.get("schema_version"))||!schema.manifest().equals(origin.get("schema_manifest_sha256")))bad();
                for(String field:List.of("tenant_id","project_id","task_id","run_id","draft_revision","draft_sha256","candidate_receipt_sha256","check_receipt_sha256","operation_id"))if(!receipt.required(field).asText().equals(origin.get(field)))bad();if(!receipt.required("actor_id").asText().equals(origin.get("created_by")))bad();
                var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",draft.tenantId(),origin.get("audit_id")));var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",draft.tenantId(),origin.get("event_id")));
                if(!receipt.required("actor_id").asText().equals(audit.get("subject_id"))||!"task.plan_draft.adopted".equals(audit.get("action"))||!"SUCCESS".equals(audit.get("result"))||!receipt.equals(schema.read(str(audit,"resource_ref")))||!receipt.equals(schema.read(str(event,"payload")))||!"task.plan_draft.adopted".equals(event.get("event_type"))||!draft.taskId().equals(event.get("aggregate_id")))bad();
                found=new Found(draft.tenantId(),draft.projectId(),draft.taskId(),draft.planRevision(),draft.contentSha256(),str(origin,"run_id"),id,hash,receipt,List.copyOf(chain));break;
            }
            Object parent=row.get("base_version");if(parent==null){if(row.get("base_version_sha256")!=null)bad();id=null;}else{id=parent.toString();WireValues.uuid(id);expected=str(row,"base_version_sha256");WireValues.hash(expected);}
        }
        return Optional.ofNullable(found);
    }
    record Found(String tenant,String project,String task,String targetDraft,String targetHash,String run,String originDraft,String originReceiptSha256,JsonNode origin,List<String> chain){Found{origin=origin.deepCopy();chain=List.copyOf(chain);}@Override public JsonNode origin(){return origin.deepCopy();}}
    private static String str(Map<String,Object> r,String k){if(!(r.get(k) instanceof String))bad();return (String)r.get(k);}private static <T>T one(List<T> r){if(r.size()!=1||r.get(0)==null)bad();return r.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
