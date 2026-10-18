package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentRevisionAssignmentCursorTest {
    static DocumentRevisionAssignmentCursor.Owner owner(String actor,String context) {
        return new DocumentRevisionAssignmentCursor.Owner("tenant",actor,"session",context,"1","1",10);
    }
    @Test void tokenBindsActorSessionContextEpochsAndLimit() {
        var cache=new DocumentRevisionAssignmentCursor(); var owner=owner("actor","1"); var token=cache.issue(owner,"after");
        assertEquals("after",cache.resolve(token,owner));
        for(var changed:new DocumentRevisionAssignmentCursor.Owner[]{owner("other","1"),owner("actor","2"),
                new DocumentRevisionAssignmentCursor.Owner("tenant","actor","other","1","1","1",10),
                new DocumentRevisionAssignmentCursor.Owner("tenant","actor","session","1","2","1",10),
                new DocumentRevisionAssignmentCursor.Owner("tenant","actor","session","1","1","2",10),
                new DocumentRevisionAssignmentCursor.Owner("tenant","actor","session","1","1","1",20)})
            assertEquals(ErrorCode.CURSOR_INVALID,assertThrows(TenantAccessException.class,()->cache.resolve(token,changed)).code());
    }
    @Test void expiredEvictedAndRestartedTokensRequireExplicitFirstPageReload() {
        var clock=new MutableClock(); var cache=new DocumentRevisionAssignmentCursor(clock); var owner=owner("actor","1");
        var expired=cache.issue(owner,"after"); clock.now=clock.now.plusSeconds(300);
        assertEquals(ErrorCode.CURSOR_EXPIRED,assertThrows(TenantAccessException.class,()->cache.resolve(expired,owner)).code());
        var evicted=cache.issue(owner,"after"); for(int i=0;i<1024;i++) cache.issue(owner,"next");
        assertEquals(ErrorCode.CURSOR_EXPIRED,assertThrows(TenantAccessException.class,()->cache.resolve(evicted,owner)).code());
        assertEquals(ErrorCode.CURSOR_EXPIRED,assertThrows(TenantAccessException.class,()->new DocumentRevisionAssignmentCursor().resolve(expired,owner)).code());
    }
    static final class MutableClock extends Clock {
        Instant now=Instant.parse("2026-10-18T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;} public Instant instant(){return now;}
    }
}
