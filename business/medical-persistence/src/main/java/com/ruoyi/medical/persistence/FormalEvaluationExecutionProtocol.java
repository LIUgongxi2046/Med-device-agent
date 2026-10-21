package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.WireValues;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;

/** Data contract only. No constructor in this class confers execution or acceptance authority. */
public final class FormalEvaluationExecutionProtocol {
 private FormalEvaluationExecutionProtocol(){}
 public record Start(String schemaVersion,String evaluationId,String definitionSha256,String expectedEvaluationRevision,
   PermissionProtocol.Basis candidate,PermissionProtocol.Basis executionPolicy,
   PermissionProtocol.Basis enterpriseBudget,PermissionProtocol.Basis parentBudget) {
  public Start {
   if(!"1.0.0".equals(schemaVersion)||expectedEvaluationRevision==null||!expectedEvaluationRevision.matches("[1-9][0-9]{0,19}"))bad();
   WireValues.uuid(evaluationId);WireValues.hash(definitionSha256);
   for(var b:List.of(candidate,executionPolicy,enterpriseBudget,parentBudget))basis(b);
  }
 }
 public record Cell(String evaluationId,String definitionSha256,PermissionProtocol.Basis candidate,
   PermissionProtocol.Basis policy,String sampleId,String sourceFamily,String split,int repeatIndex,
   PermissionProtocol.Basis input,PermissionProtocol.Basis expected,String runtimeManifestSha256) {
  public Cell {
   WireValues.uuid(evaluationId);WireValues.hash(definitionSha256);WireValues.uuid(sampleId);WireValues.uuid(sourceFamily);
   basis(candidate);basis(policy);basis(input);basis(expected);WireValues.hash(runtimeManifestSha256);
   if(!List.of("DEVELOPMENT","TUNING","ACCEPTANCE").contains(split)||repeatIndex<0||repeatIndex>=100)bad();
  }
 }
 public record Node(Cell cell,String nodeKind,PermissionProtocol.Basis role,PermissionProtocol.Basis model,
   PermissionProtocol.Basis tokenizer,String parametersSha256) {
  public Node {
   Objects.requireNonNull(cell);basis(role);WireValues.hash(parametersSha256);
   if(nodeKind==null||!nodeKind.matches("N0[0-9]"))bad();
   if((model==null)!=(tokenizer==null))bad();if(model!=null){basis(model);basis(tokenizer);}
  }
 }
 public record Artifact(String artifactId,String sha256,String objectVersion,String schemaRef) {
  public Artifact {WireValues.uuid(artifactId);WireValues.hash(sha256);if(objectVersion==null||objectVersion.isBlank()||schemaRef==null||schemaRef.isBlank())bad();}
 }
 public record Invocation(String invocationId,String actionId,Artifact modelResult,Artifact providerCapture,
   String requestSha256,String sendManifestSha256,String settlementReceiptSha256) {
  public Invocation {WireValues.uuid(invocationId);WireValues.uuid(actionId);Objects.requireNonNull(modelResult);Objects.requireNonNull(providerCapture);WireValues.hash(requestSha256);WireValues.hash(sendManifestSha256);WireValues.hash(settlementReceiptSha256);}
 }
 public record Record(Node node,String runId,String stepId,long leaseGeneration,Artifact inputManifest,
   Artifact stepEnvelope,Artifact stepResult,Artifact sessionExport,List<Invocation> invocations,
   List<Artifact> actualArtifacts,List<String> checkIds,String runReceiptSha256,String stepReceiptSha256) {
  public Record {
   Objects.requireNonNull(node);WireValues.uuid(runId);WireValues.uuid(stepId);if(leaseGeneration<1)bad();
   Objects.requireNonNull(inputManifest);Objects.requireNonNull(stepEnvelope);Objects.requireNonNull(stepResult);
   invocations=List.copyOf(invocations);actualArtifacts=List.copyOf(actualArtifacts);checkIds=List.copyOf(checkIds);
   // Shape is not authority: only the private deterministic runtime proof can complete this branch.
   if(deterministic(node)){if(sessionExport!=null||!invocations.isEmpty()||!checkIds.isEmpty()||actualArtifacts.size()!=1)bad();}
   else Objects.requireNonNull(sessionExport);
   for(var id:checkIds)WireValues.uuid(id);WireValues.hash(runReceiptSha256);WireValues.hash(stepReceiptSha256);
  }
 }
 static boolean deterministic(Node node){return java.util.Set.of("N03","N06").contains(node.nodeKind())&&node.model()==null&&node.tokenizer()==null;}
 private static void basis(PermissionProtocol.Basis b){Objects.requireNonNull(b);WireValues.uuid(b.version_id());WireValues.hash(b.content_sha256());}
 private static void bad(){throw new IllegalArgumentException("FORMAL_EVALUATION_BINDING_INVALID");}
}
