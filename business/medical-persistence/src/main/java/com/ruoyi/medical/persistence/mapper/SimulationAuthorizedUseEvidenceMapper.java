package com.ruoyi.medical.persistence.mapper;
import java.util.*;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface SimulationAuthorizedUseEvidenceMapper {
 List<String> state(Map<String,Object> values);
 List<String> decision(Map<String,Object> values);
 List<String> operation(Map<String,Object> values);
 List<String> audits(Map<String,Object> values);
 List<String> outbox(Map<String,Object> values);
}
