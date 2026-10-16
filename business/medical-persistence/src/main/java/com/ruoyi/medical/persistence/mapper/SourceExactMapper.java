package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SourceExactMapper {
    List<Map<String,Object>> head(Map<String,Object> values);
    List<Map<String,Object>> revision(Map<String,Object> values);
    List<Map<String,Object>> fields(Map<String,Object> values);
    List<Map<String,Object>> chunk(Map<String,Object> values);
    List<Map<String,Object>> candidates(Map<String,Object> values);
    List<Map<String,Object>> query(Map<String,Object> values);
    List<Map<String,Object>> hits(Map<String,Object> values);
    List<SelectionOperationRow> operation(Map<String,Object> values);
    int saveVersion(Map<String,Object> values);
    int saveRevision(Map<String,Object> values);
    int saveField(Map<String,Object> values);
    int createHead(Map<String,Object> values);
    int updateHead(Map<String,Object> values);
    int saveQuery(Map<String,Object> values);
    int saveHit(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
    int saveLineage(Map<String,Object> values);
    int saveHold(Map<String,Object> values);
    int advanceGovernance(Map<String,Object> values);
}
