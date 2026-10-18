package com.ruoyi.medical.persistence.mapper;
import java.util.*;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface FormalEvaluationPolicyMapper {
 record Snapshot(String tenantId,String recordId,String contentSha256,String snapshotJson,String snapshotSha256,String inputSchemaSha256,String policySchemaSha256){}
 record Classification(String versionId,String entityKind,String contentSha256,String classification,String ownerClassification){}
 List<Classification> dependencyClassification(Map<String,Object> values);
 List<Snapshot> snapshot(Map<String,Object> values);
 int insertSnapshot(Map<String,Object> values);
}
