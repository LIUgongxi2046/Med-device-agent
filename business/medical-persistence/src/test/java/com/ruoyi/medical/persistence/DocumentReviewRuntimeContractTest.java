package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Offline negative authority cases. Written without executing tests, providers or runtimes. */
class DocumentReviewRuntimeContractTest {
 @Test void authorityAndSupervisionArePrivateCapabilities(){
  for(Class<?> type:new Class<?>[]{DocumentReviewRuntimeAuthority.Verified.class,DocumentReviewRuntimeAdapter.Envelope.class,DocumentReviewRuntimeAdapter.Session.class,DocumentReviewSupervisor.Inspection.class,DocumentReviewSupervisor.Admitted.class,DocumentReviewSupervisor.Closed.class,DocumentReviewSupervisor.ModelRequest.class,DocumentReviewControlAccess.Proof.class})
   for(var constructor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()),type.getName());
 }
 @Test void zeroOwnedProcessExitIsRequired()throws Exception{
  var json=new JsonMapper();var method=DocumentReviewSupervisor.class.getDeclaredMethod("successfulExit",com.fasterxml.jackson.databind.JsonNode.class);method.setAccessible(true);
  var good=json.readTree("{\"exit\":{\"kind\":\"OWNED_WAITID\",\"code\":\"EXITED\",\"status\":0}}");assertDoesNotThrow(()->method.invoke(null,good));
  for(String changed:new String[]{"{\"exit\":{\"kind\":\"HOST_REPORTED\",\"code\":\"EXITED\",\"status\":0}}","{\"exit\":{\"kind\":\"OWNED_WAITID\",\"code\":\"KILLED\",\"status\":0}}","{\"exit\":{\"kind\":\"OWNED_WAITID\",\"code\":\"EXITED\",\"status\":1}}"})assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(null,json.readTree(changed)));
 }
 @Test void bodyReviewUsesSeparateServiceRoutes(){
  assertTrue(DocumentReviewRuntimeSecurityConfiguration.ROUTES.contains("/internal/v1/document-review-runtime/open-workflow"));
  assertTrue(DocumentReviewRuntimeSecurityConfiguration.ROUTES.contains("/internal/v1/document-review-runtime/completed-original"));
  assertTrue(DocumentReviewRuntimeSecurityConfiguration.ROUTES.stream().noneMatch(s->s.contains("source-verifier-runtime")||s.contains("plan-draft-runtime")));
 }
 @Test void terminalResultIsNotCallerConstructible(){
  for(var constructor:DocumentReviewResultService.VerifiedCompletion.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
  for(var constructor:DocumentReviewInvocationService.Terminal.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
 }
}
