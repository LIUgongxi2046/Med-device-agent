package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.io.IOException;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.ruoyi.medical.persistence.FormalEvaluationExecutionProtocol.*;

/** Current original-definition admission. This proof grants neither model.send nor source.external.process. */
public final class FormalEvaluationExecutionAdmission {
 private final EvaluationService evaluations;private final FormalAgentCandidateAuthority candidates;
 private final AgentExecutionPolicyAuthority policies;private final EvaluationReferences references;
 private final SourceReadAuthority sources;private final SourceReadMapper sourceRows;private final RunCreateBudget budgets;
 private final PermissionSchema json;private final JdbcTemplate db;
 public FormalEvaluationExecutionAdmission(EvaluationService evaluations,FormalAgentCandidateAuthority candidates,
   AgentExecutionPolicyAuthority policies,EvaluationReferences references,SourceReadAuthority sources,
   SourceReadMapper sourceRows,RunCreateBudget budgets,PermissionSchema json,DataSource data){
  this(evaluations,candidates,policies,references,sources,sourceRows,budgets,json,new JdbcTemplate(data));
 }
 FormalEvaluationExecutionAdmission(EvaluationService evaluations,FormalAgentCandidateAuthority candidates,AgentExecutionPolicyAuthority policies,EvaluationReferences references,SourceReadAuthority sources,SourceReadMapper sourceRows,RunCreateBudget budgets,PermissionSchema json,JdbcTemplate db){
  this.evaluations=evaluations;this.candidates=candidates;this.policies=policies;this.references=references;
  this.sources=sources;this.sourceRows=sourceRows;this.budgets=budgets;this.json=json;this.db=db;db.setQueryTimeout(5);
 }
 public Prepared require(EnterpriseAccess.Admission a,String context,Start request)throws IOException {
  current(a);if(!a.context().context_revision().equals(context))fail(ErrorCode.CONTEXT_CHANGED);
  var evaluation=evaluations.requireExecutionDefinition(a,request.evaluationId());
  evaluation.requireConsumer(a,request.evaluationId(),request.definitionSha256());
  if(!evaluation.row().revision().toString().equals(request.expectedEvaluationRevision()))fail(ErrorCode.REVISION_CONFLICT);
  var d=evaluation.definition();String project=evaluation.row().projectId();
  if(!d.request().candidates().contains(request.candidate()))fail(ErrorCode.CONTENT_MISMATCH);
  var candidate=candidates.require(a,request.candidate(),project,"EVALUATION_RESULTS");
  if("DISABLED".equals(candidate.status())||!candidate.executionPolicy().equals(request.executionPolicy()))fail(ErrorCode.ACCESS_DENIED);
  var policy=policies.requireByVersion(a,request.executionPolicy().version_id(),request.executionPolicy().content_sha256(),project,candidate.classification(),"RUN_CREATION");
  if("SIMULATION".equals(policy.definition().environment()))fail(ErrorCode.ACCESS_DENIED);
  String runtimeHash=candidate.runtimeManifestSha256();
  var matrix=matrix(d,request,runtimeHash,policy.definition().bindings(),json);
  var inputRefs=new ArrayList<PermissionProtocol.Basis>();for(var s:d.request().samples()){inputRefs.add(s.input_ref());inputRefs.add(s.expected_ref());}
  references.require(a,project,inputRefs,"EVALUATION_RESULTS");
  // Review the exact selected-source purposes independently. This is NOT a FORMAL or MODEL_SEND content proof.
  var sourceAuth=json.object();var selected=policy.definition().sourceSelection().sources();
  var purposes=new TreeSet<String>();purposes.add(policy.businessPurpose());
  for(var node:policy.definition().bindings())for(var action:node.actions())if("SELECTED_SOURCE".equals(action.targetSelector()))purposes.add(action.purpose());
  for(var source:selected){var rows=sourceRows.source(source.version_id());if(rows.size()!=1)fail(ErrorCode.CONTENT_MISMATCH);var row=rows.get(0);
   if(!source.content_sha256().equals(row.registeredSha256()))fail(ErrorCode.CONTENT_MISMATCH);
   var perSource=json.object();for(String purpose:purposes){var checked=sources.require(a,new SourceReadProtocol.ReadRequirement(source.version_id(),row.rightsRevision(),project,null,null,null,SourceReadProtocol.Use.REVIEW,purpose,policy.definition().environment(),policy.definition().region(),null));perSource.set(purpose,AgentPublicationWitness.tree(checked));}sourceAuth.set(source.version_id(),perSource);
  }
  var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
  var budgetAuth=json.object().putArray("rows");String currency=null;Set<String> accounts=new HashSet<>();int index=0;
  for(var ref:List.of(request.enterpriseBudget(),request.parentBudget())){
   String actualCurrency=one(db.query("SELECT currency FROM budget_revision WHERE tenant_id=? AND budget_revision=? LIMIT 2",(r,n)->r.getString(1),a.context().tenant_id(),ref.version_id()));
   if(currency!=null&&!currency.equals(actualCurrency))fail(ErrorCode.MODEL_POLICY_INCOMPLETE);currency=actualCurrency;
   var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("projectId",project);values.put("budgetRevision",ref.version_id());
   var original=budgets.require(a,values,now,currency);if(!ref.content_sha256().equals(original.required("content_sha256").asText())||index++==0&&!original.required("project_id").isNull()||!accounts.add(original.required("budget_id").asText()))fail(ErrorCode.CONTENT_MISMATCH);budgetAuth.add(original);
  }
  var auth=json.object();auth.set("evaluation_run",AgentPublicationWitness.tree(evaluation.runGrant()));auth.set("candidate",candidate.authorizations());auth.set("execution_policy",policy.authorizations());auth.set("selected_source_review",sourceAuth);auth.set("budgets",budgetAuth);
  var snapshot=json.object();snapshot.set("request",json.tree(request));snapshot.set("definition",json.tree(d));snapshot.set("nodes",json.tree(matrix));snapshot.set("authorizations",auth);
  evaluation.requireUnchanged();candidate.requireUnchanged();policy.requireUnchanged();
  return new Prepared(this,a,context,request,project,candidate.classification(),d,policy.definition(),matrix,auth,json.canonical(snapshot));
 }
 static List<Node> matrix(EvaluationProtocol.Definition d,Start request,String runtime,List<AgentExecutionPolicyProtocol.NodeBinding> bindings,PermissionSchema json)throws IOException {
  if(!d.evaluation_id().equals(request.evaluationId())||!d.request().candidates().contains(request.candidate())||d.request().samples().isEmpty()||bindings.isEmpty())fail(ErrorCode.CONTENT_MISMATCH);
  int repeats=d.request().variance_policy().repeat_count();long size=(long)d.request().samples().size()*repeats*bindings.size();
  if(repeats<1||repeats>100||size<1||size>10000)fail(ErrorCode.CONTEXT_LIMIT);
  var samples=new HashSet<String>();var kinds=new HashSet<String>();for(var n:bindings)if(!kinds.add(n.nodeKind()))fail(ErrorCode.CONTENT_MISMATCH);
  var result=new ArrayList<Node>();for(var sample:d.request().samples()){
   if(!"REAL".equals(sample.evidence_class())||!samples.add(sample.sample_id()))fail(ErrorCode.CONTENT_MISMATCH);
   for(int repeat=0;repeat<repeats;repeat++){
    var cell=new Cell(d.evaluation_id(),request.definitionSha256(),request.candidate(),request.executionPolicy(),sample.sample_id(),sample.source_family(),sample.split(),repeat,sample.input_ref(),sample.expected_ref(),runtime);
    for(var node:bindings)result.add(new Node(cell,node.nodeKind(),node.roleVersion(),node.modelVersion(),node.tokenizerVersion(),org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(node.generationParameters()))));
   }
  }return List.copyOf(result);
 }
 public static final class Prepared {
  private final FormalEvaluationExecutionAdmission owner;private final EnterpriseAccess.Admission admission;
  private final String context,project,classification,canonical;private final Start request;private final EvaluationProtocol.Definition definition;
  private final TenantScope originalScope;private final Thread originalThread;
  private final AgentExecutionPolicyProtocol.Definition policy;private final List<Node> nodes;private final JsonNode authorizations;
  private Prepared(FormalEvaluationExecutionAdmission owner,EnterpriseAccess.Admission a,String context,Start request,String project,String classification,EvaluationProtocol.Definition definition,AgentExecutionPolicyProtocol.Definition policy,List<Node> nodes,JsonNode auth,String canonical){this.owner=owner;admission=a;originalScope=TenantScope.requireCurrent();originalThread=Thread.currentThread();this.context=context;this.request=request;this.project=project;this.classification=classification;this.definition=definition;this.policy=policy;this.nodes=List.copyOf(nodes);authorizations=auth.deepCopy();this.canonical=canonical;}
  public Start request(){return request;}public String projectId(){return project;}public String classification(){return classification;}public List<Node> nodes(){return nodes;}public EvaluationProtocol.Definition definition(){return copyDefinition(definition);}public AgentExecutionPolicyProtocol.Definition policy(){return policy;}public JsonNode authorizations(){return authorizations.deepCopy();}
  String canonical(){return canonical;}
  public void requireConsumer(EnterpriseAccess.Admission a,Start expected)throws IOException{if(a!=admission||!request.equals(expected))fail(ErrorCode.ACCESS_DENIED);requireUnchanged();}
  public void requireUnchanged()throws IOException{if(Thread.currentThread()!=originalThread||TenantScope.requireCurrent()!=originalScope)fail(ErrorCode.ACCESS_DENIED);if(!canonical.equals(owner.require(admission,context,request).canonical))fail(ErrorCode.SCOPE_STALE);}
 }
 private static EvaluationProtocol.Definition copyDefinition(EvaluationProtocol.Definition d){var c=d.candidate_snapshots().stream().map(x->new EvaluationProtocol.Candidate(x.version_id(),x.content_sha256(),x.agent_id(),x.created_by(),x.dependency_manifest().deepCopy(),x.limits().deepCopy(),x.schema_version(),x.schema_manifest_sha256())).toList();return new EvaluationProtocol.Definition(d.schema_version(),d.tenant_id(),d.evaluation_id(),d.request(),c,d.approved_expectation_samples(),d.authority_grant_id(),d.authority_revision(),d.created_by(),d.created_at());}
 static void current(EnterpriseAccess.Admission a){var s=TenantScope.requireCurrent();if(!TransactionSynchronizationManager.isActualTransactionActive()||a.simulationWorker()||!s.tenantId().equals(a.context().tenant_id())||!s.subjectId().equals(a.actorId()))fail(ErrorCode.ACCESS_DENIED);}
 static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)fail(ErrorCode.CONTENT_MISMATCH);return rows.get(0);}
 static void fail(ErrorCode code){throw new TenantAccessException(code);}
}
