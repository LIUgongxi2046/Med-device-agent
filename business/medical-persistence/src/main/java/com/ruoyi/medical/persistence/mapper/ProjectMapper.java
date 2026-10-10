package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.PermissionProtocol;
import com.ruoyi.medical.persistence.ProjectProtocol;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper {
    List<PermissionProtocol.GrantRow> grants(Map<String,Object> values);
    List<ProjectProtocol.Row> project(Map<String,Object> values);
    List<ProjectProtocol.Profile> profile(Map<String,Object> values);
    List<String> profileRegistry(Map<String,Object> values);
    List<String> projects(Map<String,Object> values);
    BigInteger projectCount(Map<String,Object> values);
    int insertProject(Map<String,Object> values);
    int insertVersion(Map<String,Object> values);
    int insertProfile(Map<String,Object> values);
    int insertRecord(Map<String,Object> values);
    int advanceProfile(Map<String,Object> values);
    int advanceProject(Map<String,Object> values);
    int advanceRecord(Map<String,Object> values);
    int saveDecision(Map<String,Object> values);
    List<String> decision(Map<String,Object> values);
    List<SelectionOperationRow> currentOperation(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
    int advanceGovernance(Map<String,Object> values);
    BigInteger taskCount(Map<String,Object> values);
    List<String> tasks(Map<String,Object> values);
    BigInteger runCount(Map<String,Object> values);
    List<ProjectProtocol.Run> runs(Map<String,Object> values);
    BigInteger waitingCount(Map<String,Object> values);
    List<ProjectProtocol.Waiting> waiting(Map<String,Object> values);
    List<String> audit(Map<String,Object> values);
    List<String> event(Map<String,Object> values);
}
