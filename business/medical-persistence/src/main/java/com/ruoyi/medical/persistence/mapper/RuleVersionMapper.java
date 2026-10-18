package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.RuleVersionProtocol;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RuleVersionMapper {
    List<String> simulationBinding(Map<String,Object> values);
    int insertVersion(Map<String,Object> values);
    int insertRule(Map<String,Object> values);
    int insertDefinition(Map<String,Object> values);
    int insertLineage(Map<String,Object> values);
    int insertBaseLineage(Map<String,Object> values);
    List<RuleVersionProtocol.StorageRow> definition(Map<String,Object> values);
    List<String> approvedWorkflow(Map<String,Object> values);
    List<String> parents(Map<String,Object> values);
    List<String> baseParents(Map<String,Object> values);
    List<SelectionOperationRow> currentOperation(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
    List<String> audit(Map<String,Object> values);
    List<String> event(Map<String,Object> values);
    List<String> auditForOperation(Map<String,Object> values);
    List<String> eventForOperation(Map<String,Object> values);
    List<SelectionOperationRow> approvalOperation(Map<String,Object> values);
    int saveApprovalOperation(Map<String,Object> values);
    int advanceRule(Map<String,Object> values);
    int advanceDefinition(Map<String,Object> values);
    int advanceGovernance(Map<String,Object> values);
    int saveApproval(Map<String,Object> values);
    List<String> approval(Map<String,Object> values);
    List<String> approvalAudit(Map<String,Object> values);
    List<String> approvalEvent(Map<String,Object> values);
    List<String> approvalAuditForOperation(Map<String,Object> values);
    List<String> approvalEventForOperation(Map<String,Object> values);
}
