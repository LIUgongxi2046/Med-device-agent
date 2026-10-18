package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SimulationFixtureMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
public class SimulationFixtureConfiguration {
    @Bean
    SimulationInvocationSources simulationInvocationSources(EnterpriseAccess access,SourceReadAuthority sources,
            SourceReadMapper sourceRows,SourceRightsSchema rights,SourceUploadTransport transport,SimulationFixtureMapper mapper,
            PermissionMapper permissions,ResourceReadAuthority resources,ModelConfigurationService configurations,
            PermissionSchema json,DependencyAccessAuthority dependencies,RetrievalPolicy deployment) throws Exception {
        return new SimulationInvocationSources(access,sources,sourceRows,rights,transport,mapper,permissions,resources,configurations,json,dependencies,deployment);
    }
}
