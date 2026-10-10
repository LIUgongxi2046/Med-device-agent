package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.SourceReadProtocol;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SourceReadMapper {
    List<SourceReadProtocol.SourceRow> source(@Param("id") String id);
    List<SourceReadProtocol.RightsRow> rights(@Param("id") String id);
    List<SourceReadProtocol.SelectionRow> selection(@Param("id") String sourceVersion, @Param("projectId") String projectId,
            @Param("snapshotId") String snapshotId, @Param("parseVersion") String parseVersion);
    List<SourceReadProtocol.SelectionRow> snapshotCandidate(@Param("id") String sourceVersion, @Param("projectId") String projectId,
            @Param("parseVersion") String parseVersion, @Param("indexVersion") String indexVersion,
            @Param("approvalRevision") String approvalRevision, @Param("bindingRevision") String bindingRevision,
            @Param("userId") String userId);
    List<SourceReadProtocol.GateRow> gates(@Param("id") String sourceVersion);
    List<SourceReadProtocol.SelectionRow> scopeCandidates(@Param("userId") String userId, @Param("projectId") String projectId,
            @Param("mode") String mode, @Param("sourceVersions") List<String> sourceVersions,
            @Param("collectionVersions") List<String> collectionVersions, @Param("limit") int limit);
    List<SourceReadProtocol.CollectionMember> collectionMembers(@Param("userId") String userId,
            @Param("id") String collectionVersion, @Param("limit") int limit);
    List<SourceReadProtocol.ConversationRow> conversation(@Param("id") String conversationId);
    List<SourceReadProtocol.ParentRow> parents(@Param("id") String consumerVersion);
}
