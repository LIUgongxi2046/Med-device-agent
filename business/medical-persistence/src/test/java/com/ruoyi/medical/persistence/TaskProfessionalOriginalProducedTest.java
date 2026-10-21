package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real output factory/075 ledger/governance/registration and original reader against JDBC
 * rows; only current permissions, physical transport and independent099 are explicit doubles. */
class TaskProfessionalOriginalProducedTest extends TaskProfessionalClosedRegistrationTest {
    final TaskProfessionalSupervisor independent=mock(TaskProfessionalSupervisor.class);
    final TaskProfessionalSupervisor.OriginalClosure historicalClosure=mock(TaskProfessionalSupervisor.OriginalClosure.class);
    TaskProfessionalOriginalProducedTest()throws Exception {
        when(independent.requireClosedOriginal(eq(admission),any(),eq(session),eq(id(95)),eq(hash('e')),eq(deployment))).thenReturn(historicalClosure);
    }
    @Override <T>T tx(CheckedSupplier<T> work)throws Exception{return super.tx(()->{var key=new Object();TransactionSynchronizationManager.bindResource(key,new Object());try{return work.get();}finally{TransactionSynchronizationManager.unbindResource(key);}});}
    TaskProfessionalObjectRegistration.Registered registerOriginal()throws Exception {
        var produced=produced();var attempt=attempt(produced);var physical=transfer(attempt);var registration=tx(()->professionalRegistrations.register(leasedProducer,produced,attempt,physical));
        var start=json.object();start.set("binding",closedSession.binding());
        var row=new LinkedHashMap<String,Object>();row.put("tenant_id",tenant);row.put("project_id",project);row.put("run_id",run);row.put("step_id",step);row.put("created_by",actor);row.put("classification","INTERNAL");row.put("session_id",session);row.put("action_id",action);row.put("lease_generation",1L);row.put("input_sha256",hash('a'));row.put("envelope_sha256",hash('b'));row.put("service_uri","spiffe://test.invalid/worker");row.put("admission_state","CLOSED");row.put("canonical_start",json.canonical(start));row.put("start_sha256",TaskProfessionalObjectGovernance.hash(start));row.put("created_at",now);rows("professional_runtime_session").add(row);
        rows("professional_runtime_supervision").add(new LinkedHashMap<>(Map.of("tenant_id",tenant,"session_id",session,"phase","CLOSED","receipt_id",id(95),"evidence_sha256",hash('e'),"created_at",now)));
        return registration;
    }
    TaskProfessionalObjectRegistration.OriginalProduced original(String artifact)throws Exception{return professionalRegistrations.readOriginalProduced(admission,artifact,"SESSION_EXPORT",independent,deployment);}
    @Test void fullOriginal075OutputNeedsRealClosureAndPreservesExactPins()throws Exception {
        var registered=registerOriginal();tx(()->{var actual=original(registered.artifactId());assertEquals(registered.reference(),actual.reference());assertEquals(registered.receipt(),actual.receipt());assertEquals(registered.receiptSha256(),actual.receiptSha256());actual.requireUnchanged();return null;});verify(historicalClosure,atLeastOnce()).requireUnchanged();
        verify(historicalClosure,never()).requireSuccessfulExit(); // diagnostic export itself asserts no success
    }
    @Test void originalOutputRejectsObjectKeyMetadataContentAndJournalCorruption()throws Exception {
        var registered=registerOriginal();var artifact=only("artifact");Object key=artifact.get("object_key");artifact.put("object_key","runtime/forged");assertThrows(TenantAccessException.class,()->tx(()->original(registered.artifactId())));artifact.put("object_key",key);
        var audit=rows("audit_event").stream().filter(r->"runtime.professional.registered".equals(r.get("action"))).findFirst().orElseThrow();audit.put("classification","PUBLIC");assertThrows(TenantAccessException.class,()->tx(()->original(registered.artifactId())));audit.put("classification","INTERNAL");
        only("runtime_professional_object_binding").put("input_receipt_sha256",hash('f'));assertThrows(TenantAccessException.class,()->tx(()->original(registered.artifactId())));
    }
    @Test void closureRevocationOrPreparedEnvelopeCannotMintOriginalProduced()throws Exception {
        var registered=registerOriginal();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(historicalClosure).requireUnchanged();assertThrows(TenantAccessException.class,()->tx(()->original(registered.artifactId())));
        assertThrows(TenantAccessException.class,()->tx(()->professionalRegistrations.readOriginalProduced(admission,registered.artifactId(),"STEP_ENVELOPE",independent,deployment)));
    }
    @Test void originalProducedDoesNotEscapeTransactionAndConstructorIsPrivate()throws Exception {
        var registered=registerOriginal();var original=tx(()->original(registered.artifactId()));assertThrows(TenantAccessException.class,original::bytes);
        for(var constructor:TaskProfessionalObjectRegistration.OriginalProduced.class.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
    }
}
