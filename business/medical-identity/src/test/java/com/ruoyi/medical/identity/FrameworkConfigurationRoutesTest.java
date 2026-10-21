package com.ruoyi.medical.identity;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.http.server.PathContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Offline route registration checks, not a replacement for actual HTTP/browser refresh tests. */
class FrameworkConfigurationRoutesTest {
    @Test void publicationAndContextPagesDoNotBecomeApiCatchAllRoutes() {
        var required = List.of("/agents/publication", "/agents/context-bindings");
        assertTrue(FrameworkFrontendConfiguration.ROUTES.containsAll(required));
        var parser = new PathPatternParser();
        var entries = required.stream().map(parser::parse).toList();
        for (var path : required)
            assertTrue(entries.stream().anyMatch(entry -> entry.matches(PathContainer.parsePath(path))), path);
        for (var path : List.of("/agents/publication/unknown", "/agents/context-bindings/unknown",
                "/api/formal-agent-publications", "/api/formal-evaluation-context-bindings"))
            assertFalse(entries.stream().anyMatch(entry -> entry.matches(PathContainer.parsePath(path))), path);
    }

    @Test void fieldTargetPreparationPagesHaveExactEntriesWithoutApiOrNestedFallback() {
        var required = List.of("/projects/{projectId}/tasks/{taskId}/field-targets",
                "/projects/{projectId}/tasks/{taskId}/field-target-capabilities");
        assertTrue(FrameworkFrontendConfiguration.ROUTES.containsAll(required));
        var parser = new PathPatternParser();
        var entries = required.stream().map(parser::parse).toList();
        for (var path : List.of("/projects/p/tasks/t/field-targets", "/projects/p/tasks/t/field-target-capabilities"))
            assertTrue(entries.stream().anyMatch(entry -> entry.matches(PathContainer.parsePath(path))), path);
        for (var path : List.of("/api/projects/p/tasks/t/field-targets", "/api/projects/p/tasks/t/field-target-capabilities",
                "/projects/p/tasks/t/field-targets/unknown", "/projects/p/tasks/t/field-target-capabilities/unknown"))
            assertFalse(entries.stream().anyMatch(entry -> entry.matches(PathContainer.parsePath(path))), path);
    }

    @Test void nodeProducerPageHasOnlyAnExactSpaEntry() {
        String route = "/projects/{projectId}/tasks/{taskId}/node-producer-rules";
        assertTrue(FrameworkFrontendConfiguration.ROUTES.contains(route));
        var pattern = new PathPatternParser().parse(route);
        assertTrue(pattern.matches(PathContainer.parsePath("/projects/p/tasks/t/node-producer-rules")));
        for (var path : List.of("/api/tasks/t/node-producer-rules",
                "/projects/p/tasks/t/node-producer-rules/decisions",
                "/projects/p/tasks/t/node-producer-rules/recovery"))
            assertFalse(pattern.matches(PathContainer.parsePath(path)), path);
    }

    @Test void fixedConfigurationAndEvaluationPagesHaveExactServerEntries() {
        assertTrue(FrameworkFrontendConfiguration.ROUTES.containsAll(List.of(
            "/rules", "/rules/new", "/rules/{ruleId}", "/model-configurations", "/simulation-model-configurations",
            "/evaluation", "/evaluation/new", "/evaluation/{evaluationId}")));
        assertEquals(FrameworkFrontendConfiguration.ROUTES.size(), new HashSet<>(FrameworkFrontendConfiguration.ROUTES).size());
    }

    @Test void newBusinessDeepLinksHaveExactEntriesWithoutCatchAll() {
        var required=List.of("/sources/uploads", "/library/candidates", "/access/denied", "/agents", "/agents/simulation-preparation",
            "/agents/simulation-rules", "/agents/simulation-rules/{ruleVersion}",
            "/projects/{projectId}/tasks/{taskId}/documents/{documentId}/write",
            "/projects/{projectId}/tasks/{taskId}/documents/{documentId}/review");
        assertTrue(FrameworkFrontendConfiguration.ROUTES.containsAll(required));
        var parser=new PathPatternParser();
        var entries=required.stream().map(parser::parse).toList();
        for(var path:List.of("/sources/uploads", "/library/candidates", "/access/denied", "/agents/simulation-rules/new",
                "/agents/simulation-rules/535a9633-9459-4cdc-9af2-a159b718df90", "/projects/p/tasks/t/documents/d/review"))
            assertTrue(entries.stream().anyMatch(p->p.matches(PathContainer.parsePath(path))),path);
        for(var path:List.of("/api/simulation-feature-availability", "/api/simulation-rule-versions", "/sources/uploads/unrecognized",
                "/agents/simulation-rules/id/approvals", "/projects/p/tasks/t/documents/d/unrecognized"))
            assertFalse(entries.stream().anyMatch(p->p.matches(PathContainer.parsePath(path))),path);
    }

    @Test void deepLinksMatchWithoutExposingApiOrUnknownNestedPaths() {
        var parser = new PathPatternParser();
        var entries = List.of("/rules", "/rules/new", "/rules/{ruleId}", "/model-configurations", "/simulation-model-configurations",
            "/evaluation", "/evaluation/new", "/evaluation/{evaluationId}").stream().map(parser::parse).toList();
        for (var path : List.of("/rules", "/rules/new", "/rules/535a9633-9459-4cdc-9af2-a159b718df90",
                "/model-configurations", "/simulation-model-configurations", "/evaluation", "/evaluation/new", "/evaluation/535a9633-9459-4cdc-9af2-a159b718df90"))
            assertTrue(entries.stream().anyMatch(p -> p.matches(PathContainer.parsePath(path))), path);
        for (var path : List.of("/api/evaluations", "/api/rule-versions", "/api/model-config-revisions",
                "/evaluation/a/scores", "/rules/a/approvals", "/model-configurations/unknown", "/simulation-model-configurations/unknown"))
            assertFalse(entries.stream().anyMatch(p -> p.matches(PathContainer.parsePath(path))), path);
    }
}
