package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.SourceCompletionCheckpointStore.Row;
import com.ruoyi.medical.persistence.SourceCompletionRegistration.Candidate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SourceCompletionMapper {
    List<Row> checkpoint(Map<String,Object> values);
    int saveCheckpoint(Map<String,Object> values);
    int verifyOriginal(Map<String,Object> values);
    List<Candidate> duplicate(Map<String,Object> values);
    java.math.BigInteger versionNumber(Map<String,Object> values);
    int saveSourceVersion(Map<String,Object> values);
    int saveDedup(Map<String,Object> values);
    int saveSessionSource(Map<String,Object> values);
    int completeUpload(Map<String,Object> values);
    int completeOperation(Map<String,Object> values);
    int completeDelivery(Map<String,Object> values);
    int saveReceipt(Map<String,Object> values);
    int saveUploadResult(Map<String,Object> values);
    List<String> receipt(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.SourceCompletionExecution.Runtime> execution(Map<String,Object> values);
    int startExecution(Map<String,Object> values);
    int failDelivery(Map<String,Object> values);
    int failUpload(Map<String,Object> values);
    int failSession(Map<String,Object> values);
    int failOperation(Map<String,Object> values);
}
