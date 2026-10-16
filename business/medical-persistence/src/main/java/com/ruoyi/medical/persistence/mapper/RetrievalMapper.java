package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.RetrievalProtocol;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RetrievalMapper {
    List<RetrievalProtocol.Candidate> candidates(Map<String,Object> values);
    List<RetrievalProtocol.QueryRow> query(Map<String,Object> values);
    List<RetrievalProtocol.Hit> hits(Map<String,Object> values);
    List<SelectionOperationRow> operation(Map<String,Object> values);
    int insertQuery(Map<String,Object> values);
    int insertHit(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
}
