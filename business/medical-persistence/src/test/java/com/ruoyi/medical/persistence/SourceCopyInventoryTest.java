package com.ruoyi.medical.persistence;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred under the user's implementation-first instruction. No provider calls. */
class SourceCopyInventoryTest {
    private static final String TENANT="11111111-1111-4111-8111-111111111111";
    private static final String SOURCE="22222222-2222-4222-8222-222222222222";
    private static final Instant NOW=Instant.parse("2026-10-21T00:00:00Z");
    private final PermissionSchema json=new PermissionSchema();
    SourceCopyInventoryTest()throws Exception{}
    @Test void partialEnumerationNeverProvesAbsence(){
        var result=SourceCopyInventory.produce(json,"backup","data/",TENANT,SOURCE,NOW,List.of(),false);
        assertFalse(result.path("complete").asBoolean());
        assertThrows(TenantAccessException.class,()->SourceCopyInventory.requireAbsent(result));
    }
    @Test void unlabelledContentCannotBeIgnoredAsAnotherTenant(){
        var copy=new SourceCopyInventory.Copy("data/a","v1",false,null,null,null,null,null);
        var result=SourceCopyInventory.produce(json,"backup","data/",TENANT,SOURCE,NOW,List.of(copy),true);
        assertEquals("UNKNOWN",result.path("status").asText());
        assertThrows(TenantAccessException.class,()->SourceCopyInventory.requireAbsent(result));
    }
    @Test void expiredButStillPresentIsNotDeleted(){
        var copy=new SourceCopyInventory.Copy("data/a","v1",false,TENANT,SOURCE,"a".repeat(64),NOW.minusSeconds(1),false);
        var result=SourceCopyInventory.produce(json,"backup","data/",TENANT,SOURCE,NOW,List.of(copy),true);
        assertTrue(result.path("complete").asBoolean());
        assertEquals("PRESENT",result.path("status").asText());
        assertThrows(TenantAccessException.class,()->SourceCopyInventory.requireAbsent(result));
    }
    @Test void retentionHoldIsPreserved(){
        var copy=new SourceCopyInventory.Copy("data/a","v1",false,TENANT,SOURCE,"a".repeat(64),NOW.minusSeconds(1),true);
        var result=SourceCopyInventory.produce(json,"backup","data/",TENANT,SOURCE,NOW,List.of(copy),true);
        assertTrue(result.path("copies").get(0).path("legal_hold").asBoolean());
        assertThrows(TenantAccessException.class,()->SourceCopyInventory.requireAbsent(result));
    }
    @Test void unknownRetentionRemainsUnknown(){
        var copy=new SourceCopyInventory.Copy("data/a","v1",false,TENANT,SOURCE,"a".repeat(64),null,null);
        var result=SourceCopyInventory.produce(json,"backup","data/",TENANT,SOURCE,NOW,List.of(copy),true);
        assertFalse(result.path("complete").asBoolean());
        assertThrows(TenantAccessException.class,()->SourceCopyInventory.requireAbsent(result));
    }
    @Test void duplicatedVersionCannotBeDoubleCounted(){
        var copy=new SourceCopyInventory.Copy("data/a","v1",true,null,null,null,null,null);
        assertThrows(TenantAccessException.class,()->SourceCopyInventory.produce(json,"backup","data/",TENANT,SOURCE,NOW,List.of(copy,copy),true));
    }
    @Test void deleteMarkerDoesNotHideOlderContent(){
        var marker=new SourceCopyInventory.Copy("data/a","v2",true,null,null,null,null,null);
        var content=new SourceCopyInventory.Copy("data/a","v1",false,TENANT,SOURCE,"a".repeat(64),NOW.minusSeconds(1),false);
        var result=SourceCopyInventory.produce(json,"backup","data/",TENANT,SOURCE,NOW,List.of(marker,content),true);
        assertEquals(1,result.path("copies").size());
        assertThrows(TenantAccessException.class,()->SourceCopyInventory.requireAbsent(result));
    }
    @Test void exhaustiveEmptyProviderScopeProvesOnlyThatScope(){
        var result=SourceCopyInventory.produce(json,"backup","data/",TENANT,SOURCE,NOW,List.of(),true);
        assertEquals("CONFIGURED_STORE_SCOPE",result.path("authority").asText());
        assertDoesNotThrow(()->SourceCopyInventory.requireAbsent(result));
    }
    @Test void outOfScopeObjectsAreRejected(){
        var copy=new SourceCopyInventory.Copy("other/a","v1",true,null,null,null,null,null);
        assertThrows(TenantAccessException.class,()->SourceCopyInventory.produce(json,"backup","data/",TENANT,SOURCE,NOW,List.of(copy),true));
    }
    @Test void callerCompleteFlagAloneIsNotEnough(){
        assertThrows(TenantAccessException.class,()->SourceCopyInventory.requireAbsent(json.object().put("complete",true)));
    }
}
