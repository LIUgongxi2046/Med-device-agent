package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.ErrorCode;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AgentAuthoringCursorTest {
 final AgentAuthoringCursor.Owner owner=new AgentAuthoringCursor.Owner("tenant","actor","session","1","2","3",50,"POLICY_DRAFT");
 @Test void opaqueTokenDoesNotDiscloseSkippedRecordId(){var c=new AgentAuthoringCursor();String skipped="00000000-0000-4000-8000-000000000099";String token=c.issue(owner,skipped);assertNotEquals(token,skipped);assertEquals(skipped,c.resolve(token,owner));}
 @Test void anotherActorCannotReuseCursor(){var c=new AgentAuthoringCursor();var token=c.issue(owner,"last");var other=new AgentAuthoringCursor.Owner("tenant","other","session","1","2","3",50,"POLICY_DRAFT");assertEquals(ErrorCode.CURSOR_INVALID,assertThrows(TenantAccessException.class,()->c.resolve(token,other)).code());}
 @Test void anotherKindOrContextCannotReuseCursor(){var c=new AgentAuthoringCursor();var token=c.issue(owner,"last");assertThrows(TenantAccessException.class,()->c.resolve(token,new AgentAuthoringCursor.Owner("tenant","actor","session","2","2","3",50,"POLICY_DRAFT")));assertThrows(TenantAccessException.class,()->c.resolve(token,new AgentAuthoringCursor.Owner("tenant","actor","session","1","2","3",50,"POLICY")));}
 @Test void expiredTokensDoNotRestartFromBeginning(){var clock=new Mutable();var c=new AgentAuthoringCursor(clock);var token=c.issue(owner,"last");clock.time=clock.time.plusSeconds(301);assertEquals(ErrorCode.CURSOR_EXPIRED,assertThrows(TenantAccessException.class,()->c.resolve(token,owner)).code());}
 static class Mutable extends Clock {Instant time=Instant.parse("2026-10-18T00:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return time;}}
}
