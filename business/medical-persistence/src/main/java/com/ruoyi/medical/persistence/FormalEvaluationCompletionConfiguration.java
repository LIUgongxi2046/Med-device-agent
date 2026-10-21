package com.ruoyi.medical.persistence;

import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import com.ruoyi.medical.persistence.mapper.*;

/** Real execution evidence wiring, disabled until genuine runtime/deployment/object authorities exist.
 * Enabling this feature does not create any approval, qualification, historical receipt or score. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="medical.formal-evaluation-execution",name="enabled",havingValue="true",matchIfMissing=false)
public class FormalEvaluationCompletionConfiguration {
 @Bean FormalEvaluationRuntimeObjects formalEvaluationRuntimeObjects(DataSource d,PermissionSchema j,FormalEvaluationRuntimeSchema s,DependencyManifestSchema m,DependencyLineage l,SourceReadMapper sources,ArtifactRegistryMapper artifacts,FormalEvaluationDeterministicObjects deterministic,FormalEvaluationStageInputObjects stageInputs){return new FormalEvaluationRuntimeObjects(d,j,s,m,l,sources,artifacts,deterministic,stageInputs);}
 @Bean FormalEvaluationDeterministicObjects formalEvaluationDeterministicObjects(DataSource d,PermissionSchema j,DependencyManifestSchema m,DependencyLineage l,SourceReadMapper sources,ArtifactRegistryMapper artifacts){return new FormalEvaluationDeterministicObjects(d,j,m,l,sources,artifacts);}
 @Bean FormalEvaluationDeterministicRuntime formalEvaluationDeterministicRuntime(DataSource d,PermissionSchema j,FormalEvaluationJobs jobs,FormalEvaluationLaunchLedger launches,FormalEvaluationNodeLeases leases,FormalEvaluationNodeInputs inputs,FormalEvaluationDeterministicObjects objects,FormalCapabilityIdentityAuthority capabilities){return new FormalEvaluationDeterministicRuntime(d,j,jobs,launches,leases,inputs,objects,capabilities);}
 @Bean(name="formalEvaluationRuntimeTransport") RuntimeObjectTransport formalEvaluationRuntimeTransport(SourceUploadTransport source,@Value("${medical.formal-evaluation-execution.object-directory}")String directory)throws Exception{return new RuntimeObjectTransport(source,java.nio.file.Path.of(directory),16777216);}
 @Bean FormalEvaluationCompletionService formalEvaluationCompletionService(DataSource d,PermissionSchema j,FormalEvaluationNodeRuntime r){return new FormalEvaluationCompletionService(d,j,r);}
 @Bean FormalEvaluationOriginalObjects formalEvaluationOriginalObjects(EnterpriseAccess a,ArtifactContentService c,SourceUploadTransport t,ArtifactRegistryMapper artifacts,SourceReadMapper sources,DependencyLineage lineage,EvaluationReferences refs,EvaluationDependencyPolicy policy,@Value("${medical.formal-evaluation-execution.maximum-original-bytes:16777216}")int max){return new FormalEvaluationOriginalObjects(a,c,t,artifacts,sources,lineage,refs,policy,max);}
 @Bean FormalEvaluationExecutionEvidence formalEvaluationExecutionEvidence(EnterpriseAccess a,DataSource d,PermissionSchema j,EvaluationService evaluations,FormalAgentCandidateAuthority candidates,AgentExecutionPolicyAuthority policies,ResourceReadAuthority resources,FormalEvaluationOriginalObjects objects,ObjectProvider<FormalEvaluationProfessionalCompletions> completions){return new FormalEvaluationExecutionEvidence(a,d,j,evaluations,candidates,policies,resources,objects,completions);}
 @Bean FormalEvaluationFixedResults formalEvaluationFixedResults(EnterpriseAccess a,EvaluationService e,FormalAgentCandidateAuthority c,FormalEvaluationExecutionEvidence proofs,PermissionSchema j){return new FormalEvaluationFixedResults(a,e,c,proofs,j);}
 @Bean
 @ConditionalOnProperty(prefix="medical.expert-qualification",name="enabled",havingValue="true",matchIfMissing=false)
 FormalEvaluationScoringGate formalEvaluationScoringGate(EnterpriseAccess a,EvaluationService e,EvaluationSchema s,FormalAgentCandidateAuthority c,FormalEvaluationExecutionEvidence proofs,ExpertQualificationAuthority experts,DataSource d){return new FormalEvaluationScoringGate(a,e,s,c,proofs,experts,d);}
}
