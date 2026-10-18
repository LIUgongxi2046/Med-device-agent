package com.ruoyi.medical.persistence.mapper;
import java.util.*;import java.time.LocalDateTime;import org.apache.ibatis.annotations.Mapper;
@Mapper public interface FormalAgentAcceptanceMapper {
 record Acceptance(String tenantId,String projectId,String acceptanceId,String candidateVersionId,String candidateSha256,String evaluationId,String evaluationSha256,String releaseId,String manifestSha256,String contentSha256,String decision,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification){}
 List<Acceptance> acceptance(Map<String,Object> v);int insertAcceptance(Map<String,Object> v);int decideCandidate(Map<String,Object> v);
}
