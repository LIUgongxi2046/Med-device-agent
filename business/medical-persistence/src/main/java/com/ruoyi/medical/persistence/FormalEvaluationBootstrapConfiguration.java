package com.ruoyi.medical.persistence;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.workflow.FormalEvaluationDelivery;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.beans.factory.annotation.*;
/** Dedicated opt-in producer. It installs no synthetic evidence or default accepting runtime. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="medical.formal-evaluation-execution",name="enabled",havingValue="true",matchIfMissing=false)
public class FormalEvaluationBootstrapConfiguration {
    @Bean FormalEvaluationJobs formalEvaluationJobs(@Qualifier("masterDataSource")DataSource data,PermissionSchema json,RunTokenAuthoritySchema runtime,ResourceReadAuthority resources,VersionResourceMapper owners,SourceReadMapper sources){return new FormalEvaluationJobs(data,json,runtime,resources,owners,sources);}
    @Bean FormalEvaluationLaunchLedger formalEvaluationLaunchLedger(@Qualifier("masterDataSource")DataSource data,FormalEvaluationJobs jobs,PermissionSchema json){return new FormalEvaluationLaunchLedger(data,jobs,json);}
    @Bean FormalEvaluationLaunchLedger.Configuration formalEvaluationLaunchConfiguration(@Value("${medical.formal-evaluation-execution.control-task-queue}")String control,@Value("${medical.formal-evaluation-execution.professional-task-queue}")String professional,@Value("${medical.formal-evaluation-execution.activity-timeout-millis}")long activity,@Value("${medical.formal-evaluation-execution.heartbeat-timeout-millis}")long heartbeat,@Value("${medical.formal-evaluation-execution.recovery-poll-millis}")long recovery){return new FormalEvaluationLaunchLedger.Configuration(control,professional,activity,heartbeat,recovery);}
    @Bean FormalEvaluationDelivery formalEvaluationDelivery(io.temporal.client.WorkflowClient client){return new FormalEvaluationDelivery(client);}
    @Bean FormalEvaluationBootstrapService formalEvaluationBootstrapService(EnterpriseAccess access,FormalEvaluationExecutionInputs inputs,FormalEvaluationBoundExecutions bound,FormalEvaluationJobs jobs,FormalEvaluationLaunchLedger ledger,FormalEvaluationDelivery delivery,FormalEvaluationLaunchLedger.Configuration configuration,PermissionSchema json,ResourceReadAuthority resources){return new FormalEvaluationBootstrapService(access,inputs,bound,jobs,ledger,delivery,configuration,json,resources);}
}
