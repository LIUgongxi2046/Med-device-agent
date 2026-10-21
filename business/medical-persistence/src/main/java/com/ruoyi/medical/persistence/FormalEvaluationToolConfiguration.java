package com.ruoyi.medical.persistence;
import javax.sql.DataSource;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="medical.formal-evaluation-execution",name="enabled",havingValue="true",matchIfMissing=false)
public class FormalEvaluationToolConfiguration {
 @Bean FormalEvaluationToolReceipts formalEvaluationToolReceipts(DataSource data,PermissionSchema json,FormalRuntimeCapabilityIdentityAuthority capabilities,FormalCapabilityIdentityAuthority roles,ResourceReadAuthority resources,SourceReadMapper sources,TaskFieldSourceAuthority processing,TaskResearchSourceReader literals,FormalEvaluationCaseOriginals cases)throws Exception{return new FormalEvaluationToolReceipts(data,json,capabilities,roles,resources,sources,processing,literals,cases);}
}
