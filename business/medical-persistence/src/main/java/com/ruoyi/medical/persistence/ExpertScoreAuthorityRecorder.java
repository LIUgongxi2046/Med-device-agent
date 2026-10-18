package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.ExpertQualificationMapper;
import com.ruoyi.medical.persistence.ExpertQualificationProtocol.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import static com.ruoyi.medical.persistence.ExpertQualificationStore.*;
/** Captures only the private proof emitted synchronously by the original real score write. */
@Component @ConditionalOnProperty(prefix="medical.expert-qualification",name="enabled",havingValue="true")
public final class ExpertScoreAuthorityRecorder implements EvaluationService.OriginalScoreWitnessSink {
 private final ExpertQualificationMapper mapper;private final ExpertQualificationSchema schema;private final EvaluationSchema evaluation;private final PermissionSchema permissions;
 public ExpertScoreAuthorityRecorder(ExpertQualificationMapper mapper,ExpertQualificationSchema schema,EvaluationSchema evaluation,PermissionSchema permissions){this.mapper=mapper;this.schema=schema;this.evaluation=evaluation;this.permissions=permissions;}
 @Override public void capture(EvaluationService.CreatedScoreWitness proof)throws IOException{
  var a=proof.admission();if(a.simulationWorker()||EvaluationDependencyUse.forDefinition(proof.definition().request())!=EvaluationDependencyUse.RESULTS)return;
  proof.requireCurrent();var score=proof.score();var definition=proof.definition();same(a.actorId(),score.scorer_id());var grant=proof.grant();same(score.authority_grant_id(),grant.grantId());same(score.authority_revision(),grant.revision().toString());
  var now=LocalDateTime.ofInstant(Instant.parse(score.created_at()),ZoneOffset.UTC);var scope=new PermissionProtocol.Scope(a.context().tenant_id(),definition.request().project_id(),"EVALUATION",score.evaluation_id());
  if(new PermissionAuthority(permissions).findResource(scope,score.scorer_id(),proof.identityMode(),"evaluation.score","EVALUATION_RESULTS",List.of(grant),now).isEmpty())bad();
  var receipt=proof.originalReceipt();evaluation.receipt(schema.canonical(receipt));same(score.score_id(),receipt.path("data").path("score_id").asText());same(score.evaluation_id(),receipt.path("data").path("evaluation_id").asText());
  var content=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("identity_mode",proof.identityMode()).put("purpose","EVALUATION_RESULTS");content.set("score",schema.tree(score));content.set("grant",schema.tree(grant));content.set("scope",schema.tree(scope));content.set("original_receipt",receipt);
  var v=values(a);v.put("scoreId",score.score_id());if(!mapper.scoreAuthority(v).isEmpty())bad();String classification=one(mapper.scoreClassification(v));var cell=score.observation().cell();
  var row=new ScoreAuthorityWitness(a.context().tenant_id(),definition.request().project_id(),score.score_id(),score.evaluation_id(),cell.sample_id(),cell.agent_version_id(),cell.dimension(),cell.repeat_index(),evaluation.hash(score),score.definition_sha256(),receipt.path("data").path("operation_id").asText(),schema.canonical(content),schema.hash(content),UUID.randomUUID().toString(),UUID.randomUUID().toString(),"1.0.0",schema.manifest(),a.actorId(),now,classification);
  v.put("witness",row);v.put("payload",schema.canonical(schema.tree(row)));count(mapper.saveScoreAuthorityAudit(v));count(mapper.saveScoreAuthorityEvent(v));count(mapper.insertScoreAuthority(v));same(row,one(mapper.scoreAuthority(v)));verify(a,row,score);proof.requireCurrent();
 }
 void verify(EnterpriseAccess.Admission a,ScoreAuthorityWitness row,EvaluationProtocol.Score score)throws IOException{
  same(a.context().tenant_id(),row.tenantId());same("1.0.0",row.schemaVersion());same(schema.manifest(),row.schemaManifest());same(score.score_id(),row.scoreId());same(score.evaluation_id(),row.evaluationId());same(score.definition_sha256(),row.definitionHash());same(evaluation.hash(score),row.scoreHash());same(score.scorer_id(),row.createdBy());same(score.created_at(),time(row.createdAt()));
  var cell=score.observation().cell();same(cell.sample_id(),row.sampleId());same(cell.agent_version_id(),row.candidateId());same(cell.dimension(),row.dimension());same(cell.repeat_index(),row.repeatIndex());
  var content=schema.parse(row.authorityJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));same(row.authorityHash(),schema.hash(content));same(row.authorityJson(),schema.canonical(content));if(content.size()!=7)bad();same("1.0.0",content.path("schema_version").asText());same("EVALUATION_RESULTS",content.path("purpose").asText());same(schema.tree(score),content.path("score"));
  var grant=schema.bind(content.path("grant"),PermissionProtocol.GrantRow.class);same(score.authority_grant_id(),grant.grantId());same(score.authority_revision(),grant.revision().toString());var scope=new PermissionProtocol.Scope(row.tenantId(),row.projectId(),"EVALUATION",row.evaluationId());same(schema.tree(scope),content.path("scope"));
  if(new PermissionAuthority(permissions).findResource(scope,score.scorer_id(),content.path("identity_mode").asText(),"evaluation.score","EVALUATION_RESULTS",List.of(grant),row.createdAt()).isEmpty())bad();
  var receipt=evaluation.receipt(schema.canonical(content.path("original_receipt")));same(row.originalOperationId(),receipt.path("data").path("operation_id").asText());same(row.scoreId(),receipt.path("data").path("score_id").asText());same(row.evaluationId(),receipt.path("data").path("evaluation_id").asText());same(row.definitionHash(),receipt.path("data").path("definition_sha256").asText());
  var v=values(a);v.put("witness",row);v.put("scoreId",row.scoreId());v.put("creatorId",row.createdBy());var operation=one(mapper.originalScoreOperation(v));same(row.originalOperationId(),operation.operationId());same("COMPLETED",operation.state());same(Integer.valueOf(201),operation.httpStatus());same(row.evaluationId(),operation.targetId());same("1.0.0",operation.schemaVersion());same(evaluation.manifestSha256(),operation.schemaManifestSha256());same(receipt,schema.parse(operation.responseRef().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  var evidence=schema.tree(row);same(evidence,schema.parse(one(mapper.scoreAuthorityAudit(v)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));same(evidence,schema.parse(one(mapper.scoreAuthorityEvent(v)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
 }
}
