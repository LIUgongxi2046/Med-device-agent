package com.ruoyi.medical.persistence.erasure;

import java.time.Instant;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.erasure.ExternalErasure.*;

class ExternalErasureAdapterTest {
    @TempDir Path directory;
    private static final Instant NOW=Instant.parse("2026-10-21T00:00:00Z");
    private static final String HASH="a".repeat(64);
    static final class Guard implements Authority {
        boolean revoked;int calls;int revokeAt=Integer.MAX_VALUE;
        public <T>T current(Approval a,Action<T> action)throws Exception {if(revoked||++calls>=revokeAt)throw new IllegalStateException("REVOKED");return action.run();}
        public Instant now(){return NOW;}
    }
    record Fixture(Scope scope,Copy copy,Approval approval,SyntheticErasureService api,Guard guard,ExternalErasureAdapter adapter,ExternalErasureFileJournal journal) {
        Delete request(){return new Delete(VERSION,Proof.SIMULATED,"sim-key",approval,copy);}
    }
    Fixture fixture(Kind kind,Hold hold,Instant retained) {
        Scope scope=new Scope("sim-tenant","sim-source","sim-store",kind,"revision-1");
        Copy copy=new Copy(scope,"sim-copy","v1",HASH,kind==Kind.PROVIDER?"sim-dense-operation":null,hold,retained);
        Approval approval=new Approval("sim-authority",scope,"sim-frozen-inventory","sim-declarer","sim-reviewer",NOW.plusSeconds(3600),Proof.SIMULATED);
        var api=new SyntheticErasureService(scope,List.of(copy),NOW,1);var guard=new Guard();
        var journal=new ExternalErasureFileJournal(directory);var adapter=new ExternalErasureAdapter(kind,api,journal,guard,100);
        return new Fixture(scope,copy,approval,api,guard,adapter,journal);
    }
    @Test void offlineInventoryDeleteReceiptAndFinalAbsence()throws Exception {
        var f=fixture(Kind.OFFLINE,Hold.CLEAR,null);assertEquals(1,f.adapter.inventory(f.approval).copies().size());
        assertEquals(Outcome.ACKNOWLEDGED,f.adapter.erase(f.request()).outcome());
        var absence=f.adapter.finalAbsence(f.approval);assertEquals(Proof.SIMULATED,absence.proof());assertFalse(absence.productionPurgeAllowed());
        assertEquals(1,f.api.deleteCalls());
    }
    @Test void providerCoverageSurvivesRemoteFixtureErasure()throws Exception {
        var f=fixture(Kind.PROVIDER,Hold.CLEAR,null);f.adapter.erase(f.request());
        assertEquals(Set.of("sim-dense-operation"),f.adapter.inventory(f.approval).coveredOperations());
        assertTrue(f.adapter.inventory(f.approval).copies().isEmpty());
    }
    @Test void incompleteInventoryCannotAuthorizeDeletion() {
        var f=fixture(Kind.OFFLINE,Hold.CLEAR,null);f.api.authoritative(false);
        assertThrows(IllegalStateException.class,()->f.adapter.erase(f.request()));assertEquals(0,f.api.deleteCalls());
    }
    @Test void sameActorCannotApprove(){var f=fixture(Kind.OFFLINE,Hold.CLEAR,null);assertThrows(IllegalArgumentException.class,()->new Approval("sim-a",f.scope,"s","sim-person","sim-person",NOW,Proof.SIMULATED));}
    @Test void lostAcknowledgementRecoversOriginalWithoutResending()throws Exception {
        var f=fixture(Kind.PROVIDER,Hold.CLEAR,null);f.api.loseNextAcknowledgement();
        assertThrows(java.io.IOException.class,()->f.adapter.erase(f.request()));assertEquals(Phase.UNKNOWN,f.journal.read("sim-key").phase());
        assertEquals(Outcome.ACKNOWLEDGED,f.adapter.erase(f.request()).outcome());assertEquals(1,f.api.deleteCalls());
    }
    @Test void crashAfterDispatchBeforeSendStaysUnknownWithoutResend()throws Exception {
        var f=fixture(Kind.PROVIDER,Hold.CLEAR,null);f.journal.prepare(f.request());f.journal.transition("sim-key",Phase.PREPARED,Phase.DISPATCHING,null);
        assertEquals(Outcome.UNKNOWN,f.adapter.erase(f.request()).outcome());assertEquals(0,f.api.deleteCalls());
    }
    @Test void legalHoldBlocksDeleteAndAbsence() {
        var f=fixture(Kind.OFFLINE,Hold.LEGAL,null);assertThrows(IllegalStateException.class,()->f.adapter.erase(f.request()));
        assertThrows(IllegalStateException.class,()->f.adapter.finalAbsence(f.approval));assertEquals(0,f.api.deleteCalls());
    }
    @Test void unknownHoldBlocksDeletion(){var f=fixture(Kind.OFFLINE,Hold.UNKNOWN,null);assertThrows(IllegalStateException.class,()->f.adapter.erase(f.request()));assertEquals(0,f.api.deleteCalls());}
    @Test void unexpiredRetentionBlocksDeletion(){var f=fixture(Kind.OFFLINE,Hold.RETENTION,NOW.plusSeconds(60));assertThrows(IllegalStateException.class,()->f.adapter.erase(f.request()));}
    @Test void expiredRetentionAllowsExactDeletion()throws Exception{var f=fixture(Kind.OFFLINE,Hold.RETENTION,NOW.minusSeconds(1));assertEquals(Outcome.ACKNOWLEDGED,f.adapter.erase(f.request()).outcome());}
    @Test void revocationBetweenInventoryAndDeletePreventsSend(){var f=fixture(Kind.PROVIDER,Hold.CLEAR,null);f.guard.revokeAt=3;assertThrows(IllegalStateException.class,()->f.adapter.erase(f.request()));assertEquals(0,f.api.deleteCalls());}
    @Test void revokedRecoveryCannotLeakReceipt()throws Exception{var f=fixture(Kind.PROVIDER,Hold.CLEAR,null);f.adapter.erase(f.request());f.guard.revoked=true;assertThrows(IllegalStateException.class,()->f.adapter.recover(f.approval,"sim-key"));}
    @Test void durableRestartDoesNotResend()throws Exception {
        var f=fixture(Kind.OFFLINE,Hold.CLEAR,null);f.adapter.erase(f.request());
        var restarted=new ExternalErasureAdapter(Kind.OFFLINE,f.api,new ExternalErasureFileJournal(directory),f.guard,100);
        assertEquals(Outcome.ACKNOWLEDGED,restarted.erase(f.request()).outcome());assertEquals(1,f.api.deleteCalls());
    }
    @Test void changedVersionAndDigestCannotDeleteOtherCopy(){
        var f=fixture(Kind.OFFLINE,Hold.CLEAR,null);Copy other=new Copy(f.scope,"sim-copy","v2","b".repeat(64),null,Hold.CLEAR,null);
        assertThrows(IllegalStateException.class,()->f.adapter.erase(new Delete(VERSION,Proof.SIMULATED,"sim-key",f.approval,other)));assertEquals(0,f.api.deleteCalls());
    }
    @Test void noSimulationReceiptCanClaimProductionPurge(){var f=fixture(Kind.OFFLINE,Hold.CLEAR,null);assertThrows(IllegalArgumentException.class,()->new Absence(VERSION,Proof.SIMULATED,f.scope,"s",NOW,true));}
    @Test void syntheticServiceRefusesRealIdentifiers(){Scope scope=new Scope("tenant","source","store",Kind.OFFLINE,"revision");assertThrows(IllegalArgumentException.class,()->new SyntheticErasureService(scope,List.of(),NOW,1));}
    @Test void paginationEnumeratesUnregisteredSurvivingCopies()throws Exception {
        var f=fixture(Kind.OFFLINE,Hold.CLEAR,null);Copy other=new Copy(f.scope,"sim-unregistered","v2",HASH,null,Hold.CLEAR,null);
        var api=new SyntheticErasureService(f.scope,List.of(f.copy,other),NOW,1);
        var adapter=new ExternalErasureAdapter(Kind.OFFLINE,api,f.journal,f.guard,100);assertEquals(2,adapter.inventory(f.approval).copies().size());
        adapter.erase(f.request());assertThrows(IllegalStateException.class,()->adapter.finalAbsence(f.approval));assertNotNull(api.inspect(f.scope,other.copyId(),other.version()));
    }
    @Test void realHttpClientRejectsLoopbackCleartextAndRedirectConfiguration(){
        assertThrows(IllegalArgumentException.class,()->new ExternalErasureHttpApi(java.net.URI.create("http://127.0.0.1:9000/"),"fixture",Proof.SIMULATED));
        assertThrows(IllegalArgumentException.class,()->new ExternalErasureHttpApi(java.net.URI.create("https://user@example.invalid/"),"fixture",Proof.AUTHORITATIVE));
    }
}
