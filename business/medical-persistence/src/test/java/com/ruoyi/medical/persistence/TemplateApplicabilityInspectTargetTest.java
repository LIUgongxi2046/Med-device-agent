package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.TemplateMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual staged TemplateApplicabilityService methods, published schemas, complete reference
 * directory, and RuleExpression run here. ProjectService, TemplateVersionStore, TemplateMapper,
 * resource/dependency authorization and their snapshots are explicit upstream doubles. These
 * fixtures are synthetic approved rows, not live database, authority or provider evidence. */
class TemplateApplicabilityInspectTargetTest {
    private static final String PURPOSE = "EVALUATION_RESULTS";
    private static final String DOCUMENT = "REGISTRATION_DIRECTORY";
    private static final String DIRECTORY = "NON_IVD_FIRST_REGISTRATION_2021_121";
    private static final String TENANT = id(1), ACTOR = id(2), PROJECT = id(3), PROFILE = id(4), TEMPLATE = id(5);

    @Test void realProducerProjectsLegacyWireAndValidatesAdditiveInternalProof() throws Exception {
        var f = new Fixture(false);
        var view = f.inspect().view();
        var json = new PermissionSchema();
        var schema = new TemplateApplicabilitySchema(json);
        var legacy = json.tree(TemplateApplicabilityService.legacyView(view));
        assertDoesNotThrow(() -> schema.verify("TemplateView", legacy));
        assertThrows(SchemaRejection.class, () -> schema.verify("TemplateView", json.tree(view)));
        assertDoesNotThrow(() -> schema.verifyApplication("TemplateView", json.tree(view)));
        assertThrows(SchemaRejection.class, () -> schema.verifyApplication("TemplateView", legacy));
    }

    @Test void applicableTargetAndFixedAuthorityBindingsRemainIdenticalForBothMethods() throws Exception {
        var f = new Fixture(false);
        var inspected = f.inspect();
        var required = f.require();
        assertEquals(required, inspected);
        assertEquals("APPLICABLE", inspected.view().applicability());
        assertSame(f.admission, inspected.admission());
        assertSame(f.profile, inspected.profile());
        assertSame(f.grant, inspected.grant());
        assertEquals(PURPOSE, inspected.purpose());
        assertEquals(PROFILE, inspected.profile().project().profileRevision());
        assertEquals(TEMPLATE, inspected.view().template().template_version());
        assertEquals(46, inspected.view().conditions().size(), "Complete 44-node official directory is evaluated");
        assertTrue(inspected.view().missing_requirements().isEmpty());
        assertEquals(DigestUtils.sha256Hex(inspected.dependencyAuthorizations()), inspected.view().dependency_authorizations_sha256());
        verify(f.projects, times(2)).fixedProfile(f.admission, PROJECT, PROFILE, PURPOSE);
        verify(f.authority, times(4)).require(f.admission, f.scope, "template.read", PURPOSE);
        verify(f.dependencies, times(2)).requireProfile(f.admission, f.profile, PURPOSE);
        verify(f.dependencies, times(2)).require(eq(f.admission), any(TemplateProtocol.Validated.class), eq(PURPOSE));
    }

    @Test void authorizedProfileIncompatibilityIsInspectableButLegacyTargetDeniesBeforeEvaluation() throws Exception {
        var f = new Fixture(true);
        f.attributes(f.attributesWith("ivd", true));
        var inspected = f.inspect();
        assertEquals("NOT_APPLICABLE", inspected.view().applicability());
        assertEquals("NOT_APPLICABLE", inspected.view().conditions().get(0).status());
        assertEquals("APPLICABLE", inspected.view().conditions().get(1).status());
        assertTrue(inspected.view().missing_requirements().isEmpty());
        verify(f.engine).evaluate(any(RuleExpression.Compiled.class), eq(f.profile.attributes()));
        verify(f.authority, times(2)).require(f.admission, f.scope, "template.read", PURPOSE);

        f.clearCalls();
        assertDenied(ErrorCode.ACCESS_DENIED, f::require);
        verify(f.authority).require(f.admission, f.scope, "template.read", PURPOSE);
        verify(f.store).applicationTarget(f.admission, TEMPLATE, PURPOSE);
        verify(f.dependencies, never()).require(any(), any(TemplateProtocol.Validated.class), anyString());
        verify(f.dependencies, never()).snapshot(any(), anyList(), any());
        verifyNoInteractions(f.definitions, f.engine, f.mapper);
    }

