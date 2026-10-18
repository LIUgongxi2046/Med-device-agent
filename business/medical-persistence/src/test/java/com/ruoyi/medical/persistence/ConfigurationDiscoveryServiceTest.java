package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.ConfigurationDiscoveryMapper;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real contracts/cursors; isolated candidate mapper and immutable readers, without a database or provider call. */
class ConfigurationDiscoveryServiceTest {
    private static final String TENANT = id(9000), ACTOR = id(9001), HASH = "a".repeat(64);
    private PermissionSchema json;
    private ConfigurationDiscoverySchema schema;
    private ConfigurationDiscoveryService service;
    private ConfigurationDiscoveryMapper mapper;
    private ModelConfigurationService models;
    private RuleVersionStore rules;
    private RuleVersionSchema ruleSchema;
    private ApplicationSessionIdentity.Verified subject;
    private BusinessSessionProtocol.Context context;
    private final List<ConfigurationDiscoveryService.Candidate> candidates = new ArrayList<>();
    private final Map<String,String> searchable = new HashMap<>();
    private final Set<String> denied = new HashSet<>();
    private final Set<String> simulationBindings = new HashSet<>();
    private int reads, queries;
    private String authorizationClock = "2026-10-16T00:00:00.000000Z";

    @BeforeEach @SuppressWarnings("unchecked") void prepare() throws Exception {
        json = new PermissionSchema(); schema = new ConfigurationDiscoverySchema(json);
        var expressions = new RuleExpression();
        ruleSchema = new RuleVersionSchema(json, new TemplateApplicabilitySchema(json), expressions);
        mapper = mock(ConfigurationDiscoveryMapper.class); models = mock(ModelConfigurationService.class); rules = mock(RuleVersionStore.class);
        subject = mock(ApplicationSessionIdentity.Verified.class); var binding = mock(AuthenticatedIdentityBinding.class);
        when(subject.user()).thenReturn(new BusinessSessionProtocol.User(ACTOR, "fixture", "Fixture", "USER"));
        when(subject.sessionId()).thenReturn("isolated-session"); when(subject.binding()).thenReturn(binding);
        when(binding.view()).thenReturn(new AuthenticatedIdentityBinding.View("local", ACTOR, null, null));
        context = new BusinessSessionProtocol.Context(TENANT, "1", "1", "1");
        var access = mock(EnterpriseAccess.class);
        when(access.execute(eq(subject), any())).thenAnswer(call -> {
            Function<EnterpriseAccess.Admission,Object> work = call.getArgument(1);
            return work.apply(new EnterpriseAccess.Admission(subject, "context", context,
                    new TenantWatermarks(new BigInteger(context.auth_epoch()), new BigInteger(context.governance_epoch()))));
        });
        when(mapper.authorizationClock(anyMap())).thenAnswer(call -> authorizationClock);
        when(mapper.models(anyMap())).thenAnswer(call -> page(call.getArgument(0)));
        when(mapper.rules(anyMap())).thenAnswer(call -> page(call.getArgument(0), true));
        when(models.read(any(EnterpriseAccess.Admission.class), anyString())).thenAnswer(call -> {
            String id = call.getArgument(1); reads++;
            if (denied.contains(id)) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
            var envelope = json.object(); envelope.set("data", model(id)); return envelope;
        });
        when(rules.read(any(EnterpriseAccess.Admission.class), anyString())).thenAnswer(call -> {
            String id = call.getArgument(1); reads++;
            if (simulationBindings.contains(id)) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
            if (denied.contains(id)) throw new TenantAccessException(ErrorCode.ACCESS_DENIED); return rule(id);
        });
        service = new ConfigurationDiscoveryService(access, mapper, models, rules, ruleSchema, schema, new ConfigurationDiscoveryCursor(new byte[32], java.time.Clock.systemUTC()), json);
    }

    @Test void modelsReturnOnlyCurrentAuthorizedViewsAndNoTotalsAcrossExactPages() {
        rows(5); denied.add(id(2));
        var first = models(2, null, null); assertIds(first, "config_id", 1, 3);
        assertFalse(first.has("total")); assertTrue(first.required("next_after").isTextual());
        var second = models(2, first.required("next_after").textValue(), null); assertIds(second, "config_id", 4, 5);
        assertTrue(second.required("next_after").isNull()); assertFalse(first.toString().contains(id(2)));
        verifyNoInteractions(rules); assertNull(PageHelper.getLocalPage());
    }

