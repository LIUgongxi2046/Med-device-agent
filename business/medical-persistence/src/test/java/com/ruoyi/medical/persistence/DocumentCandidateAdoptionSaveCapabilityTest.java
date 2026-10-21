package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Deferred unit tests: capability rejection, not physical-store or DB acceptance. */
class DocumentCandidateAdoptionSaveCapabilityTest {
    @AfterEach void reset(){TransactionSynchronizationManager.setActualTransactionActive(false);}
    @Test void receiptsAndIntermediateRegistrationHaveNoCallerConstructibleConstructor() {
        for(var type:List.of(DocumentCandidateAdoptionSaveService.Saved.class,DocumentCandidateAdoptionSaveService.Registration.class)) {
            assertEquals(0,type.getConstructors().length);
            assertTrue(Arrays.stream(type.getDeclaredConstructors()).allMatch(c->Modifier.isPrivate(c.getModifiers())));
        }
    }
    @Test void normalizedRevisionPersistenceRequiresPrivateMintedRegistrationNotRawIds() throws Exception {
        var method=DocumentRevisionService.class.getDeclaredMethod("persistAdoptionContent",EnterpriseAccess.Admission.class,
            DocumentCandidateAdoptionService.Confirmed.class,DocumentCandidateAdoptionSaveService.Registration.class);
        assertFalse(Modifier.isPublic(method.getModifiers()));
        assertTrue(Arrays.stream(method.getParameterTypes()).noneMatch(t->t==String.class));
    }
    @Test void savedReceiptRejectsUseOutsideItsTransactionBeforeAnyReadback()throws Exception {
        var a=mock(EnterpriseAccess.Admission.class);var c=mock(DocumentCandidateAdoptionService.Confirmed.class);
        var saved=saved(a,c);assertDenied(()->saved.requireFor(a,c));
    }
    @Test void savedReceiptRejectsAnotherAdmissionOrConfirmationEvenInsideTransaction()throws Exception {
        var a=mock(EnterpriseAccess.Admission.class);var c=mock(DocumentCandidateAdoptionService.Confirmed.class);
        var saved=saved(a,c);TransactionSynchronizationManager.setActualTransactionActive(true);
        assertDenied(()->saved.requireFor(mock(EnterpriseAccess.Admission.class),c));
        assertDenied(()->saved.requireFor(a,mock(DocumentCandidateAdoptionService.Confirmed.class)));
    }
    @Test void retiredReceiptCannotBeConsumedAgain()throws Exception {
        var a=mock(EnterpriseAccess.Admission.class);var c=mock(DocumentCandidateAdoptionService.Confirmed.class);
        var saved=saved(a,c);var active=saved.getClass().getDeclaredField("active");active.setAccessible(true);active.setBoolean(saved,false);
        TransactionSynchronizationManager.setActualTransactionActive(true);assertDenied(()->saved.requireFor(a,c));
    }
    private static DocumentCandidateAdoptionSaveService.Saved saved(EnterpriseAccess.Admission a,DocumentCandidateAdoptionService.Confirmed c)throws Exception {
        // Reflection is test-only to exercise denial paths. Production has no receipt factory.
        var ctor=DocumentCandidateAdoptionSaveService.Saved.class.getDeclaredConstructors()[0];ctor.setAccessible(true);
        return (DocumentCandidateAdoptionSaveService.Saved)ctor.newInstance(mock(DocumentCandidateAdoptionSaveService.class),a,c,
            "00000000-0000-4000-8000-000000000001","2",List.of());
    }
    private static void assertDenied(org.junit.jupiter.api.function.Executable action){assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,action).code());}
}
