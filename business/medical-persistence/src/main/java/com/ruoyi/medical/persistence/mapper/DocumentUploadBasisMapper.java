package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.DocumentUploadBasisProtocol;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentUploadBasisMapper {
    List<DocumentUploadBasisProtocol.TaskRow> task(Map<String,Object> values);
    List<DocumentUploadBasisProtocol.TaskRevisionRow> taskRevision(Map<String,Object> values);
    List<DocumentUploadBasisProtocol.PlanRow> plan(Map<String,Object> values);
    List<DocumentUploadBasisProtocol.SnapshotRow> snapshot(Map<String,Object> values);
    List<DocumentUploadBasisProtocol.MemberRow> members(Map<String,Object> values);
}
