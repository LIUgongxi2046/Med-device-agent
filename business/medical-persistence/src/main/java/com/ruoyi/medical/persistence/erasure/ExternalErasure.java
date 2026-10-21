package com.ruoyi.medical.persistence.erasure;

import java.time.Instant;
import java.util.*;

/** Versioned external-erasure SPI. The SPI is deliberately
 * not a Spring bean or an input to SourceCopyAuthority / SourceDeletionEvidence. */
public final class ExternalErasure {
    private ExternalErasure() {}
    public static final String VERSION = "1.0.0";
    public enum Proof { SIMULATED, AUTHORITATIVE }
    public enum Kind { OFFLINE, PROVIDER }
    public enum Hold { CLEAR, LEGAL, RETENTION, UNKNOWN }
    public enum Outcome { ACKNOWLEDGED, ALREADY_ABSENT, UNKNOWN, RETAINED }
    public enum Phase { PREPARED, DISPATCHING, UNKNOWN, COMPLETE }
    public record Scope(String tenant, String source, String scopeId, Kind kind, String revision) {
        public Scope { token(tenant); token(source); token(scopeId); token(revision); Objects.requireNonNull(kind); }
    }
    public record Copy(Scope scope, String copyId, String version, String sha256,
                       String providerOperation, Hold hold, Instant retainedUntil) {
        public Copy {
            Objects.requireNonNull(scope); token(copyId); token(version); hash(sha256); Objects.requireNonNull(hold);
            if (scope.kind()==Kind.PROVIDER) token(providerOperation);
            else if (providerOperation!=null) throw new IllegalArgumentException("OFFLINE_PROVIDER_REFERENCE");
            if (hold==Hold.RETENTION && retainedUntil==null) throw new IllegalArgumentException("UNKNOWN_RETENTION");
        }
        public String identity() { return copyId+"/"+version; }
        public boolean retained(Instant now) { return hold==Hold.UNKNOWN || hold==Hold.LEGAL ||
            retainedUntil!=null && retainedUntil.isAfter(now); }
    }
    /** Snapshot and revision stay fixed over every page; complete means authoritative enumeration,
     * not absence. Empty/incomplete inventories may never authorize deletion or final absence. */
    public record Page(String schemaVersion, Proof proof, Scope scope, String snapshot,
                       List<Copy> copies, List<String> coveredOperations, String nextCursor, boolean authoritative, Instant observedAt) {
        public Page { version(schemaVersion); Objects.requireNonNull(proof); Objects.requireNonNull(scope); token(snapshot);
            copies=List.copyOf(copies); coveredOperations=List.copyOf(coveredOperations); coveredOperations.forEach(ExternalErasure::token); Objects.requireNonNull(observedAt);
            if(nextCursor!=null) token(nextCursor); }
    }
    public record Inventory(Scope scope, String snapshot, List<Copy> copies, Set<String> coveredOperations, Instant observedAt) {
        public Inventory { Objects.requireNonNull(scope); token(snapshot); copies=List.copyOf(copies); coveredOperations=Set.copyOf(coveredOperations); Objects.requireNonNull(observedAt); }
    }
    public record Approval(String id, Scope scope, String snapshot, String declarer,
                           String approver, Instant validUntil, Proof proof) {
        public Approval { token(id); Objects.requireNonNull(scope); token(snapshot); token(declarer); token(approver);
            if(declarer.equals(approver)) throw new IllegalArgumentException("INDEPENDENT_APPROVAL_REQUIRED");
            Objects.requireNonNull(validUntil); Objects.requireNonNull(proof); }
    }
    public record Delete(String schemaVersion, Proof proof, String key, Approval approval, Copy copy) {
        public Delete { version(schemaVersion); Objects.requireNonNull(proof); token(key); Objects.requireNonNull(approval);
            Objects.requireNonNull(copy); if(proof!=approval.proof()) throw new IllegalArgumentException("PROOF_MISMATCH"); if(!approval.scope().equals(copy.scope())) throw new IllegalArgumentException("SCOPE_MISMATCH"); }
    }
    public record Receipt(String schemaVersion, Proof proof, String key, Scope scope, String copyId,
                          String copyVersion, String sha256, Outcome outcome, String providerReceipt,
                          Instant observedAt) {
        public Receipt { version(schemaVersion); Objects.requireNonNull(proof); token(key); Objects.requireNonNull(scope);
            token(copyId); token(copyVersion); hash(sha256); Objects.requireNonNull(outcome); Objects.requireNonNull(observedAt);
            if(outcome==Outcome.ACKNOWLEDGED) token(providerReceipt);
            else if(providerReceipt!=null) throw new IllegalArgumentException("INVENTED_RECEIPT"); }
    }
    public record Absence(String schemaVersion, Proof proof, Scope scope, String snapshot,
                          Instant observedAt, boolean productionPurgeAllowed) {
        public Absence { version(schemaVersion); Objects.requireNonNull(proof); Objects.requireNonNull(scope); token(snapshot);
            Objects.requireNonNull(observedAt); if(productionPurgeAllowed && proof!=Proof.AUTHORITATIVE) throw new IllegalArgumentException("SIMULATION_NOT_PRODUCTION_AUTHORITY"); }
    }
    /** Every operation is against one exact scope. Implementations may never widen it. */
    public interface Api {
        Page inventory(Scope scope, String cursor) throws Exception;
        Copy inspect(Scope scope, String copyId, String version) throws Exception;
        Receipt delete(Delete request) throws Exception;
        Optional<Receipt> recover(Scope scope, String originalKey) throws Exception;
    }
    /** Must atomically revalidate identities, independent approval, grant and scope revision,
     * expiration and revocation around each API call, not just at orchestration start. */
    public interface Authority {
        <T> T current(Approval approval, Action<T> action) throws Exception;
        Instant now();
    }
    @FunctionalInterface public interface ActionWithApproval<T> { T run(Approval approval) throws Exception; }
    @FunctionalInterface public interface Action<T> { T run() throws Exception; }
    public record Entry(Delete request, Phase phase, Receipt receipt) {}
    /** Persist before dispatch. Implementations must serialize per key and fail closed on conflicts.
     * Losing this journal must block recovery, never cause a resend. */
    public interface Journal {
        Entry prepare(Delete request);
        void transition(String key, Phase expected, Phase next, Receipt receipt);
        Entry read(String key);
    }
    static void fixture(String value) {
        if(value==null || !value.matches("sim-[A-Za-z0-9._-]{1,120}")) throw new IllegalArgumentException("SYNTHETIC_FIXTURE_REQUIRED");
    }
    static void token(String value) {
        if(value==null || !value.matches("[A-Za-z0-9._-]{1,160}")) throw new IllegalArgumentException("INVALID_TOKEN");
    }
    static void hash(String value) {
        if(value==null || !value.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("INVALID_DIGEST");
    }
    static void version(String value) { if(!VERSION.equals(value)) throw new IllegalArgumentException("UNSUPPORTED_SCHEMA"); }
    static void simulation(Proof proof) { if(proof!=Proof.SIMULATED) throw new IllegalArgumentException("SIMULATION_REQUIRED"); }
}
