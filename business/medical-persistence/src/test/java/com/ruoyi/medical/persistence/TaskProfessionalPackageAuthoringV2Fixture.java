package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.jdbc.core.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Offline SQL, rights and object-store doubles around the real immutable producer ledger,
 * physical transfer/full GET and registration. No real business approval or deployed profile. */
final class TaskProfessionalPackageAuthoringV2Fixture {
    final String tenant,project,actor;
    final PermissionSchema json=new PermissionSchema();
    final TaskProfessionalPackageAuthoringSchema schema=new TaskProfessionalPackageAuthoringSchema(json).v2();
    final JdbcTemplate db=mock(JdbcTemplate.class);
    final TaskProfessionalPackageAuthoringLedger ledger=new TaskProfessionalPackageAuthoringLedger(mock(DataSource.class),json,schema,new IdentityDirectoryPolicy("INTERNAL"));
    final EnterpriseAccess.Admission a=mock(EnterpriseAccess.Admission.class);
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
    final SourceUploadTransport storage=mock(SourceUploadTransport.class);
    final TaskProfessionalPackageAuthoringTransport transport=new TaskProfessionalPackageAuthoringTransport(storage).forSchema(schema);
    final ArtifactRegistryMapper registry=mock(ArtifactRegistryMapper.class);
    final DependencyLineage lineage=mock(DependencyLineage.class);
    final TaskProfessionalPackageAuthoringRegistration registration;
    final Map<String,List<Map<String,Object>>> tables=new HashMap<>();
    final Map<String,byte[]> physical=new HashMap<>();
    final Map<InterfaceProtocol.ObjectRef,byte[]> bytes=new HashMap<>();
    final Map<String,InterfaceProtocol.ObjectRef> objects=new HashMap<>();
    final AtomicInteger puts=new AtomicInteger(),gets=new AtomicInteger();
    final TaskProfessionalPackageAuthoringAuthority.Checked checked=new TaskProfessionalPackageAuthoringAuthority.Checked(json.object().put("offline_current_authority","verified"),new TaskProfessionalPackageAuthoringAuthority.Content(List.of(),List.of()),"INTERNAL");
    final LocalDateTime now=LocalDateTime.of(2026,10,20,12,0);
    boolean failCas,corruptRead,denyRead;
    TaskProfessionalPackageAuthoringV2Fixture(String tenant,String project,String actor)throws Exception {
        this.tenant=tenant;this.project=project;this.actor=actor;
        ReflectionTestUtils.setField(ledger,"db",db);
        when(a.context()).thenReturn(new BusinessSessionProtocol.Context(tenant,"1","1","1"));when(a.actorId()).thenReturn(actor);when(a.subject()).thenReturn(subject);when(subject.sessionId()).thenReturn(UUID.randomUUID().toString());
        when(db.query(eq("SELECT UTC_TIMESTAMP(6)"),any(RowMapper.class))).thenReturn(List.of(now));
        when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(c->select(c.getArgument(0),args(c.getArguments())));
        when(db.update(anyString(),any(Object[].class))).thenAnswer(c->update(c.getArgument(0),args(c.getArguments())));
        when(storage.writeDocumentProjection(eq(subject),eq(tenant),any(),any(),anyBoolean(),anyInt(),any())).thenAnswer(c->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());S3ObjectWriter.WriteIntent i=c.getArgument(2);byte[] bytes=c.getArgument(3);boolean recovery=c.getArgument(4);S3ObjectReader.Admission guard=c.getArgument(6);guard.requireCurrent();
            assertEquals(i.sha256(),WireValues.sha256(bytes));assertEquals(i.sizeBytes(),Integer.toString(bytes.length));
            if(recovery){assertTrue(physical.containsKey(i.objectKey()));assertArrayEquals(bytes,physical.get(i.objectKey()));}
            else {assertFalse(physical.containsKey(i.objectKey()),"conditional original PUT may not replace bytes");physical.put(i.objectKey(),bytes.clone());puts.incrementAndGet();}
            return new InterfaceProtocol.ObjectRef(i.objectKey(),"immutable-original-version",i.sha256(),i.sizeBytes(),i.mediaType());
        });
        when(storage.readDocument(eq(subject),eq(tenant),anyInt(),any(),any())).thenAnswer(c->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());S3ObjectReader.Admission guard=c.getArgument(3);guard.requireCurrent();return ((SourceUploadTransport.DocumentRead<?>)c.getArgument(4)).apply(reader(),timed(guard));});
        registration=new TaskProfessionalPackageAuthoringRegistration(ledger,schema,json,registry,new DependencyManifestSchema(),lineage,new EvaluationDependencyPolicy("test","local",256,512,List.of()));
        when(registry.artifact(anyString())).thenAnswer(c->rows("artifact").stream().filter(r->c.getArgument(0).equals(r.get("artifact_id"))).map(r->new ArtifactRegistryProtocol.ArtifactRow(s(r,"tenant_id"),s(r,"project_id"),s(r,"artifact_id"),s(r,"artifact_kind"),s(r,"object_ref"),s(r,"object_key"),s(r,"object_version"),s(r,"sha256"),new BigInteger(s(r,"size_bytes")),s(r,"media_type"),s(r,"dependency_access_set_id"),s(r,"content_sha256"),s(r,"schema_version"),s(r,"schema_manifest_sha256"),s(r,"classification"))).toList());
        when(registry.binding(anyString())).thenAnswer(c->rows("artifact_content_binding").stream().filter(r->c.getArgument(0).equals(r.get("artifact_id"))).map(r->new ArtifactRegistryProtocol.BindingRow(s(r,"tenant_id"),s(r,"project_id"),s(r,"artifact_id"),s(r,"resource_kind"),s(r,"resource_id"),s(r,"schema_ref"),s(r,"schema_document_sha256"),s(r,"dependency_access_set_id"),s(r,"dependency_manifest_sha256"))).toList());
        when(registry.manifest(anyString())).thenAnswer(c->rows("dependency_access_manifest").stream().filter(r->c.getArgument(0).equals(r.get("access_set_id"))).map(r->new ArtifactRegistryProtocol.ManifestRow(s(r,"tenant_id"),s(r,"project_id"),s(r,"access_set_id"),s(r,"manifest_json"),s(r,"manifest_sha256"),s(r,"content_sha256"),new BigInteger(s(r,"member_count")),s(r,"schema_version"),s(r,"schema_manifest_sha256"))).toList());
        when(registry.members(anyString())).thenAnswer(c->rows("dependency_access_member").stream().filter(r->c.getArgument(0).equals(r.get("access_set_id"))).map(r->new ArtifactRegistryProtocol.MemberRow(s(r,"upstream_version_id"),s(r,"rights_revision"))).toList());
        when(registry.version(anyString())).thenAnswer(c->rows("version_registry").stream().filter(r->c.getArgument(0).equals(r.get("version_id"))).map(r->new ArtifactRegistryProtocol.VersionRow(s(r,"version_id"),s(r,"owner_project_id"),s(r,"entity_kind"),s(r,"content_sha256"))).toList());
    }
    ObjectNode request(String kind,JsonNode payload){var request=json.object().put("schema_version","2.0.0").put("kind",kind).put("preparation_sha256","a".repeat(64));request.set("package",payload.deepCopy());request.putArray("content_sources");return request;}
    TaskProfessionalPackageAuthoringLedger.Attempt begin(String key,String kind,JsonNode payload)throws Exception {var request=request(kind,payload);schema.validateRequest(request);return tx(()->ledger.begin(a,"1",project,key,request,checked,json.object().putArray("reads")));}
    TaskProfessionalPackageAuthoringRegistration.Registered publish(String kind,JsonNode payload)throws Exception {
        var attempt=begin(UUID.randomUUID().toString(),kind,payload);var transfer=transport.transfer(subject,attempt,()->{});var registered=tx(()->registration.register(a,transfer,checked));transport.readRegistered(subject,registered,()->{});objects.put(registered.original().text("artifact_id"),registered.reference());bytes.put(registered.reference(),physical.get(registered.reference().objectKey()).clone());return registered;
    }
    S3ObjectReader reader()throws Exception {var reader=mock(S3ObjectReader.class);when(reader.readVerified(any(),any())).thenAnswer(c->{InterfaceProtocol.ObjectRef ref=c.getArgument(0);S3ObjectReader.Admission guard=c.getArgument(1);guard.requireCurrent();if(denyRead)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);gets.incrementAndGet();byte[] bytes=Objects.requireNonNull(physical.get(ref.objectKey())).clone();if(corruptRead)bytes[0]='!';var object=mock(S3ObjectReader.VerifiedObject.class);when(object.reference()).thenReturn(ref);when(object.fullSha256()).thenReturn(WireValues.sha256(bytes));when(object.chunk(anyLong(),anyInt())).thenAnswer(chunk->Arrays.copyOfRange(bytes,Math.toIntExact(chunk.getArgument(0,Long.class)),Math.toIntExact(chunk.getArgument(0,Long.class))+chunk.getArgument(1,Integer.class)));return object;});return reader;}
    static S3ObjectReader.TimedAdmission timed(S3ObjectReader.Admission guard){return new S3ObjectReader.TimedAdmission(){public void requireCurrent(){guard.requireCurrent();}public java.time.Duration remainingTime(){return java.time.Duration.ofSeconds(5);}};}
    interface Work<T>{T get()throws Exception;}
    <T>T tx(Work<T> work)throws Exception {assertFalse(TransactionSynchronizationManager.isActualTransactionActive());try(var ignored=TenantScope.open(new TenantScope.Identity(tenant,actor,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.initSynchronization();try{T value=work.get();for(var hook:TransactionSynchronizationManager.getSynchronizations()){hook.afterCommit();hook.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);}return value;}catch(Exception e){for(var hook:TransactionSynchronizationManager.getSynchronizations())hook.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);throw e;}finally{TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false);}}}
    List<Map<String,Object>> rows(String table){return tables.computeIfAbsent(table,k->new ArrayList<>());}
    List<Map<String,Object>> select(String sql,Object[] args){if(sql.startsWith("SELECT user_id FROM tenant_member"))return List.of(Map.of("user_id",actor));var match=Pattern.compile("FROM ([a-z_]+)").matcher(sql);if(!match.find())throw new AssertionError(sql);var found=new ArrayList<Map<String,Object>>();for(var row:rows(match.group(1))){var terms=Pattern.compile("([a-z_]+)=\\?").matcher(sql);int index=0;boolean matches=true;while(terms.find())if(!Objects.equals(row.get(terms.group(1)),args[index++])){matches=false;break;}if(matches)found.add(new LinkedHashMap<>(row));}return found;}
    int update(String sql,Object[] args){if(sql.startsWith("INSERT INTO ")){var m=Pattern.compile("INSERT INTO ([a-z_]+)\\(([^)]+)\\)").matcher(sql);if(!m.find())throw new AssertionError(sql);var columns=m.group(2).split(",");assertEquals(columns.length,args.length);var row=new LinkedHashMap<String,Object>();for(int i=0;i<columns.length;i++)row.put(columns[i],args[i]);rows(m.group(1)).add(row);return 1;}if(sql.startsWith("UPDATE operation_record SET ")){if(failCas)return 0;boolean complete=sql.contains("state='COMPLETED'");String operation=args[complete?3:2].toString();var op=rows("operation_record").stream().filter(r->operation.equals(r.get("operation_id"))).findFirst().orElseThrow();if(!Integer.valueOf(complete?2:1).equals(op.get("revision")))return 0;op.put("revision",complete?3:2);op.put("updated_at",args[complete?1:0]);if(complete){op.put("state","COMPLETED");op.put("http_status",201);op.put("response_ref",args[0]);}return 1;}throw new AssertionError(sql);}
    static String s(Map<String,Object> row,String field){return Objects.requireNonNull(row.get(field),field).toString();}
    static Object[] args(Object[] raw){var args=Arrays.copyOfRange(raw,1,raw.length);return args.length==1&&args[0] instanceof Object[] a?a:args;}
}
