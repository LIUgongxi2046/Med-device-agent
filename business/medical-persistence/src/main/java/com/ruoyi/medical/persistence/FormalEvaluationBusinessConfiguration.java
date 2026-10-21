package com.ruoyi.medical.persistence;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
@Configuration(proxyBeanMethods=false)
public class FormalEvaluationBusinessConfiguration {
 @Bean @ConditionalOnBean({FormalEvaluationInvocationService.class,FormalEvaluationToolReceipts.class,FormalEvaluationRuntimeSchema.class})
 FormalEvaluationNodeOutcomes evaluationNodeOutcomes(DataSource data,PermissionSchema json,TaskPlanPolicy plans,FormalCapabilityIdentityAuthority capabilities,FormalEvaluationInvocationService invocations,FormalEvaluationRuntimeSchema schema,TaskPlanService owners,FormalEvaluationToolReceipts tools){return new FormalEvaluationNodeOutcomes(data,json,plans,capabilities,invocations,schema,owners,tools);}
}
