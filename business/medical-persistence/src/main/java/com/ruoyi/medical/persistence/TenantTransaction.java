package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.TenantWatermarkMapper;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public final class TenantTransaction {
    private final TransactionTemplate transaction;
    private final TenantWatermarkMapper watermarks;

    public TenantTransaction(PlatformTransactionManager manager, TenantWatermarkMapper watermarks) {
        transaction = new TransactionTemplate(Objects.requireNonNull(manager));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.watermarks = Objects.requireNonNull(watermarks);
    }

    public <T> T execute(Function<TenantWatermarks, T> operation) {
        Objects.requireNonNull(operation);
        TenantScope scope = TenantScope.requireCurrent();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        }
        return transaction.execute(status -> {
            TenantWatermarks current = watermarks.lockCurrentTenant();
            scope.verifyTransaction(current);
            try {
                return operation.apply(current);
            } finally {
                scope.finishTransaction();
            }
        });
    }
}
