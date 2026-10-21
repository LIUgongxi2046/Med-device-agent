package com.ruoyi.medical.persistence;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Independent original-observation graph. It depends on no token issuer or live model context. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.professional-runtime.task-enabled",havingValue="true",matchIfMissing=false)
class TaskInvocationRecoveryConfiguration {
 @Bean TaskInvocationRecovery taskInvocationRecovery(DataSource data,FormalInvocationOutcomeAuthority outcomes,TaskInvocationRecords records,
   TaskNodeProducerAuthority producers,FormalInvocationReservations reservations,FormalInvocationSettlement settlement,
   FormalInvocationObjectRegistration objects,FormalProviderCaptureEvidence captures,TaskInvocationResults results,TaskInvocationLocalBridge adapter){
  return new TaskInvocationRecovery(data,outcomes,records,producers,reservations,settlement,objects,captures,results,adapter);
 }
}
