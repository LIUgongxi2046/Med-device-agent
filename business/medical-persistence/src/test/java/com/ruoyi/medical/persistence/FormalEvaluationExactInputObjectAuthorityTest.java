package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual 089 authority, registration authorizer, ledger, transport and registration methods.
 * The private079/088 originals/compiler and database/S3 are explicitly mocked boundaries. */
class FormalEvaluationExactInputObjectAuthorityTest extends ExactInputAuthorizationFixture {
    @TempDir Path directory;
    final ApplicationSessionIdentity.Verified subject = mock(ApplicationSessionIdentity.Verified.class);
    final EnterpriseAccess access = mock(EnterpriseAccess.class);
    final FormalEvaluationExecutionInputs inputs = mock(FormalEvaluationExecutionInputs.class);
    final FormalEvaluationExecutionInputs.Prepared source = mock(FormalEvaluationExecutionInputs.Prepared.class);
    final FormalEvaluationBoundExecutions bounds = mock(FormalEvaluationBoundExecutions.class);
    final FormalEvaluationBoundExecutions.Bound bound = mock(FormalEvaluationBoundExecutions.Bound.class);
    final FormalEvaluationContextStore.Approved context = mock(FormalEvaluationContextStore.Approved.class);
    final FormalEvaluationExecutionScopes.Prepared sourceScope = mock(FormalEvaluationExecutionScopes.Prepared.class);
    final FormalEvaluationModelPreparation models = mock(FormalEvaluationModelPreparation.class);
    final FormalEvaluationModelPreparation.Prepared model = mock(FormalEvaluationModelPreparation.Prepared.class);
    final FormalEvaluationExactInputPreparation exact = mock(FormalEvaluationExactInputPreparation.class);
    final FormalEvaluationExactInputPreparation.Capture capture = mock(FormalEvaluationExactInputPreparation.Capture.class);
    final FormalEvaluationExactInputPreparation.PreparedExactInput compiled = mock(FormalEvaluationExactInputPreparation.PreparedExactInput.class);
    final FormalEvaluationExactInputObjectDependencies dependencies = mock(FormalEvaluationExactInputObjectDependencies.class);
    final FormalEvaluationExactInputObjectAuthority authority = new FormalEvaluationExactInputObjectAuthority(
            access, inputs, bounds, models, exact, dependencies, authorization, schema, json);
    final S3ObjectWriter writer = mock(S3ObjectWriter.class);

