package com.ruoyi.medical.persistence.mapper;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SourceDeletionMapper {
    List<Map<String,Object>> lease(Map<String,Object> values);
    List<Map<String,Object>> originalParts(Map<String,Object> values);
    List<Map<String,Object>> originalCopies(Map<String,Object> values);
    String actor(Map<String,Object> values);
    int claim(Map<String,Object> values);
    int renew(Map<String,Object> values);
    int saveEvidence(Map<String,Object> values);
    int saveReceipt(Map<String,Object> values);
    int release(Map<String,Object> values);
    int advanceGovernance(Map<String,Object> values);
    BigInteger additionalReferenceCount(Map<String,Object> values);
    int eraseParsedChunks(Map<String,Object> values);
    int eraseParsedNodes(Map<String,Object> values);
    int eraseReadPositions(Map<String,Object> values);
    int eraseProcessingDiagnostics(Map<String,Object> values);
    int eraseParseDiagnostics(Map<String,Object> values);
    int eraseFulltext(Map<String,Object> values);
    int withdrawIndex(Map<String,Object> values);
    BigInteger remainingParsed(Map<String,Object> values);
    BigInteger remainingFulltext(Map<String,Object> values);
    BigInteger remainingDiagnostics(Map<String,Object> values);
    String workerReceipt(Map<String,Object> values);
    String executionEvidence(Map<String,Object> values);
}
