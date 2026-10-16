package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.SourceProcessingRow;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SourceProcessingMapper {
    List<SourceProcessingRow> current(Map<String,Object> values);
    int claim(Map<String,Object> values);
    int renew(Map<String,Object> values);
    int startParse(Map<String,Object> values);
    int saveWriteIntent(Map<String,Object> values);
    int saveVersion(Map<String,Object> values);
    int saveLineage(Map<String,Object> values);
    int saveNode(Map<String,Object> values);
    int saveChunk(Map<String,Object> values);
    int saveChunkNode(Map<String,Object> values);
    int completeParse(Map<String,Object> values);
    int completeAttempt(Map<String,Object> values);
    int failParse(Map<String,Object> values);
    int failAttempt(Map<String,Object> values);
    int advanceGovernance(Map<String,Object> values);
    BigInteger nodeCount(Map<String,Object> values);
    BigInteger chunkCount(Map<String,Object> values);
    List<Map<String,Object>> materialized(Map<String,Object> values);
    List<Map<String,Object>> storedNodes(Map<String,Object> values);
}
