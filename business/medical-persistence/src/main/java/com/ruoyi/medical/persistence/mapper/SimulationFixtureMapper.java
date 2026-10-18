package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.SimulationInvocationSources;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SimulationFixtureMapper {
    List<com.ruoyi.medical.persistence.SimulationArtifactReadGuard.Retention> artifactRetention(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.SimulationArtifactReadGuard.Settled> settledArtifact(Map<String,Object> values);
    List<SimulationInvocationSources.Registration> registration(Map<String,Object> values);
    List<SimulationInvocationSources.Original> original(Map<String,Object> values);
    List<SimulationInvocationSources.Context> context(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.SimulationContextDependencies.Classification> contextConfigurationClassifications(Map<String,Object> values);
    int insertContextVersion(Map<String,Object> values);
    int insertContextAccessSet(Map<String,Object> values);
    int insertContextDependencyManifest(Map<String,Object> values);
    int insertContextDependencyMember(Map<String,Object> values);
    int insertContextLineage(Map<String,Object> values);
    int insertSimulationContext(Map<String,Object> values);
    int insertSimulationContextGraph(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.SimulationContextDependencies.Stored> contextGraph(Map<String,Object> values);
    int insertRegistration(Map<String,Object> values);
}
