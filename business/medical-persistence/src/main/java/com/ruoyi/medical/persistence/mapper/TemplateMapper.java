package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.TemplateProtocol;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TemplateMapper {
    List<String> applicationHead(Map<String,Object> values);
    int insertApplicationHead(Map<String,Object> values);
    int advanceApplicationHead(Map<String,Object> values);
    List<String> applicationDecisions(Map<String,Object> values);
    List<String> applicationDecision(Map<String,Object> values);
    int saveApplicationDecision(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.SelectionOperationRow> applicationOperation(Map<String,Object> values);
    int saveApplicationOperation(Map<String,Object> values);
    List<String> applicationAudit(Map<String,Object> values);
    List<String> applicationEvent(Map<String,Object> values);
    int insertVersion(Map<String,Object> values);
    int insertTemplate(Map<String,Object> values);
    int insertDefinition(Map<String,Object> values);
    int insertNode(Map<String,Object> values);
    int insertField(Map<String,Object> values);
    int insertRepeat(Map<String,Object> values);
    int insertAttachment(Map<String,Object> values);
    int insertDependency(Map<String,Object> values);
    int insertLineage(Map<String,Object> values);
    List<TemplateProtocol.StorageRow> definition(Map<String,Object> values);
    List<TemplateProtocol.Projection> nodes(Map<String,Object> values);
    List<TemplateProtocol.Projection> fields(Map<String,Object> values);
    List<TemplateProtocol.Projection> repeats(Map<String,Object> values);
    List<TemplateProtocol.Projection> attachments(Map<String,Object> values);
    List<TemplateProtocol.Projection> dependencies(Map<String,Object> values);
    List<String> parents(Map<String,Object> values);
    List<String> ruleState(Map<String,Object> values);
    List<String> approvedVersions(Map<String,Object> values);
    List<String> applicableVersions(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.TemplateApplicabilityProtocol.RuleRow> ruleDefinition(Map<String,Object> values);
    List<String> ruleParents(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.SelectionOperationRow> currentOperation(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
    List<String> audit(Map<String,Object> values);
    List<String> event(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.SelectionOperationRow> approvalOperation(Map<String,Object> values);
    int saveApprovalOperation(Map<String,Object> values);
    int advanceTemplate(Map<String,Object> values);
    int advanceDefinition(Map<String,Object> values);
    int advanceGovernance(Map<String,Object> values);
    int saveApproval(Map<String,Object> values);
    List<String> approval(Map<String,Object> values);
    List<String> approvalAudit(Map<String,Object> values);
    List<String> approvalEvent(Map<String,Object> values);
    List<String> approvalAuditForOperation(Map<String,Object> values);
    List<String> approvalEventForOperation(Map<String,Object> values);
}
