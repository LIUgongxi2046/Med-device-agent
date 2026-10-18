package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Short-lived opaque navigation only. It never caches permission decisions or document content. */
@Component
public final class AgentAuthoringCursor {
    record Owner(String tenant, String actor, String session, String context, String auth, String governance, int limit,String kind) { }
    private record Entry(Owner owner, String after, Instant expires) { }
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private final Clock clock;
    public AgentAuthoringCursor() { this(Clock.systemUTC()); }
    AgentAuthoringCursor(Clock clock) { this.clock = clock; }
    synchronized String resolve(String token, Owner owner) {
        purge();
        if (token == null) return "00000000-0000-0000-0000-000000000000";
        var entry = entries.get(token);
        if (entry == null) throw new TenantAccessException(ErrorCode.CURSOR_EXPIRED);
        if (!Objects.equals(owner, entry.owner())) throw new TenantAccessException(ErrorCode.CURSOR_INVALID);
        return entry.after();
    }
    synchronized String issue(Owner owner, String after) {
        purge();
        // Evicted tokens fail explicitly with CURSOR_EXPIRED; they never restart silently.
        if (entries.size() >= 1024) entries.remove(entries.keySet().iterator().next());
        String token = UUID.randomUUID().toString();
        entries.put(token, new Entry(owner, after, clock.instant().plusSeconds(300)));
        return token;
    }
    private void purge() { var now = clock.instant(); entries.values().removeIf(entry -> !entry.expires().isAfter(now)); }
}