    @Test void authorizedFalseRuleIsInspectableWhileLegacyTargetStillDenies() throws Exception {
        var f = new Fixture(true);
        f.attributes(f.attributesWith("rule_enabled", false));
        var inspected = f.inspect();
        assertEquals("NOT_APPLICABLE", inspected.view().applicability());
        assertEquals("APPLICABLE", inspected.view().conditions().get(0).status());
        assertEquals("NOT_APPLICABLE", inspected.view().conditions().get(1).status());
        assertEquals(f.ruleBasis, inspected.view().conditions().get(1).rule_revision());
        verify(f.engine).compile(any(TemplateApplicabilityProtocol.Expression.class));
        verify(f.engine).evaluate(any(RuleExpression.Compiled.class), eq(f.profile.attributes()));
        f.clearCalls();
        assertDenied(ErrorCode.ACCESS_DENIED, f::require);
        verify(f.engine).evaluate(any(RuleExpression.Compiled.class), eq(f.profile.attributes()));
        verify(f.authority).require(f.admission, f.scope, "template.read", PURPOSE);
    }

    @Test void unknownProfileInputsRemainUnknownAndAllowedThroughBothMethods() throws Exception {
        var f = new Fixture(false);
        f.attributes(f.profile.attributes().stream().filter(a -> !a.key().equals("directory_profile")).toList());
        var inspected = f.inspect();
        assertEquals(inspected, f.require());
        assertEquals("UNKNOWN", inspected.view().applicability());
        assertEquals(List.of("directory_profile"), inspected.view().conditions().get(0).unknown_input_keys());
        assertEquals(45, inspected.view().missing_requirements().size());
        assertTrue(inspected.view().missing_requirements().stream().allMatch(r -> r.reasonCode().equals("APPLICABILITY_UNKNOWN")));
    }

    @Test void actualRuleExpressionUnknownInputsRemainUnknownAndAllowedThroughBothMethods() throws Exception {
        var f = new Fixture(true);
        f.attributes(f.profile.attributes().stream().filter(a -> !a.key().equals("rule_enabled")).toList());
        var inspected = f.inspect();
        assertEquals(inspected, f.require());
        assertEquals("UNKNOWN", inspected.view().applicability());
        assertEquals("APPLICABLE", inspected.view().conditions().get(0).status());
        assertEquals("UNKNOWN", inspected.view().conditions().get(1).status());
        assertEquals(List.of("rule_enabled"), inspected.view().conditions().get(1).unknown_input_keys());
        assertEquals(45, inspected.view().missing_requirements().size());
        verify(f.engine, times(2)).evaluate(any(RuleExpression.Compiled.class), eq(f.profile.attributes()));
    }

    @Test void deniedTemplateNeverRevealsStoredMetadataOrAnInapplicableResult() throws Exception {
        for (boolean inspect : List.of(false, true)) {
            var f = new Fixture(true);
            f.attributes(f.attributesWith("ivd", true));
            var rejection = new TenantAccessException(ErrorCode.ACCESS_DENIED);
            when(f.authority.require(f.admission, f.scope, "template.read", PURPOSE)).thenThrow(rejection);
            assertSame(rejection, assertThrows(TenantAccessException.class, () -> f.call(inspect)));
            assertEquals("ACCESS_DENIED", rejection.getMessage());
            verifyNoInteractions(f.store, f.definitions, f.mapper, f.engine);
            verify(f.dependencies, never()).require(any(), any(TemplateProtocol.Validated.class), anyString());
            verify(f.dependencies, never()).snapshot(any(), anyList(), any());
        }
    }

