package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import java.math.BigInteger;
import java.time.LocalDateTime;
/** Immutable, enterprise-only input registration. It contains no score, acceptance or eligibility claim. */
public final class FormalEvaluationInputProtocol {
 private FormalEvaluationInputProtocol(){}
 public record Original(String tenantId,String sourceVersion,String sha256,String objectKey,String objectVersion,BigInteger sizeBytes,String mediaType){
  public InterfaceProtocol.ObjectRef reference(){return new InterfaceProtocol.ObjectRef(objectKey,objectVersion,sha256,sizeBytes.toString(),mediaType);}
 }
 public record Registration(String tenantId,String projectId,String artifactId,String sourceId,String sourceVersion,String rightsRevision,
  String purpose,String accessSetId,String dependencySha256,String documentSha256,String objectVersion,String schemaRef,String documentSchemaSha256,
  String canonicalDocument,String canonicalRequest,String requestBodySha256,String operationRequestSha256,String idempotencyKey,
  String requestContext,String requestSession,String authorizationJson,String authorizationSha256,String receiptJson,String operationId,String auditId,
  String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification){}
}
