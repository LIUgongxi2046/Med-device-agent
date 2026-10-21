package com.ruoyi.medical.persistence;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.task-candidate-assembly.enabled",havingValue="true",matchIfMissing=false)
public class TaskCandidateAssemblyInputConfiguration {
    @Bean TaskCandidateAssemblyNormalizer taskCandidateAssemblyNormalizer(TaskSourceVerifierSchema json)throws Exception{return new TaskCandidateAssemblyNormalizer(json);}
    @Bean TaskCandidateAssemblyCrossFileAuthority taskCandidateAssemblyCrossFileAuthority(@Qualifier("masterDataSource")DataSource data,
            TaskSourceVerifierSchema json,@Qualifier("taskCandidateAssemblyProducerService")TaskNodeProducerService producers,
            TaskRunActivityPreparation runs,FormalRunControlAccess control,DocumentReadService documents,TaskSourceVerifierRules rules,RuleExpression engine){
        return new TaskCandidateAssemblyCrossFileAuthority(data,json,producers,runs,control,documents,rules,engine);
    }
    @Bean TaskCandidateAssemblyAuthority taskCandidateAssemblyAuthority(@Qualifier("masterDataSource")DataSource data,TaskSourceVerifierSchema json,
            TaskSourceVerifierCommittedReviewAuthority reviews,TaskSectionWriterInputAuthority writers,TemplateApplicabilityService applicability,
            TaskRunReplayContentAuthority contents,TaskSourceVerifierRules rules,TaskCandidateAssemblyNormalizer normalizer,TaskCandidateAssemblyCrossFileAuthority crossFiles,FormalCapabilityIdentityAuthority roles){
        return new TaskCandidateAssemblyAuthority(data,json,reviews,writers,applicability,contents,rules,normalizer,crossFiles,roles);
    }
}
