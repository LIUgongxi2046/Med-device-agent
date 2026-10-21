package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.util.function.ThrowingSupplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Owns actual079 physical reads and088 private compiler/tokenizer proof. Public request data
 * never constructs this authority, an object attempt, or a professional completion. */
public final class FormalEvaluationExactInputObjectAuthority {
    private final EnterpriseAccess access;
    private final FormalEvaluationExecutionInputs inputs;
    private final FormalEvaluationBoundExecutions bounds;
    private final FormalEvaluationModelPreparation models;
    private final FormalEvaluationExactInputPreparation exact;
    private final FormalEvaluationExactInputObjectDependencies dependencies;
    private final FormalEvaluationExactInputObjectAuthorization authorization;
    private final FormalEvaluationExactInputObjectSchema schema;
    private final PermissionSchema json;

    public FormalEvaluationExactInputObjectAuthority(EnterpriseAccess access, FormalEvaluationExecutionInputs inputs,
            FormalEvaluationBoundExecutions bounds, FormalEvaluationModelPreparation models, FormalEvaluationExactInputPreparation exact,
            FormalEvaluationExactInputObjectDependencies dependencies, FormalEvaluationExactInputObjectAuthorization authorization,
            FormalEvaluationExactInputObjectSchema schema, PermissionSchema json) {
        this.access = access;
        this.inputs = inputs;
        this.bounds = bounds;
        this.models = models;
        this.exact = exact;
        this.dependencies = dependencies;
        this.authorization = authorization;
        this.schema = schema;
        this.json = json;
    }

    Prepared prepare(ApplicationSessionIdentity.Verified subject, String context, FormalEvaluationExactInputObjectSchema.Original original) throws Exception {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        var source = inputs.prepare(subject, context, original.request().start());
        var capture = access.execute(subject, a -> ThrowingSupplier.of(() -> {
            var root = source.requireCurrent(a);
            var bound = bounds.require(a, original.request().executionId(), source);
            var node = node(root, original.request());
            var write = schema.canonical(authorization.require(a, root, node));
            var model = models.require(a, source, bound.scope(), node);
            var result = exact.capture(a, source, bound.scope(), model, bound.context(), node);
            bound.requireUnchanged();
            if (!write.equals(schema.canonical(authorization.require(a, root, node)))) {
                throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            }
            return new AuthorizedCapture(result, write);
        }).get());
        var compiled = exact.materialize(subject, context, capture.compilation());
        return access.execute(subject, a -> ThrowingSupplier.of(() -> {
            var prepared = new Prepared(this, original, context, a.context().tenant_id(), a.actorId(), source, compiled, capture.registrationAuthorization());
            prepared.current(a);
            return prepared;
        }).get());
    }

    private record AuthorizedCapture(FormalEvaluationExactInputPreparation.Capture compilation,
            String registrationAuthorization) { }

    static final class Prepared {
        private final FormalEvaluationExactInputObjectAuthority owner;
        private final FormalEvaluationExactInputObjectSchema.Original original;
        private final String context, tenant, actor, registrationAuthorization;
        private final FormalEvaluationExecutionInputs.Prepared inputs;
        private final FormalEvaluationExactInputPreparation.PreparedExactInput compiled;
        private Prepared(FormalEvaluationExactInputObjectAuthority owner, FormalEvaluationExactInputObjectSchema.Original original,
                String context, String tenant, String actor, FormalEvaluationExecutionInputs.Prepared inputs, FormalEvaluationExactInputPreparation.PreparedExactInput compiled, String registrationAuthorization) {
            this.owner = owner;
            this.original = original;
            this.context = context;
            this.tenant = tenant;
            this.actor = actor;
            this.inputs = inputs;
            this.compiled = compiled;
            this.registrationAuthorization = registrationAuthorization;
        }

        Checked current(EnterpriseAccess.Admission a) throws Exception {
            FormalEvaluationExecutionAdmission.current(a);
            if (!tenant.equals(a.context().tenant_id()) || !actor.equals(a.actorId()) || !context.equals(a.context().context_revision())) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
            var root = inputs.requireCurrent(a);
            var bound = owner.bounds.require(a, original.request().executionId(), inputs);
            var node = node(root, original.request());
            var write = owner.authorization.require(a, root, node);
            if (!registrationAuthorization.equals(owner.schema.canonical(write))) {
                throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            }
            compiled.requireCurrent(a, root, node);
            var evidence = compiled.evidence(a, root, node);
            ((com.fasterxml.jackson.databind.node.ObjectNode) evidence).set("artifact_registration", write);
            if (!owner.json.tree(bound.context().binding()).equals(evidence.required("context_binding"))) bad();
            var ancestry = owner.dependencies.require(a, root, node, evidence);
            var bytes = new AtomicReference<byte[]>();
            compiled.consume(a, root, node, bytes::set);
            if (!compiled.inputSha256().equals(WireValues.sha256(bytes.get()))) bad();
            var stable = owner.json.object();
            stable.set("compiler", FormalEvaluationExactInputObjectDependencies.stableEvidence(evidence));
            stable.set("roots", owner.json.tree(ancestry.roots()));
            stable.set("members", owner.json.tree(ancestry.members()));
            stable.set("authorizations", ancestry.authorizations());
            stable.put("classification", ancestry.classification()).put("execution_id", original.request().executionId());
            bound.requireUnchanged();
            compiled.requireCurrent(a, root, node);
            if (!registrationAuthorization.equals(owner.schema.canonical(owner.authorization.require(a, root, node)))) {
                throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            }
            return new Checked(tenant, actor, context, root.projectId(), original, root.request().evaluationId(), node,
                    bound.context().binding(), bytes.get(), evidence, owner.schema.canonical(stable), ancestry);
        }
    }

    record Checked(String tenant, String actor, String context, String project, FormalEvaluationExactInputObjectSchema.Original original,
            String evaluation, FormalEvaluationExecutionProtocol.Node node, PermissionProtocol.Basis contextBinding, byte[] bytes,
            JsonNode evidence, String stable, FormalEvaluationExactInputObjectDependencies.Checked dependencies) {
        Checked {
            bytes = bytes.clone();
            evidence = evidence.deepCopy();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public JsonNode evidence() {
            return evidence.deepCopy();
        }
    }

    private static FormalEvaluationExecutionProtocol.Node node(FormalEvaluationExecutionAdmission.Prepared root, FormalEvaluationExactInputObjectSchema.Request request) {
        var matches = root.nodes().stream().filter(n -> n.nodeKind().equals(request.nodeKind()) && n.cell().sampleId().equals(request.sampleId())
                && n.cell().repeatIndex() == request.repeatIndex()).toList();
        if (matches.size() != 1 || !"N01".equals(request.nodeKind()) || matches.get(0).model() == null) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        return matches.get(0);
    }

    private static void bad() {
        throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    }
}