    FormalEvaluationExactInputObjectAuthorityTest() throws Exception {
        when(access.execute(eq(subject), any())).thenAnswer(invocation -> tx(() -> {
            Function<EnterpriseAccess.Admission, Object> work = invocation.getArgument(1);
            return work.apply(admission);
        }));
        when(inputs.prepare(subject, "1", checked.original().request().start())).thenReturn(source);
        when(source.requireCurrent(admission)).thenReturn(root);
        when(bounds.require(admission, execution, source)).thenReturn(bound);
        when(bound.context()).thenReturn(context);
        when(bound.scope()).thenReturn(sourceScope);
        when(context.binding()).thenReturn(checked.contextBinding());
        when(models.require(admission, source, sourceScope, checked.node())).thenReturn(model);
        when(exact.capture(admission, source, sourceScope, model, context, checked.node())).thenReturn(capture);
        when(exact.materialize(subject, "1", capture)).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return compiled;
        });
        when(compiled.evidence(admission, root, checked.node())).thenAnswer(invocation -> {
            var evidence = (ObjectNode) checked.evidence();
            evidence.set("context_binding", json.tree(checked.contextBinding()));
            return evidence;
        });
        when(compiled.inputSha256()).thenReturn(WireValues.sha256(checked.bytes()));
        doAnswer(invocation -> {
            Consumer<byte[]> consumer = invocation.getArgument(3);
            consumer.accept(checked.bytes());
            return null;
        }).when(compiled).consume(eq(admission), eq(root), eq(checked.node()), any());
        when(dependencies.require(eq(admission), eq(root), eq(checked.node()), any())).thenReturn(checked.dependencies());
        when(writer.writeVerified(any(), any(), any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            S3ObjectWriter.WriteIntent intent = invocation.getArgument(0);
            assertArrayEquals(checked.bytes(), Files.readAllBytes(invocation.getArgument(1)));
            return object(intent);
        });
        when(writer.recoverVerified(any(), any())).thenAnswer(invocation -> object(invocation.getArgument(0)));
    }

    FormalEvaluationExactInputObjectAuthority.Prepared prepareAuthority() throws Exception {
        return authority.prepare(subject, "1", checked.original());
    }

    RuntimeObjectTransport transport() throws Exception {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        return new RuntimeObjectTransport(() -> writer, directory, 2 * 1024 * 1024);
    }

    InterfaceProtocol.ObjectRef object(S3ObjectWriter.WriteIntent intent) {
        return new InterfaceProtocol.ObjectRef(intent.objectKey(), "actual-object-v1",
                intent.sha256(), intent.sizeBytes(), intent.mediaType());
    }

    @Test void missingRegistrationGrantBlocksBeforeCompilerCaptureOrAny089Writes() {
        grants.clear();
        assertEquals(ErrorCode.ACCESS_DENIED,
                assertThrows(TenantAccessException.class, this::prepareAuthority).code());
        verifyNoInteractions(exact, writer);
        assertTrue(tables.isEmpty());
    }

    @Test void actualPrivatePreparedProofRetainsFullGrantInOriginalAndStableWitnesses() throws Exception {
        var prepared = prepareAuthority();
        var current = tx(() -> prepared.current(admission));
        var registration = current.evidence().required("artifact_registration");
        assertEquals(AgentPublicationWitness.tree(grants.get(0)), registration.required("grant"));
        assertEquals(schema.canonical(registration), schema.canonical(
                schema.read(current.stable()).at("/compiler/artifact_registration")));
        tx(() -> ledger.prepare(admission, key, prepared));
        var intent = schema.read((String) only("formal_evaluation_exact_input_intent").get("canonical_intent"));
        assertEquals(schema.canonical(registration), schema.canonical(intent.at("/original_evidence/artifact_registration")));
        assertEquals(schema.canonical(registration), schema.canonical(intent.at("/stable_provenance/compiler/artifact_registration")));
    }

    @Test void revokedGrantDuringPhysicalOriginalMaterializationCannotProducePreparedProof() throws Exception {
        when(exact.materialize(subject, "1", capture)).thenAnswer(invocation -> {
            grants.clear();
            return compiled;
        });
        assertEquals(ErrorCode.ACCESS_DENIED,
                assertThrows(TenantAccessException.class, this::prepareAuthority).code());
        assertTrue(tables.isEmpty());
    }

    @Test void freshGrantRevisionCannotReplaceOriginalPreparedAuthority() throws Exception {
        var prepared = prepareAuthority();
        grants.set(0, grant("EVALUATION", evaluation, project, "artifact.register", "EVALUATION_RESULTS", actor, 2));
        assertEquals(ErrorCode.SCOPE_STALE,
                assertThrows(TenantAccessException.class, () -> tx(() -> prepared.current(admission))).code());
    }

    @Test void changedRolePermissionSetCannotReplaceOriginalPreparedAuthority() throws Exception {
        var prepared = prepareAuthority();
        ((ObjectNode) roleScope).withArray("permissions").add(scope("EVALUATION", evaluation, project,
                "evaluation.run", "EVALUATION_RESULTS").required("permissions").get(0));
        assertEquals(ErrorCode.SCOPE_STALE,
                assertThrows(TenantAccessException.class, () -> tx(() -> prepared.current(admission))).code());
    }

    @Test void revocationBeforePhysicalWriteGuardPreventsPutWithoutErasingAttempt() throws Exception {
        var prepared = prepareAuthority();
        var attempt = tx(() -> ledger.prepare(admission, key, prepared));
        grants.clear();
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> transport().transferVerified(attempt,
                        () -> org.springframework.util.function.ThrowingSupplier.of(
                        () -> tx(() -> { ledger.requireTransfer(admission, attempt, prepared); return null; })).get())).code());
        verifyNoInteractions(writer);
        assertEquals(1, rows("formal_evaluation_exact_input_attempt").size());
        assertTrue(rows("artifact").isEmpty());
    }

    @Test void revocationAfterPhysicalReadbackFailsFreshFinalRegistration() throws Exception {
        var prepared = prepareAuthority();
        var attempt = tx(() -> ledger.prepare(admission, key, prepared));
        var physical = transport().transferVerified(attempt,
                () -> org.springframework.util.function.ThrowingSupplier.of(
                        () -> tx(() -> { ledger.requireTransfer(admission, attempt, prepared); return null; })).get());
        grants.clear();
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> tx(() -> registrations.register(admission, prepared, attempt, physical))).code());
        assertTrue(rows("artifact").isEmpty());
        assertTrue(rows("formal_evaluation_exact_input_registered").isEmpty());
        verify(writer, times(1)).writeVerified(any(), any(), any());
    }

    @Test void recoveredPreparationWithDifferentGrantCannotRewriteOriginalIntent() throws Exception {
        var prepared = prepareAuthority();
        tx(() -> ledger.prepare(admission, key, prepared));
        String originalIntent = (String) only("formal_evaluation_exact_input_intent").get("canonical_intent");
        grants.set(0, grant("EVALUATION", evaluation, project, "artifact.register", "EVALUATION_RESULTS", actor, 2));
        var fresh = prepareAuthority();
        assertThrows(TenantAccessException.class, () -> tx(() -> ledger.recover(admission, key, fresh)));
        assertEquals(originalIntent, only("formal_evaluation_exact_input_intent").get("canonical_intent"));
        verifyNoInteractions(writer);
    }

    @Test void realCoordinatorRechecksRegistrationGrantAndStillUsesOnlyOnePhysicalPut() throws Exception {
        var service = new FormalEvaluationExactInputObjectService(access, schema, authority, ledger,
                transport(), registrations, json);
        byte[] body = checked.original().body().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var receipt = service.register(subject, "1", key, body);
        assertEquals(receipt, service.recover(subject, "1", key, body));
        verify(writer, times(1)).writeVerified(any(), any(), any());
        verify(permissionRows, atLeast(12)).resourceGrants(actor, "artifact.register");
        grants.clear();
        assertEquals(ErrorCode.ACCESS_DENIED,
                assertThrows(TenantAccessException.class, () -> service.recover(subject, "1", key, body)).code());
        assertEquals(1, rows("artifact").size());
    }
}