    @Test void unreadableOrUnapprovedTemplateIsNeverConvertedToNotApplicable() throws Exception {
        for (var code : List.of(ErrorCode.ACCESS_DENIED, ErrorCode.SOURCE_NOT_READY, ErrorCode.CONTENT_MISMATCH)) {
            for (boolean inspect : List.of(false, true)) {
                var f = new Fixture(true);
                f.attributes(f.attributesWith("ivd", true));
                var rejection = new TenantAccessException(code);
                when(f.store.applicationTarget(f.admission, TEMPLATE, PURPOSE)).thenThrow(rejection);
                assertSame(rejection, assertThrows(TenantAccessException.class, () -> f.call(inspect)));
                assertEquals(code.name(), rejection.getMessage());
                verifyNoInteractions(f.definitions, f.mapper, f.engine);
                verify(f.dependencies, never()).require(any(), any(TemplateProtocol.Validated.class), anyString());
                verify(f.dependencies, never()).snapshot(any(), anyList(), any());
                verify(f.authority).require(f.admission, f.scope, "template.read", PURPOSE);
            }
        }
    }

    @Test void deniedProfileDependenciesStopBeforeTemplateAuthorizationOrMetadata() throws Exception {
        for (boolean inspect : List.of(false, true)) {
            var f = new Fixture(false);
            when(f.dependencies.requireProfile(f.admission, f.profile, PURPOSE)).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
            assertDenied(ErrorCode.ACCESS_DENIED, () -> f.call(inspect));
            verifyNoInteractions(f.authority, f.store, f.definitions, f.mapper, f.engine);
        }
    }

    @Test void incompatibleInspectionStillRequiresReadableTemplateDependencies() throws Exception {
        var f = new Fixture(true);
        f.attributes(f.attributesWith("ivd", true));
        var rejection = new TenantAccessException(ErrorCode.ACCESS_DENIED);
        when(f.dependencies.require(eq(f.admission), any(TemplateProtocol.Validated.class), eq(PURPOSE))).thenThrow(rejection);
        assertSame(rejection, assertThrows(TenantAccessException.class, f::inspect));
        verify(f.definitions).version(any(byte[].class));
        verifyNoInteractions(f.mapper, f.engine);
        verify(f.dependencies, never()).snapshot(any(), anyList(), any());
        verify(f.authority).require(f.admission, f.scope, "template.read", PURPOSE);
    }

    @Test void fixedProfileMismatchIsPropagatedUnchangedBeforeTemplateReads() throws Exception {
        for (boolean inspect : List.of(false, true)) {
            var f = new Fixture(false);
            String staleProfile = id(99);
            var rejection = new TenantAccessException(ErrorCode.RESULT_VERSION_MISMATCH);
            when(f.projects.fixedProfile(f.admission, PROJECT, staleProfile, PURPOSE)).thenThrow(rejection);
            assertSame(rejection, assertThrows(TenantAccessException.class,
                    () -> f.call(inspect, staleProfile, DOCUMENT)));
            verify(f.projects).fixedProfile(f.admission, PROJECT, staleProfile, PURPOSE);
            verifyNoInteractions(f.dependencies, f.authority, f.store, f.definitions, f.mapper, f.engine);
        }
    }

    @Test void fixedDocumentTypeMismatchStillFailsBeforeCompatibilityEvaluation() throws Exception {
        for (boolean inspect : List.of(false, true)) {
            var f = new Fixture(true);
            f.attributes(f.attributesWith("ivd", true));
            assertDenied(ErrorCode.RESULT_VERSION_MISMATCH, () -> f.call(inspect, PROFILE, "REGISTRATION_DOSSIER"));
            verify(f.store).applicationTarget(f.admission, TEMPLATE, PURPOSE);
            verifyNoInteractions(f.definitions, f.mapper, f.engine);
            verify(f.dependencies, never()).require(any(), any(TemplateProtocol.Validated.class), anyString());
            verify(f.dependencies, never()).snapshot(any(), anyList(), any());
        }
    }

