package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.WireValues;
import java.util.List;

public final class DocumentRevisionProtocol {
    private DocumentRevisionProtocol() { }
    public record PreparedObject(String artifactId, String kind, String schemaRef, S3ObjectWriter.WriteIntent intent,
            boolean writing, InterfaceProtocol.ObjectRef reference) {
        public PreparedObject {
            WireValues.uuid(artifactId);
            if (!List.of("DOCUMENT_FIELDS", "DOCUMENT_EVIDENCE").contains(kind))
                throw new IllegalArgumentException("正文投影类型无效");
            if (reference != null && (!writing || !intent.objectKey().equals(reference.objectKey())
                    || !intent.sha256().equals(reference.sha256()) || !intent.sizeBytes().equals(reference.sizeBytes())))
                throw new IllegalArgumentException("正文投影固定对象不一致");
        }
    }
    public record Command(String documentRevision, String requestId, String uploadId, String authorizationSha256,
            List<PreparedObject> projections) {
        public Command {
            WireValues.uuid(documentRevision); WireValues.uuid(requestId); WireValues.uuid(uploadId);
            WireValues.hash(authorizationSha256); projections = List.copyOf(projections);
            if (projections.size() != 2 || projections.get(0).kind().equals(projections.get(1).kind()))
                throw new IllegalArgumentException("正文保存必须具有两个独立投影");
        }
    }
    public record NodeRow(String nodeId, String parentId, long ordinal, String kind, String sha256) { }
    public record Section(String sectionId, String nodeId) { }
    public record FieldRow(String fieldId, String nodeId, String definitionId, String valueJson) { }
    public record EvidenceRow(String linkId, String nodeId, String evidenceId, String claimId, String valueJson) { }
    public record UsageRow(String factId, String nodeId, String fieldId) { }
}
