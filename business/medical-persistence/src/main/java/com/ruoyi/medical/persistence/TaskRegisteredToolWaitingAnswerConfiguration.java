package com.ruoyi.medical.persistence;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
/** Human answer linkage needs the actual waiting commit journal reader. Keeping it
 * with the same explicit server gate does not enable an inbound endpoint by accident. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"medical.professional-runtime.task-enabled","medical.professional-runtime.waiting-v2-enabled","medical.task-run.waiting-v2.server-enabled"},havingValue="true",matchIfMissing=false)
public class TaskRegisteredToolWaitingAnswerConfiguration {
 @Bean TaskRegisteredToolWaitingAnswerAuthority taskRegisteredToolWaitingAnswerAuthority(@Qualifier("masterDataSource")DataSource data,PermissionSchema json,ResourceReadAuthority resources,TaskRegisteredToolWaitingService waiting,TaskRegisteredToolWaitingCompletion completion)throws Exception{return new TaskRegisteredToolWaitingAnswerAuthority(data,json,resources,waiting,completion);}
 @Bean TaskRegisteredToolWaitingNewRun taskRegisteredToolWaitingNewRun(@Qualifier("masterDataSource")DataSource data,PermissionSchema json,TaskPlanDecisionSchema plans,com.ruoyi.medical.persistence.mapper.TaskPlanWriteMapper planRows,RunCreateSchema runs,com.ruoyi.medical.persistence.mapper.RunCreateMapper runRows,RunTokenAuthoritySchema authority,RuntimeReplaySchema replay,RuntimeReplayOriginalAuthority originals,TaskInputSchema inputs,TaskRunWorkflowLedger launches,TaskRunAcceptedStart starts)throws Exception{return new TaskRegisteredToolWaitingNewRun(data,json,plans,planRows,runs,runRows,authority,replay,originals,inputs,launches,starts);}
 @Bean TaskRegisteredToolWaitingAnswerService taskRegisteredToolWaitingAnswerService(@Qualifier("masterDataSource")DataSource data,EnterpriseAccess access,TaskRegisteredToolWaitingAnswerAuthority authority,TaskRegisteredToolWaitingNewRun links,TaskRunWaitingCommitService commits,PermissionSchema json)throws Exception{return new TaskRegisteredToolWaitingAnswerService(data,access,authority,links,commits,json);}
}
