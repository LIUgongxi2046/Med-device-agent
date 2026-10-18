package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.CommonProtocol;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import java.math.BigInteger;
import java.time.LocalDateTime;
public final class AnnotationAnchorProtocol {
 private AnnotationAnchorProtocol() { }
 public record Create(String schemaVersion,String documentRevision,String expectedRevision,String expectedDocumentRevision,
   String expectedAnchorId,String expectedAnchorRevision,CommonProtocol.Selection selection,String reason,boolean humanConfirmed) { }
 public record Locator(String tenantId,String projectId,String documentId,String annotationId,String originalDocumentRevision) { }
 public record Pointer(String anchorId,BigInteger revision) { }
 public record Anchor(String tenantId,String projectId,String anchorId,String annotationId,String documentRevision,String nodeId,
   String selectionJson,String selectionArtifactId,String selectionSha256,String selectionObjectVersion,String previousAnchorId,
   BigInteger annotationRevision,String reason,String actorId,String createdBy,LocalDateTime createdAt,String classification,String schemaVersion,String schemaManifest) { }
 public record Command(String anchorId,String artifactId,String requestId,String transitionId,String authorizationSha256,
   String authorizationClock,S3ObjectWriter.WriteIntent intent,boolean writing,InterfaceProtocol.ObjectRef reference) { }
}
