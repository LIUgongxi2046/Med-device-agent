package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.DocumentRevisionProtocol;
import com.ruoyi.medical.persistence.UploadOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentRevisionMapper {
    List<UploadOperationRow> operation(Map<String,Object> values);
    List<String> completedUpload(Map<String,Object> values);
    int startOperation(Map<String,Object> values);
    int updateCommand(Map<String,Object> values);
    int completeOperation(Map<String,Object> values);
    int saveVersion(Map<String,Object> values);
    int saveRevision(Map<String,Object> values);
    int saveMetadata(Map<String,Object> values);
    int adoptBody(Map<String,Object> values);
    int saveArtifact(Map<String,Object> values);
    int saveBinding(Map<String,Object> values);
    int saveLineage(Map<String,Object> values);
    int saveNode(Map<String,Object> values);
    int saveField(Map<String,Object> values);
    int saveClaim(Map<String,Object> values);
    int saveEvidence(Map<String,Object> values);
    int saveEvidenceProjection(Map<String,Object> values);
    int saveUsage(Map<String,Object> values);
    List<DocumentRevisionProtocol.Section> planSections(Map<String,Object> values);
    List<String> planContent(Map<String,Object> values);
    int saveSection(Map<String,Object> values);
    int saveDocumentSection(Map<String,Object> values);
    int saveWatermarks(Map<String,Object> values);
    int advanceHead(Map<String,Object> values);
    int invalidateChecks(Map<String,Object> values);
    int invalidateEvidence(Map<String,Object> values);
    int invalidateReview(Map<String,Object> values);
    List<DocumentRevisionProtocol.NodeRow> nodes(Map<String,Object> values);
    List<DocumentRevisionProtocol.FieldRow> fields(Map<String,Object> values);
    List<DocumentRevisionProtocol.EvidenceRow> evidence(Map<String,Object> values);
    List<DocumentRevisionProtocol.UsageRow> usages(Map<String,Object> values);
}
