package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.UploadOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UploadIssuanceMapper {
    List<UploadOperationRow> currentOperation(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
    List<String> audit(Map<String,Object> values);
    List<String> event(Map<String,Object> values);
    List<String> documentUploadForGrant(Map<String,Object> values);
    List<UploadOperationRow> completionOperation(Map<String,Object> values);
    int startCompletionOperation(Map<String,Object> values);
    int completeCompletionOperation(Map<String,Object> values);
    int saveArtifactVersion(Map<String,Object> values);
    int saveArtifact(Map<String,Object> values);
    int saveContentBinding(Map<String,Object> values);
    int saveArtifactLineage(Map<String,Object> values);
    int completeGrant(Map<String,Object> values);
    List<String> completionAudit(Map<String,Object> values);
    List<String> completionEvent(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.DocumentBodyDomainAuthority.Chunk> sourceChunk(Map<String,Object> values);
}
