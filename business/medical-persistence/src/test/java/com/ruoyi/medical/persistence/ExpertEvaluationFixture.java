package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.FormalInputFixture.*;
/** Real EvaluationService append/load/schema/verdict. DB and preexisting rule/dependency authority boundaries are explicit doubles. */
final class ExpertEvaluationFixture {
 final ExpertFixture f;final EvaluationMapper mapper;final EvaluationService service;final EvaluationProtocol.Definition definition;final String hash;
 final List<EvaluationProtocol.Score> scores=new ArrayList<>();final List<EvaluationProtocol.StoredScore> stored=new ArrayList<>();final Map<String,SelectionOperationRow> operations=new HashMap<>();String audit,event;BigInteger revision=BigInteger.ONE;String status="QUEUED";EvaluationProtocol.Result result;
 final EvaluationReferences refs=mock(EvaluationReferences.class);final EvaluationRuleAuthority rules=mock(EvaluationRuleAuthority.class);boolean originalScoreCorrupt=false;
 ExpertEvaluationFixture(ExpertFixture f)throws Exception{
  this.f=f;when(f.f.admission.subject()).thenReturn(f.f.subject);var seed=EvaluationFixture.definition(f.evaluationSchema,List.of(EvaluationFixture.sample(1000,"REAL","ACCEPTANCE")),List.of("EVAL-02"),1);var rq=(com.fasterxml.jackson.databind.node.ObjectNode)f.f.json.tree(seed.request());rq.putNull("project_id");var request=f.evaluationSchema.create(f.f.json.canonical(rq).getBytes(java.nio.charset.StandardCharsets.UTF_8));definition=new EvaluationProtocol.Definition("1.0.0",TENANT,id(600),request,seed.candidate_snapshots(),seed.approved_expectation_samples(),seed.authority_grant_id(),seed.authority_revision(),seed.created_by(),"2026-10-18T18:00:00Z");hash=f.evaluationSchema.hash(definition);
  when(rules.boundSamples(any(),any())).thenReturn(definition.approved_expectation_samples());when(f.f.permissions.resourceOwner(anyString(),anyString())).thenAnswer(i->List.of(new PermissionProtocol.OwnerRow(i.getArgument(1),null)));
  when(f.f.permissions.saveAudit(anyString(),isNull(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{audit=i.getArgument(4);return 1;});when(f.f.permissions.saveEvent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{event=i.getArgument(5);return 1;});
  mapper=mock(EvaluationMapper.class,i->{if(i.getMethod().getDeclaringClass()==Object.class)return org.mockito.Answers.RETURNS_DEFAULTS.answer(i);Map<String,Object> v=i.getArgument(0);assertEquals(f.actor,v.get("userId"));String name=i.getMethod().getName();return switch(name){
   case "definition"->List.of(row());case "candidate"->{String id=(String)v.get("candidateId");var c=definition.candidate_snapshots().stream().filter(x->x.version_id().equals(id)).findFirst().orElseThrow();yield List.of(new EvaluationProtocol.AgentRow(TENANT,null,id,c.content_sha256(),c.content_sha256(),c.agent_id(),c.created_by(),f.schema.canonical(c.dependency_manifest()),f.schema.canonical(c.limits()),"DRAFT","1.0.0",c.schema_manifest_sha256()));}
   case "parents"->{var ids=new TreeSet<String>();var r=definition.request();for(var ref:List.of(r.dataset(),r.sample_manifest_artifact(),r.rule_revision(),r.authorized_use_evidence()))ids.add(ref.version_id());r.candidates().forEach(x->ids.add(x.version_id()));r.samples().forEach(x->{ids.add(x.input_ref().version_id());ids.add(x.expected_ref().version_id());});yield List.copyOf(ids);}
   case "scores"->List.copyOf(stored);case "currentOperation"->list(operations.get(v.get("key")));
   case "insertScore"->1;case "insertObservation"->{var score=f.evaluationSchema.score((String)v.get("scoreJson"));scores.add(score);var projection=call("scoreProjection",new Class[]{EvaluationProtocol.Score.class},score);stored.add(new EvaluationProtocol.StoredScore(score.score_id(),score.evaluation_id(),(String)v.get("scoreJson"),(String)v.get("scoreHash"),f.schema.canonical((JsonNode)projection),"1.0.0",f.evaluationSchema.manifestSha256(),score.scorer_id(),(LocalDateTime)v.get("now"),"INTERNAL"));yield 1;}
   case "advanceBatch"->{revision=revision.add(BigInteger.ONE);status=(String)v.get("nextStatus");yield 1;}
   case "saveOperation"->{var op=new SelectionOperationRow((String)v.get("operationId"),(String)v.get("requestHash"),"COMPLETED",201,(String)v.get("response"),(String)v.get("evaluationId"),"1.0.0",f.evaluationSchema.manifestSha256());operations.put((String)v.get("key"),op);f.operations.put(op.operationId(),op);yield 1;}
   case "audit"->List.of(audit);case "event"->List.of(event);default->throw new AssertionError(name);
  };});
  service=new EvaluationService(f.f.access,mapper,f.f.permissions,f.f.resources,refs,f.evaluationSchema,new EvaluationVerdict(f.evaluationSchema),f.f.json,new IdentityDirectoryPolicy("INTERNAL"),rules,null);
  sink(f.recorder);
  when(f.evaluations.requireAcceptance(any(),anyString())).thenAnswer(i->service.requireAcceptance(i.getArgument(0),i.getArgument(1)));
 }
 void sink(EvaluationService.OriginalScoreWitnessSink sink)throws Exception{ObjectProvider<EvaluationService.OriginalScoreWitnessSink> provider=mock(ObjectProvider.class);when(provider.orderedStream()).thenAnswer(i->sink==null?java.util.stream.Stream.empty():java.util.stream.Stream.of(sink));var field=EvaluationService.class.getDeclaredField("originalScoreWitnessSinks");field.setAccessible(true);field.set(service,provider);}
 Object call(String name,Class<?>[] types,Object...args)throws Exception{var m=EvaluationService.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(service,args);}
 EvaluationProtocol.Stored row()throws Exception{var missing=result==null?List.<String>of():result.missing_requirements();var projection=(JsonNode)call("projection",new Class[]{EvaluationProtocol.Create.class,List.class},definition.request(),missing);return new EvaluationProtocol.Stored(TENANT,null,definition.evaluation_id(),f.schema.canonical(f.schema.tree(definition)),hash,hash,f.schema.canonical(projection),status,revision,result==null?null:f.schema.canonical(f.schema.tree(result)),result==null?null:f.evaluationSchema.hash(result),"1.0.0",f.evaluationSchema.manifestSha256(),definition.created_by(),LocalDateTime.ofInstant(Instant.parse(definition.created_at()),ZoneOffset.UTC),"INTERNAL");}
 JsonNode append()throws Exception{f.actor(f.expert);var sample=definition.request().samples().get(0);var cell=new EvaluationProtocol.Cell(sample.sample_id(),definition.request().candidates().get(0).version_id(),"EVAL-02",0);var append=EvaluationFixture.append(definition,hash,cell,revision.toString(),null,"10","10");var n=(com.fasterxml.jackson.databind.node.ObjectNode)f.f.json.tree(append);n.putNull("control_numerator").putNull("control_denominator");return service.append(f.f.subject,"1","evaluation-real-score-key",definition.evaluation_id(),f.schema.canonical(n).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 void finish()throws Exception{result=new EvaluationVerdict(f.evaluationSchema).compute(definition,hash,scores);status=switch(result.verdict()){case "PASS"->"COMPLETED";case "FAIL"->"FAILED";default->"INSUFFICIENT_EVIDENCE";};revision=revision.add(BigInteger.ONE);}
 JsonNode bindRequest(JsonNode qualification)throws Exception{var score=scores.get(0);return f.schema.tree(new com.ruoyi.medical.persistence.ExpertQualificationProtocol.BindScore("1.0.0",definition.evaluation_id(),score.score_id(),hash,f.evaluationSchema.hash(score),f.schema.bind(qualification.path("qualification"),PermissionProtocol.Basis.class),"PTR expert review","Original expert score binding"));}
}
