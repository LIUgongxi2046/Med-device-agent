package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual B service and immutable-ledger verifier; only persistence/access boundaries are doubles. */
public class ConnectionVerificationLedgerTest {
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
 final PermissionSchema json=new PermissionSchema(); final RuntimeGovernanceSchema local=new RuntimeGovernanceSchema(); final ConnectionVerificationSchema schema=new ConnectionVerificationSchema();
 final EnterpriseAccess access=mock(EnterpriseAccess.class);final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);final ApplicationSessionIdentity.Verified actor=mock(ApplicationSessionIdentity.Verified.class);
 final ConnectionVerificationMapper mapper=mock(ConnectionVerificationMapper.class);final FormalInvocationService invocations=mock(FormalInvocationService.class);
 final ConnectionVerificationService service=new ConnectionVerificationService(access,mapper,mock(PermissionMapper.class),mock(ResourceReadAuthority.class),mock(ModelConfigurationService.class),mock(ApprovedCredentialAuthority.class),invocations,schema,json,mock(IdentityDirectoryPolicy.class),3600);
 final ObjectNode request=new ConnectionVerificationSchemaTest().request();final String time="2026-10-18T12:00:00Z";
 ConnectionVerificationMapper.Intent intent;ObjectNode base,operation,anchor;
 @SuppressWarnings("unchecked") public ConnectionVerificationLedgerTest()throws Exception{
  when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(id(1),"1","1","1"));when(admission.actorId()).thenReturn(id(2));
  when(access.execute(eq(actor),any())).thenAnswer(i->((Function<EnterpriseAccess.Admission,Object>)i.getArgument(1)).apply(admission));
  intent=new ConnectionVerificationMapper.Intent(id(1),id(3),id(4),id(2),id(5),id(6),"a".repeat(64),id(7),id(8),local.canonical(request),local.hash(request),"original-key-123456",id(9),"1.0.0",schema.manifest(),"INTERNAL",time,id(10),"1",id(11),id(12));
  anchor=json.object().put("schema_version","1.0.0").put("verification_id",id(4)).put("intent_sha256",local.hash(json.tree(intent)));
  base=json.object().put("verification_id",id(4)).put("config_id",id(6)).put("model_revision",id(5)).put("credential_reference_id",id(7)).put("credential_revision",id(8)).put("created_by",id(2)).put("schema_manifest_sha256",schema.manifest()).put("schema_version","1.0.0").put("classification","INTERNAL").putNull("error_code").put("configuration_kind","MODEL").put("created_at",time).put("updated_at",time).put("revision","1").put("status","PENDING").putNull("receipt_ref").putNull("receipt_artifact_id").putNull("endpoint_identity").putNull("verified_at");
  operation=json.object().putNull("error_code").put("created_at",time).put("updated_at",time).put("state","PROCESSING").putNull("http_status").put("revision","1");operation.set("response_ref",request.deepCopy());
  when(mapper.intentAudit(anyMap())).thenAnswer(i->List.of(local.canonical(anchor)));when(mapper.intentEvent(anyMap())).thenAnswer(i->List.of(local.canonical(anchor)));
  when(mapper.verification(anyMap())).thenAnswer(i->List.of(local.canonical(base)));when(mapper.operation(anyMap())).thenAnswer(i->List.of(local.canonical(operation)));
 }
 void verifyLedger()throws Throwable{try(var ignored=TenantScope.open(new TenantScope.Identity(id(1),id(2),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){var method=ConnectionVerificationService.class.getDeclaredMethod("verifyLedger",ConnectionVerificationMapper.Intent.class);method.setAccessible(true);try{method.invoke(service,intent);}catch(InvocationTargetException e){throw e.getCause();}}}
 void replaceIntent(String name,Object value)throws Exception{var fields=ConnectionVerificationMapper.Intent.class.getRecordComponents();Class<?>[] types=new Class<?>[fields.length];Object[] values=new Object[fields.length];for(int i=0;i<fields.length;i++){types[i]=fields[i].getType();values[i]=fields[i].getName().equals(name)?value:fields[i].getAccessor().invoke(intent);}intent=ConnectionVerificationMapper.Intent.class.getDeclaredConstructor(types).newInstance(values);}
 @Test void databaseJsonFormattingAndEquivalentUtcTimestampDoNotChangeAnchor()throws Throwable{replaceIntent("requestJson",request.toPrettyString());replaceIntent("createdAt","2026-10-18T12:00:00.000000Z");verifyLedger();}
 @Test void changingPrivateGrantCannotBeHiddenBehindSelfConsistentRow()throws Throwable{replaceIntent("grantId",id(99));assertThrows(TenantAccessException.class,this::verifyLedger);}
 @Test void pendingIntentRequiresBothIndependentWholeIntentAnchors()throws Throwable{verifyLedger();anchor.put("intent_sha256","b".repeat(64));assertThrows(TenantAccessException.class,this::verifyLedger);verifyNoInteractions(invocations);}
 @Test void missingIndependentEventCannotBeReplacedBySelfConsistentIntent()throws Throwable{when(mapper.intentEvent(anyMap())).thenReturn(List.of());assertThrows(TenantAccessException.class,this::verifyLedger);}
 @Test void changedOriginalRequestInOperationFailsClosed()throws Throwable{((ObjectNode)operation.get("response_ref")).put("reason","changed after original intent");assertThrows(TenantAccessException.class,this::verifyLedger);}
 @Test void pendingCannotClaimPassedReceiptOrFreshVerificationTime()throws Throwable{for(String field:List.of("receipt_ref","receipt_artifact_id","endpoint_identity","verified_at")){base.put(field,"invented");assertThrows(TenantAccessException.class,this::verifyLedger);base.putNull(field);}verifyLedger();}
 @Test void processingWithoutBindingCannotClaimRunningOrAdvancedRevision()throws Throwable{base.put("status","RUNNING");assertThrows(TenantAccessException.class,this::verifyLedger);base.put("status","PENDING").put("revision","2");assertThrows(TenantAccessException.class,this::verifyLedger);}
 @Test void completedOperationWithoutActualCompletionIsRejected()throws Throwable{operation.put("state","COMPLETED");assertThrows(TenantAccessException.class,this::verifyLedger);}
 @Test void readonlyMissingKeyReturnsNotFoundWithoutStartingJob(){var value=service.recover(actor,"1","original-key-123456");assertEquals("NOT_FOUND",value.path("status").asText());assertTrue(value.path("receipt").isNull());verifyNoInteractions(invocations);verify(mapper,never()).insertIntent(anyMap());}
 @Test void contextChangeStopsBeforeLedgerLookupOrECall(){assertThrows(TenantAccessException.class,()->service.recover(actor,"2","original-key-123456"));verifyNoInteractions(mapper,invocations);}
 public static void main(String[]args)throws Throwable{int count=0;for(var method:ConnectionVerificationLedgerTest.class.getDeclaredMethods())if(method.isAnnotationPresent(Test.class)){try{method.invoke(new ConnectionVerificationLedgerTest());}catch(InvocationTargetException e){throw e.getCause();}count++;}System.out.println("PASS "+count+" actual connection ledger/service tests");}
}
