package com.ruoyi.medical.persistence.mapper;
import com.ruoyi.medical.persistence.SimulationInputArtifactService.Link;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface SimulationInputArtifactMapper {
    List<String> lockFixture(Map<String,Object> values);
    List<Link> link(Map<String,Object> values);
    int insertArtifact(Map<String,Object> values);
    int insertBinding(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.SimulationInputArtifactService.Operation> operation(Map<String,Object> values);
    int insertOperation(Map<String,Object> values);
    int insertLink(Map<String,Object> values);
}
