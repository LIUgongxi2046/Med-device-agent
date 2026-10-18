package com.ruoyi.medical.persistence;
import javax.sql.DataSource;import com.ruoyi.medical.persistence.mapper.PermissionMapper;import org.springframework.context.annotation.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;import org.springframework.beans.factory.annotation.Qualifier;
@Configuration(proxyBeanMethods=false)
@ConditionalOnExpression("${medical.model-runtime.enabled:false} and ${medical.formal-invocation.enabled:false}")
class FormalWorkerAccessConfiguration {
 @Bean FormalWorkerAccess formalWorkerAccess(@Qualifier("masterDataSource") DataSource d,RunTokenAuthority t,RunTokenAuthoritySchema s,PermissionMapper p){return new FormalWorkerAccess(d,t,s,p);}
}
