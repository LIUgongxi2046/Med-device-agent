package com.ruoyi.medical.persistence.mapper;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskCreationAgentDirectoryMapper {
    record Candidate(String tenantId,String projectId,String releaseId,String agentId,String agentVersionId,
            String name,String evaluationId,String evaluationProjectId,String artifactId,String artifactProjectId,
            BigInteger revision,LocalDateTime updatedAt) { }
    List<Candidate> candidates(Map<String,Object> values);
}
