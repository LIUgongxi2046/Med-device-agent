package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.ProfessionalCheckService.*;

/** Actual saved-body/N07/N03 authority. A receipt, UUID or human selection cannot mint PASS. */
public final class StrictFieldHumanBasis {
 private final EnterpriseAccess access; private final JdbcTemplate db; private final DocumentReadService documents;
 private final ProfessionalCheckService checks; private final TaskStrictFieldOutputLedger outputs;
 private final StrictComparisonPolicyService policies; private final TemplateApplicabilityService templates;
 private final StrictDecimalEvidenceService decimals;private final TaskStrictFieldTextEvidence texts;private final TaskStrictFieldTypedTargetService typedTargets;private final TaskStrictFieldSchema strictJson;private final TaskStrictFieldContextService contexts;private final ResourceReadAuthority resources; private final SourceUploadTransport transport; private final DocumentUploadPolicy limits;
 private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
 public StrictFieldHumanBasis(DataSource data,EnterpriseAccess access,DocumentReadService documents,ProfessionalCheckService checks,TaskStrictFieldOutputLedger outputs,StrictComparisonPolicyService policies,TemplateApplicabilityService templates,ResourceReadAuthority resources,SourceUploadTransport transport,DocumentUploadPolicy limits,TaskStrictFieldSchema strictJson,TaskStrictFieldContextService contexts,TaskStrictFieldTypedTargetService typedTargets,StrictDecimalEvidenceService decimals,TaskStrictFieldTextEvidence texts){this.decimals=decimals;this.texts=texts;this.typedTargets=typedTargets;this.strictJson=strictJson;this.contexts=contexts;this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.access=access;this.documents=documents;this.checks=checks;this.outputs=outputs;this.policies=policies;this.templates=templates;this.resources=resources;this.transport=transport;this.limits=limits;}
 record Target(String document,String revision,String field,String row,String check,String artifact){Target{for(String v:List.of(document,revision,field,check))WireValues.uuid(v);if(row!=null)WireValues.uuid(row);if(artifact!=null)WireValues.uuid(artifact);}}
 record State(JsonNode witness,ArtifactProtocol.FieldCheckResult comparison,TemplateApplicabilityProtocol.ConditionResult applicability){}
 final class Verified {
  final Target target;final ProfessionalCheckService.Verified check;final DocumentReadService.FixedRead document;final State initial;
  private Verified(Target target,ProfessionalCheckService.Verified check,DocumentReadService.FixedRead document,State initial){this.target=target;this.check=check;this.document=document;this.initial=initial;}
  State current(EnterpriseAccess.Admission a,String context)throws Exception{check.requireCurrent(a);var current=inspect(a,context,target,document,check);same(initial.witness(),current.witness());return current;}
 }
 Verified prepare(ApplicationSessionIdentity.Verified subject,String context,Target target)throws Exception {
  var document=documents.readFixed(subject,context,target.document(),target.revision());var check=checks.verified(subject,context,target.check());
  var state=tx(subject,a->inspect(a,context,target,document,check));
  if(target.artifact()!=null){var ref=tx(subject,a->{inspect(a,context,target,document,check);return original(a,document,target.artifact()).reference();});
   S3ObjectReader.Admission guard=()->tx(subject,a->{same(state.witness(),inspect(a,context,target,document,check).witness());return null;});
   byte[] bytes=transport.readDocument(subject,document.header().tenantId(),limits.maximumBytes().intValueExact(),guard,(reader,current)->{long count=Long.parseLong(ref.sizeBytes());if(count<1||count>limits.maximumBytes().longValueExact())throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);try(var object=reader.readVerified(ref,current)){byte[] value=new byte[(int)count];for(int start=0;start<value.length;){int n=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,value.length-start);byte[] part=object.chunk(start,n);if(part.length!=n)bad();System.arraycopy(part,0,value,start,n);start+=n;current.requireCurrent();}return value;}});
   same(ref.sha256(),WireValues.sha256(bytes));same(json.tree(state.comparison()),json.read(bytes));
  }
  return tx(subject,a->{same(state.witness(),inspect(a,context,target,document,check).witness());return new Verified(target,check,document,state);});
 }
 private State inspect(EnterpriseAccess.Admission a,String context,Target t,DocumentReadService.FixedRead document,ProfessionalCheckService.Verified check)throws Exception {
  StrictComparisonAuthority.human(a,context);documents.requireCurrent(a,document);check.requireCurrent(a);var h=document.header();same(t.document(),check.snapshot().document().header().documentId());same(t.revision(),check.findings().targetRevision());same(h.documentRevision(),h.currentDocumentRevision());
  var grant=resources.require(a,new PermissionProtocol.Scope(h.tenantId(),h.projectId(),"DOCUMENT",h.documentId()),"field.confirm","DOCUMENT_BODY");
  var field=one(document.body().fields().stream().filter(f->f.fieldId().equals(t.field())).toList());var node=one(document.body().nodes().stream().filter(n->n.nodeId().equals(field.nodeId())).toList());
  var physical=one(db.queryForList("SELECT * FROM field_value WHERE tenant_id=? AND project_id=? AND document_revision=? AND field_id=? AND row_key=? FOR UPDATE",h.tenantId(),h.projectId(),t.revision(),t.field(),t.row()==null?"SCALAR":t.row()));same(json.tree(field),json.read(str(physical,"value_json")));same(field.nodeId(),str(physical,"node_id"));same(t.row(),physical.get("row_id"));
  if(t.row()!=null){if(!(node.content() instanceof InterfaceProtocol.TableContent table)||!table.rowIds().contains(t.row()))bad();}
  var template=one(db.queryForList("SELECT document_type FROM template_version WHERE tenant_id=? AND template_version=? FOR UPDATE",h.tenantId(),h.templateVersion()));
  var app=templates.inspectTarget(a,h.projectId(),h.profileRevision(),str(template,"document_type"),h.templateVersion(),"DOCUMENT_BODY");
  var condition=one(app.view().machine_conditions().stream().filter(c->"FIELD".equals(c.scope())&&field.fieldDefinitionId().equals(c.target_id())).toList());
  ArtifactProtocol.FieldCheckResult comparison=null;JsonNode output=json.tree(null);var governance=new ArrayList<JsonNode>();
  if(t.artifact()!=null){var original=original(a,document,t.artifact());comparison=original.content();output=original.receipt();
   var set=one(db.queryForList("SELECT manifest_json FROM task_strict_field_rule_set WHERE tenant_id=? AND project_id=? AND revision_id=? FOR UPDATE",h.tenantId(),h.projectId(),comparison.ruleRevision()));
   for(var p:json.read(str(set,"manifest_json")).required("policies")){var current=policies.requireApproved(a,h.projectId(),text(p,"revision"),text(p,"digest"));same(text(p,"decision_id"),current.decisionId());current.requireUnchanged();governance.add(json.tree(Map.of("policy",current.revisionId(),"decision",current.decisionId(),"digest",current.digest())));}
   var entry=one(comparison.entries().stream().filter(e->e.fieldId().equals(t.field())&&Objects.equals(e.rowId(),t.row())).toList());
   same(field.originalValue(),entry.candidateValue().rawText());same(field.decimalValue(),entry.candidateValue().decimalValue());same(field.unit(),entry.candidateValue().unit());
   var inputRow=one(db.queryForList("SELECT canonical_input FROM task_strict_field_input WHERE tenant_id=? AND project_id=? AND input_id=? FOR UPDATE",h.tenantId(),h.projectId(),text(original.receipt(),"input_id")));
   var input=strictJson.input(str(inputRow,"canonical_input").getBytes(java.nio.charset.StandardCharsets.UTF_8));same(text(original.receipt(),"input_sha256"),strictJson.hash(input));same(input.targetArtifactId(),comparison.targetArtifactId());same(input.planRevision(),h.planRevision());same(input.snapshotId(),h.snapshotId());same(input.taskId(),h.taskId());
   if(!TaskStrictFieldTypedProtocol.TARGET_REF.equals(input.targetManifest().path("schema_ref").asText()))throw new TenantAccessException(ErrorCode.METHOD_COVERAGE_INCOMPLETE);
   var targetVersion=one(db.queryForList("SELECT content_sha256 FROM task_strict_field_typed_revision WHERE tenant_id=? AND project_id=? AND revision_id=? FOR UPDATE",h.tenantId(),h.projectId(),text(input.targetManifest(),"target_revision")));
   var approvedTarget=typedTargets.requireHuman(a,h.projectId(),text(input.targetManifest(),"target_revision"),str(targetVersion,"content_sha256"));same(approvedTarget.manifest(),input.targetManifest());governance.add(json.tree(Map.of("typed_target",approvedTarget.receipt(),"decision",approvedTarget.decision())));
   var author=strictJson.bind(input.targetManifest().required("request"),TaskStrictFieldTypedProtocol.Author.class);same(author.scope().taskRevision(),h.taskRevision());same(author.scope().templateVersion(),h.templateVersion());
   var binding=one(author.entries().stream().filter(e->e.fieldId().equals(t.field())&&Objects.equals(e.rowId(),t.row())).toList());same(binding.nodeId(),field.nodeId());same(binding.fieldDefinitionId(),field.fieldDefinitionId());
   var candidate=contexts.requireCurrent(a,h.projectId(),binding.candidateContext().version_id(),binding.candidateContext().content_sha256());candidate.requireApproved();same(candidate.models().ids(),entry.modelIds());same(candidate.conditions().ids(),entry.conditionIds());requireLiteral(candidate.models(),field.model());requireLiteral(candidate.conditions(),field.condition());
   var observed=contexts.requireCurrent(a,h.projectId(),binding.observedContext().version_id(),binding.observedContext().content_sha256());observed.requireApproved();
   if(binding.observedDecimal()!=null){var source=decimals.requireCurrent(a,h.projectId(),binding.observedDecimal().version_id(),binding.observedDecimal().content_sha256());same(source.value(),entry.sourceValue());source.requireUnchanged();}
   else{var source=texts.require(a,h.projectId(),observed,binding.observedExact());same(source.value(),entry.sourceValue());source.requireUnchanged();}
   var currentTarget=strictJson.target(input);var exactEntry=one(currentTarget.entries().stream().filter(e->e.fieldId().equals(t.field())&&Objects.equals(e.rowId(),t.row())).toList());same(exactEntry.candidateValue(),entry.candidateValue());same(binding.comparisonPolicy().version_id(),entry.comparisonRuleId());
   for(var requirement:author.requirements())if(requirement.kind()==TaskStrictFieldTypedProtocol.RequirementKind.METHOD)for(var indicator:requirement.indicators())if(indicator.fieldDefinitionId().equals(field.fieldDefinitionId())){
    if(indicator.methods().isEmpty())throw new TenantAccessException(ErrorCode.METHOD_COVERAGE_INCOMPLETE);
    for(var method:indicator.methods()){var source=contexts.requireCurrent(a,h.projectId(),method.sourceContext().version_id(),method.sourceContext().content_sha256());source.requireApproved();}
    for(String model:entry.modelIds())for(String conditionId:entry.conditionIds()){var coverage=one(comparison.methodCoverage().stream().filter(m->m.indicatorId().equals(field.fieldDefinitionId())&&m.modelId().equals(model)&&m.conditionId().equals(conditionId)).toList());if(!new HashSet<>(coverage.methodIds()).equals(indicator.methods().stream().map(TaskStrictFieldTypedProtocol.MethodEvidence::methodId).collect(java.util.stream.Collectors.toSet())))throw new TenantAccessException(ErrorCode.METHOD_COVERAGE_INCOMPLETE);}
   }
   governance.add(json.tree(Map.of("fixed_input_sha256",strictJson.hash(input),"binding",binding,"candidate_context",candidate.receipt(),"observed_context",observed.receipt())));
  }
  var w=json.tree(Map.of("document_sha256",h.bodySha256(),"document_revision",h.documentRevision(),"field",field,"node",node,"check",check.snapshot().receipt(),"applicability",condition,"strict_output",output,"governance",governance));
  return new State(w,comparison,condition);
 }
 private TaskStrictFieldOutputLedger.Registered original(EnterpriseAccess.Admission a,DocumentReadService.FixedRead document,String artifact)throws Exception {
  var h=document.header();if(document.dependencies().members().stream().noneMatch(m->artifact.equals(m.upstreamVersionId())))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
  resources.require(a,new PermissionProtocol.Scope(h.tenantId(),h.projectId(),"ARTIFACT",artifact),"artifact.read","DOCUMENT_BODY");
  var r=one(db.queryForList("SELECT receipt_json FROM task_strict_field_output_registration WHERE tenant_id=? AND project_id=? AND task_id=? AND artifact_id=? FOR UPDATE",h.tenantId(),h.projectId(),h.taskId(),artifact));var receipt=json.read(str(r,"receipt_json"));var original=outputs.original(a,receipt.required("result_receipt"));same(receipt,original.receipt());return original;
 }
 static void requireDecision(Verified basis,State state,String decision){
  if("REQUEST_EVIDENCE".equals(decision))return;
  String applicability=state.applicability().status();if("UNKNOWN".equals(applicability))throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
  if("NOT_APPLICABLE".equals(decision)){if(state.comparison()!=null&&state.comparison().entries().stream().filter(e->e.fieldId().equals(basis.target.field())&&Objects.equals(e.rowId(),basis.target.row())).anyMatch(e->e.result()!=ArtifactProtocol.ComparisonResult.NOT_APPLICABLE))throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);if(!"NOT_APPLICABLE".equals(applicability)||state.applicability().rule_revision()==null)throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
   var field=one(basis.document.body().fields().stream().filter(f->f.fieldId().equals(basis.target.field())).toList());var rules=basis.check.findings().ruleChecks().stream().filter(r->r.targetNodeIds().contains(field.nodeId())).toList();if(rules.isEmpty()||rules.stream().anyMatch(r->r.result()!=ArtifactProtocol.RuleResult.NOT_APPLICABLE))throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);return;}
  if(!"APPLICABLE".equals(applicability)||state.comparison()==null)throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
  var comparison=state.comparison();if(!comparison.missingRequirementIds().isEmpty()||comparison.methodCoverage().stream().anyMatch(m->m.result()!=ArtifactProtocol.CoverageResult.COMPLETE||m.methodIds().isEmpty()||m.evidenceRefs().isEmpty()))throw new TenantAccessException(ErrorCode.METHOD_COVERAGE_INCOMPLETE);
  var entry=one(comparison.entries().stream().filter(e->e.fieldId().equals(basis.target.field())&&Objects.equals(e.rowId(),basis.target.row())).toList());if(entry.result()!=ArtifactProtocol.ComparisonResult.MATCH||entry.evidenceRefs().isEmpty())throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
  var field=one(basis.document.body().fields().stream().filter(f->f.fieldId().equals(basis.target.field())).toList());String claim=UUID.nameUUIDFromBytes(("N07/"+basis.target.revision()+"/"+field.fieldId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
  var checked=one(basis.check.findings().claimChecks().stream().filter(c->c.claimId().equals(claim)).toList());if(checked.supportCheck()!=ArtifactProtocol.SupportCheck.SUPPORTED||!"ALLOWED".equals(checked.accessCheck().name())||!"LOCATED".equals(checked.locatorCheck().name())||checked.readReceiptIds().isEmpty())throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
  var applicable=basis.check.findings().ruleChecks().stream().filter(r->r.targetNodeIds().contains(field.nodeId())).toList();if(applicable.isEmpty()||applicable.stream().anyMatch(r->r.result()!=ArtifactProtocol.RuleResult.PASS))throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
 }
 private static void requireLiteral(TaskStrictFieldTypedProtocol.Context context,String literal){
  if(context.mode()==TaskStrictFieldTypedProtocol.Mode.GLOBAL){if(!Objects.equals(literal,context.globalLiteral()))throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);}
  else if(context.labels().size()!=1||!Objects.equals(context.labels().get(0).literal(),literal))throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
 }
 private <T>T tx(ApplicationSessionIdentity.Verified s,Work<T> w){return access.execute(s,a->ThrowingSupplier.of(()->w.run(a)).get());}private interface Work<T>{T run(EnterpriseAccess.Admission a)throws Exception;}
}
