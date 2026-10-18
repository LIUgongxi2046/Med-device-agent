package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.DocumentReviewReturnProtocol;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentReviewReturnMapper {
    List<DocumentReviewReturnProtocol.QueueCandidate> myAssignments(Map<String,Object> values);
    List<DocumentReviewReturnProtocol.Task> task(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.TenantMemberRow> assigneeCandidates(Map<String,Object> values);
    List<String> signatures(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.PermissionProtocol.GrantRow> targetGrants(Map<String,Object> values);
    List<String> checks(Map<String,Object> values);
    List<String> decision(Map<String,Object> values);
    List<String> invalidations(Map<String,Object> values);
    List<String> confirmations(Map<String,Object> values);
    List<DocumentReviewReturnProtocol.Stored> assignments(Map<String,Object> values);
    List<DocumentReviewReturnProtocol.Stored> stored(Map<String,Object> values);
    List<SelectionOperationRow> historyOperation(Map<String,Object> values);
    List<String> historyAudit(Map<String,Object> values);
    List<SelectionOperationRow> operation(Map<String,Object> values);
    int insertDecision(Map<String,Object> values);
    int insertAssignment(Map<String,Object> values);
    int invalidate(Map<String,Object> values);
    int advanceDocument(Map<String,Object> values);
    int advanceTask(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
    List<String> audit(Map<String,Object> values);
    List<String> event(Map<String,Object> values);
}
