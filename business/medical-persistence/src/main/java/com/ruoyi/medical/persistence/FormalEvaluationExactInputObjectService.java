package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.springframework.util.function.ThrowingSupplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.medical.contracts.ErrorCode;

/** Three-phase coordinator: fresh physical originals/compiler outside transactions; durable intent
 * and attempt inside a transaction; actual object I/O outside; fresh final registration transaction.
 * No default-on bean or HTTP route is installed by this bounded slice. */
public final class FormalEvaluationExactInputObjectService {
    private final EnterpriseAccess access;
    private final FormalEvaluationExactInputObjectSchema schema;
    private final FormalEvaluationExactInputObjectAuthority authority;
    private final FormalEvaluationExactInputObjectLedger ledger;
    private final RuntimeObjectTransport transport;
    private final FormalEvaluationExactInputObjectRegistration registrations;
    private final PermissionSchema json;

    public FormalEvaluationExactInputObjectService(EnterpriseAccess access, FormalEvaluationExactInputObjectSchema schema,
            FormalEvaluationExactInputObjectAuthority authority, FormalEvaluationExactInputObjectLedger ledger, RuntimeObjectTransport transport,
            FormalEvaluationExactInputObjectRegistration registrations, PermissionSchema json) {
        this.access = access;
        this.schema = schema;
        this.authority = authority;
        this.ledger = ledger;
        this.transport = transport;
        this.registrations = registrations;
        this.json = json;
    }

    public FormalEvaluationExactInputObjectRegistration.Receipt register(ApplicationSessionIdentity.Verified subject,
            String context, String originalKey, byte[] originalBody) throws Exception {
        return execute(subject, context, originalKey, originalBody, false);
    }
    /** Does not create missing intent/attempt, change the deadline or resend uncertain bytes. */
    public FormalEvaluationExactInputObjectRegistration.Receipt recover(ApplicationSessionIdentity.Verified subject,
            String context, String originalKey, byte[] originalBody) throws Exception {
        return execute(subject, context, originalKey, originalBody, true);
    }

    private FormalEvaluationExactInputObjectRegistration.Receipt execute(ApplicationSessionIdentity.Verified subject,
            String context, String key, byte[] bytes, boolean recovery) throws Exception {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        json.headers(context, key);
        var original = schema.request(bytes);
        var prepared = authority.prepare(subject, context, original);
        var attempt = access.execute(subject, a -> ThrowingSupplier.of(() -> recovery ? ledger.recover(a, key, prepared) : ledger.prepare(a,
                key, prepared)).get());
        var existing = access.execute(subject, a -> ThrowingSupplier.of(() -> registrations.read(a, prepared, attempt)).get());
        if (existing.isPresent()) return existing.get();
        // A failed/unknown initial send is never retried. Persisted attempts and the in-memory claim
        // both force subsequent transport to the original immutable object's readback-only path.
        var physical = transport.transferVerified(attempt, () -> access.execute(subject, a -> ThrowingSupplier.of(() -> {
            ledger.requireTransfer(a, attempt, prepared);
            return null;
        }).get()));
        return access.execute(subject, a -> ThrowingSupplier.of(() -> registrations.register(a, prepared, attempt, physical)).get());
    }
}