    @Test void rulesReuseOriginalDomainReaderAndDependencyDenialsAreFiltered() {
        rows(4); denied.add(id(1)); denied.add(id(3));
        var first = rules(1, null, null); assertRuleIds(first, 2);
        assertRuleIds(rules(1, first.required("next_after").textValue(), null), 4);
        verifyNoInteractions(models); assertFalse(first.toString().contains(id(1))); assertFalse(first.has("total"));
    }


    @Test void mixedOrdinaryAndSimulationRulesAreSeparatedByCandidateQueryBeforePaging() throws Exception {
        // Explicit mapper double models the SQL NOT EXISTS boundary; the mapper test checks actual generated SQL.
        rows(6); simulationBindings.addAll(List.of(id(1),id(3),id(6)));
        var first=rules(1,null,null);assertRuleIds(first,2);
        var second=rules(1,first.required("next_after").textValue(),null);assertRuleIds(second,4);
        var third=rules(1,second.required("next_after").textValue(),null);assertRuleIds(third,5);assertTrue(third.required("next_after").isNull());
        for(String sim:simulationBindings)verify(rules,never()).read(any(),eq(sim));
        verifyNoInteractions(models);
        when(rules.read(any(),eq(id(2)))).thenThrow(new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->rules(10,null,null)).code(),
            "The service must still propagate genuine integrity failures rather than swallow/filter them");
    }

    @Test void emptyAndEntirelyDeniedPagesNeverInventRowsOrTotals() {
        var empty = models(50, null, null); assertTrue(empty.required("items").isEmpty()); assertTrue(empty.required("next_after").isNull());
        rows(3); denied.addAll(candidates.stream().map(ConfigurationDiscoveryService.Candidate::id).toList());
        var hidden = models(50, null, null); assertTrue(hidden.required("items").isEmpty()); assertTrue(hidden.required("next_after").isNull());
        assertFalse(hidden.has("total"));
    }

    @Test void boundedScanContinuesWithoutExposingHiddenAnchor() {
        rows(502); for (int i = 1; i <= 501; i++) denied.add(id(i));
        var first = models(1, null, null); assertEquals(500, reads); assertEquals(5, queries);
        assertTrue(first.required("items").isEmpty()); assertTrue(first.required("next_after").isTextual());
        assertFalse(first.toString().contains(id(500))); assertTrue(first.required("next_after").textValue().length() <= 2048);
        var second = models(1, first.required("next_after").textValue(), null); assertIds(second, "config_id", 502);
        assertTrue(second.required("next_after").isNull()); assertEquals(502, reads);
    }

    @Test void cursorRejectsLimitFilterResourceActorSessionAndEveryContextComponent() {
        rows(3); String cursor = models(1, null, null).required("next_after").textValue(); int before = queries;
        invalidCursor(() -> models(2, cursor, null)); invalidCursor(() -> models(1, cursor, "different"));
        invalidCursor(() -> rules(1, cursor, null));
        when(subject.sessionId()).thenReturn("changed-session"); invalidCursor(() -> models(1, cursor, null));
        when(subject.sessionId()).thenReturn("isolated-session");
        when(subject.user()).thenReturn(new BusinessSessionProtocol.User(id(9999), "other", "Other", "USER")); invalidCursor(() -> models(1, cursor, null));
        when(subject.user()).thenReturn(new BusinessSessionProtocol.User(ACTOR, "fixture", "Fixture", "USER"));
        for (var changed : List.of(new BusinessSessionProtocol.Context(id(9998), "1", "1", "1"),
                new BusinessSessionProtocol.Context(TENANT, "2", "1", "1"), new BusinessSessionProtocol.Context(TENANT, "1", "2", "1"),
                new BusinessSessionProtocol.Context(TENANT, "1", "1", "2"))) {
            context = changed; invalidCursor(() -> models(1, cursor, null));
        }
        assertEquals(before, queries, "Invalid continuations fail before listing candidates");
    }

    @Test void unchangedEpochsCannotContinueAcrossGrantOrSourceRightsValidityTransitions() {
        rows(3); String token = models(1, null, null).required("next_after").textValue();
        authorizationClock = "2026-10-16T01:00:00.000000Z";
        invalidCursor(() -> models(1, token, null));
        String refreshed = models(1, null, null).required("next_after").textValue();
        authorizationClock = "2026-10-16T02:00:00.000000Z";
        invalidCursor(() -> models(1, refreshed, null));
        assertEquals("1", context.auth_epoch()); assertEquals("1", context.governance_epoch());
    }

    @Test void validityBoundaryCrossingDuringReadFailsClosed() {
        rows(2); when(mapper.authorizationClock(anyMap())).thenReturn("before", "after");
        invalidCursor(() -> models(1, null, null));
    }

    @Test void staleHeaderFailsBeforeQueryAndMalformedQueryCannotRead() {
        assertEquals(ErrorCode.CONTEXT_CHANGED, assertThrows(TenantAccessException.class,
                () -> service.models(subject, "2", new ConfigurationDiscoverySchema.Query(10, null, null))).code());
        for (int limit : List.of(0, -1, 101, Integer.MAX_VALUE)) assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> models(limit, null, null));
        assertEquals(0, queries); assertEquals(0, reads);
    }

    @Test void literalSearchAndUuidSearchKeepStablePagination() {
        rows(4); searchable.put(id(1), "Provider%_One"); searchable.put(id(3), "Provider%_Three");
        var first = models(1, null, "%_"); assertIds(first, "config_id", 1);
        assertIds(models(1, first.required("next_after").textValue(), "%_"), "config_id", 3);
        assertIds(models(50, null, id(4)), "config_id", 4); assertTrue(models(50, null, "missing").required("items").isEmpty());
        searchable.put(id(2), "medical.rule-expression.v1"); assertRuleIds(rules(50, null, "RULE-EXPRESSION"), 2);
    }

    @Test void corruptedVisibleRowsAndUnsortedCandidatesFailClosed() throws Exception {
        rows(2); when(models.read(any(EnterpriseAccess.Admission.class), eq(id(1)))).thenThrow(new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> models(2, null, null)).code());
        Collections.reverse(candidates);
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> models(2, null, null)).code());
    }

    @Test void simulationDiscoveryHasSeparateModeSchemaAndCursorBinding() throws Exception {
        rows(3); when(models.read(any(EnterpriseAccess.Admission.class),anyString(),eq(true))).thenAnswer(call->{
            String id=call.getArgument(1);var view=model(id);view.set("definition",json.tree(SimulationModelConfigurationFixtures.definition()));
            view.put("dependency_validation","DECLARED_SIMULATION_BINDING_ONLY");var envelope=json.object();envelope.set("data",view);return envelope;
        });
        var first=service.simulationModels(subject,"1",new ConfigurationDiscoverySchema.Query(1,null,null)).required("data");
        assertIds(first,"config_id",1);assertEquals("SIMULATION",first.at("/items/0/definition/execution_mode").textValue());
        var token=first.required("next_after").textValue();invalidCursor(()->models(1,token,null));
        var external=models(1,null,null).required("next_after").textValue();
        invalidCursor(()->service.simulationModels(subject,"1",new ConfigurationDiscoverySchema.Query(1,external,null)));
        var second=service.simulationModels(subject,"1",new ConfigurationDiscoverySchema.Query(1,token,null)).required("data");assertIds(second,"config_id",2);
        verify(mapper,atLeastOnce()).models(argThat(values->"SIMULATION".equals(values.get("mode"))));
        verify(mapper,atLeastOnce()).models(argThat(values->"EXTERNAL".equals(values.get("mode"))));
    }
    @Test void simulationDiscoveryRejectsExternalOrPromotedRows() throws Exception {
        rows(1);when(models.read(any(EnterpriseAccess.Admission.class),anyString(),eq(true))).thenAnswer(call->{
            var envelope=json.object();envelope.set("data",model(call.getArgument(1)));return envelope;
        });
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->service.simulationModels(subject,"1",new ConfigurationDiscoverySchema.Query(50,null,null)));
    }

    private List<ConfigurationDiscoveryService.Candidate> page(Map<String,Object> values) { return page(values, false); }
    private List<ConfigurationDiscoveryService.Candidate> page(Map<String,Object> values, boolean ruleQuery) {
        queries++; Page<ConfigurationDiscoveryService.Candidate> page = PageHelper.getLocalPage(); assertNotNull(page); assertFalse(page.isCount());
        String after = (String) values.get("after"), q = (String) values.get("q");
        page.addAll(candidates.stream().filter(row -> !ruleQuery || !simulationBindings.contains(row.id())).filter(row -> after == null || row.id().compareTo(after) > 0)
                .filter(row -> q == null || (row.id() + " " + searchable.getOrDefault(row.id(), "")).toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)))
                .limit(page.getPageSize()).toList()); PageHelper.clearPage(); return page;
    }
    private void rows(int count) { for (int i = 1; i <= count; i++) candidates.add(new ConfigurationDiscoveryService.Candidate(id(i), id(i))); }
    private JsonNode models(int limit, String after, String q) { return service.models(subject, context.context_revision(), new ConfigurationDiscoverySchema.Query(limit, after, q)).required("data"); }
    private JsonNode rules(int limit, String after, String q) { return service.rules(subject, context.context_revision(), new ConfigurationDiscoverySchema.Query(limit, after, q)).required("data"); }
    private void invalidCursor(org.junit.jupiter.api.function.Executable read) { assertEquals(ErrorCode.CURSOR_INVALID, assertThrows(TenantAccessException.class, read).code()); }
    private static void assertIds(JsonNode page, String field, int... ids) {
        assertEquals(ids.length, page.required("items").size());
        for (int i = 0; i < ids.length; i++) assertEquals(id(ids[i]), page.required("items").get(i).required(field).textValue());
    }
    private static void assertRuleIds(JsonNode page, int... ids) {
        assertEquals(ids.length, page.required("items").size());
        for (int i = 0; i < ids.length; i++) assertEquals(id(ids[i]), page.required("items").get(i).required("rule").required("rule_version").textValue());
    }
    private ObjectNode model(String id) throws Exception {
        var definition = new ModelConfigurationProtocol.Definition("fixture-provider", "https://provider.invalid/v1", "fixture-region", "fixture-model", "fixture-v1",
                "secret://must-not-leak/reference", id(9501), "must-not-leak-secret-version",
                new ModelConfigurationProtocol.Limits("128000", "8000", "2", "10", "100000", 60, false, List.of("none"), 0),
                new PermissionProtocol.Basis(id(9502), HASH), "CNY",
                new ModelConfigurationProtocol.DataPolicy(List.of("INTERNAL"), List.of("fixture-region"), false, "0", new PermissionProtocol.Basis(id(9503), HASH)),
                "urn:medical:model-parameters:1.0.0", HASH, "1.0.0");
        var view = json.object(); view.put("schema_version", "1.0.0"); view.put("tenant_id", TENANT); view.putNull("project_id");
        view.put("config_id", id); view.put("model_config_revision", id); view.put("current_model_revision", id); view.putNull("based_on_revision");
        view.set("definition", new ModelConfigurationSchema(json).publicDefinition(definition)); view.put("content_sha256", HASH);
        view.put("status", "DRAFT"); view.put("revision", "1"); view.put("created_by", ACTOR); view.put("created_at", "2026-10-16T00:00:00Z");
        view.put("dependency_validation", "REGISTRY_REFERENCE_ONLY"); view.putArray("allowed_actions").add("configuration.read"); return view;
    }
    private RuleVersionProtocol.Stored rule(String id) throws Exception {
        var content = new TemplateApplicabilityProtocol.RuleContent("1.0.0", RuleExpression.ENGINE,
                new TemplateApplicabilityProtocol.Expression("1.0.0", RuleExpression.ENGINE, "true", List.of(), List.of()), List.of(new PermissionProtocol.Basis(id(9511), HASH)));
        var valid = ruleSchema.version(new RuleVersionProtocol.VersionInput("1.0.0", null, RuleVersionSchema.CONTENT_REF, content, "Synthetic rule fixture"));
        return new RuleVersionProtocol.Stored(TENANT, id, "DRAFT", "1", content, valid.content_sha256(), null,
                "Synthetic rule fixture", id(9510), "1", ACTOR, "2026-10-16T00:00:00Z", valid.dependency_refs());
    }
    static String id(int number) { return "00000000-0000-4000-8000-%012d".formatted(number); }
}
