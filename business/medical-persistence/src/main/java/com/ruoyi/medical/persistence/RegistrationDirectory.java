package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.stereotype.Component;

@Component
public final class RegistrationDirectory {
    private static final ObjectMapper JSON = new ObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
            DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final Catalogue catalogue;

    public RegistrationDirectory() throws IOException {
        try (var source = Objects.requireNonNull(RegistrationDirectory.class.getResourceAsStream("/registration-directory-2021-121.json"),
                "完整注册目录资源缺失")) {
            catalogue = JSON.readValue(source, Catalogue.class);
        }
        var graph = new DirectedAcyclicGraph<String, DefaultEdge>(DefaultEdge.class);
        var ordinals = new HashMap<String, Integer>();
        for (var node : catalogue.nodes()) {
            if (!graph.addVertex(node.officialNumber())) mismatch();
            String group = node.parentNumber() == null ? "ROOT" : node.parentNumber();
            if (node.ordinal() != ordinals.merge(group, 1, Integer::sum)) mismatch();
        }
        for (var node : catalogue.nodes()) {
            if (node.parentNumber() != null) {
                if (!graph.containsVertex(node.parentNumber())) mismatch();
                graph.addEdge(node.parentNumber(), node.officialNumber());
            }
        }
        if (catalogue.nodes().stream().filter(node -> node.parentNumber() == null).count() != 6 || catalogue.nodes().size() != 44)
            mismatch();
    }

    public static RegistrationDirectory fromClasspath() throws IOException { return new RegistrationDirectory(); }
    public Catalogue catalogue() { return catalogue; }

    public void requireComplete(JsonNode candidate) throws IOException {
        // 不适用决定只影响项目适用状态，完整规范目录始终保留。
        var supplied = Arrays.asList(JSON.treeToValue(Objects.requireNonNull(candidate), Entry[].class));
        if (!catalogue.nodes().equals(supplied)) mismatch();
    }

    public record Catalogue(String schemaVersion, String profileId, String referenceState, String sourceId,
            String sourceSha256, List<Entry> nodes) {
        public Catalogue {
            if (!"1.0.0".equals(schemaVersion) || !"NON_IVD_FIRST_REGISTRATION_2021_121".equals(profileId)
                    || !"REFERENCE_ONLY".equals(referenceState) || !"official-requirements-2021-121-a5-text".equals(sourceId)) mismatch();
            WireValues.hash(sourceSha256); nodes = List.copyOf(nodes);
        }
    }

    public record Entry(String officialNumber, String parentNumber, String title, int ordinal) {
        public Entry {
            Objects.requireNonNull(officialNumber); Objects.requireNonNull(title);
            if (officialNumber.isBlank() || title.isBlank() || ordinal < 1) mismatch();
        }
    }

    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
