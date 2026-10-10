package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.SelectionOperationRow;
import com.ruoyi.medical.persistence.TaskPlanWriteProtocol;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskPlanWriteMapper {
    List<TaskPlanWriteProtocol.Head> currentPlanHead(Map<String,Object> values);
    List<SelectionOperationRow> operation(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.TaskPlanDecisionProtocol.Row> decision(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.TaskPlanDecisionProtocol.TaskRevision> decisionTask(Map<String,Object> values);
    List<String> decisionAudit(Map<String,Object> values);
    List<String> decisionEvent(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.PermissionProtocol.GrantRow> ownerGrants(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.TenantMemberRow> creationOwnerCandidates(Map<String,Object> values);
    int beginOperation(Map<String,Object> values);
    int completeOperation(Map<String,Object> values);
    int insertRegisteredVersion(Map<String,Object> values);
    int insertPlan(Map<String,Object> values);
    int insertContent(Map<String,Object> values);
    int insertPlanHead(Map<String,Object> values);
    int advancePlanHead(Map<String,Object> values);
    int insertTaskRevision(Map<String,Object> values);
    int insertTaskHead(Map<String,Object> values);
    int insertTaskInput(Map<String,Object> values);
    int bindCreatedTask(Map<String,Object> values);
    List<String> creationInputOperation(Map<String,Object> values);
    List<String> creationAudit(Map<String,Object> values);
    List<String> creationEvent(Map<String,Object> values);
    List<java.time.LocalDateTime> draftOperationTime(Map<String,Object> values);
    List<String> draftAudit(Map<String,Object> values);
    List<String> draftEvent(Map<String,Object> values);
    int copyTaskInput(Map<String,Object> values);
    int bindConfirmedPlan(Map<String,Object> values);
    int advanceTaskHead(Map<String,Object> values);
    int insertDecision(Map<String,Object> values);
    int bindDecision(Map<String,Object> values);
    int insertLineage(Map<String,Object> values);
    List<String> audit(Map<String,Object> values);
    List<String> event(Map<String,Object> values);
}
