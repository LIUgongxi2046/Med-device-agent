package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

/** Approved configuration data only; never a service credential, business grant or execution proof. */
public final class AgentExecutionPolicyProtocol {
 private AgentExecutionPolicyProtocol(){}
 public record SourceSelectionInput(String mode,List<PermissionProtocol.Basis> sources) {public SourceSelectionInput{sources=List.copyOf(sources);}}
 public record SourceSelection(PermissionProtocol.Basis version,String mode,List<PermissionProtocol.Basis> sources) {public SourceSelection{sources=List.copyOf(sources);}}
 public record RetrievalProfileInput(String mode,int maximumResults,List<String> permittedFields,PermissionProtocol.Basis indexConfiguration) {public RetrievalProfileInput{permittedFields=List.copyOf(permittedFields);}}
 public record RetrievalProfile(PermissionProtocol.Basis version,String mode,int maximumResults,List<String> permittedFields,PermissionProtocol.Basis indexConfiguration) {public RetrievalProfile{permittedFields=List.copyOf(permittedFields);}}
 public record NetworkPolicyInput(List<String> httpsOrigins,boolean externalRequestsAllowed) {public NetworkPolicyInput{httpsOrigins=List.copyOf(httpsOrigins);}}
 public record NetworkPolicy(PermissionProtocol.Basis version,List<String> httpsOrigins,boolean externalRequestsAllowed) {public NetworkPolicy{httpsOrigins=List.copyOf(httpsOrigins);}}
 public record Worker(String serviceUri,String audience){}
 /** A template whose concrete resource owner is resolved by the actual run/step consumer. */
 public record ActionBinding(String resourceKind,String targetSelector,String action,String purpose,
   PermissionProtocol.Basis fixedVersion,List<String> artifactKinds) {public ActionBinding{artifactKinds=List.copyOf(artifactKinds);}}
 public record NodeBinding(String nodeKind,PermissionProtocol.Basis roleVersion,PermissionProtocol.Basis modelVersion,
   PermissionProtocol.Basis tokenizerVersion,AgentReleaseProtocol.SchemaBoundArtifact tokenizerArtifact,
   JsonNode generationParameters,List<ActionBinding> actions) {
  public NodeBinding {generationParameters=generationParameters.deepCopy();actions=List.copyOf(actions);}
  @Override public JsonNode generationParameters(){return generationParameters.deepCopy();}
 }
 /** Public author input intentionally contains no policy/component IDs or approval claims. */
 public record DefinitionInput(String schemaVersion,String projectId,String businessPurpose,String profileEvidencePurpose,List<String> allowedClassifications,
   SourceSelectionInput sourceSelection,List<RetrievalProfileInput> retrievalProfiles,NetworkPolicyInput networkPolicy,
   Worker worker,List<NodeBinding> bindings,String locale,String timezone,String environment,String region,
   boolean trainingAllowed,String validFrom,String validUntil) {
  public DefinitionInput{allowedClassifications=List.copyOf(allowedClassifications);retrievalProfiles=List.copyOf(retrievalProfiles);bindings=List.copyOf(bindings);}
 }
 public record Definition(String schemaVersion,String projectId,String businessPurpose,String profileEvidencePurpose,List<String> allowedClassifications,
   SourceSelection sourceSelection,List<RetrievalProfile> retrievalProfiles,NetworkPolicy networkPolicy,
   Worker worker,List<NodeBinding> bindings,String locale,String timezone,String environment,String region,
   boolean trainingAllowed,String validFrom,String validUntil) {
  public Definition{allowedClassifications=List.copyOf(allowedClassifications);retrievalProfiles=List.copyOf(retrievalProfiles);bindings=List.copyOf(bindings);}
 }
 public record Author(String schemaVersion,DefinitionInput definition,String reason){}
 public record Decide(String schemaVersion,String expectedRevision,PermissionProtocol.Basis policyVersion,String decision,String reason){}
 public record Fixed(String schemaVersion,String tenantId,String policyId,String versionId,String authorRecordId,
   Definition definition,String definitionSha256,String createdBy,String createdAt,String classification){}
 public record Decision(String schemaVersion,String tenantId,String projectId,String policyId,String versionId,String definitionSha256,
   String decisionRecordId,String previousStatus,String previousRevision,String decision,String status,String revision,
   String actorId,String decidedAt,String authorityGrantId,String authorityRevision,String reason){}
 public record Row(String tenantId,String projectId,String policyId,String versionId,String authorRecordId,String fixedJson,String definitionHash,
   String registryHash,String registryKind,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification){}
 public record State(String tenantId,String projectId,String policyId,String versionId,String status,BigInteger revision,
   String latestDecisionId,LocalDateTime updatedAt){}
 public record Component(String tenantId,String projectId,String policyId,String policyVersionId,String componentId,String kind,
   String contentJson,String contentHash,String registryHash,String registryKind,String schemaVersion,String schemaManifest,
   String createdBy,LocalDateTime createdAt,String classification){}
 public record DecisionRow(String tenantId,String projectId,String policyId,String versionId,String decisionRecordId,String definitionHash,
   String decisionJson,String decisionHash,String decision,String status,BigInteger beforeRevision,BigInteger afterRevision,
   String actorId,LocalDateTime decidedAt,String schemaVersion,String schemaManifest,String classification){}
 public record DomainRow(String versionId,String contentHash,String entityKind,String projectId,String resourceId,String kind,String status,String executionMode){}
 public record ListQuery(String schemaVersion,String projectId,int limit,String cursor){}
}
