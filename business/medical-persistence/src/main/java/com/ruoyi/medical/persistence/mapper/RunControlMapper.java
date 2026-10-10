package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RunControlMapper {
    List<SelectionOperationRow> operation(Map<String,Object> values);
    List<String> activeRun(Map<String,Object> values);
    List<String> originalCancellation(Map<String,Object> values);
    int requestCancellation(Map<String,Object> values);
    int requestTaskCancellation(Map<String,Object> values);
    int advanceStream(Map<String,Object> values);
    int appendEvent(Map<String,Object> values);
    int saveCommand(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
}
