package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TaskNodeProducerControllerQueryTest {
 TaskNodeProducerControllerQueryTest() throws Exception { }
 final ApplicationSessionIdentity identities=mock(ApplicationSessionIdentity.class);
 final TaskNodeProducerService service=mock(TaskNodeProducerService.class);
 final TaskNodeProducerController controller=new TaskNodeProducerController(identities,service,new PermissionSchema());
 final String rule="11111111-1111-4111-8111-111111111111",task="22222222-2222-4222-8222-222222222222";
 MockHttpServletRequest request(){var q=new MockHttpServletRequest("GET","/api/tasks/"+task+"/node-producer-preparation");q.addHeader("X-Schema-Version","1.0.0");q.addHeader("X-Context-Revision","1");q.setQueryString("original_rule_id="+rule);q.addParameter("original_rule_id",rule);return q;}
 @Test void exactPreparationQueryReachesCurrentAuthenticatedService()throws Exception {
  var q=request();var identity=mock(ApplicationSessionIdentity.Verified.class);when(identities.requireCurrent(q,null)).thenReturn(identity);when(service.preparation(identity,"1",task,rule)).thenReturn(new PermissionSchema().object());
  var response=new MockHttpServletResponse();assertNotNull(controller.preparation(task,rule,q,response,null));verify(service).preparation(identity,"1",task,rule);assertEquals("no-store",response.getHeader("Cache-Control"));
 }
 @Test void duplicateUnknownAndMalformedQueriesNeverReachService() {
  for(String query:java.util.List.of("original_rule_id="+rule+"&original_rule_id="+rule,"original_rule_id="+rule+"&other=1","other="+rule,"original_rule_id=%31"+rule.substring(1),"original_rule_id="+rule+"&")) {
   var q=request();q.setQueryString(query);assertThrows(SchemaRejection.class,()->controller.preparation(task,rule,q,new MockHttpServletResponse(),null));
  }
  var q=request();q.addParameter("original_rule_id",rule);assertThrows(SchemaRejection.class,()->controller.preparation(task,rule,q,new MockHttpServletResponse(),null));verifyNoInteractions(service,identities);
 }
 @Test void otherEndpointsStillRejectEveryQuery() {
  var q=request();assertThrows(SchemaRejection.class,()->controller.list(task,q,new MockHttpServletResponse(),null));verifyNoInteractions(service,identities);
 }
}
