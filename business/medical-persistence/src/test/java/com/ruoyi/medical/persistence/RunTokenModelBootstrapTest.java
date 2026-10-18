package com.ruoyi.medical.persistence;
import com.ruoyi.medical.identity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.lang.reflect.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
/** Real token base gate and schema; external current metadata authority mocked independently. */
class RunTokenModelBootstrapTest {
 FormalWorkerAccessTest f;RunTokenAuthority authority;FormalModelVerificationBootstrap bootstrap;FormalModelVerificationBootstrap.Verified proof;ServiceCertificateIdentity.Verified service;
 @BeforeEach void setup()throws Exception{
  f=new FormalWorkerAccessTest();f.setUp();bootstrap=mock(FormalModelVerificationBootstrap.class);proof=mock(FormalModelVerificationBootstrap.Verified.class);service=mock(ServiceCertificateIdentity.Verified.class);when(service.serviceUri()).thenReturn("spiffe://medical/runtime");
  authority=new RunTokenAuthority(mock(DataSource.class),mock(TenantTransaction.class),f.permissions,mock(PermissionAuthority.class),f.schema,mock(RunTokenPolicyStore.class),mock(RunTokenSigningKeyStore.class),mock(ServiceCertificateIdentity.class),bootstrap);
  set("store",f.store);set("jdbc",f.jdbc);
  when(f.persisted.schema()).thenReturn("1.0.0");when(f.persisted.manifest()).thenReturn(f.schema.manifestSha256());when(f.persisted.principalSchema()).thenReturn("1.0.0");when(f.persisted.principalManifest()).thenReturn(f.schema.manifestSha256());when(f.persisted.kind()).thenReturn("SYSTEM_JOB");
  var scope=new ObjectMapper().readTree(f.persisted.scope());((com.fasterxml.jackson.databind.node.ObjectNode)scope).put("execution_kind","SYSTEM_JOB");when(f.persisted.scope()).thenReturn(f.schema.canonical(scope));
  var p=new ObjectMapper().readTree(f.persisted.capabilities());((com.fasterxml.jackson.databind.node.ObjectNode)p).put("scope_sha256",f.schema.sha256(scope));when(f.persisted.capabilities()).thenReturn(f.schema.canonical(p));
  var role=f.schema.object().put("schema_version","1.0.0");role.set("permissions",p.required("permissions"));when(f.leased.roleScope()).thenReturn(f.schema.canonical(role));
  when(f.leased.roleSha256()).thenReturn("a".repeat(64));when(f.leased.registeredSha256()).thenReturn("a".repeat(64));when(f.leased.entityKind()).thenReturn("CAPABILITY_VERSION");when(f.leased.roleSchema()).thenReturn("1.0.0");when(f.leased.roleStatus()).thenReturn("DRAFT");when(f.leased.configurationStatus()).thenReturn("DRAFT");when(f.leased.roleManifest()).thenReturn("b".repeat(64));
  when(f.jdbc.query(contains("SELECT job_type"),any(RowMapper.class),eq(f.tenant),eq(f.run),eq(f.actor))).thenReturn(List.of("MODEL_VERIFICATION"));
  when(f.jdbc.query(contains("SELECT role_version"),any(RowMapper.class),eq(f.tenant),eq(f.run),eq(f.step),eq(f.project))).thenReturn(List.of(f.id(52)));
  when(f.jdbc.query(contains("platform_local_account"),any(RowMapper.class),eq(f.actor))).thenReturn(List.of(f.actor));when(f.jdbc.query(contains("tenant_member"),any(RowMapper.class),eq(f.tenant),eq(f.actor))).thenReturn(List.of(f.actor));when(f.permissions.projectStatus(f.project)).thenReturn(List.of("ACTIVE"));
  when(bootstrap.require(f.tenant,f.actor,f.run,f.step,f.id(52),"a".repeat(64),"spiffe://medical/runtime","medical-runtime")).thenReturn(proof);when(proof.nodePermissions()).thenReturn(f.schema.permissions(p));
 }
 void set(String field,Object value)throws Exception{var target=RunTokenAuthority.class.getDeclaredField(field);target.setAccessible(true);target.set(authority,value);}
 Object base()throws Throwable{var method=RunTokenAuthority.class.getDeclaredMethod("base",String.class,String.class,String.class,java.math.BigInteger.class,ServiceCertificateIdentity.Verified.class,String.class);method.setAccessible(true);try{return method.invoke(authority,f.run,f.actor,f.step,null,service,"medical-runtime");}catch(InvocationTargetException e){throw e.getCause();}}
 @AfterEach void cleanup(){f.cleanup();}
 @Test void exactBootstrapAllowsDraftWithIndependentRecheck()throws Throwable{assertNotNull(base());verify(proof).requireUnchanged();}
 @Test void jobTypeWithoutOriginalProofStillRejects()throws Exception{when(bootstrap.require(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,this::base);}
 @Test void differentSystemJobCannotUseDraftException(){when(f.jdbc.query(contains("SELECT job_type"),any(RowMapper.class),eq(f.tenant),eq(f.run),eq(f.actor))).thenReturn(List.of());assertThrows(TenantAccessException.class,this::base);verifyNoInteractions(bootstrap);}
 @Test void ordinaryTaskDraftCannotBorrowModelException()throws Exception{when(f.persisted.kind()).thenReturn("TASK");var scope=new ObjectMapper().readTree(f.persisted.scope());((com.fasterxml.jackson.databind.node.ObjectNode)scope).put("execution_kind","TASK");when(f.persisted.scope()).thenReturn(f.schema.canonical(scope));var principal=new ObjectMapper().readTree(f.persisted.capabilities());((com.fasterxml.jackson.databind.node.ObjectNode)principal).put("scope_sha256",f.schema.sha256(scope));when(f.persisted.capabilities()).thenReturn(f.schema.canonical(principal));assertThrows(TenantAccessException.class,this::base);verifyNoInteractions(bootstrap);}
 @Test void bootstrapCannotOverrideRegisteredRoleHash(){when(f.leased.registeredSha256()).thenReturn("c".repeat(64));assertThrows(TenantAccessException.class,this::base);}
 @Test void extraActualPrincipalPermissionCannotBorrowProof(){when(proof.nodePermissions()).thenReturn(List.of());assertThrows(TenantAccessException.class,this::base);}
 @Test void currentBootstrapRevocationRejects()throws Exception{doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.SCOPE_STALE)).when(proof).requireUnchanged();assertThrows(TenantAccessException.class,this::base);}
}
