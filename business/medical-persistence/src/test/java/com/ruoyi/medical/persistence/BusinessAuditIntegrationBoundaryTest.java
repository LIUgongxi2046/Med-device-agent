package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;import com.ruoyi.medical.identity.*;
import java.util.*;import java.math.BigInteger;import org.junit.jupiter.api.Test;import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
/** Actual controller and actual narrow prepare; source authority remains a boundary double. */
class BusinessAuditIntegrationBoundaryTest {
 MockHttpServletRequest request(){var r=new MockHttpServletRequest("GET","/api/audit-events");r.addHeader("X-Schema-Version","1.0.0");r.addHeader("X-Context-Revision","1");r.setParameter("limit","1");return r;}
 @Test void exactGetRouteRequiresIdentityBeforeAnyBusinessRead()throws Exception{
  var f=new BusinessAuditRuntimeTest();var identities=mock(ApplicationSessionIdentity.class);var service=mock(BusinessAuditService.class);var controller=new BusinessAuditController(identities,service,f.policy,f.json);var r=request();
  when(identities.requireCurrent(r,null)).thenThrow(new org.springframework.security.authentication.BadCredentialsException("missing"));
  assertThrows(org.springframework.security.core.AuthenticationException.class,()->controller.read(r,new MockHttpServletResponse(),null));verifyNoInteractions(service);
  var method=BusinessAuditController.class.getMethod("read",jakarta.servlet.http.HttpServletRequest.class,jakarta.servlet.http.HttpServletResponse.class,org.springframework.security.core.Authentication.class);
  assertArrayEquals(new String[]{"/api/audit-events"},method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value());assertNull(method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class));
 }
 @Test void repeatedContextHeaderAndGetBodyAreRejectedBeforeIdentityOrService()throws Exception{
  var f=new BusinessAuditRuntimeTest();var identities=mock(ApplicationSessionIdentity.class);var service=mock(BusinessAuditService.class);var controller=new BusinessAuditController(identities,service,f.policy,f.json);var r=request();r.addHeader("X-Context-Revision","1");
  assertThrows(SchemaRejection.class,()->controller.read(r,new MockHttpServletResponse(),null));var body=request();body.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));assertThrows(SchemaRejection.class,()->controller.read(body,new MockHttpServletResponse(),null));verifyNoInteractions(identities,service);
 }
 @Test void missingOrDuplicateMetadataHeaderCannotBecomeUnreadableSkip()throws Exception{
  var f=new DocumentMetadataReadBoundedTest();when(f.reads.metadataHeader(anyMap())).thenReturn(List.of());assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,f::proof).code());
  when(f.reads.metadataHeader(anyMap())).thenReturn(List.of(f.f.header,f.f.header));assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,f::proof).code());verifyNoInteractions(f.sources);f.noUnbounded();
 }
 @Test void duplicateSnapshotBytesAndMissingCurrentnessWatermarksFailClosed()throws Exception{
  var f=new DocumentMetadataReadBoundedTest();when(f.reads.metadataSnapshotBytes(anyMap())).thenReturn(List.of(BigInteger.TEN,BigInteger.TEN));assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,f::proof).code());verify(f.reads,never()).metadataSnapshot(anyMap());
  var valid=new DocumentMetadataReadBoundedTest();var proof=valid.proof();when(valid.reads.metadataWatermarks(anyMap())).thenReturn(List.of());assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->valid.f.within(()->{valid.service.requireMetadataCurrent(valid.f.admission,proof);return null;})).code());valid.noUnbounded();
 }
}
