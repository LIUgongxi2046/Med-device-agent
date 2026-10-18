package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.AnnotationThreadProtocol;
import com.ruoyi.medical.persistence.UploadOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnnotationThreadMapper {
    String authorizationClock(Map<String,Object> values);
    List<String> artifactDocumentRevision(Map<String,Object> values);
    Long annotationCount(Map<String,Object> values);
    List<AnnotationThreadProtocol.Row> annotations(Map<String,Object> values);
    List<AnnotationThreadProtocol.Row> annotation(Map<String,Object> values);
    List<AnnotationThreadProtocol.Row> originalAnnotation(Map<String,Object> values);
    List<AnnotationThreadProtocol.Row> effectiveAnnotation(Map<String,Object> values);
    List<AnnotationThreadProtocol.Review> confirmations(Map<String,Object> values);
    List<AnnotationThreadProtocol.Invalidation> invalidations(Map<String,Object> values);
    List<UploadOperationRow> operation(Map<String,Object> values);
    int startOperation(Map<String,Object> values);
    int updateCommand(Map<String,Object> values);
    int completeOperation(Map<String,Object> values);
    int saveVersion(Map<String,Object> values);
    int saveArtifact(Map<String,Object> values);
    int saveBinding(Map<String,Object> values);
    int saveLineage(Map<String,Object> values);
    int insertAnnotation(Map<String,Object> values);
    int advanceHead(Map<String,Object> values);
    int advanceIssues(Map<String,Object> values);
    int invalidateReview(Map<String,Object> values);
    List<String> audit(Map<String,Object> values);
    List<String> event(Map<String,Object> values);
}
