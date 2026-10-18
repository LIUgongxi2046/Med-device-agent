package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import java.math.BigInteger;import java.time.LocalDateTime;
public final class ModelEnablementPolicyProtocol {
 private ModelEnablementPolicyProtocol(){}
 public record Original(String tenantId,String sourceVersion,String sha256,String objectKey,String objectVersion,BigInteger sizeBytes,String mediaType){InterfaceProtocol.ObjectRef reference(){return new InterfaceProtocol.ObjectRef(objectKey,objectVersion,sha256,sizeBytes.toString(),mediaType);}}
 public record Stored(String tenantId,String projectId,String preparationId,String configId,String modelRevision,String profileRevision,
  String sourceId,String sourceVersion,String rightsRevision,String sourceSha256,String originalObject,String originalAuthorizationSha256,
  String snapshotId,String snapshotSha256,String parseVersion,String canonicalRequest,String requestSha256,String operationRequestSha256,
  String originalKey,String originalContext,String originalSession,String authorizationJson,String authorizationSha256,String receiptJson,
  String operationId,String auditId,String workflowSha256,String createdBy,LocalDateTime createdAt,String classification){}
}
