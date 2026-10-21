package com.ruoyi.medical.persistence;

/** Only real immutable ledger readers may construct these attempt proofs.
 * Neither caller JSON nor an arbitrary boolean adapter is an authorized write. */
public sealed interface RuntimeObjectAttempt permits FormalEvaluationDeterministicObjects.Attempt, FormalEvaluationStageInputObjects.Attempt, FormalEvaluationInvocationObjects.Attempt, FormalEvaluationRuntimeObjects.Attempt, DocumentReviewInvocationObjects.Attempt, DocumentReviewObjectLedger.Attempt, TaskCandidateAssemblyObjectLedger.Attempt, TaskSourceVerifierObjectLedger.Attempt, TaskSourceVerifierInvocationObjects.Attempt, DocumentInitialRevisionService.Attempt, TaskSectionWriterInvocationObjects.Attempt, TaskSectionWriterObjectLedger.Attempt, TaskStrictFieldOutputLedger.Attempt, TaskStrictFieldTypedTargetLedger.Attempt, TaskPlanDraftInvocationObjects.Attempt, TaskPlanDraftObjectLedger.Attempt, TaskProfessionalObjectLedger.Attempt, TaskNodeInputLedger.Attempt, TaskFieldTargetLedger.Attempt, TaskRunReplayLedger.Attempt, RuntimeObjectLedger.Attempt, FormalInvocationObjectLedger.Attempt, FormalAgentPublicationObjectLedger.Attempt, FormalEvaluationExactInputObjectLedger.Attempt, FormalEvaluationPlanPreparationLedger.Attempt, FormalEvaluationNodeInputObjectLedger.Attempt {
 String tenantId();
 String actorId();
 String expectedObjectKey();
 S3ObjectWriter.WriteIntent writeIntent();
 byte[] bytes();
 boolean priorAttempt();
 enum TransferMode { INITIAL_WRITE, RECOVER_ONLY }
 TransferMode claimTransferMode();
}