    @Test void inspectionDoesNotReturnMetadataWhenFinalReadAuthorizationChangesOrIsRevoked() throws Exception {
        for (boolean revoked : List.of(false, true)) {
            var f = new Fixture(true);
            f.attributes(f.attributesWith("ivd", true));
            if (revoked) when(f.authority.require(f.admission, f.scope, "template.read", PURPOSE))
                    .thenReturn(f.grant).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
            else when(f.authority.require(f.admission, f.scope, "template.read", PURPOSE))
                    .thenReturn(f.grant, f.grant(BigInteger.TWO));
            assertDenied(revoked ? ErrorCode.ACCESS_DENIED : ErrorCode.SCOPE_STALE, f::inspect);
            verify(f.engine).evaluate(any(RuleExpression.Compiled.class), eq(f.profile.attributes()));
            verify(f.authority, times(2)).require(f.admission, f.scope, "template.read", PURPOSE);
        }
    }

    @Test void malformedFixedInputsStillUsePublishedQueryValidationBeforeUpstreamReads() throws Exception {
        for (boolean inspect : List.of(false, true)) {
            var f = new Fixture(false);
            assertThrows(SchemaRejection.class, () -> f.call(inspect, "not-a-profile-version", DOCUMENT));
            assertThrows(SchemaRejection.class, () -> f.call(inspect, PROFILE, "NOT_A_DOCUMENT_TYPE"));
            verifyNoInteractions(f.projects, f.dependencies, f.authority, f.store, f.definitions, f.mapper, f.engine);
        }
    }

    private static void assertDenied(ErrorCode code, Checked call) {
        var failure = assertThrows(TenantAccessException.class, call::get);
        assertEquals(code, failure.code());
        assertEquals(code.name(), failure.getMessage(), "Failure contains a code, not template metadata");
    }
    private interface Checked { TemplateApplicabilityService.Target get() throws Exception; }
    private static String id(int value) { return String.format("00000000-0000-4000-8000-%012d", value); }
    private static PermissionProtocol.Basis basis(int value) { return new PermissionProtocol.Basis(id(value), "a".repeat(64)); }

    private static final class Fixture {
        final PermissionSchema json = new PermissionSchema();
        final RegistrationDirectory directory = new RegistrationDirectory();
        final TemplateDefinitionSchema definitions = spy(new TemplateDefinitionSchema(json, directory));
        final TemplateApplicabilitySchema schema = new TemplateApplicabilitySchema(json);
        final RuleExpression engine = spy(new RuleExpression());
        final ProjectService projects = mock(ProjectService.class);
        final TemplateMapper mapper = mock(TemplateMapper.class);
        final TemplateVersionStore store = mock(TemplateVersionStore.class);
        final TemplateDependencyAuthority dependencies = mock(TemplateDependencyAuthority.class);
        final ResourceReadAuthority authority = mock(ResourceReadAuthority.class);
        final EnterpriseAccess.Admission admission = mock(EnterpriseAccess.Admission.class, RETURNS_DEEP_STUBS);
        final PermissionProtocol.Scope scope = new PermissionProtocol.Scope(TENANT, null, "TEMPLATE", TEMPLATE);
        final PermissionProtocol.GrantRow grant = grant(BigInteger.ONE);
        final TemplateApplicabilityService service;
        final TemplateProtocol.Stored template;
        final PermissionProtocol.Basis ruleBasis;
        ProjectProtocol.VerifiedProfile profile;

