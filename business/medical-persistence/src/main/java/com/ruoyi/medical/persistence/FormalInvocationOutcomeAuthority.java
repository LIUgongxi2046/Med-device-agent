package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.ruoyi.medical.contracts.*;import com.ruoyi.medical.identity.ServiceCertificateIdentity;import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;import java.math.BigInteger;import java.util.*;import javax.sql.DataSource;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.util.function.ThrowingSupplier;
/** Original authenticated adapter may record facts for an actual dispatch after user grants/lease expire.
 * This authority never returns dispatch permission or a user content-read proof. */
public final class FormalInvocationOutcomeAuthority {
 private final ServiceCertificateIdentity services;private final TenantTransaction transactions;private final JdbcTemplate db;private final FormalInvocationSchema schema;private final SchemaCatalogue shared;private final int maximumBytes;
 public FormalInvocationOutcomeAuthority(ServiceCertificateIdentity services,TenantTransaction transactions,DataSource data,FormalInvocationSchema schema,int maximumBytes)throws Exception{this.services=services;this.transactions=transactions;this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.shared=SchemaCatalogue.fromClasspath();if(maximumBytes<1)throw new IllegalArgumentException();this.maximumBytes=maximumBytes;}
 @FunctionalInterface public interface Work<T>{T apply(VerifiedOutcome proof)throws Exception;}
 public final class VerifiedOutcome {
  private final HttpServletRequest http;private final String tenant,actor,invocation,project,attempt,digest,classification;private final byte[] bytes;private final JsonNode binding,result;private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();private boolean active=true;
  private VerifiedOutcome(HttpServletRequest http,String tenant,String actor,String invocation,JsonNode binding,JsonNode result,byte[] bytes)throws Exception{this.http=http;this.tenant=tenant;this.actor=actor;this.invocation=invocation;this.binding=binding.deepCopy();this.result=result.deepCopy();this.bytes=bytes.clone();project=text(binding,"project_id");attempt=text(binding,"attempt_id");classification=text(binding,"classification");digest=schema.hash(binding);}
  public String tenantId(){return tenant;}public String actorId(){return actor;}public String projectId(){return project;}public String invocationId(){return invocation;}public String attemptId(){return attempt;}public String classification(){return classification;}public byte[] bytes(){return bytes.clone();}public JsonNode result(){return result.deepCopy();}public JsonNode binding(){return binding.deepCopy();}public String bindingSha256(){return digest;}
  public void requireActive()throws Exception{if(!active||Thread.currentThread()!=thread||TenantScope.requireCurrent()!=scope||!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);var actual=services.require(http);if(!actual.serviceUri().equals(text(binding,"service_uri"))||!actual.certificateSha256().equals(text(binding,"certificate_sha256"))||!actual.trustRevision().equals(text(binding,"trust_revision")))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
  public String kind(){return "MODEL_RESULT";}
  public String schemaRef(){return shared.schemaRef("model-stream","ModelResult");}public String schemaSha256(){return shared.schemaDigest("model-stream");}
  public <T>T current(Work<T> work)throws Exception{return execute(http,tenant,actor,invocation,bytes,now->{if(!digest.equals(now.digest))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);return work.apply(now);});}
  public void requireCurrent()throws Exception{current(now->null);}
 }
 public <T>T execute(HttpServletRequest http,String tenant,String actor,String invocation,byte[] resultBytes,Work<T> work)throws Exception{
  WireValues.uuid(tenant);WireValues.uuid(actor);WireValues.uuid(invocation);Objects.requireNonNull(work);var service=services.require(http);
  if(resultBytes==null||resultBytes.length<1||resultBytes.length>maximumBytes)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
  var result=shared.bindPublished(shared.schemaRef("model-stream","ModelResult"),shared.schemaDigest("model-stream"),resultBytes);var value=schema.parse(result.canonicalJson());byte[] canonical=ContentReadAuthorization.canonicalBytes(value);
  var epochs=one(db.query("SELECT auth_epoch,governance_epoch FROM tenant WHERE tenant_id=? LIMIT 2",(r,n)->new TenantWatermarks(r.getBigDecimal(1).toBigIntegerExact(),r.getBigDecimal(2).toBigIntegerExact()),tenant));
  try(var scope=TenantScope.open(new TenantScope.Identity(tenant,actor,BigInteger.ZERO,epochs.authEpoch(),epochs.governanceEpoch()))){return transactions.execute(ignored->ThrowingSupplier.of(()->{
   var b=schema.parse(one(db.query("""
    SELECT JSON_OBJECT('project_id',i.project_id,'invocation_id',i.invocation_id,'run_id',i.run_id,'step_id',i.step_id,
      'action_id',i.action_id,'config_revision',i.config_revision,'context_manifest_id',i.context_manifest_id,
      'input_sha256',i.input_sha256,'actual_send_sha256',i.actual_send_sha256,'actual_send_manifest',i.actual_send_manifest,
      'created_by',i.created_by,'classification',i.classification,'attempt_id',a.attempt_id,'service_uri',a.service_uri,
      'certificate_sha256',a.certificate_sha256,'trust_revision',a.trust_revision,'lease_generation',CAST(a.lease_generation AS CHAR),
      'request_sha256',a.request_sha256,'canonical_request',b.canonical_request,'binding_request_sha256',b.request_sha256)
    FROM invocation i JOIN formal_invocation_attempt a ON a.tenant_id=i.tenant_id AND a.invocation_id=i.invocation_id
      JOIN formal_invocation_binding b ON b.tenant_id=i.tenant_id AND b.invocation_id=i.invocation_id
    WHERE i.tenant_id=? AND i.invocation_id=? AND i.created_by=? AND i.status<>'RESERVED'
      AND i.dispatch_generation=1 AND a.dispatch_generation=1 AND a.input_sha256=i.input_sha256
      AND a.lease_generation=i.lease_generation AND a.created_by=i.created_by AND b.created_by=i.created_by
      AND a.schema_version='1.0.0' AND b.schema_version='1.0.0' AND a.schema_manifest_sha256=? AND b.schema_manifest_sha256=? LIMIT 2 FOR UPDATE
    """,(r,n)->r.getString(1),tenant,invocation,actor,schema.manifestSha256(),schema.manifestSha256())));
   if(!service.serviceUri().equals(text(b,"service_uri"))||!service.certificateSha256().equals(text(b,"certificate_sha256"))||!service.trustRevision().equals(text(b,"trust_revision")))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
   for(String key:List.of("invocation_id","context_manifest_id"))if(!text(b,key).equals(text(value,key)))bad();if(!text(b,"config_revision").equals(text(value,"model_config_revision")))bad();
   var request=schema.parse(text(b,"canonical_request"));if(!schema.hash(request).equals(text(b,"request_sha256"))||!text(b,"request_sha256").equals(text(b,"binding_request_sha256"))||!schema.hash(b.required("actual_send_manifest")).equals(text(b,"actual_send_sha256")))bad();
   FormalInvocationDispatch.verifyEvidence(db,schema,tenant,invocation,b);
   var now=services.require(http);if(!service.serviceUri().equals(now.serviceUri())||!service.certificateSha256().equals(now.certificateSha256())||!service.trustRevision().equals(now.trustRevision()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
   var proof=new VerifiedOutcome(http,tenant,actor,invocation,b,value,canonical);try{proof.requireActive();return work.apply(proof);}finally{proof.active=false;}
  }).get());}
 }
 private static String text(JsonNode n,String k){var v=n.required(k);if(!v.isTextual())bad();return v.textValue();}private static <T>T one(List<T> r){if(r.size()!=1||r.get(0)==null)bad();return r.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
