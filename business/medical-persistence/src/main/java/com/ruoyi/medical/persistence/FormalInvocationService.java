package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;import java.util.Optional;
import org.springframework.util.function.ThrowingSupplier;

/** Formal verification orchestration. A dispatch permit is single-use; recovery never returns another permit. */
public final class FormalInvocationService {
 private final FormalInvocationStarts starts;private final FormalInvocationCompleted completed;
 private final FormalWorkerAccess workers;private final FormalInvocationReservations reservations;
 private final FormalInvocationSendPreparation preparation;private final FormalInvocationDispatch dispatch;
 private final FormalInvocationReceiptObjects receiptObjects;private final FormalInvocationSettlement settlement;
 public FormalInvocationService(FormalInvocationStarts starts,FormalInvocationCompleted completed,FormalWorkerAccess workers,
    FormalInvocationReservations reservations,FormalInvocationSendPreparation preparation,FormalInvocationDispatch dispatch,
    FormalInvocationReceiptObjects receiptObjects,FormalInvocationSettlement settlement){this.starts=starts;this.completed=completed;this.workers=workers;this.reservations=reservations;this.preparation=preparation;this.dispatch=dispatch;this.receiptObjects=receiptObjects;this.settlement=settlement;}
 public FormalInvocationStarts.StartProof startVerification(ApplicationSessionIdentity.Verified actor,String context,ConnectionVerificationIntentProof intent)throws IOException{return starts.startVerification(actor,context,intent);}
 public Optional<FormalInvocationStarts.StartProof> readVerificationStart(EnterpriseAccess.Admission a,String verificationId)throws IOException{return starts.readVerificationStart(a,verificationId);}
 public Optional<FormalInvocationStarts.StartProof> readAuthorizedVerificationStart(EnterpriseAccess.Admission a,String verificationId)throws IOException{return starts.readAuthorizedVerificationStart(a,verificationId);}
 public FormalInvocationCompleted.SettledProof requireCompletedVerification(EnterpriseAccess.Admission a,String invocationId,String verificationId)throws IOException{return checked(()->completed.require(a,invocationId,verificationId));}
 public FormalInvocationReservations.Reserved reserveExpected(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,String model,String context,com.fasterxml.jackson.databind.JsonNode parameters)throws IOException{return checked(()->workers.executeAction(http,token,action,(a,w)->ThrowingSupplier.of(()->reservations.reserveExpected(a,w,model,context,parameters)).get()));}
 public FormalInvocationReservations.Reserved reserve(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action)throws IOException{return checked(()->workers.executeAction(http,token,action,(a,w)->ThrowingSupplier.of(()->reservations.reserve(a,w)).get()));}
 /** Input object I/O occurs between authenticated transactions, before the immutable one-shot dispatch. */
 public Optional<FormalInvocationDispatch.Permit> beginDispatch(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,String invocationId)throws IOException{return checked(()->{
  var prepared=preparation.prepare(http,token,action);
  return workers.executeAction(http,token,action,(a,w)->ThrowingSupplier.of(()->dispatch.begin(http,a,w,invocationId,prepared)).get());
 });}
 public FormalInvocationSettlement.Recorded recordOutcome(HttpServletRequest http,String tenant,String originalActor,String invocationId,byte[] modelResult,byte[] providerCapture)throws IOException{return checked(()->receiptObjects.record(http,tenant,originalActor,invocationId,modelResult,providerCapture,settlement::record));}
 @FunctionalInterface private interface Work<T>{T run()throws Exception;}
 private static <T>T checked(Work<T> work)throws IOException{try{return work.run();}catch(IOException e){throw e;}catch(RuntimeException e){throw e;}catch(Exception e){throw new IOException("Formal invocation evidence unavailable",e);}}
}
