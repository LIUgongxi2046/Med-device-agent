package com.ruoyi.medical.persistence.mapper;
import java.util.*;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface FormalAgentCandidateMapper {
 record Binding(String tenantId,String projectId,String versionId,String contentSha256,String authorRecordId,String policyVersionId,String policySha256,String fixedJson,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification){}
 List<AgentPublicationMapper.Journal> runtimeAuthor(Map<String,Object> v);
 List<Binding> candidate(Map<String,Object> v);
 List<String> parents(Map<String,Object> v);
 List<String> runtimeList(Map<String,Object> v);
 List<String> list(Map<String,Object> v);
 int insertCandidate(Map<String,Object> v);
}
