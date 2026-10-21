package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Current consumer gate. Historical impact entries never themselves grant confirmation/signing. */
@Component
public final class VersionChangeImpactGate {
 private final JdbcTemplate db;private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
 public VersionChangeImpactGate(DataSource data){db=new JdbcTemplate(data);db.setQueryTimeout(5);}
 /** B09 calls after its normal complete human evidence/strict-field checks, in the same tenant transaction. */
 JsonNode reviewBasis(EnterpriseAccess.Admission a,DocumentReadService.FixedRead fixed,List<ProfessionalCheckService.Verified> proofs,HumanEvidenceReviewService.CurrentBatch human,StrictFieldHumanService.Confirmed fields)throws Exception{
  active(a);var h=fixed.header();var entries=entries(a,h.projectId(),h.documentRevision());ArrayNode result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
  if(!entries.isEmpty()&&(proofs.isEmpty()||fields==null))stale();
  for(var entry:entries){if(!"REVALIDATE".equals(s(entry,"requested_action")))stale();human.requireCurrent(a);fields.requireCurrent(a);var ids=new ArrayList<String>();
   for(var proof:proofs){if(fixed.body().evidenceLinks().isEmpty()||proof.invalidation()==null)proof.requireValid(a);else proof.requireValidForHumanEvidence(a,human);var check=proof.snapshot().row();if(!"PASS".equals(s(check,"status"))||!h.documentRevision().equals(s(check,"target_revision")))stale();String checkId=s(check,"check_id");
    // Timestamp is only an ordering guard; the unforgeable Verified proof above rechecks real job,
    // receipt, physical findings/coverage, current input, rules, evidence and issue watermarks.
    var fresh=db.queryForList("SELECT c.check_id FROM check_run c JOIN change_revision_entry e ON e.tenant_id=c.tenant_id AND e.project_id=c.project_id AND e.document_revision=c.document_revision WHERE c.tenant_id=? AND c.project_id=? AND c.check_id=? AND e.entry_id=? AND c.created_at>e.created_at AND c.status='PASS' FOR UPDATE",a.context().tenant_id(),h.projectId(),checkId,s(entry,"entry_id"));if(fresh.size()!=1)stale();ids.add(checkId);
   }
   Collections.sort(ids);result.addObject().put("entry_id",s(entry,"entry_id")).put("impact_id",s(entry,"impact_id")).put("impact_sha256",s(entry,"content_sha256")).set("check_ids",json.tree(ids));
  }return result;
 }
 /** Called only after the genuine whole-document CONFIRM batch and immutable B09 receipt exist. */
 void resolveBatch(EnterpriseAccess.Admission a,DocumentReadService.FixedRead fixed,String batch,JsonNode baseline)throws Exception{
  active(a);var h=fixed.header();var receipt=one(db.queryForList("SELECT c.baseline_sha256,c.baseline_json,c.schema_manifest_sha256,c.created_at,c.created_by,c.classification FROM document_review_confirmation_receipt c JOIN review_decision d ON d.tenant_id=c.tenant_id AND d.project_id=c.project_id AND d.batch_id=c.batch_id WHERE c.tenant_id=? AND c.project_id=? AND c.document_revision=? AND c.batch_id=? AND d.target_key='DOCUMENT' AND d.decision='CONFIRM' FOR UPDATE",a.context().tenant_id(),h.projectId(),h.documentRevision(),batch));if(!json.hash(baseline).equals(s(receipt,"baseline_sha256"))||!baseline.equals(json.read(s(receipt,"baseline_json")))||!a.actorId().equals(s(receipt,"created_by")))stale();
  var entries=entries(a,h.projectId(),h.documentRevision());var supplied=new HashMap<String,JsonNode>();for(var pin:baseline.required("impact_revalidation"))if(supplied.put(pin.required("entry_id").asText(),pin)!=null)stale();if(entries.size()!=supplied.size())stale();
  for(var entry:entries){var pin=supplied.get(s(entry,"entry_id"));if(pin==null||!"REVALIDATE".equals(s(entry,"requested_action"))||!s(entry,"impact_id").equals(pin.required("impact_id").asText())||!s(entry,"content_sha256").equals(pin.required("impact_sha256").asText()))stale();
   int count=db.update("INSERT INTO change_impact_resolution(tenant_id,project_id,entry_id,batch_id,document_revision,baseline_sha256,check_ids_json,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",a.context().tenant_id(),h.projectId(),s(entry,"entry_id"),batch,h.documentRevision(),s(receipt,"baseline_sha256"),json.canonical(pin.required("check_ids")),s(receipt,"schema_manifest_sha256"),a.actorId(),receipt.get("created_at"),s(receipt,"classification"));if(count!=1)stale();
  }
 }
 /** B10 additionally consumes B09 Confirmed.requireCurrent for full physical revalidation. */
 public void requireCurrent(EnterpriseAccess.Admission a,String project,String documentRevision)throws Exception{
  active(a);for(var entry:entries(a,project,documentRevision)){if(!"REVALIDATE".equals(s(entry,"requested_action")))stale();var row=one(db.queryForList("SELECT r.*,c.baseline_json,c.baseline_sha256 AS receipt_baseline,c.receipt_json,c.receipt_sha256 FROM change_impact_resolution r JOIN document_review_confirmation_receipt c ON c.tenant_id=r.tenant_id AND c.project_id=r.project_id AND c.batch_id=r.batch_id AND c.document_revision=r.document_revision JOIN review_decision d ON d.tenant_id=r.tenant_id AND d.project_id=r.project_id AND d.batch_id=r.batch_id AND d.document_revision=r.document_revision WHERE r.tenant_id=? AND r.project_id=? AND r.document_revision=? AND r.entry_id=? AND d.target_key='DOCUMENT' AND d.decision='CONFIRM' AND NOT EXISTS(SELECT 1 FROM review_invalidation i WHERE i.tenant_id=d.tenant_id AND i.project_id=d.project_id AND i.review_id=d.review_id) FOR UPDATE",a.context().tenant_id(),project,documentRevision,s(entry,"entry_id")));
   var baseline=json.read(s(row,"baseline_json"));if(!s(row,"baseline_sha256").equals(s(row,"receipt_baseline"))||!s(row,"baseline_sha256").equals(json.hash(baseline))||!s(row,"receipt_sha256").equals(json.hash(json.read(s(row,"receipt_json")))))stale();var pins=new ArrayList<JsonNode>();baseline.required("impact_revalidation").forEach(p->{if(s(entry,"entry_id").equals(p.required("entry_id").asText()))pins.add(p);});if(pins.size()!=1||!pins.get(0).required("check_ids").equals(json.read(s(row,"check_ids_json")))||!s(entry,"content_sha256").equals(pins.get(0).required("impact_sha256").asText()))stale();
  }
 }
 private List<Map<String,Object>> entries(EnterpriseAccess.Admission a,String project,String revision)throws Exception{
  var entries=db.queryForList("SELECT e.*,m.impact_id,m.content_sha256,m.result_ref,m.complete,p.snapshot_sha256,d.decision FROM change_revision_entry e JOIN change_impact_application p ON p.tenant_id=e.tenant_id AND p.decision_id=e.decision_id JOIN change_impact_decision d ON d.tenant_id=e.tenant_id AND d.decision_id=e.decision_id JOIN impact_manifest m ON m.tenant_id=p.tenant_id AND m.impact_id=p.impact_id WHERE e.tenant_id=? AND e.project_id=? AND e.document_revision=? ORDER BY e.entry_id LIMIT 1001 FOR UPDATE",a.context().tenant_id(),project,revision);if(entries.size()>1000)stale();
  var count=db.queryForObject("SELECT COUNT(*) FROM change_revision_entry WHERE tenant_id=? AND project_id=? AND document_revision=?",Long.class,a.context().tenant_id(),project,revision);if(count==null||count!=entries.size())stale();
  for(var entry:entries){if(!"ACCEPT".equals(s(entry,"decision"))||!s(entry,"content_sha256").equals(s(entry,"snapshot_sha256"))||!s(entry,"content_sha256").equals(json.hash(json.read(s(entry,"result_ref")))))stale();var snapshot=json.read(s(entry,"result_ref"));if(!snapshot.required("complete").asBoolean())stale();}
  return entries;
 }
 private static void active(EnterpriseAccess.Admission a){if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()||!TenantScope.requireCurrent().tenantId().equals(a.context().tenant_id()))stale();}
 private static Map<String,Object> one(List<Map<String,Object>> rows){if(rows.size()!=1)stale();return rows.get(0);}private static String s(Map<String,Object> row,String key){return Objects.requireNonNull(row.get(key)).toString();}private static void stale(){throw new TenantAccessException(ErrorCode.REVIEW_STALE);}
}
