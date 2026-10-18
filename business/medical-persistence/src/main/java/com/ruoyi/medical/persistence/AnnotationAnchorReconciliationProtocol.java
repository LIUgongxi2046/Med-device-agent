package com.ruoyi.medical.persistence;
public final class AnnotationAnchorReconciliationProtocol {
 private AnnotationAnchorReconciliationProtocol() { }
 public record Create(String schemaVersion,String documentRevision,String expectedRevision,String expectedDocumentRevision,
   String expectedAnchorId,String expectedAnchorRevision,String reason,boolean humanConfirmed) { }
}
