package com.ruoyi.medical.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Process-local, short-lived administrative references, never authentication credentials. */
final class FrameworkSessionHandles {
    private final Clock clock;
    private final Map<String, Target> targets = new HashMap<>();
    FrameworkSessionHandles() { this(Clock.systemUTC()); }
    FrameworkSessionHandles(Clock clock) { this.clock = clock; }
    synchronized String issue(String actorSession, String session, String user, String credential) {
        expire();
        for (var entry : targets.entrySet()) {
            Target t = entry.getValue();
            if (t.actorSession.equals(actorSession) && t.session.equals(session) && t.credential.equals(credential)) return entry.getKey();
        }
        if (targets.size() >= 10000) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MONITOR_HANDLE_CAPACITY");
        String handle = UUID.randomUUID().toString();
        targets.put(handle, new Target(actorSession, session, user, credential, clock.instant().plus(Duration.ofMinutes(5))));
        return handle;
    }
    synchronized Target resolve(String actorSession, String handle) {
        expire();
        Target target = targets.get(handle);
        if (target == null || !target.actorSession.equals(actorSession)) throw new ResponseStatusException(HttpStatus.CONFLICT, "ONLINE_LIST_REFRESH_REQUIRED");
        return target;
    }
    synchronized void remove(String handle) { targets.remove(handle); }
    private void expire() { Instant now = clock.instant(); targets.values().removeIf(t -> !t.expires.isAfter(now)); }
    record Target(String actorSession, String session, String user, String credential, Instant expires) {
        @Override public String toString() { return "AdministrativeSessionReference"; }
    }
}
