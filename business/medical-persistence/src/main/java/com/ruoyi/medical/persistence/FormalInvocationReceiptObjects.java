package com.ruoyi.medical.persistence;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Commits two original intents before object I/O. A retry recovers those exact attempts only. */
public final class FormalInvocationReceiptObjects {
 private final FormalInvocationOutcomeAuthority outcomes;
 private final FormalProviderCaptureEvidence captures;
 private final FormalInvocationObjectLedger ledger;
 private final RuntimeObjectTransport transport;
 private final FormalInvocationObjectRegistration registrations;
 public FormalInvocationReceiptObjects(FormalInvocationOutcomeAuthority outcomes,FormalProviderCaptureEvidence captures,
    FormalInvocationObjectLedger ledger,RuntimeObjectTransport transport,FormalInvocationObjectRegistration registrations){
  this.outcomes=Objects.requireNonNull(outcomes);this.captures=Objects.requireNonNull(captures);this.ledger=Objects.requireNonNull(ledger);this.transport=Objects.requireNonNull(transport);this.registrations=Objects.requireNonNull(registrations);
 }
 private record Prepared(FormalInvocationOutcomeAuthority.VerifiedOutcome original,FormalInvocationObjectLedger.Attempt result,FormalInvocationObjectLedger.Attempt capture){}
 @FunctionalInterface interface RegisteredWork<T>{T apply(FormalInvocationOutcomeAuthority.VerifiedOutcome outcome,FormalProviderCaptureEvidence.Verified capture,
    FormalInvocationObjectRegistration.Registered resultObject,FormalInvocationObjectRegistration.Registered captureObject)throws Exception;}
 /** Package-private callback keeps factual registration from becoming a public content-read capability. */
 <T>T record(HttpServletRequest http,String tenant,String actor,String invocation,byte[] result,byte[] capture,RegisteredWork<T> work)throws Exception{
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.CONTEXT_CHANGED);
  Objects.requireNonNull(work);byte[] fixedResult=Objects.requireNonNull(result).clone(),fixedCapture=Objects.requireNonNull(capture).clone();
  Prepared prepared=outcomes.execute(http,tenant,actor,invocation,fixedResult,proof->{
   var verified=captures.require(proof,fixedCapture);
   return new Prepared(proof,ledger.prepare(proof),ledger.prepareCapture(proof,verified));
  });
  // There is deliberately no fallback to a new intent when either transfer is uncertain.
  var resultTransfer=transport.transferVerified(prepared.result(),()->guard(prepared.original()));
  var captureTransfer=transport.transferVerified(prepared.capture(),()->guard(prepared.original()));
  return prepared.original().current(proof->{
   var verified=captures.require(proof,fixedCapture);
   var resultObject=registrations.register(proof,prepared.result(),resultTransfer);
   var captureObject=registrations.registerCapture(proof,verified,prepared.capture(),captureTransfer);
   resultObject.requireUnchanged();captureObject.requireUnchanged();proof.requireActive();
   return work.apply(proof,verified,resultObject,captureObject);
  });
 }
 private static void guard(FormalInvocationOutcomeAuthority.VerifiedOutcome proof){try{proof.requireCurrent();}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("Original dispatch evidence revalidation failed",e);}}
}
