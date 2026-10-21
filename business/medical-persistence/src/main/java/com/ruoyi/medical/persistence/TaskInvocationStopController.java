package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import java.math.BigInteger;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Original adapter stop signal, not a provider-terminal receipt or settlement authority. */
@RestController
@ConditionalOnProperty(name="medical.professional-runtime.task-enabled",havingValue="true")
public final class TaskInvocationStopController {
 private final JdbcTemplate db;private final ServiceCertificateIdentity identities;private final FormalInvocationSchema schema;private final TenantTransaction transactions;
 public TaskInvocationStopController(DataSource data,ServiceCertificateIdentity identities,FormalInvocationSchema schema,TenantTransaction transactions){this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.identities=identities;this.schema=schema;this.transactions=transactions;}
 @PostMapping(value={"/internal/v1/task-invocations/{id}/cancellation-status","/internal/v1/section-writer-invocations/{id}/cancellation-status"},consumes="application/json")
 public JsonNode status(HttpServletRequest request,HttpServletResponse response,@PathVariable("id")String invocation)throws Exception {
  TaskInvocationController.recoveryHeaders(request);WireValues.uuid(invocation);var identity=identities.require(request);
  var input=schema.parse(new String(FormalInvocationController.body(request),java.nio.charset.StandardCharsets.UTF_8));
  if(input.size()!=4||!input.isObject()||!"1.0.0".equals(input.path("schema_version").asText()))bad();
  for(String key:List.of("tenant_id","original_actor_id","dispatch_attempt_id")){if(!input.path(key).isTextual())bad();WireValues.uuid(input.path(key).asText());}
  String tenant=input.required("tenant_id").asText(),actor=input.required("original_actor_id").asText(),attempt=input.required("dispatch_attempt_id").asText();
  var epochs=one(db.query("SELECT auth_epoch,governance_epoch FROM tenant WHERE tenant_id=? LIMIT 2",(r,n)->new TenantWatermarks(r.getBigDecimal(1).toBigIntegerExact(),r.getBigDecimal(2).toBigIntegerExact()),tenant));
  try(var ignored=TenantScope.open(new TenantScope.Identity(tenant,actor,BigInteger.ZERO,epochs.authEpoch(),epochs.governanceEpoch()))){
   var result=transactions.execute(status->org.springframework.util.function.ThrowingSupplier.of(()->{
    var row=one(db.queryForList("SELECT a.service_uri,a.certificate_sha256,a.trust_revision,r.status,r.dispatch_closed_at FROM invocation i JOIN formal_invocation_attempt a ON a.tenant_id=i.tenant_id AND a.invocation_id=i.invocation_id JOIN task_run r ON r.tenant_id=i.tenant_id AND r.run_id=i.run_id AND r.execution_kind='TASK' WHERE i.tenant_id=? AND i.invocation_id=? AND i.created_by=? AND a.attempt_id=? AND a.created_by=i.created_by AND a.input_sha256=i.input_sha256 AND a.lease_generation=i.lease_generation AND a.dispatch_generation=1 AND i.dispatch_generation=1 AND a.schema_version='1.0.0' AND a.schema_manifest_sha256=? LIMIT 2",tenant,invocation,actor,attempt,schema.manifestSha256()));
    if(!identity.serviceUri().equals(row.get("service_uri"))||!identity.certificateSha256().equals(row.get("certificate_sha256"))||!identity.trustRevision().equals(row.get("trust_revision")))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    boolean stop=row.get("dispatch_closed_at")!=null&&Set.of("CANCEL_REQUESTED","CANCELLED","FAILED").contains(row.get("status"));
    var fresh=identities.require(request);if(!identity.serviceUri().equals(fresh.serviceUri())||!identity.certificateSha256().equals(fresh.certificateSha256())||!identity.trustRevision().equals(fresh.trustRevision()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("schema_version","1.0.0").put("invocation_id",invocation).put("dispatch_attempt_id",attempt).put("stop_requested",stop).put("strategy","TRANSPORT_ABORT").put("provider_terminal",false);
   }).get());return FormalInvocationResponses.envelope(result,"1",response);
  }
 }
 private static <T>T one(List<T> rows){if(rows.size()!=1)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return rows.get(0);}
 private static void bad(){throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);}
 @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException e,HttpServletResponse response)throws java.io.IOException{new IdentityErrorResponseWriter().write(response,e.code()==ErrorCode.ACCESS_DENIED?403:409,e.code());}
}
