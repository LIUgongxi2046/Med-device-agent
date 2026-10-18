package com.ruoyi.medical.persistence;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Exercises the existing permission schema and real ResourceReadAuthority/PermissionAuthority. No grants are written. */
public final class GruRecordExportPermissionTest {
 static String id(int n){return GruServiceTest.id(n);}
 @org.junit.jupiter.api.Test void focusedRegressions()throws Exception{main(new String[0]);}
 public static void main(String[] args)throws Exception{
  var json=new PermissionSchema();var scope=new PermissionProtocol.Scope(id(1),id(4),"DOCUMENT",id(5));
  var condition=new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of("GRU_RECORD_EXPORT"),new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),null,null));
  var request=new PermissionProtocol.Request("1.0.0","GRANT",id(2),scope,"conversation.export",null,condition,null,null,new PermissionProtocol.Basis(id(9),"a".repeat(64)),"0","Explicit export grant request validation only");
  assertEquals(request,json.request(ContentReadAuthorization.canonicalBytes(request)));
  var grant=new PermissionProtocol.GrantRow(id(10),id(2),id(4),"DOCUMENT",id(5),"conversation.export",json.canonicalObject(condition),"ACTIVE",LocalDateTime.of(2026,10,18,11,0),null,id(9),"1.0.0",json.manifestSha256(),BigInteger.ONE);
  var mapper=mock(PermissionMapper.class);var admission=mock(EnterpriseAccess.Admission.class);when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(id(1),"1","1","1"));when(admission.actorId()).thenReturn(id(2));when(admission.bindingView()).thenReturn(new AuthenticatedIdentityBinding.View("local",id(2),null,null));
  when(mapper.resourceOwner("DOCUMENT",id(5))).thenReturn(List.of(new PermissionProtocol.OwnerRow(id(5),id(4))));when(mapper.resourceGrants(id(2),"conversation.export")).thenReturn(List.of(grant));when(mapper.databaseTime()).thenReturn(LocalDateTime.of(2026,10,18,12,0));
  var authority=new ResourceReadAuthority(mapper,new PermissionAuthority(json),json);
  org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
  try(var ignored=TenantScope.open(new TenantScope.Identity(id(1),id(2),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){
   ignored.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
   try{assertEquals(grant,authority.require(admission,scope,"conversation.export","GRU_RECORD_EXPORT"));
   assertThrows(TenantAccessException.class,()->authority.require(admission,scope,"conversation.read","GRU_RECORD_EXPORT"));
   assertThrows(TenantAccessException.class,()->authority.require(admission,scope,"conversation.export","DOCUMENT_BODY"));
   when(mapper.resourceOwner("DOCUMENT",id(5))).thenReturn(List.of(new PermissionProtocol.OwnerRow(id(5),id(44))));assertThrows(TenantAccessException.class,()->authority.require(admission,scope,"conversation.export","GRU_RECORD_EXPORT"));
   }finally{ignored.finishTransaction();}
  }finally{org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
  System.out.println("PASS 5 real permission cases (schema grant, action/purpose, no read substitution, wrong purpose, real owner)");
 }
}
