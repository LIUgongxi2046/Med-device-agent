package com.ruoyi.medical.persistence;
import java.util.List;
/** Closed authored data. None of these records is execution/approval authority. */
public final class FormalEvaluationContextProtocol {
 private FormalEvaluationContextProtocol(){}
 public record Schema(String schemaRef,String schemaSha256){}
 public record Node(String nodeKind,PermissionProtocol.Basis role,PermissionProtocol.Basis prompt,PermissionProtocol.Basis contextAssembler,Schema inputSchema,Schema outputSchema){}
 public record Definition(String schemaVersion,String projectId,PermissionProtocol.Basis candidate,PermissionProtocol.Basis executionPolicy,List<Node> nodeBindings){public Definition{nodeBindings=List.copyOf(nodeBindings);}}
 public record BoundStart(String schemaVersion,FormalEvaluationExecutionProtocol.Start start,PermissionProtocol.Basis contextBinding){}
 public record Author(String schemaVersion,Definition definition,String reason){}
 public record Decide(String schemaVersion,PermissionProtocol.Basis binding,String expectedRevision,String decision,String reason){}
}
