package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskPlanGraphTest {
    private final RegistrationDirectory.Catalogue directory = RegistrationDirectory.fromClasspath().catalogue();

    TaskPlanGraphTest() throws Exception { }

    private List<String> nodes() { return directory.nodes().stream().map(RegistrationDirectory.Entry::officialNumber).toList(); }

    private Map<String, List<String>> dependencies() {
        var result = new LinkedHashMap<String, List<String>>();
        for (var entry : directory.nodes()) result.put(entry.officialNumber(),
                entry.parentNumber() == null ? List.of() : List.of(entry.parentNumber()));
        return result;
    }

    private List<String> order(List<String> nodes, Map<String, List<String>> dependencies) {
        return TaskPlanPolicy.dependencyOrder(nodes, dependencies);
    }

    @Test void completeOfficialDirectoryPreservesEveryDependency() throws Exception {
        var result = order(nodes(), dependencies());
        assertEquals(44, result.size());
        assertEquals(new java.util.HashSet<>(nodes()), new java.util.HashSet<>(result));
        for (var entry : directory.nodes()) if (entry.parentNumber() != null)
            assertTrue(result.indexOf(entry.parentNumber()) < result.indexOf(entry.officialNumber()));
        assertThrows(UnsupportedOperationException.class, () -> result.add(directory.profileId()));
    }

    @Test void inputOrderCannotChangeIndependentNodeOrder() throws Exception {
        var reordered = new ArrayList<>(nodes()); Collections.reverse(reordered);
        assertEquals(order(nodes(), dependencies()), order(reordered, dependencies()));
    }

    @Test void actualDirectoryEdgeCannotFormAPlanCycle() throws Exception {
        var dependencies = dependencies();
        var entry = directory.nodes().stream().filter(value -> value.parentNumber() != null).findFirst().orElseThrow();
        dependencies.put(entry.parentNumber(), List.of(entry.officialNumber()));
        var error = assertThrows(TenantAccessException.class, () -> order(nodes(), dependencies));
        assertEquals(ErrorCode.PLAN_CYCLE, error.code());
    }

    @Test void referencesOutsideTheCurrentDirectoryAreRejected() throws Exception {
        var dependencies = dependencies(); dependencies.put(nodes().get(0), List.of(directory.sourceId()));
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class, () -> order(nodes(), dependencies));
    }

    @Test void duplicatedNodesAndDependencyEdgesAreRejected() throws Exception {
        var duplicate = new ArrayList<>(nodes()); duplicate.add(nodes().get(0));
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class, () -> order(duplicate, dependencies()));
        var dependencies = dependencies();
        var entry = directory.nodes().stream().filter(value -> value.parentNumber() != null).findFirst().orElseThrow();
        dependencies.put(entry.officialNumber(), List.of(entry.parentNumber(), entry.parentNumber()));
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class, () -> order(nodes(), dependencies));
    }

    @Test void selfDependencyIsRejectedWithThePlanCycleCode() throws Exception {
        var dependencies = dependencies(); dependencies.put(nodes().get(0), List.of(nodes().get(0)));
        assertEquals(ErrorCode.PLAN_CYCLE, assertThrows(TenantAccessException.class, () -> order(nodes(), dependencies)).code());
    }

    @Test void aMissingDependencyManifestCannotOmitAnOfficialNode() throws Exception {
        var dependencies = dependencies(); dependencies.remove(nodes().get(0));
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class, () -> order(nodes(), dependencies));
        assertEquals(List.of(), order(List.of(), Map.of()));
    }
}