        Fixture(boolean withRule) throws Exception {
            when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(TENANT, "1", "1", "1"));
            when(admission.subject().user().user_id()).thenReturn(ACTOR);
            when(authority.require(admission, scope, "template.read", PURPOSE)).thenReturn(grant);
            var always = new TemplateProtocol.Condition("ALWAYS", null, null, List.of());
            var applicability = always;
            List<DependencySourceAuthority.VerifiedDependencies> templateProofs = List.of();
            if (withRule) {
                var expression = new TemplateApplicabilityProtocol.Expression("1.0.0", RuleExpression.ENGINE,
                        "#attributes['rule_enabled'] == true", List.of("rule_enabled"), List.of());
                var content = new TemplateApplicabilityProtocol.RuleContent("1.0.0", RuleExpression.ENGINE, expression, List.of(basis(10)));
                String hash = DigestUtils.sha256Hex(json.canonicalObject(content));
                ruleBasis = new PermissionProtocol.Basis(id(20), hash);
                applicability = new TemplateProtocol.Condition("RULE", ruleBasis, RuleExpression.ENGINE, List.of("rule_enabled"));
                var row = new TemplateApplicabilityProtocol.RuleRow(TENANT, id(20), RuleExpression.ENGINE,
                        json.canonicalObject(expression), json.canonicalObject(content.basis_manifest()), "APPROVED", hash, hash,
                        "1.0.0", schema.ruleManifestSha256(), ACTOR, LocalDateTime.of(2026, 10, 18, 12, 0), "RESTRICTED");
                when(mapper.ruleDefinition(anyMap())).thenReturn(List.of(row));
                when(mapper.ruleParents(anyMap())).thenReturn(List.of(id(10)));
                templateProofs = List.of(new DependencySourceAuthority.VerifiedDependencies(admission, id(20), Map.of(),
                        Map.of(id(20), version(ruleBasis, VersionResourceProtocol.Domain.RULE_VERSION),
                                id(10), version(basis(10), VersionResourceProtocol.Domain.SOURCE_VERSION))));
            } else ruleBasis = null;
            var nodeIds = new LinkedHashMap<String, String>();
            for (var entry : directory.catalogue().nodes()) nodeIds.put(entry.officialNumber(), id(100 + nodeIds.size()));
            var nodes = new ArrayList<TemplateProtocol.Node>();
            for (var entry : directory.catalogue().nodes()) nodes.add(new TemplateProtocol.Node(nodeIds.get(entry.officialNumber()),
                    entry.parentNumber() == null ? null : nodeIds.get(entry.parentNumber()), entry.officialNumber(), entry.title(),
                    entry.ordinal(), always, true, "DIRECTORY", new TemplateProtocol.EvidenceRule("NARRATIVE", List.of(),
                    List.of(), null, List.of(), true, true), List.of(basis(10)), "OFFICIAL", List.of()));
            var content = new TemplateProtocol.Content("1.0.0", DOCUMENT, "FIRST_REGISTRATION", false, DIRECTORY,
                    nodes, List.of(), List.of(), List.of(), List.of(),
                    new TemplateProtocol.ConditionSchema(withRule ? List.of("rule_enabled") : List.of(), applicability, "UNKNOWN"),
                    List.of(basis(10)), new TemplateProtocol.FormatProfile(basis(11), List.of("DOCX"), basis(12), List.of(basis(13)),
                    List.of(), "DECLARED_ORDINAL"));
            var input = new TemplateProtocol.VersionInput("1.0.0", null, "urn:medical:templates:1.0.0#/$defs/Content", content,
                    "Synthetic approved fixture; upstream database and authorization are explicit doubles");
            var validated = definitions.version(json.canonicalObject(input).getBytes(StandardCharsets.UTF_8));
            template = new TemplateProtocol.Stored(TENANT, TEMPLATE, "APPROVED", "1", content, validated.content_sha256(), null,
                    input.reason(), id(6), "1", ACTOR, "2026-10-18T12:00:00Z", validated.dependency_refs());
            when(store.applicationTarget(admission, TEMPLATE, PURPOSE)).thenReturn(template);
            when(dependencies.require(eq(admission), any(TemplateProtocol.Validated.class), eq(PURPOSE))).thenReturn(templateProofs);
            when(dependencies.snapshot(eq(admission), anyList(), eq(json)))
                    .thenAnswer(invocation -> json.object().put("synthetic_authority_snapshot", true));
            attributes(List.of(attribute("application_path", "FIRST_REGISTRATION"), attribute("ivd", false),
                    attribute("directory_profile", DIRECTORY), attribute("rule_enabled", true)));
            service = new TemplateApplicabilityService(mock(EnterpriseAccess.class), projects, mapper, store, definitions,
                    dependencies, authority, engine, schema, new TemplateDependencyPolicy("test", "local", 1000, 2000, List.of()),
                    new IdentityDirectoryPolicy("RESTRICTED"), json);
            clearCalls();
        }
        ProjectProtocol.Attribute attribute(String key, Object value) {
            return new ProjectProtocol.Attribute(key, "KNOWN", json.tree(value), List.of(), "Synthetic test attribute");
        }
        List<ProjectProtocol.Attribute> attributesWith(String key, Object value) {
            return profile.attributes().stream().map(a -> a.key().equals(key) ? attribute(key, value) : a).toList();
        }
        void attributes(List<ProjectProtocol.Attribute> values) throws Exception {
            var row = new ProjectProtocol.Row(TENANT, PROJECT, "ACTIVE", BigInteger.ONE, "Synthetic project", ACTOR, PROFILE,
                    id(10), basis(10).content_sha256(), "Synthetic fixture", id(6), BigInteger.ONE, "1.0.0", "b".repeat(64), ACTOR,
                    LocalDateTime.of(2026, 10, 18, 12, 0), LocalDateTime.of(2026, 10, 18, 12, 0), BigInteger.ONE);
            profile = new ProjectProtocol.VerifiedProfile(row, DigestUtils.sha256Hex(json.canonicalObject(values)), values, List.of(basis(10)));
            when(projects.fixedProfile(admission, PROJECT, PROFILE, PURPOSE)).thenReturn(profile);
            when(dependencies.requireProfile(admission, profile, PURPOSE)).thenReturn(List.of());
        }
        PermissionProtocol.GrantRow grant(BigInteger revision) {
            return new PermissionProtocol.GrantRow(id(6), ACTOR, null, "TEMPLATE", TEMPLATE, "template.read", "{}", "ACTIVE",
                    LocalDateTime.of(2026, 1, 1, 0, 0), null, id(10), "1.0.0", "b".repeat(64), revision);
        }
        VersionResourceAuthority.VerifiedVersion version(PermissionProtocol.Basis basis, VersionResourceProtocol.Domain domain) {
            return new VersionResourceAuthority.VerifiedVersion(new ArtifactRegistryProtocol.VersionRow(basis.version_id(), null,
                    domain == VersionResourceProtocol.Domain.RULE_VERSION ? "RULE" : "SOURCE", basis.content_sha256()), domain,
                    null, null, null, null);
        }
        TemplateApplicabilityService.Target inspect() throws Exception { return call(true); }
        TemplateApplicabilityService.Target require() throws Exception { return call(false); }
        TemplateApplicabilityService.Target call(boolean inspect) throws Exception { return call(inspect, PROFILE, DOCUMENT); }
        TemplateApplicabilityService.Target call(boolean inspect, String profileRevision, String documentType) throws Exception {
            return inspect ? service.inspectTarget(admission, PROJECT, profileRevision, documentType, TEMPLATE, PURPOSE)
                    : service.requireTarget(admission, PROJECT, profileRevision, documentType, TEMPLATE, PURPOSE);
        }
        void clearCalls() { clearInvocations(projects, mapper, store, dependencies, authority, definitions, engine); }
    }
}
