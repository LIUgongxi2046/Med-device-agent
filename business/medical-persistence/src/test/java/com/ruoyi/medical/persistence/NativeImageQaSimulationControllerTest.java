package com.ruoyi.medical.persistence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.contracts.ErrorCode;
import org.springframework.mock.web.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import static com.ruoyi.medical.persistence.NativeImageQaProtocol.*;

/** Direct controller tests; real authentication/CSRF remains the application's existing filter chain. */
class NativeImageQaSimulationControllerTest {
 private final ApplicationSessionIdentity identities=mock(ApplicationSessionIdentity.class);
 private final EnterpriseAccess access=mock(EnterpriseAccess.class);
 private final ProjectService projects=mock(ProjectService.class);
 private final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
 private final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
 private NativeImageQaSimulationController controller(Context c)throws Exception{
  when(subject.user()).thenReturn(new BusinessSessionProtocol.User(UUID.randomUUID().toString(),"tester","Tester","USER"));
  when(identities.requireCurrent(any(),any())).thenReturn(subject);
  when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(c.tenantId(),"1","1","1"));
  when(access.execute(eq(subject),any())).thenAnswer(i->((Function<EnterpriseAccess.Admission,Object>)i.getArgument(1)).apply(admission));
  return new NativeImageQaSimulationController(identities,access,projects,new PermissionSchema());
 }
 private MockHttpServletRequest request(String body){var r=new MockHttpServletRequest();r.addHeader("X-Schema-Version",VERSION);r.addHeader("X-Context-Revision","1");r.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));return r;}
 @Test void normalApiDrivesFixtureDispatchRecoveryAndReview()throws Exception{
  Context c=NativeImageQaSimulationTest.context(),r=NativeImageQaSimulationTest.reviewer(c);var api=controller(c);
  var profile=api.profile(request(""),new MockHttpServletResponse(),null,c.projectId());assertTrue(profile.required("synthetic").asBoolean());assertEquals(PROVIDER,profile.required("provider").asText());
  var prepared=api.operate(request(write(c)),new MockHttpServletResponse(),null,"prepare");assertEquals(2,prepared.required("reserved_units").asInt());
  var sent=api.operate(request(write(c)),new MockHttpServletResponse(),null,"dispatch");assertFalse(sent.required("formal_evidence_eligible").asBoolean());assertEquals(2,sent.required("settled_units").asInt());
  var recovered=api.operate(request(write(c)),new MockHttpServletResponse(),null,"recover");assertEquals(sent,recovered);
  api.operate(request(write(r)),new MockHttpServletResponse(),null,"dispatch");var reviewed=api.operate(request("{\"answer\":"+write(c)+",\"reviewer\":"+write(r)+"}"),new MockHttpServletResponse(),null,"review");assertTrue(reviewed.required("data").required("synthetic").asBoolean());assertFalse(reviewed.required("data").required("formal_evidence_eligible").asBoolean());assertEquals(4,reviewed.required("settled_units").asInt());
 }
 @Test void currentTenantAndProjectRevocationFailClosed()throws Exception{
  Context c=NativeImageQaSimulationTest.context();var api=controller(c);api.operate(request(write(c)),new MockHttpServletResponse(),null,"dispatch");
  when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(UUID.randomUUID().toString(),"1","1","1"));assertThrows(TenantAccessException.class,()->api.operate(request(write(c)),new MockHttpServletResponse(),null,"recover"));
  when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(c.tenantId(),"1","1","1"));when(projects.read(subject,"1",c.projectId())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,()->api.operate(request(write(c)),new MockHttpServletResponse(),null,"recover"));
 }
 @Test void unregisteredSourceRevisionCannotUseTheFixtureBytes()throws Exception{
  Context c=NativeImageQaSimulationTest.context();var api=controller(c);String raw=write(c).replace(c.images().get(0).rightsRevision(),UUID.randomUUID().toString());assertThrows(IllegalArgumentException.class,()->api.operate(request(raw),new MockHttpServletResponse(),null,"dispatch"));
 }
 @Test void concurrentScopeAdmissionIsBoundedAndFullCapacityStillReusesExistingScopes()throws Exception{
  var api=new NativeImageQaSimulationController(identities,access,projects,new PermissionSchema());
  var existing=new ArrayList<NativeImageQaSimulationController.Runtime>();
  for(int i=0;i<127;i++)existing.add(api.runtimeForScope("tenant","project","actor-"+i));
  int contenders=32;var ready=new CountDownLatch(contenders);var start=new CountDownLatch(1);
  var executor=Executors.newFixedThreadPool(contenders);
  var attempts=new ArrayList<Future<NativeImageQaSimulationController.Runtime>>();
  try{
   for(int i=0;i<contenders;i++){
    String actor="new-actor-"+i;
    attempts.add(executor.submit(()->{
     ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new AssertionError("Admission start timed out");
     try{return api.runtimeForScope("tenant","project",actor);}
     catch(IllegalArgumentException e){assertEquals("SIMULATION_CAPACITY_REACHED",e.getMessage());return null;}
    }));
   }
   assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
   int accepted=0;
   for(int i=0;i<attempts.size();i++){
    var runtime=attempts.get(i).get(10,TimeUnit.SECONDS);
    if(runtime!=null){accepted++;assertSame(runtime,api.runtimeForScope("tenant","project","new-actor-"+i));}
   }
   assertEquals(1,accepted,"Only the final slot may be admitted concurrently");
   for(int i=0;i<existing.size();i++)assertSame(existing.get(i),api.runtimeForScope("tenant","project","actor-"+i));
   assertEquals("SIMULATION_CAPACITY_REACHED",assertThrows(IllegalArgumentException.class,()->api.runtimeForScope("tenant","project","overflow")).getMessage());
   var reuse=new ArrayList<Future<NativeImageQaSimulationController.Runtime>>();
   for(int i=0;i<contenders;i++)reuse.add(executor.submit(()->api.runtimeForScope("tenant","project","actor-0")));
   for(var result:reuse)assertSame(existing.get(0),result.get(10,TimeUnit.SECONDS));
  }finally{start.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
 }
 @Test void remainsOptInAndNeverFormalController(){var config=NativeImageQaSimulationController.class.getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);assertEquals("medical.multimodal-qa-simulation",config.prefix());assertFalse(config.matchIfMissing());assertEquals("true",config.havingValue());}
}
