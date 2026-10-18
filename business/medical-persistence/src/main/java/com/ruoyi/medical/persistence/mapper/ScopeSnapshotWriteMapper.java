package com.ruoyi.medical.persistence.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScopeSnapshotWriteMapper {
    java.util.List<com.ruoyi.medical.persistence.TaskProtocol.Snapshot> boundedSnapshot(Map<String,Object> values);
    java.util.List<com.ruoyi.medical.persistence.DocumentUploadBasisProtocol.MemberRow> boundedMembers(Map<String,Object> values);
    List<String> boundedParents(Map<String,Object> values);
    int insertVersion(Map<String,Object> values);
    int insertSnapshot(Map<String,Object> values);
    int insertMember(Map<String,Object> values);
    int insertLineage(Map<String,Object> values);
    List<String> parents(Map<String,Object> values);
}
