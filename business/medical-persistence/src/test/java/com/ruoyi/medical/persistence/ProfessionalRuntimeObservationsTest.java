package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.FormalEvaluationCoverageTest.*;
/** Actual lifecycle code over explicitly synthetic SQL, transaction and mTLS doubles. No real supervisor/DB claim. */
class ProfessionalRuntimeObservationsTest {
 final PermissionSchema json=new PermissionSchema();final ServiceCertificateIdentity identities=mock(ServiceCertificateIdentity.class);final ServiceCertificateIdentity.Verified certificate=mock(ServiceCertificateIdentity.Verified.class);
 final TenantTransaction transactions=mock(TenantTransaction.class);final JdbcTemplate db=mock(JdbcTemplate.class);final HttpServletRequest http=mock(HttpServletRequest.class);
 final ProfessionalRuntimeObservations service=new ProfessionalRuntimeObservations(identities,transactions,db,json);
 final Map<String,Map.Entry<String,String>> observations=new HashMap<>();String state="OPEN";long revision=1;String serviceUri="spiffe://fixture.invalid/runtime";
 ProfessionalRuntimeObservationsTest()throws Exception{
  when(identities.require(http)).thenReturn(certificate);when(certificate.serviceUri()).thenAnswer(i->serviceUri);when(certificate.certificateSha256()).thenReturn(H);when(certificate.trustRevision()).thenReturn(id(8));
  when(transactions.execute(any())).thenAnswer(i->((Function<TenantWatermarks,Object>)i.getArgument(0)).apply(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE)));
  doAnswer(i->{String sql=i.getArgument(0);if(sql.startsWith("SELECT created_by"))return List.of(id(7));if(sql.startsWith("SELECT auth_epoch"))return List.of(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));if(sql.startsWith("SELECT project_id")){String start=json.canonical(json.object().set("binding",binding()));var tree=json.object();tree.set("binding",binding());tree.put("session_id",id(6));tree.put("purpose","EVALUATION_VERIFICATION");start=json.canonical(tree);return List.of(new ProfessionalRuntimeObservations.Session(id(9),id(7),"spiffe://fixture.invalid/runtime",H,id(8),state,revision,start,org.apache.commons.codec.digest.DigestUtils.sha256Hex(start)));}if(sql.startsWith("SELECT observation_id")){String key=i.getArgument(i.getArguments().length-1);return observations.containsKey(key)?List.of(observations.get(key)):List.of();}throw new AssertionError(sql);}).when(db).query(anyString(),any(RowMapper.class),any(Object[].class));
  doAnswer(i->{String sql=i.getArgument(0);if(sql.startsWith("INSERT INTO professional_runtime_observation")){observations.put(i.getArgument(6),Map.entry(i.getArgument(3),i.getArgument(5)));return 1;}if(sql.startsWith("UPDATE professional_runtime_session")){state="CLOSED";revision++;return 1;}throw new AssertionError("Unexpected mutation "+sql);}).when(db).update(anyString(),any(Object[].class));
 }
 ObjectNode binding(){return json.object().put("tenant_id",id(1)).put("run_id",id(2)).put("step_id",id(3)).put("action_id",id(4)).put("lease_generation",1).put("input_sha256",H).put("step_envelope_sha256",H);}
 ObjectNode close(){var r=json.object().put("session_id",id(6)).put("reason","HOST_EXIT");r.set("binding",binding());return r;}
 @Test void closePersistsOriginalObservationAndClosesOnlySession()throws Exception{var result=service.close(http,close());assertEquals("CLOSED",result.path("admission").asText());assertEquals(1,observations.size());assertEquals(2,revision);verify(db,never()).update(contains("run_step"),any(Object[].class));verify(db,never()).update(contains("invocation"),any(Object[].class));}
 @Test void identicalRetryReusesObservationWithoutSecondClosure()throws Exception{var a=service.close(http,close());var b=service.close(http,close());assertEquals(a,b);assertEquals(1,observations.size());assertEquals(2,revision);}
 @Test void failureDoesNotTurnProcessExitedIntoRunSuccess()throws Exception{var r=close().put("host_process_exited",true);var ack=service.failure(http,r);assertEquals("REQUIRED",ack.path("reconciliation").asText());assertFalse(ack.has("completion"));assertEquals("CLOSED",state);}
 @Test void wrongOriginalServiceCannotWrite()throws Exception{serviceUri="spiffe://fixture.invalid/other";assertThrows(TenantAccessException.class,()->service.close(http,close()));assertEquals(0,observations.size());}
 @Test void unknownApprovalFlagRejected(){var r=close().put("approved",true);assertThrows(TenantAccessException.class,()->ProfessionalRuntimeObservations.validate(r,"ADMISSION_CLOSED"));}
 @Test void fractionalLeaseRejected(){var r=close();((ObjectNode)r.get("binding")).put("lease_generation",1.2);assertThrows(TenantAccessException.class,()->ProfessionalRuntimeObservations.validate(r,"ADMISSION_CLOSED"));}
 @Test void extraBoundIdentityRejected(){var r=close();((ObjectNode)r.get("binding")).put("actor_id",id(99));assertThrows(TenantAccessException.class,()->ProfessionalRuntimeObservations.validate(r,"ADMISSION_CLOSED"));}
 @Test void processExitRequiresBoolean(){var r=close().put("host_process_exited","true");assertThrows(TenantAccessException.class,()->ProfessionalRuntimeObservations.validate(r,"HOST_FAILURE"));}
 @Test void malformedHashRejected(){var r=close();((ObjectNode)r.get("binding")).put("input_sha256","x");assertThrows(IllegalArgumentException.class,()->ProfessionalRuntimeObservations.validate(r,"ADMISSION_CLOSED"));}
 @Test void finishFileTraversalRejected(){var r=json.object().put("session_id",id(6));r.set("binding",binding());r.putObject("observation").put("session_id",id(6)).put("finish_reason","completed").put("final_response","untrusted").set("binding",binding());r.putArray("files").add(json.object().put("path","../outside").put("sha256",H).put("size_bytes",1));assertThrows(TenantAccessException.class,()->ProfessionalRuntimeObservations.validate(r,"HOST_FINISH"));}
}
