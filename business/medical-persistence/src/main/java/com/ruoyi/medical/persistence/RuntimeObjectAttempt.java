package com.ruoyi.medical.persistence;

/** Only real immutable ledger readers may construct these attempt proofs.
 * Neither caller JSON nor an arbitrary boolean adapter is an authorized write. */
public sealed interface RuntimeObjectAttempt permits RuntimeObjectLedger.Attempt, FormalInvocationObjectLedger.Attempt, FormalAgentPublicationObjectLedger.Attempt {
 String tenantId();
 String actorId();
 String expectedObjectKey();
 S3ObjectWriter.WriteIntent writeIntent();
 byte[] bytes();
 boolean priorAttempt();
 enum TransferMode { INITIAL_WRITE, RECOVER_ONLY }
 TransferMode claimTransferMode();
}
