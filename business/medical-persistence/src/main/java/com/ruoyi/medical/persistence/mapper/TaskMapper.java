package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.ProjectProtocol;
import com.ruoyi.medical.persistence.TaskProtocol;
import com.ruoyi.medical.persistence.DocumentUploadBasisProtocol;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskMapper {
    List<String> listed(java.util.Map<String,Object> values);
    java.math.BigInteger datasetRevision(java.util.Map<String,Object> values);
    java.math.BigInteger visibleCount(java.util.Map<String,Object> values);
    List<TaskProtocol.Head> head(@Param("userId") String userId, @Param("taskId") String taskId);
    List<TaskProtocol.Input> input(@Param("userId") String userId, @Param("projectId") String projectId,
            @Param("taskId") String taskId, @Param("taskRevision") String taskRevision);
    List<TaskProtocol.Snapshot> snapshot(@Param("userId") String userId, @Param("projectId") String projectId,
            @Param("snapshotId") String snapshotId);
    List<DocumentUploadBasisProtocol.MemberRow> members(@Param("userId") String userId, @Param("projectId") String projectId,
            @Param("snapshotId") String snapshotId);
    List<TaskProtocol.Plan> plan(@Param("userId") String userId, @Param("projectId") String projectId,
            @Param("taskId") String taskId, @Param("planRevision") String planRevision);
    List<String> documents(@Param("userId") String userId, @Param("projectId") String projectId, @Param("taskId") String taskId);
    List<ProjectProtocol.Run> runs(@Param("userId") String userId, @Param("projectId") String projectId, @Param("taskId") String taskId);
    List<ProjectProtocol.Waiting> waiting(@Param("userId") String userId, @Param("projectId") String projectId, @Param("taskId") String taskId);
}
