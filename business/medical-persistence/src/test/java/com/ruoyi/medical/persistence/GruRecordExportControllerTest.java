package com.ruoyi.medical.persistence;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.contracts.*;
import org.springframework.mock.web.*;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.function.Function;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
/** Direct real controller/availability calls, no server or deployment claim. */
public final class GruRecordExportControllerTest {
 static String id(int n){return GruServiceTest.id(n);}
 static MockHttpServletRequest request(){var r=new MockHttpServletRequest();r.addHeader("X-Schema-Version","1.0.0");r.addHeader("X-Context-Revision","1");return r;}
 @org.junit.jupiter.api.Test void focusedRegressions()throws Exception{main(new String[0]);}
 public static void main(String[] args)throws Exception{
  var identities=mock(ApplicationSessionIdentity.class);var subject=mock(ApplicationSessionIdentity.Verified.class);when(identities.requireCurrent(any(),any())).thenReturn(subject);
  var service=mock(GruRecordExportService.class);var json=new PermissionSchema();var c=new GruRecordExportController(identities,service,json);
  var r=request();String key="original-key-/??#-001";r.addHeader("Idempotency-Key",key);when(service.recovery(subject,"1",id(1),key)).thenReturn(json.object().put("status","NOT_FOUND"));
  var response=new MockHttpServletResponse();var envelope=c.recovery(r,response,null,id(1));assertEquals("1",envelope.required("resource_revision").textValue());verify(service).recovery(subject,"1",id(1),key);assertEquals(envelope.path("request_id").textValue(),response.getHeader("X-Request-Id"));
  var invalid=request();invalid.setQueryString("unexpected=1");assertThrows(SchemaRejection.class,()->c.basis(invalid,new MockHttpServletResponse(),null,id(1)));verify(service,never()).basis(any(),anyString(),anyString());
  var range=request();range.addHeader("Range","bytes=0-1");assertThrows(SchemaRejection.class,()->c.content(range,new MockHttpServletResponse(),null,id(1)));verify(service,never()).content(any(),anyString(),anyString());
  for(Class<?> type:new Class<?>[]{GruRecordExportController.class,GruRecordExportService.class}){var gate=type.getAnnotation(ConditionalOnProperty.class);assertArrayEquals(new String[]{"gru-records.enabled","gru-record-export.enabled"},gate.name());assertFalse(gate.matchIfMissing());assertEquals("true",gate.havingValue());}
  var access=mock(EnterpriseAccess.class);var admission=mock(EnterpriseAccess.Admission.class);when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(id(1),"1","2","3"));when(access.execute(eq(subject),any())).thenAnswer(i->{Function<EnterpriseAccess.Admission,Object> f=i.getArgument(1);return f.apply(admission);});
  var beans=mock(ListableBeanFactory.class);when(beans.getBeanNamesForType(GruRecordExportController.class,false,false)).thenReturn(new String[0]);var availability=new GruRecordExportFeatureAvailability(access,beans,json);
  assertFalse(availability.read(subject,"1").path("features").path("gru_record_export").booleanValue());when(beans.getBeanNamesForType(GruRecordExportController.class,false,false)).thenReturn(new String[]{"actual-controller"});assertTrue(availability.read(subject,"1").path("features").path("gru_record_export").booleanValue());assertThrows(TenantAccessException.class,()->availability.read(subject,"2"));
  System.out.println("PASS 5 controller/availability cases (original key, query, range, dual gate, actual registration/context)");
 }
}
