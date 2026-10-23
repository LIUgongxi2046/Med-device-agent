package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.apache.commons.codec.digest.DigestUtils;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.stereotype.Component;

@Component
public final class TemplateDefinitionSchema {
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    private final PermissionSchema json;
    private final RegistrationDirectory directory;
    private final SchemaCatalogue schemas;
    private final String manifest;

    public TemplateDefinitionSchema(PermissionSchema json, RegistrationDirectory directory) throws IOException, NoSuchAlgorithmException {
        this.json = json; this.directory = directory;
        schemas = SchemaCatalogue.fromClasspath();
        byte[] resource;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/templates.schema.json"))) { resource = input.readAllBytes(); }
        schemas.requirePublished("urn:medical:templates:1.0.0", DigestUtils.sha256Hex(resource));
        var identity = json.object(); identity.put("schema_version", "1.0.0");
        identity.put("templates.schema.json", DigestUtils.sha256Hex(resource));
        identity.put("permission_manifest", json.manifestSha256());
        identity.put("official_directory", DigestUtils.sha256Hex(json.canonicalObject(directory.catalogue())));
        manifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    public TemplateProtocol.Validated version(byte[] bytes) throws IOException {
        var node = mapper.readTree(bytes); verify("VersionInput", node);
        var request = mapper.treeToValue(node, TemplateProtocol.VersionInput.class);
        ProjectSchema.text(request.reason(), 2000);
        requireContent(request.content());
        String content = json.canonicalObject(request.content());
        return new TemplateProtocol.Validated(request, content, DigestUtils.sha256Hex(content), manifest,
                references(mapper.readTree(json.canonicalObject(request))));
    }

    public TemplateProtocol.Content content(byte[] bytes) throws IOException {
        var node = mapper.readTree(bytes); verify("Content", node);
        var content = mapper.treeToValue(node, TemplateProtocol.Content.class); requireContent(content); return content;
    }

    public void requireOfficialDirectory(String profile, JsonNode nodes) throws IOException {
        verify("Directory", nodes);
        if ("NON_IVD_FIRST_REGISTRATION_2021_121".equals(profile)) directory.requireComplete(nodes);
        else if (profile != null) invalid();
    }

    private void requireContent(TemplateProtocol.Content content) throws IOException {
        var nodes = index(content.nodes(), TemplateProtocol.Node::node_id);
        var fields = index(content.field_definitions(), TemplateProtocol.Field::field_id);
        var repeats = index(content.repeat_definitions(), TemplateProtocol.Repeat::repeat_id);
        var attachments = index(content.attachment_requirements(), TemplateProtocol.Attachment::attachment_requirement_id);
        index(content.cross_document_rules(), TemplateProtocol.CrossDocumentRule::rule_id);
        var graph = new DirectedAcyclicGraph<String, DefaultEdge>(DefaultEdge.class);
        for (String id : nodes.keySet()) graph.addVertex(id);
        var numbers = new HashSet<String>(); var siblingOrdinals = new HashMap<String, Set<Integer>>();
        var official = json.object().arrayNode();
        for (var node : content.nodes()) {
            ProjectSchema.text(node.title(), 512); ProjectSchema.text(node.official_number(), 64);
            if (!numbers.add(node.official_number())) invalid();
            if (node.parent_id() != null) {
                requireKey(nodes, node.parent_id());
                if (node.node_id().equals(node.parent_id()) || graph.getAncestors(node.parent_id()).contains(node.node_id())) invalid();
                graph.addEdge(node.parent_id(), node.node_id());
            }
            ordinal(siblingOrdinals, node.parent_id(), node.ordinal());
            condition(content, node.condition()); evidence(fields, node.evidence_rule());
            if (!"DIRECTORY".equals(node.content_mode()) && !node.content_mode().equals(node.evidence_rule().mode())) invalid();
            if (node.basis_refs().isEmpty()) invalid();
            for (String requirement : node.content_requirements()) ProjectSchema.text(requirement, Integer.MAX_VALUE);
            if ("OFFICIAL".equals(node.nature())) {
                var entry = json.object(); entry.put("official_number", node.official_number()); entry.put("title", node.title()); entry.put("ordinal", node.ordinal());
                entry.set("parent_number", json.tree(node.parent_id() == null ? null : nodes.get(node.parent_id()).official_number()));
                official.add(entry);
            }
        }
        contiguous(siblingOrdinals);
        boolean fullDirectory = !content.ivd() && content.application_path().equals("FIRST_REGISTRATION")
                && Set.of("REGISTRATION_DIRECTORY", "REGISTRATION_DOSSIER").contains(content.document_type());
        if (fullDirectory != Objects.equals(content.directory_profile(), "NON_IVD_FIRST_REGISTRATION_2021_121")) invalid();
        if (fullDirectory) {
            requireOfficialDirectory(content.directory_profile(), official);
            for (var node : content.nodes()) if (node.nature().equals("OFFICIAL") && !node.required()) invalid();
        }
        if (content.document_type().equals("CHANGE_REGISTRATION") && !content.application_path().equals("CHANGE_REGISTRATION")
                || content.document_type().equals("RENEWAL_REGISTRATION") && !content.application_path().equals("RENEWAL_REGISTRATION")) invalid();
        condition(content, content.condition_schema().applicability());
        var fieldKeys = new HashSet<String>(); var fieldOrdinals = new HashMap<String, Set<Integer>>();
        var calculations = new DirectedAcyclicGraph<String, DefaultEdge>(DefaultEdge.class);
        for (String id : fields.keySet()) calculations.addVertex(id);
        for (var field : content.field_definitions()) {
            requireKey(nodes, field.node_id()); ProjectSchema.text(field.name(), Integer.MAX_VALUE); ProjectSchema.text(field.description(), Integer.MAX_VALUE);
            if (!fieldKeys.add(field.node_id() + ":" + field.key()) || !field.node_id().equals(field.output_mapping().node_id())
                    || !Objects.equals(field.repeat_id(), field.output_mapping().repeat_id())) invalid();
            ordinal(fieldOrdinals, field.node_id() + ":" + Objects.toString(field.repeat_id(), ""), field.output_mapping().ordinal());
            condition(content, field.condition()); evidence(fields, field.evidence_rule()); constraints(field);
            if (field.repeat_id() != null) {
                var repeat = requireKey(repeats, field.repeat_id());
                if (!repeat.node_id().equals(field.node_id())) invalid();
                if (!field.value_type().equals("TABLE") && (!repeat.field_ids().contains(field.field_id()) || field.output_mapping().column_key() == null)) invalid();
                if (field.value_type().equals("TABLE") && field.output_mapping().column_key() != null) invalid();
            } else if (field.output_mapping().column_key() != null || field.value_type().equals("TABLE")) invalid();
            if (field.attachment_requirement_id() != null) {
                var attachment = requireKey(attachments, field.attachment_requirement_id());
                if (!attachment.node_id().equals(field.node_id()) || !field.evidence_rule().mode().equals("AUTHORIZED_ORIGINAL_ONLY")) invalid();
            }
            if (field.value_type().equals("FILE") && field.attachment_requirement_id() == null
                    || field.value_type().equals("FORMULA") && !field.evidence_rule().mode().equals("APPROVED_CALCULATION")) invalid();
            for (String input : field.evidence_rule().input_field_ids()) {
                if (input.equals(field.field_id()) || calculations.getAncestors(input).contains(field.field_id())) invalid();
                calculations.addEdge(input, field.field_id());
            }
        }
        contiguous(fieldOrdinals);
        for (var repeat : content.repeat_definitions()) {
            requireKey(nodes, repeat.node_id()); ProjectSchema.text(repeat.name(), Integer.MAX_VALUE);
            counts(repeat.min_count(), repeat.max_count(), repeat.count_basis());
            var columns = new HashSet<String>();
            for (String id : repeat.field_ids()) {
                var field = requireKey(fields, id);
                if (!repeat.node_id().equals(field.node_id()) || !repeat.repeat_id().equals(field.repeat_id())
                        || field.value_type().equals("TABLE") || !columns.add(field.output_mapping().column_key())) invalid();
            }
        }
        var attachmentOrdinals = new HashMap<String, Set<Integer>>();
        for (var attachment : content.attachment_requirements()) {
            requireKey(nodes, attachment.node_id()); ProjectSchema.text(attachment.name(), Integer.MAX_VALUE);
            condition(content, attachment.condition()); evidence(fields, attachment.evidence_rule());
            if (!attachment.evidence_rule().mode().equals("AUTHORIZED_ORIGINAL_ONLY")) invalid();
            counts(attachment.min_count(), attachment.max_count(), attachment.count_basis());
            if (attachment.required() && PermissionSchema.revision(attachment.min_count()).signum() == 0) invalid();
            ordinal(attachmentOrdinals, attachment.node_id(), attachment.ordinal());
        }
        contiguous(attachmentOrdinals);
        for (var rule : content.cross_document_rules()) for (String id : rule.field_ids()) requireKey(fields, id);
        var displays = new HashSet<String>();
        for (var display : content.format_profile().node_displays()) {
            requireKey(nodes, display.node_id()); if (!displays.add(display.node_id())) invalid();
        }
        references(json.tree(content));
    }

    private static void constraints(TemplateProtocol.Field field) {
        var value = field.constraints(); boolean number = field.value_type().equals("NUMBER") || field.value_type().equals("FORMULA");
        if (!number && (value.minimum() != null || value.maximum() != null || value.precision() != null || value.unit() != null)) invalid();
        if (value.minimum() != null && value.maximum() != null && new BigDecimal(value.minimum()).compareTo(new BigDecimal(value.maximum())) > 0) invalid();
        if ((value.minimum() != null || value.maximum() != null || value.precision() != null || value.unit() != null || value.max_length() != null)
                && value.range_basis().isEmpty()) invalid();
        boolean enumeration = field.value_type().equals("ENUM") || field.value_type().equals("MULTI_SELECT");
        if (enumeration == value.enum_options().isEmpty()) invalid();
        index(value.enum_options(), TemplateProtocol.Option::value);
        for (var option : value.enum_options()) { ProjectSchema.text(option.label(), Integer.MAX_VALUE); if (option.basis_refs().isEmpty()) invalid(); }
    }

    private static void condition(TemplateProtocol.Content content, TemplateProtocol.Condition condition) {
        if (!content.condition_schema().attribute_keys().containsAll(condition.input_keys())) invalid();
        if (condition.engine_revision() != null) ProjectSchema.text(condition.engine_revision(), 128);
    }

    private static void evidence(Map<String, TemplateProtocol.Field> fields, TemplateProtocol.EvidenceRule rule) {
        for (String id : rule.input_field_ids()) requireKey(fields, id);
    }

    private static void counts(String minimum, String maximum, List<PermissionProtocol.Basis> basis) {
        var min = PermissionSchema.revision(minimum);
        if (maximum != null && PermissionSchema.revision(maximum).compareTo(min) < 0 || (min.signum() > 0 || maximum != null) && basis.isEmpty()) invalid();
    }

    private List<PermissionProtocol.Basis> references(JsonNode value) throws IOException {
        var result = new LinkedHashMap<String, PermissionProtocol.Basis>();
        for (var parent : value.findParents("version_id")) {
            var basis = json.basis(parent); var previous = result.putIfAbsent(basis.version_id(), basis);
            if (previous != null && !previous.equals(basis)) invalid();
        }
        return List.copyOf(result.values());
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> identity) {
        var result = new LinkedHashMap<String, T>();
        for (T value : values) if (result.putIfAbsent(identity.apply(value), value) != null) invalid();
        return result;
    }
    private static <T> T requireKey(Map<String, T> values, String id) {
        T result = values.get(id); if (result == null) invalid(); return result;
    }
    private static void ordinal(Map<String, Set<Integer>> groups, String group, int ordinal) {
        if (!groups.computeIfAbsent(group, key -> new HashSet<>()).add(ordinal)) invalid();
    }
    private static void contiguous(Map<String, Set<Integer>> groups) {
        for (var values : groups.values()) for (int ordinal = 1; ordinal <= values.size(); ordinal++) if (!values.contains(ordinal)) invalid();
    }
    public String manifestSha256() { return manifest; }
    public void verify(String name, JsonNode value) { if (value == null) invalid(); schemas.validate("templates", value, name); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
