package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.ConfigurationDiscoveryService.Candidate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConfigurationDiscoveryMapper {
    String authorizationClock(Map<String,Object> values);
    List<Candidate> models(Map<String,Object> values);
    List<Candidate> rules(Map<String,Object> values);
}
