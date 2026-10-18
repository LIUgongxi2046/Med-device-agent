package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.persistence.ExpertQualificationProtocol.*;
import java.math.BigInteger;
import java.time.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.auth.credentials.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.FormalInputFixture.*;
/** DB/session substitutes plus real source/rights/lineage/grants/registry and loopback versioned GET. */
final class ExpertFixture implements AutoCloseable {
 final FormalInputFixture f=new FormalInputFixture();final ExpertQualificationSchema schema=new ExpertQualificationSchema(f.json);final EvaluationSchema evaluationSchema=new EvaluationSchema(f.json);
 final Map<String,Journal> journals=new HashMap<>();final Map<String,Qualification> qualifications=new HashMap<>();final Map<String,Head> heads=new HashMap<>();final Map<String,ScoreBinding> bindings=new HashMap<>();final Map<String,ScoreAuthorityWitness> scoreAuthorities=new HashMap<>();final Map<String,SelectionOperationRow> operations=new HashMap<>();final Map<String,String> keys=new HashMap<>(),audits=new HashMap<>(),events=new HashMap<>();final List<String> calls=new ArrayList<>();
 final ExpertQualificationMapper mapper;final AgentPublicationDependencies dependencies;final EvaluationDependencyPolicy policy=new EvaluationDependencyPolicy("OFFLINE_TEST","LOCAL",512,2048,List.of());final EvaluationReferences references;
 final ExpertQualificationEvidenceReader reader;final ExpertQualificationStore store;final ExpertScoreAuthorityRecorder recorder;final ExpertQualificationAuthority authority;final ExpertQualificationService service;
 final EvaluationService evaluations=mock(EvaluationService.class);String actor=ACTOR;LocalDateTime now=f.now;String expert=id(3);boolean failInsert=false;
 ExpertFixture()throws Exception{
  f.source(SOURCE,"EVALUATION_RESULTS");f.bytes(f.served);f.grants.clear();for(String who:List.of(ACTOR,id(3),id(4),id(5)))for(String action:List.of("source.read","configuration.read","agent.configure","agent.publish","evaluation.read","evaluation.score"))grant(who,action);
  doAnswer(i->f.allowed.get()?f.grants.stream().filter(g->g.subjectId().equals(i.getArgument(0))&&g.action().equals(i.getArgument(1))).toList():List.of()).when(f.permissions).resourceGrants(anyString(),anyString());
  when(f.permissions.resourceOwner(anyString(),anyString())).thenAnswer(i->{String kind=i.getArgument(0),id=i.getArgument(1);return Set.of("SOURCE","ENTERPRISE","EVALUATION","PROJECT").contains(kind)?List.of(new PermissionProtocol.OwnerRow(id,kind.equals("PROJECT")?id:null)):List.of();});when(f.permissions.databaseTime()).thenAnswer(i->now);
  doAnswer(i->{Function<EnterpriseAccess.Admission,Object> fn=i.getArgument(1);return transaction(()->fn.apply(f.admission));}).when(f.access).execute(any(),any());
  doAnswer(i->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());assertEquals(ExpertQualificationEvidenceReader.MAXIMUM_BYTES,(int)i.getArgument(2));S3ObjectReader.Admission guard=i.getArgument(3);f.beforeTransport.run();guard.requireCurrent();var lease=new S3ObjectReader.TimedAdmission(){public void requireCurrent(){guard.requireCurrent();}public Duration remainingTime(){return Duration.ofMinutes(2);}};SourceUploadTransport.DocumentRead<Object> callback=i.getArgument(4);Object result;try(var reader=new S3ObjectReader(URI.create("http://127.0.0.1:"+f.server.getAddress().getPort()),"fixture","us-east-1",S3ObjectReader.Network.LOCAL_LOOPBACK,StaticCredentialsProvider.create(AwsBasicCredentials.create("synthetic-local-id","synthetic-local-secret")),f.spool,ExpertQualificationEvidenceReader.MAXIMUM_BYTES,32)){result=callback.apply(reader,lease);}f.afterTransport.run();return result;}).when(f.transport).readDocument(any(),eq(TENANT),anyInt(),any(),any());
  mapper=mock(ExpertQualificationMapper.class,i->{if(i.getMethod().getDeclaringClass()==Object.class)return org.mockito.Answers.RETURNS_DEFAULTS.answer(i);Map<String,Object> v=i.getArgument(0);String name=i.getMethod().getName();assertEquals(actor,v.get("userId"),name);assertEquals(actor,TenantScope.requireCurrent().subjectId());TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.ExpertQualificationMapper."+name);TenantScope.requireSubjectParameter("com.ruoyi.medical.persistence.mapper.ExpertQualificationMapper."+name,v);calls.add(name);Journal row=(Journal)v.get("row");ScoreAuthorityWitness witness=(ScoreAuthorityWitness)v.get("witness");
   return switch(name){
    case "original"->List.of(new Original(f.original.tenantId(),f.original.sourceVersion(),f.original.sha256(),f.original.objectKey(),f.original.objectVersion(),f.original.sizeBytes(),f.original.mediaType()));
    case "lockActor"->List.of(actor);case "databaseTime"->now;case "expertMember"->List.of(new TenantMemberRow(TENANT,id(88),(String)v.get("expertId"),"ACTIVE",now.minusDays(2),now.plusDays(2),BigInteger.ONE));
    case "journal"->list(journals.get(v.get("recordId")));case "journalByOperation"->journals.values().stream().filter(r->r.operationId().equals(v.get("operationId"))&&r.createdBy().equals(v.get("creatorId"))).toList();
    case "qualification"->list(qualifications.get(v.get("qualificationId")));case "head","lockHead"->list(heads.get(v.get("qualificationId")));case "decisions"->journals.values().stream().filter(r->r.qualificationId().equals(v.get("qualificationId"))&&Set.of("APPROVE","REJECT","REVOKE").contains(r.kind())).toList();
    case "bindingForScore"->bindings.values().stream().filter(b->b.scoreId().equals(v.get("scoreId"))&&b.qualificationId().equals(v.get("qualificationId"))).toList();case "scoreBinding"->list(bindings.get(v.get("bindingId")));case "scoreAuthority"->list(scoreAuthorities.get(v.get("scoreId")));case "scoreClassification"->List.of("INTERNAL");case "version"->list(f.versions.get(v.get("recordId")));
    case "operation"->list(operations.get(keys.get(actor+":"+v.get("operationName")+":"+v.get("key"))));case "operationById"->list(operations.get(v.get("operationId")));case "originalScoreOperation"->list(operations.get(witness.originalOperationId()));
    case "insertVersion"->{f.versions.put(row.recordId(),new ArtifactRegistryProtocol.VersionRow(row.recordId(),row.projectId(),(String)v.get("entityKind"),row.contentHash()));yield 1;}
    case "insertJournal"->{if(failInsert)throw new IllegalStateException("synthetic store failure");journals.put(row.recordId(),row);yield 1;}
    case "insertQualification"->{var q=(Qualification)v.get("qualification");qualifications.put(q.qualificationId(),q);yield 1;}
    case "insertHead"->{var h=(Head)v.get("head");heads.put(h.qualificationId(),h);yield 1;}
    case "advanceHead"->{var h=heads.get(v.get("qualificationId"));if(!h.revision().toString().equals(v.get("expected"))||!Objects.equals(h.decisionId(),v.get("previousDecision"))||!h.status().equals(v.get("previousStatus")))yield 0;heads.put(h.qualificationId(),new Head(h.tenantId(),h.projectId(),h.qualificationId(),(String)v.get("status"),h.revision().add(BigInteger.ONE),(String)v.get("decisionId"),(String)v.get("decisionKind"),h.schemaVersion(),h.schemaManifest(),h.createdBy(),h.createdAt(),h.classification(),(LocalDateTime)v.get("now")));yield 1;}
    case "insertScoreBinding"->{var b=(ScoreBinding)v.get("binding");bindings.put(b.bindingId(),b);yield 1;}
    case "insertScoreAuthority"->{scoreAuthorities.put(witness.scoreId(),witness);yield 1;}
    case "saveOperation"->{operations.put(row.operationId(),new SelectionOperationRow(row.operationId(),row.requestHash(),"COMPLETED",201,row.receiptJson(),row.recordId(),"1.0.0",row.schemaManifest()));keys.put(actor+":"+v.get("operationName")+":"+row.idempotencyKey(),row.operationId());yield 1;}
    case "saveAudit"->{audits.put(row.auditId(),(String)v.get("payload"));yield 1;}case "saveEvent"->{events.put(row.eventId(),(String)v.get("payload"));yield 1;}
    case "audit"->list(audits.get(row.auditId()));case "event"->list(events.get(row.eventId()));
    case "saveScoreAuthorityAudit"->{audits.put(witness.auditId(),(String)v.get("payload"));yield 1;}case "saveScoreAuthorityEvent"->{events.put(witness.eventId(),(String)v.get("payload"));yield 1;}
    case "scoreAuthorityAudit"->list(audits.get(witness.auditId()));case "scoreAuthorityEvent"->list(events.get(witness.eventId()));default->throw new AssertionError(name);
   };});
  references=new EvaluationReferences(f.lineage,f.versionAuthority,f.sourceAuthority,f.sources,f.artifacts,policy);dependencies=new AgentPublicationDependencies(f.lineage,f.versionAuthority,f.sourceAuthority,f.sources,f.artifacts,references,policy,f.json);
  reader=new ExpertQualificationEvidenceReader(f.access,f.transport,mapper,f.sourceAuthority,f.sources,f.artifacts,dependencies,references,policy,schema);store=new ExpertQualificationStore(mapper,schema,f.json,evaluationSchema);recorder=new ExpertScoreAuthorityRecorder(mapper,schema,evaluationSchema,f.json);authority=new ExpertQualificationAuthority(mapper,store,schema,f.resources,reader,evaluations,evaluationSchema,recorder);service=new ExpertQualificationService(f.access,mapper,schema,store,authority,reader,f.resources);actor(ACTOR);
 }
 void actor(String who){actor=who;when(f.admission.actorId()).thenReturn(who);when(f.admission.bindingView()).thenReturn(new AuthenticatedIdentityBinding.View("local",who,null,who));when(f.subject.user()).thenReturn(new BusinessSessionProtocol.User(who,"offline","Offline test",null));}
 void grant(String who,String action)throws Exception{var condition=new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of("EVALUATION_RESULTS","AGENT_PUBLICATION"),new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),null,null));f.grants.add(new PermissionProtocol.GrantRow(id(1000+f.grants.size()),who,null,"ENTERPRISE",TENANT,action,f.json.canonicalObject(condition),"ACTIVE",now.minusDays(1),now.plusDays(10),id(999),"1.0.0",f.json.manifestSha256(),BigInteger.ONE));}
 JsonNode authorRequest(){return schema.tree(new Author("1.0.0",null,expert,"PTR expert review",List.of("EVAL-01","EVAL-02"),"EVALUATION_RESULTS",now.minusDays(1).toInstant(ZoneOffset.UTC).toString(),now.plusDays(3).toInstant(ZoneOffset.UTC).toString(),List.of(new Evidence("SOURCE",new PermissionProtocol.Basis(SOURCE,f.original.sha256()))),"Human-submitted evidence; synthetic offline fixture only"));}
 JsonNode author()throws Exception{return service.author(f.subject,"1","expert-author-original-key",authorRequest());}
 JsonNode decision(JsonNode receipt,String decision,String expected){return schema.tree(new Decide("1.0.0",new PermissionProtocol.Basis(receipt.path("qualification").path("version_id").asText(),receipt.path("qualification").path("content_sha256").asText()),expected,decision,decision.equals("APPROVE")?List.of(new PermissionProtocol.Basis(SOURCE,f.original.sha256())):List.of(),decision.equals("APPROVE"),decision.equals("APPROVE"),"Actual human decision fixture"));}
 JsonNode approve(JsonNode receipt)throws Exception{actor(id(4));return service.decide(f.subject,"1","expert-approve-original-key",decision(receipt,"APPROVE","1"));}
 <T>T transaction(Callable<T> task)throws Exception{if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);TransactionSynchronizationManager.setActualTransactionActive(true);try(var scope=TenantScope.open(new TenantScope.Identity(TENANT,actor,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));try{return task.call();}finally{scope.finishTransaction();}}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}}
 @Override public void close()throws Exception{f.close();}
}
