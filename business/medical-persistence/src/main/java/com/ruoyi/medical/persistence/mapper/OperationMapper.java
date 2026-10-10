package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.OperationRow;
import com.ruoyi.medical.persistence.PermissionProtocol;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationMapper {
    List<OperationRow> lookup(Map<String,Object> values);
    List<OperationRow> operation(Map<String,Object> values);
    List<PermissionProtocol.GrantRow> readers(Map<String,Object> values);
}
