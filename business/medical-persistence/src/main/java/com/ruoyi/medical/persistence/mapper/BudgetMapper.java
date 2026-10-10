package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.BudgetProtocol;
import com.ruoyi.medical.persistence.PermissionProtocol;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BudgetMapper {
    List<PermissionProtocol.GrantRow> grants(Map<String,Object> values);
    List<String> accounts(Map<String,Object> values);
    BigInteger count(Map<String,Object> values);
    List<BudgetProtocol.Account> account(Map<String,Object> values);
    List<BudgetProtocol.Revision> approval(Map<String,Object> values);
    List<String> history(Map<String,Object> values);
    BigInteger historyCount(Map<String,Object> values);
    List<String> registry(Map<String,Object> values);
    int insertAccount(Map<String,Object> values);
    int insertVersion(Map<String,Object> values);
    int insertApproval(Map<String,Object> values);
    int initializeApproval(Map<String,Object> values);
    int advanceApproval(Map<String,Object> values);
    List<SelectionOperationRow> currentOperation(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
    int advanceGovernance(Map<String,Object> values);
    List<String> audit(Map<String,Object> values);
    List<String> event(Map<String,Object> values);
}
