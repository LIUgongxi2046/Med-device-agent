package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real mapping service/inventory/schema/journal, stateful SQL and explicit current authority doubles.
 * No database, actual grant, source evidence, approval or deployed session is asserted. */
class TaskFieldMappingServiceTest {
 @Test void authorCurrentReadAndOriginalRecoveryPreserveExactReceipt()throws Exception{
  var f=new Fixture();var receipt=f.author();assertEquals("PROPOSED",receipt.required("status").asText());
  var view=f.service.read(f.subject,"1",f.task,receipt.required("assignment_id").asText());assertTrue(view.required("is_current").asBoolean());
  assertEquals("MAPPED",view.at("/manifest/inventory/0/state").asText());assertEquals(f.request.required("mappings"),view.at("/manifest/mappings"));
  var proof=f.service.requireCurrent(f.proof,receipt.required("assignment_id").asText(),receipt.required("assignment_sha256").asText());proof.requireUnchanged();
  int writes=f.db.writes;assertEquals(receipt,f.author());assertEquals(receipt,f.recover());assertEquals(writes,f.db.writes);
  assertEquals(1,f.db.rows("task_field_mapping_revision").size());assertEquals(1,f.db.rows("audit_event").size());
  var witness=f.schema.read(f.db.only("audit_event").get("resource_ref").toString());assertEquals("2026-10-20T00:00:00",witness.at("/authorizations/grant/valid_from").asText());
 }
 @Test void changedOriginalRequestCannotReuseKey()throws Exception{var f=new Fixture();f.author();f.request.put("reason","changed");int before=f.db.writes;assertThrows(TenantAccessException.class,f::author);assertEquals(before,f.db.writes);}
 @Test void currentHeadUsesExplicitCasAndRetainsOldMapping()throws Exception{
  var f=new Fixture();var first=f.author();f.key="mapping-second-key-0002";int before=f.db.writes;assertThrows(TenantAccessException.class,f::author);assertEquals(before,f.db.writes);
  f.request.put("expected_revision","1").put("base_mapping_id",first.required("assignment_id").asText());var second=f.author();assertEquals("2",second.required("revision").asText());
  assertEquals(2,f.db.rows("task_field_mapping_revision").size());assertFalse(f.service.read(f.subject,"1",f.task,first.required("assignment_id").asText()).required("is_current").asBoolean());
 }
 @Test void auditAndOutboxMetadataMutationCannotReuseValidPayload()throws Exception{
  for(String field:List.of("schema_version","schema_manifest_sha256","classification","created_by","trace_id","reason_code","created_at")){
   var f=new Fixture();f.author();f.db.only("audit_event").put(field,field.equals("created_at")?f.now.plusSeconds(1):"tampered");assertThrows(TenantAccessException.class,f::recover,field);
  }
  for(String field:List.of("aggregate_revision","occurred_at","aggregate_id","event_type")){
   var f=new Fixture();f.author();f.db.only("outbox_event").put(field,field.equals("occurred_at")?f.now.plusSeconds(1):"tampered");assertThrows(TenantAccessException.class,f::recover,field);
  }
 }
 @Test void operationMetadataMutationCannotReuseValidReceipt()throws Exception{
  for(String field:List.of("target_id","schema_version","revision","error_code","updated_at","created_at")){
   var f=new Fixture();f.author();f.db.only("operation_record").put(field,field.endsWith("_at")?f.now.plusSeconds(1):"tampered");assertThrows(TenantAccessException.class,f::recover,field);
  }
 }
 @Test void recoveryIsOriginalActorAndTaskScopedWithoutNewWrites()throws Exception{
  var f=new Fixture();f.author();int before=f.db.writes;when(f.a.actorId()).thenReturn(id(91));assertNull(f.recover());assertEquals(before,f.db.writes);
  when(f.a.actorId()).thenReturn(f.actor);assertThrows(TenantAccessException.class,()->f.service.recover(f.subject,"1",id(92),f.recoveryBytes()));assertEquals(before,f.db.writes);
 }
 @Test void currentRevocationStopsNewAuthorAndCurrentPrivateMapping()throws Exception{
  var f=new Fixture();var receipt=f.author();var checked=f.service.requireCurrent(f.proof,receipt.required("assignment_id").asText(),receipt.required("assignment_sha256").asText());
  doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED)).when(f.proof).requireUnchanged();assertThrows(TenantAccessException.class,checked::requireUnchanged);
  f.key="mapping-second-key-0002";f.request.put("expected_revision","1").put("base_mapping_id",receipt.required("assignment_id").asText());int before=f.db.writes;assertThrows(TenantAccessException.class,f::author);assertEquals(before,f.db.writes);
 }
 @Test void originalAuthorityPinsAndReceiptTaskCannotBeSubstituted()throws Exception{
  var f=new Fixture();var receipt=f.author();var audit=f.db.only("audit_event");var witness=(ObjectNode)f.schema.read(audit.get("resource_ref").toString());((ObjectNode)witness.required("authorizations")).put("task_id",id(92));
  audit.put("resource_ref",f.schema.canonical(witness));f.db.only("outbox_event").put("payload",f.schema.canonical(witness));assertThrows(TenantAccessException.class,f::recover);
  f=new Fixture();f.author();var row=f.db.only("task_field_mapping_revision");var changed=(ObjectNode)f.schema.read(row.get("receipt_json").toString());changed.put("plan_revision",id(92));row.put("receipt_json",f.schema.canonical(changed));assertThrows(TenantAccessException.class,f::recover);
 }
 static String id(int n){return TaskFieldTargetSchemaTest.id(n);}
 static final class Fixture {
  final String tenant=id(1),project=id(2),task=id(3),actor=id(90);String key="original-mapping-key-0001";final LocalDateTime now=LocalDateTime.of(2026,10,20,0,0);
  final PermissionSchema json=new PermissionSchema();final TaskFieldTargetSchema schema=new TaskFieldTargetSchema();final ObjectNode request=TaskFieldTargetSchemaTest.mappingAuthor();
  final EnterpriseAccess access=mock(EnterpriseAccess.class);final EnterpriseAccess.Admission a=mock(EnterpriseAccess.Admission.class);final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
  final TaskFieldTargetBasis basis=mock(TaskFieldTargetBasis.class);final TaskFieldTargetBasis.Verified proof=mock(TaskFieldTargetBasis.Verified.class,RETURNS_DEEP_STUBS);
  final TaskPlanRelationStore relations=mock(TaskPlanRelationStore.class);final TaskService tasks=mock(TaskService.class);final PermissionMapper permissions=mock(PermissionMapper.class);
  final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);final IdentityDirectoryPolicy identity=mock(IdentityDirectoryPolicy.class);final Memory db=new Memory();final TaskFieldMappingService service;
  Fixture()throws Exception{
   when(a.context()).thenReturn(new BusinessSessionProtocol.Context(tenant,"1","1","1"));when(a.actorId()).thenReturn(actor);when(a.watermarks()).thenReturn(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
   when(access.execute(eq(subject),any())).thenAnswer(i->((Function<EnterpriseAccess.Admission,Object>)i.getArgument(1)).apply(a));
   var head=new TaskProtocol.Head(tenant,project,task,"READY",BigInteger.ONE,actor,id(4),id(60),id(61),id(6),id(62),id(5),"a".repeat(64),"a".repeat(64),now);
   var fixed=new TaskService.VerifiedTask(json.object(),head,null);when(tasks.fixedTask(a,task)).thenReturn(fixed);when(tasks.fixedTask(a,id(92))).thenReturn(fixed);
   when(proof.task()).thenReturn(fixed);when(proof.admission()).thenReturn(a);when(proof.classification()).thenReturn("INTERNAL");when(proof.pins()).thenAnswer(i->TaskFieldTargetSchemaTest.pins());when(proof.plan().planRevision()).thenReturn(id(5));
   when(basis.require(a,task,TaskFieldTargetProtocol.MAPPING_WRITE)).thenReturn(proof);when(identity.classification()).thenReturn("INTERNAL");when(permissions.databaseTime()).thenReturn(now);
   var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var node=mapper.treeToValue(TaskFieldTargetSchemaTest.node(),TemplateProtocol.Node.class);var field=mapper.treeToValue(TaskFieldTargetSchemaTest.field(),TemplateProtocol.Field.class);
   var template=new TemplateProtocol.Content("1.0.0","DOC","PATH",false,"PROFILE",List.of(node),List.of(field),List.of(),List.of(),List.of(),null,List.of(),null);
   when(proof.execution().template().view().template().content()).thenReturn(template);
   var conditions=List.of(new TemplateApplicabilityProtocol.ConditionResult("NODE",id(9),null,null,List.of(),"APPLICABLE",List.of()),new TemplateApplicabilityProtocol.ConditionResult("FIELD",id(12),null,null,List.of(),"APPLICABLE",List.of()));
   when(proof.execution().template().view().conditions()).thenReturn(conditions);
   var projection=new TaskPlanProjection.Projection(id(5),id(6),List.of(id(9)),List.of(),List.of(new TaskPlanProjection.Section(id(8),"goal",actor,null,0)),List.of(new TaskPlanProjection.SectionNode(id(8),id(9),0)),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
   when(relations.read(eq(a),eq(project),eq(task),eq(id(5)),any())).thenReturn(projection);
   var grant=new PermissionProtocol.GrantRow(id(80),actor,project,"TASK",task,TaskFieldTargetProtocol.MAPPING_WRITE,"{}","ACTIVE",now,now.plusDays(1),id(81),"1.0.0",schema.manifest(),BigInteger.ONE);
   when(proof.grant()).thenReturn(grant);var auth=json.object().put("tenant_id",tenant).put("task_id",task).put("actor_id",actor).put("action",TaskFieldTargetProtocol.MAPPING_WRITE).put("purpose",TaskFieldTargetProtocol.PREPARATION_PURPOSE).put("classification","INTERNAL").put("auth_epoch","1").put("governance_epoch","1");auth.set("pins",TaskFieldTargetSchemaTest.pins());auth.set("grant",TaskFieldAuthorityWitness.tree(grant));when(proof.authorizations()).thenReturn(auth);
   service=new TaskFieldMappingService(access,basis,relations,schema,new TaskFieldTargetPolicy(100,100,1048576,1048576,1048576,"TEST","local"),json,permissions,resources,tasks,mock(DataSource.class),identity);ReflectionTestUtils.setField(service,"db",db);
  }
  JsonNode author()throws Exception{return service.author(subject,"1",task,key,schema.canonical(request).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  byte[] recoveryBytes()throws Exception{return schema.canonical(json.object().put("schema_version","1.0.0").put("project_id",project).put("original_idempotency_key",key)).getBytes(java.nio.charset.StandardCharsets.UTF_8);}
  JsonNode recover()throws Exception{return service.recover(subject,"1",task,recoveryBytes());}
 }
 static final class Memory extends JdbcTemplate {
  final Map<String,List<Map<String,Object>>> data=new HashMap<>();int writes;
  List<Map<String,Object>> rows(String t){return data.computeIfAbsent(t,k->new ArrayList<>());}Map<String,Object> only(String t){assertEquals(1,rows(t).size());return rows(t).get(0);}
  @Override public List<Map<String,Object>> queryForList(String sql,Object... args){
   TaskFieldSqlCapture.positional("mapping",sql,args);
   var match=Pattern.compile("FROM ([a-z_]+)").matcher(sql);if(!match.find())throw new AssertionError(sql);String table=match.group(1);var result=new ArrayList<Map<String,Object>>();
   String where=sql.substring(sql.indexOf("WHERE"));for(var row:rows(table)){var terms=Pattern.compile("(?:[a-z]+\\.)?([a-z_]+)=\\?").matcher(where);int i=0;boolean ok=true;while(terms.find()){if(!Objects.equals(row.get(terms.group(1)),args[i++])){ok=false;break;}}if(!ok)continue;
    var copy=new LinkedHashMap<>(row);if(table.equals("task_field_mapping_revision")){var version=rows("version_registry").stream().filter(v->Objects.equals(v.get("tenant_id"),row.get("tenant_id"))&&Objects.equals(v.get("version_id"),row.get("mapping_revision"))).findFirst().orElseThrow();copy.put("registered_sha256",version.get("content_sha256"));copy.put("registered_project",version.get("owner_project_id"));copy.put("entity_kind",version.get("entity_kind"));}result.add(copy);
   }return result;
  }
  @Override public int update(String sql,Object... args){TaskFieldSqlCapture.positional("mapping",sql,args);writes++;
   if(sql.startsWith("INSERT INTO ")){var m=Pattern.compile("INSERT INTO ([a-z_]+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)").matcher(sql);if(!m.find())throw new AssertionError(sql);String[] columns=m.group(2).split(","),values=m.group(3).split(",");assertEquals(columns.length,values.length);var row=new LinkedHashMap<String,Object>();int i=0;for(int n=0;n<columns.length;n++){String v=values[n].trim();row.put(columns[n].trim(),v.equals("?")?args[i++]:v.equals("NULL")?null:v.startsWith("'")?v.substring(1,v.length()-1):Integer.valueOf(v));}assertEquals(args.length,i);rows(m.group(1)).add(row);return 1;}
   if(sql.startsWith("UPDATE tenant SET"))return 1;
   if(sql.startsWith("UPDATE task_field_mapping_head")){var r=only("task_field_mapping_head");if(!Objects.equals(r.get("revision"),args[8])||!Objects.equals(r.get("mapping_revision"),args[9]))return 0;String[] names={"task_revision","plan_revision","mapping_revision","revision","updated_at"};for(int i=0;i<5;i++)r.put(names[i],args[i]);return 1;}
   throw new AssertionError(sql);
  }
 }
}
