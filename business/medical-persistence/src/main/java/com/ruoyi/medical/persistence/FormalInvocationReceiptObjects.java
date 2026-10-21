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
 <T>T record(HttpServletRequest http,String tenant,String actor,String invocation,byte[] result,byte[] capture,RegisteredWork<T> work)throws Exception{return record(http,tenant,actor,invocation,result,capture,work,0);}
 <T>T recordTask(HttpServletRequest http,String tenant,String actor,String invocation,byte[] result,byte[] capture,RegisteredWork<T> work)throws Exception{return record(http,tenant,actor,invocation,result,capture,work,1);}
 <T>T recordPlanDraft(HttpServletRequest http,String tenant,String actor,String invocation,byte[] result,byte[] capture,RegisteredWork<T> work)throws Exception{return record(http,tenant,actor,invocation,result,capture,work,2);}
 <T>T recordSectionWriter(HttpServletRequest http,String tenant,String actor,String invocation,byte[] result,byte[] capture,RegisteredWork<T> work)throws Exception{return record(http,tenant,actor,invocation,result,capture,work,3);}
 <T>T recordSourceVerifier(HttpServletRequest http,String tenant,String actor,String invocation,byte[] result,byte[] capture,RegisteredWork<T> work)throws Exception{return record(http,tenant,actor,invocation,result,capture,work,4);}
 <T>T recordDocumentReview(HttpServletRequest http,String tenant,String actor,String invocation,byte[] result,byte[] capture,RegisteredWork<T> work)throws Exception{return record(http,tenant,actor,invocation,result,capture,work,5);}
 <T>T recordEvaluation(HttpServletRequest http,String tenant,String actor,String invocation,byte[] result,byte[] capture,RegisteredWork<T> work)throws Exception{return record(http,tenant,actor,invocation,result,capture,work,6);}
 private <T>T record(HttpServletRequest http,String tenant,String actor,String invocation,byte[] result,byte[] capture,RegisteredWork<T> work,int mode)throws Exception{
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.CONTEXT_CHANGED);
  Objects.requireNonNull(work);byte[] fixedResult=Objects.requireNonNull(result).clone(),fixedCapture=Objects.requireNonNull(capture).clone();
  Prepared prepared=outcomes.execute(http,tenant,actor,invocation,fixedResult,proof->{
   if(mode==1)registrations.requireTaskOriginal(proof);if(mode==2)registrations.requirePlanDraftOriginal(proof);if(mode==3)registrations.requireSectionWriterOriginal(proof);if(mode==4)registrations.requireSourceVerifierOriginal(proof);if(mode==5)registrations.requireDocumentReviewOriginal(proof);if(mode==6)registrations.requireEvaluationOriginal(proof);var verified=captures.require(proof,fixedCapture);registrations.requireProduction(proof,verified);registrations.requireRecoveryPolicy(proof,transport);
   return new Prepared(proof,ledger.prepare(proof),ledger.prepareCapture(proof,verified));
  });
  // There is deliberately no fallback to a new intent when either transfer is uncertain.
  var resultTransfer=transport.transferVerified(prepared.result(),()->guard(prepared.original(),fixedCapture,mode));
  var captureTransfer=transport.transferVerified(prepared.capture(),()->guard(prepared.original(),fixedCapture,mode));
  return prepared.original().current(proof->{
   if(mode==1)registrations.requireTaskOriginal(proof);if(mode==2)registrations.requirePlanDraftOriginal(proof);if(mode==3)registrations.requireSectionWriterOriginal(proof);if(mode==4)registrations.requireSourceVerifierOriginal(proof);if(mode==5)registrations.requireDocumentReviewOriginal(proof);if(mode==6)registrations.requireEvaluationOriginal(proof);var verified=captures.require(proof,fixedCapture);registrations.requireProduction(proof,verified);registrations.requireRecoveryPolicy(proof,transport);
   var resultObject=registrations.register(proof,prepared.result(),resultTransfer);
   var captureObject=registrations.registerCapture(proof,verified,prepared.capture(),captureTransfer);
   resultObject.requireUnchanged();captureObject.requireUnchanged();proof.requireActive();
   return work.apply(proof,verified,resultObject,captureObject);
  });
 }
 private void guard(FormalInvocationOutcomeAuthority.VerifiedOutcome original,byte[] capture,int mode){
  try{original.current(proof->{if(mode==1)registrations.requireTaskOriginal(proof);if(mode==2)registrations.requirePlanDraftOriginal(proof);if(mode==3)registrations.requireSectionWriterOriginal(proof);if(mode==4)registrations.requireSourceVerifierOriginal(proof);if(mode==5)registrations.requireDocumentReviewOriginal(proof);if(mode==6)registrations.requireEvaluationOriginal(proof);var verified=captures.require(proof,capture);registrations.requireProduction(proof,verified);registrations.requireRecoveryPolicy(proof,transport);return null;});}
  catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("Original dispatch recovery policy revalidation failed",e);}
 }
}
