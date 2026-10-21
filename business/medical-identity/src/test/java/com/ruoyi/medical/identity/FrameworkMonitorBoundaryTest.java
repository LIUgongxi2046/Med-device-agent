package com.ruoyi.medical.identity;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** Deferred by user instruction: authored but not executed. */
class FrameworkMonitorBoundaryTest {
    @Test void allowlistCannotReachSecurityCachesOrArbitraryConfig() {
        assertTrue(FrameworkCacheMonitorController.isAllowedKey("sys_dict:sys_user_sex"));
        assertTrue(FrameworkCacheMonitorController.isAllowedKey("sys_config:sys.index.skinName"));
        for (String key : new String[]{"login_tokens:abc", "spring:session:sessions:abc", "captcha_codes:abc",
                "repeat_submit:abc", "rate_limit:abc", "pwd_err_cnt:admin", "sys_config:sys.user.initPassword",
                "sys_config:sys.account.captchaEnabled", "sys_config:*", "sys_dict:*", "sys_dict:../login_tokens:abc", "*"})
            assertFalse(FrameworkCacheMonitorController.isAllowedKey(key), key);
        assertFalse(FrameworkCacheMonitorController.isAllowedKey(null));
    }
    @Test void handlesAreOpaqueBoundToActorAndExpire() {
        var clock = new MutableClock();
        var handles = new FrameworkSessionHandles(clock);
        String handle = handles.issue("admin-session", "target-session", "business-user", "7");
        assertFalse(handle.contains("target-session"));
        assertEquals("target-session", handles.resolve("admin-session", handle).session());
        assertThrows(ResponseStatusException.class, () -> handles.resolve("other-admin-session", handle));
        assertThrows(ResponseStatusException.class, () -> handles.resolve("admin-session", "target-session"));
        assertFalse(handles.resolve("admin-session", handle).toString().contains("target-session"));
        clock.now = clock.now.plusSeconds(300);
        assertThrows(ResponseStatusException.class, () -> handles.resolve("admin-session", handle));
    }
    @Test void successfulRemovalInvalidatesReference() {
        var handles = new FrameworkSessionHandles();
        String handle = handles.issue("admin", "target", "user", "1");
        handles.remove(handle);
        assertThrows(ResponseStatusException.class, () -> handles.resolve("admin", handle));
    }
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-21T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
