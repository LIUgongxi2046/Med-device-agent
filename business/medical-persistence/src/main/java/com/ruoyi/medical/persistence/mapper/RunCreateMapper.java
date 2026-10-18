package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RunCreateMapper {
    List<SelectionOperationRow> operation(Map<String,Object> values);
    List<String> activeRun(Map<String,Object> values);
    List<String> attempts(Map<String,Object> values);
    List<String> budget(Map<String,Object> values);
    List<String> created(Map<String,Object> values);
    int insertRegistry(Map<String,Object> values);
    int insertRun(Map<String,Object> values);
    int insertPrincipal(Map<String,Object> values);
    int insertActiveRun(Map<String,Object> values);
    int advanceTask(Map<String,Object> values);
    int insertStream(Map<String,Object> values);
    int saveCommand(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
}
