package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.workflow.WorkflowProtocol.Reference;
import com.ruoyi.medical.workflow.TaskCandidateAssemblyStageProtocolV1.CommandV1;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.ProfessionalCheckService.*;
/** N06 BX-06 v1.1 consumer. Structural readiness is explicitly not professional coverage. */
public final class CandidateSetCheckReadService {
 private final EnterpriseAccess access;private final JdbcTemplate db;private final ResourceReadAuthority resources;private final TaskCandidateAssemblyStageJournal stages;private final TaskCandidateAssemblyOriginalReader originals;private final DocumentReadService documents;
 private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();private final JsonMapper codec=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 public CandidateSetCheckReadService(DataSource data,EnterpriseAccess access,ResourceReadAuthority resources,TaskCandidateAssemblyStageJournal stages,TaskCandidateAssemblyOriginalReader originals,DocumentReadService documents){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.access=access;this.resources=resources;this.stages=stages;this.originals=originals;this.documents=documents;}
 private record Located(Reference reference,CommandV1 command,String document,String revision){}
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String check)throws Exception{
  WireValues.uuid(check);var located=access.execute(subject,a->ThrowingSupplier.of(()->locate(a,context,check)).get());var proof=documents.readFixed(subject,context,located.document(),located.revision());
  return originals.readHuman(subject,context,located.reference(),located.command(),(original,family)->{
   family.requireUnchanged();stages.recovered(original,family);same(check,family.checkId());var a=original.admission();documents.requireCurrent(a,proof);var row=one(db.queryForList("SELECT * FROM check_run WHERE tenant_id=? AND project_id=? AND check_id=? AND target_kind='CANDIDATE_SET' LIMIT 2 FOR UPDATE",original.tenantId(),original.projectId(),check));same(family.checkReceipt(),json.read(str(row,"result_ref")));var report=family.artifact("CROSS_FILE_CHECK_REPORT");
   String invalid=row.get("invalidation_reason")==null?null:str(row,"invalidation_reason");if(!proof.header().currentDocumentRevision().equals(proof.header().documentRevision()))invalid="BASE_DOCUMENT_CHANGED";
   if(!proof.header().planRevision().equals(text(original.input(),"plan_revision"))||!proof.header().snapshotId().equals(text(original.input(),"snapshot_id")))invalid="INPUT_BASELINE_CHANGED";
   var liveTask=one(db.queryForList("SELECT h.current_revision,r.task_revision FROM task_head h JOIN task_run r ON r.tenant_id=h.tenant_id AND r.project_id=h.project_id AND r.task_id=h.task_id WHERE h.tenant_id=? AND h.project_id=? AND h.task_id=? AND r.run_id=? LIMIT 2 FOR UPDATE",original.tenantId(),original.projectId(),original.taskId(),original.runId()));if(!str(liveTask,"current_revision").equals(str(liveTask,"task_revision")))invalid="TASK_REVISION_CHANGED";
   for(var rule:json.read(str(row,"rule_manifest")).required("rules")){String id=text(rule,"rule_id");resources.require(a,new PermissionProtocol.Scope(original.tenantId(),null,"RULE",id),"rule.read","DOCUMENT_BODY");var actual=one(db.queryForList("SELECT status,content_sha256 FROM rule_version WHERE tenant_id=? AND rule_version=? LIMIT 2 FOR UPDATE",original.tenantId(),id));if(!"APPROVED".equals(str(actual,"status"))||!text(rule,"sha256").equals(str(actual,"content_sha256")))invalid="RULE_MANIFEST_CHANGED";}
   for(var pin:original.input().required("candidates")){
    var c=one(db.queryForList("SELECT content_artifact_id,status FROM candidate WHERE tenant_id=? AND project_id=? AND candidate_id=? LIMIT 2 FOR UPDATE",original.tenantId(),original.projectId(),text(pin,"candidate_id")));if(!text(pin,"body_artifact_id").equals(str(c,"content_artifact_id"))||!"READY".equals(str(c,"status")))invalid="CANDIDATE_BASELINE_CHANGED";
    var upstream=one(db.queryForList("SELECT input_watermarks,invalidation_reason FROM check_run WHERE tenant_id=? AND project_id=? AND check_id=? LIMIT 2 FOR UPDATE",original.tenantId(),original.projectId(),text(pin,"check_id")));if(upstream.get("invalidation_reason")!=null)invalid="UPSTREAM_CHECK_INVALIDATED";var marks=json.read(str(upstream,"input_watermarks"));if(!marks.required("issue_watermark").asText().equals(proof.watermarks().issueWatermark().toString())||!marks.required("evidence_watermark").asText().equals(proof.watermarks().evidenceWatermark().toString()))invalid="UPSTREAM_WATERMARK_CHANGED";
   }
   String range=ObjectContentPaging.rangeDigest(ObjectContentPaging.objectDigest(report.reference()),0,Long.parseLong(report.reference().sizeBytes()));var coverage=new CommonProtocol.Coverage(range,range,true,null);
   var n=json.tree(Map.of("check_id",check,"target_kind","CANDIDATE_SET","target_id",original.runId(),"target_revision",report.artifactId(),"input_sha256",original.inputSha256(),"rule_manifest_id",str(row,"rule_manifest_id"),"status",str(row,"status"),"coverage",coverage,"findings",new CommonProtocol.ArtifactRef(report.artifactId(),report.reference().sha256(),TaskCandidateAssemblyObjectLedger.REF,coverage)));
   var o=(com.fasterxml.jackson.databind.node.ObjectNode)n;o.set("issue_ids",json.tree(List.of()));o.put("evidence_watermark",str(row,"evidence_watermark")).put("issue_watermark",str(row,"issue_watermark")).put("check_kind","CROSS_FILE_STRUCTURE").put("professional_coverage_complete",false).put("professional_approval",false);if(invalid==null)o.putNull("invalidation_reason");else o.put("invalidation_reason",invalid);family.requireUnchanged();return o;
  });
 }
 private Located locate(EnterpriseAccess.Admission a,String context,String check)throws Exception{
  context(a,context);var row=one(db.queryForList("SELECT c.*,r.task_id FROM check_run c JOIN task_run r ON r.tenant_id=c.tenant_id AND r.project_id=c.project_id AND r.run_id=c.run_id WHERE c.tenant_id=? AND c.check_id=? AND c.target_kind='CANDIDATE_SET' LIMIT 2 FOR UPDATE",a.context().tenant_id(),check));resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),str(row,"project_id"),"TASK",str(row,"task_id")),"task.read","DOCUMENT_BODY");
  var launch=one(db.queryForList("SELECT reference_json,reference_sha256,intent_json,intent_sha256 FROM task_run_workflow_launch WHERE tenant_id=? AND project_id=? AND run_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),str(row,"project_id"),str(row,"run_id")));var reference=json.read(str(launch,"reference_json"));same(json.hash(reference),str(launch,"reference_sha256"));var intent=json.read(str(launch,"intent_json"));same(json.hash(intent),str(launch,"intent_sha256"));same(reference,intent.required("reference"));var ref=codec.treeToValue(reference,Reference.class);same(str(row,"run_id"),ref.runId());var original=originals.humanOriginal(a,ref);var cmd=original.command();stages.committed(original).orElseThrow(()->new TenantAccessException(ErrorCode.STEP_NOT_READY));return new Located(ref,cmd,text(original.input(),"document_id"),text(original.input(),"base_document_revision"));
 }
}
