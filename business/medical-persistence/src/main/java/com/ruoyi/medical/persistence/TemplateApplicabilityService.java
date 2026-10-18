package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.pagehelper.PageHelper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.ResourceProtocol;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.TemplateMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
@EnableConfigurationProperties(TemplateDependencyPolicy.class)
public final class TemplateApplicabilityService {
    static final String PURPOSE = "TEMPLATE_APPLICATION";
    static final List<String> PROFILE_KEYS = List.of("application_path", "ivd", "directory_profile");
    private final EnterpriseAccess access;
    private final ProjectService projects;
    private final TemplateMapper mapper;
    private final TemplateVersionStore store;
    private final TemplateDefinitionSchema definitions;
    private final TemplateDependencyAuthority dependencies;
    private final ResourceReadAuthority authority;
    private final RuleExpression engine;
    private final TemplateApplicabilitySchema schema;
    private final TemplateDependencyPolicy policy;
    private final IdentityDirectoryPolicy identity;
    private final PermissionSchema json;

    public TemplateApplicabilityService(EnterpriseAccess access, ProjectService projects, TemplateMapper mapper,
            TemplateVersionStore store, TemplateDefinitionSchema definitions, TemplateDependencyAuthority dependencies,
            ResourceReadAuthority authority, RuleExpression engine, TemplateApplicabilitySchema schema,
            TemplateDependencyPolicy policy, IdentityDirectoryPolicy identity, PermissionSchema json) {
        this.access = access; this.projects = projects; this.mapper = mapper; this.store = store; this.definitions = definitions;
        this.dependencies = dependencies; this.authority = authority; this.engine = engine; this.schema = schema;
        this.policy = policy; this.identity = identity; this.json = json;
    }

    public JsonNode list(ApplicationSessionIdentity.Verified subject, String context, String projectId, TemplateApplicabilityProtocol.Query query) {
        return access.execute(subject, admission -> checked(() -> {
            if (!admission.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            var profile = projects.fixedProfile(admission, projectId, query.profile_revision(), PURPOSE);
            var profileProofs = dependencies.requireProfile(admission, profile, PURPOSE);
            var values = values(admission); values.put("documentType", query.document_type());
            var candidates = PageHelper.startPage(1, policy.maximumVersions() + 1, false)
                    .<String>doSelectPageInfo(() -> mapper.approvedVersions(values)).getList();
            if (candidates.size() > policy.maximumVersions()) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            var views = new LinkedHashMap<String,TemplateApplicabilityProtocol.TemplateView>();
            var rules = new HashMap<String,RuleExpression.Compiled>();
            for (String id : candidates) {
                var scope = new PermissionProtocol.Scope(admission.context().tenant_id(), null, "TEMPLATE", id);
                if (authority.find(admission, scope, "template.read", PURPOSE).isEmpty()) continue;
                var template = store.applicationTarget(admission, id, PURPOSE);
                if (!template.content().document_type().equals(query.document_type())) mismatch();
                var compatible = compatible(profile.attributes(), template.content(), projectId);
                if (compatible.status().equals("NOT_APPLICABLE")) continue;
                var view = evaluate(admission, profile, profileProofs, template, compatible, rules, PURPOSE).view();
                if (view.applicability().equals("NOT_APPLICABLE")) continue;
                if (views.putIfAbsent(id, view) != null) mismatch();
            }
            var state = json.object(); state.put("project_id", projectId); state.put("document_type", query.document_type());
            state.put("profile_revision", query.profile_revision()); state.put("profile_sha256", profile.sha256());
            state.put("subject_id", admission.subject().user().user_id()); state.put("context_id", admission.contextId());
            state.put("context_revision", admission.context().context_revision()); state.put("auth_epoch", admission.watermarks().authEpoch().toString());
            state.put("governance_epoch", admission.watermarks().governanceEpoch().toString()); state.set("items", json.tree(views.values()));
            String filter = DigestUtils.sha256Hex(json.canonical(state)), watermark = admission.watermarks().governanceEpoch().toString();
            if (query.dataset_watermark() != null && !query.dataset_watermark().equals(watermark)
                    || query.filter_sha256() != null && !query.filter_sha256().equals(filter)) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            values.put("versionIds", List.copyOf(views.keySet()));
            var page = PageHelper.startPage(query.page(), query.page_size(), true)
                    .<String>doSelectPageInfo(() -> mapper.applicableVersions(values));
            if (page.getTotal() != views.size()) mismatch();
            var items = json.object().arrayNode();
            for (String id : page.getList()) {
                var view = views.get(id); if (view == null) mismatch(); items.add(json.tree(view));
            }
            var data = json.object(); data.put("schema_version", "1.0.0"); data.put("project_id", projectId);
            data.put("profile_revision", query.profile_revision()); data.put("profile_sha256", profile.sha256()); data.put("document_type", query.document_type());
            data.set("items", items);
            data.set("missing_requirements", json.tree(views.isEmpty() ? List.of(new ResourceProtocol.Requirement(
                    "template:" + query.document_type() + ":compatible", null, null, "SOURCE_MISSING", List.of())) : List.of()));
            data.set("page_info", json.tree(new InterfaceProtocol.PageInfo(query.page(), query.page_size(), Long.toString(page.getTotal()), watermark, filter)));
            return schema.envelope(data, UUID.randomUUID().toString(), profile.project().revision().toString());
        }));
    }

    Target requireTarget(EnterpriseAccess.Admission admission, String projectId, String profileRevision,
            String documentType, String templateVersion, String purpose) throws IOException {
        WireValues.uuid(templateVersion); ProjectSchema.text(purpose, 200);
        var query = json.object(); query.put("document_type", documentType); query.put("profile_revision", profileRevision);
        query.put("page", 1); query.put("page_size", 1); schema.verify("Query", query);
        var profile = projects.fixedProfile(admission, projectId, profileRevision, purpose);
        var profileProofs = dependencies.requireProfile(admission, profile, purpose);
        var scope = new PermissionProtocol.Scope(admission.context().tenant_id(), null, "TEMPLATE", templateVersion);
        var grant = authority.require(admission, scope, "template.read", purpose);
        var template = store.applicationTarget(admission, templateVersion, purpose);
        if (!template.content().document_type().equals(documentType)) throw new TenantAccessException(ErrorCode.RESULT_VERSION_MISMATCH);
        var compatible = compatible(profile.attributes(), template.content(), projectId);
        if (compatible.status().equals("NOT_APPLICABLE")) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var evaluation = evaluate(admission, profile, profileProofs, template, compatible, new HashMap<>(), purpose);
        if (evaluation.view().applicability().equals("NOT_APPLICABLE")) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        if (!grant.equals(authority.require(admission, scope, "template.read", purpose))) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        return new Target(admission, purpose, profile, evaluation.view(), grant, evaluation.dependencyAuthorizations());
    }

    void requireUnchanged(EnterpriseAccess.Admission admission, Target original) throws IOException {
        if (original.admission() != admission) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        var current = requireTarget(admission, original.profile().project().projectId(), original.profile().project().profileRevision(),
                original.view().template().content().document_type(), original.view().template().template_version(), original.purpose());
        if (!original.equals(current)) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
    }

    private Evaluation evaluate(EnterpriseAccess.Admission admission, ProjectProtocol.VerifiedProfile profile,
            List<DependencySourceAuthority.VerifiedDependencies> profileProofs, TemplateProtocol.Stored template,
            TemplateApplicabilityProtocol.ConditionResult compatible, Map<String,RuleExpression.Compiled> rules,
            String purpose) throws IOException {
        var validated = definitions.version(json.canonicalObject(new TemplateProtocol.VersionInput("1.0.0",
                template.base_version(), "urn:medical:templates:1.0.0#/$defs/Content", template.content(), template.reason())).getBytes(StandardCharsets.UTF_8));
        if (!validated.dependency_refs().equals(template.dependency_refs()) || !validated.content_sha256().equals(template.content_sha256())) mismatch();
        var proofs = dependencies.require(admission, validated, purpose);
        var conditions = conditions(admission, profile, template, compatible, proofs, rules);
        var overall = combine(List.of(conditions.get(0), conditions.get(1)));
        var fingerprint = json.object(); fingerprint.set("profile", dependencies.snapshot(admission, profileProofs, json));
        fingerprint.set("template", dependencies.snapshot(admission, proofs, json));
        String authorization = json.canonical(fingerprint);
        var view = new TemplateApplicabilityProtocol.TemplateView(template, overall.status(), conditions, missing(template, conditions),
                DigestUtils.sha256Hex(authorization));
        schema.verify("TemplateView", json.tree(view));
        return new Evaluation(view, authorization);
    }

    record Target(EnterpriseAccess.Admission admission, String purpose, ProjectProtocol.VerifiedProfile profile,
            TemplateApplicabilityProtocol.TemplateView view, PermissionProtocol.GrantRow grant, String dependencyAuthorizations) { }

    private record Evaluation(TemplateApplicabilityProtocol.TemplateView view, String dependencyAuthorizations) { }

    private List<TemplateApplicabilityProtocol.ConditionResult> conditions(EnterpriseAccess.Admission admission,
            ProjectProtocol.VerifiedProfile profile, TemplateProtocol.Stored template,
            TemplateApplicabilityProtocol.ConditionResult compatible, List<DependencySourceAuthority.VerifiedDependencies> proofs,
            Map<String,RuleExpression.Compiled> rules) throws IOException {
        var content = template.content(); var results = new ArrayList<TemplateApplicabilityProtocol.ConditionResult>(); results.add(compatible);
        results.add(condition(admission, "TEMPLATE", template.template_version(), content.condition_schema().applicability(), profile.attributes(), proofs, rules));
        for (var node : content.nodes()) results.add(condition(admission, "NODE", node.node_id(), node.condition(), profile.attributes(), proofs, rules));
        for (var field : content.field_definitions()) results.add(condition(admission, "FIELD", field.field_id(), field.condition(), profile.attributes(), proofs, rules));
        for (var attachment : content.attachment_requirements()) results.add(condition(admission, "ATTACHMENT", attachment.attachment_requirement_id(),
                attachment.condition(), profile.attributes(), proofs, rules));
        return List.copyOf(results);
    }

    private TemplateApplicabilityProtocol.ConditionResult condition(EnterpriseAccess.Admission admission, String scope, String target,
            TemplateProtocol.Condition condition, List<ProjectProtocol.Attribute> attributes,
            List<DependencySourceAuthority.VerifiedDependencies> proofs, Map<String,RuleExpression.Compiled> rules) throws IOException {
        if (condition.mode().equals("ALWAYS")) return new TemplateApplicabilityProtocol.ConditionResult(scope, target, null, null, List.of(), "APPLICABLE", List.of());
        if (!condition.mode().equals("RULE")) mismatch();
        var ref = Objects.requireNonNull(condition.rule_revision());
        var proof = proofs.stream().filter(item -> item.root().equals(ref.version_id())).findFirst().orElseThrow();
        if (proof.admission() != admission) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        var root = proof.versions().get(ref.version_id());
        if (root == null || root.domain() != VersionResourceProtocol.Domain.RULE_VERSION || !ref.content_sha256().equals(root.version().contentSha256())) mismatch();
        var values = values(admission); values.put("upstreamId", ref.version_id());
        var rows = mapper.ruleDefinition(values); if (rows.size() != 1) mismatch(); var row = rows.get(0);
        if (!row.tenantId().equals(admission.context().tenant_id()) || !row.ruleVersion().equals(ref.version_id())
                || !row.contentHash().equals(ref.content_sha256()) || !row.classification().equals(identity.classification())) mismatch();
        if (!row.status().equals("APPROVED")) throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
        WireValues.uuid(row.createdBy()); WireValues.utcTime(DatabaseTimestamp.format(row.createdAt()));
        var rule = schema.rule(row);
        if (!rule.engine_revision().equals(condition.engine_revision()) || !rule.expression().input_keys().equals(condition.input_keys())) mismatch();
        var parents = rule.basis_manifest().stream().map(PermissionProtocol.Basis::version_id).sorted().toList();
        if (!mapper.ruleParents(values).equals(parents)) mismatch();
        for (var basis : rule.basis_manifest()) {
            var version = proof.versions().get(basis.version_id());
            if (version == null || !version.version().contentSha256().equals(basis.content_sha256())) mismatch();
        }
        var compiled = rules.get(ref.version_id());
        if (compiled == null) { compiled = engine.compile(rule.expression()); rules.put(ref.version_id(), compiled); }
        else if (!compiled.input().equals(rule.expression())) mismatch();
        var result = engine.evaluate(compiled, attributes);
        return new TemplateApplicabilityProtocol.ConditionResult(scope, target, ref, condition.engine_revision(), condition.input_keys(), result.status(), result.unknown_input_keys());
    }

    static TemplateApplicabilityProtocol.ConditionResult compatible(List<ProjectProtocol.Attribute> attributes, TemplateProtocol.Content content, String projectId) {
        var indexed = new HashMap<String,ProjectProtocol.Attribute>();
        for (var attribute : attributes) if (indexed.putIfAbsent(attribute.key(), attribute) != null) mismatch();
        var unknown = new ArrayList<String>(); boolean compatible = true;
        for (String key : PROFILE_KEYS) {
            var actual = indexed.get(key);
            if (actual == null || actual.status().equals("UNKNOWN")) { unknown.add(key); continue; }
            if (!actual.status().equals("KNOWN") || actual.value() == null || actual.value().isNull()) mismatch();
            var value = actual.value();
            if (key.equals("ivd")) {
                if (!value.isBoolean()) throw new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID);
                compatible &= value.booleanValue() == content.ivd();
            } else {
                if (!value.isTextual()) throw new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID);
                compatible &= value.textValue().equals(key.equals("application_path") ? content.application_path() : content.directory_profile());
            }
        }
        return new TemplateApplicabilityProtocol.ConditionResult("PROFILE", projectId, null, null, PROFILE_KEYS,
                !compatible ? "NOT_APPLICABLE" : unknown.isEmpty() ? "APPLICABLE" : "UNKNOWN", compatible ? unknown : List.of());
    }

    // 完整条件结果保留原表达式的三态；缺项计算同时核查全部祖先及附件条件。
    static List<ResourceProtocol.Requirement> missing(TemplateProtocol.Stored template, List<TemplateApplicabilityProtocol.ConditionResult> conditions) {
        var indexed = new HashMap<String,TemplateApplicabilityProtocol.ConditionResult>();
        for (var condition : conditions) if (indexed.putIfAbsent(condition.scope() + ":" + condition.target_id(), condition) != null) mismatch();
        var overall = combine(conditions.subList(0, 2));
        var result = new ArrayList<ResourceProtocol.Requirement>();
        if (overall.status().equals("UNKNOWN")) result.add(new ResourceProtocol.Requirement("template:" + template.template_version() + ":applicability",
                null, null, "APPLICABILITY_UNKNOWN", List.of()));
        var effective = new HashMap<String,TemplateApplicabilityProtocol.Result>();
        var nodes = new HashMap<String,TemplateProtocol.Node>(); var graph = new DirectedAcyclicGraph<String,DefaultEdge>(DefaultEdge.class);
        for (var node : template.content().nodes()) { nodes.put(node.node_id(), node); graph.addVertex(node.node_id()); }
        for (var node : template.content().nodes()) if (node.parent_id() != null) graph.addEdge(node.parent_id(), node.node_id());
        var order = new TopologicalOrderIterator<>(graph);
        while (order.hasNext()) {
            var node = nodes.get(order.next()); var own = requireCondition(indexed, "NODE", node.node_id());
            var parent = node.parent_id() == null ? overall : Objects.requireNonNull(effective.get(node.parent_id()));
            var value = combineResults(List.of(parent, new TemplateApplicabilityProtocol.Result(own.status(), own.unknown_input_keys())));
            effective.put(node.node_id(), value);
            if (value.status().equals("UNKNOWN")) result.add(new ResourceProtocol.Requirement("template:" + template.template_version() + ":node:" + node.node_id(),
                    node.node_id(), null, "APPLICABILITY_UNKNOWN", List.of()));
        }
        var attachments = new HashMap<String,TemplateApplicabilityProtocol.Result>();
        for (var attachment : template.content().attachment_requirements()) {
            var own = requireCondition(indexed, "ATTACHMENT", attachment.attachment_requirement_id());
            var value = combineResults(List.of(Objects.requireNonNull(effective.get(attachment.node_id())),
                    new TemplateApplicabilityProtocol.Result(own.status(), own.unknown_input_keys())));
            attachments.put(attachment.attachment_requirement_id(), value);
            if (value.status().equals("UNKNOWN")) result.add(new ResourceProtocol.Requirement("template:" + template.template_version() + ":attachment:" + attachment.attachment_requirement_id(),
                    attachment.node_id(), null, "APPLICABILITY_UNKNOWN", List.of()));
        }
        for (var field : template.content().field_definitions()) {
            var own = requireCondition(indexed, "FIELD", field.field_id());
            var parents = new ArrayList<TemplateApplicabilityProtocol.Result>(); parents.add(Objects.requireNonNull(effective.get(field.node_id())));
            if (field.attachment_requirement_id() != null) parents.add(Objects.requireNonNull(attachments.get(field.attachment_requirement_id())));
            parents.add(new TemplateApplicabilityProtocol.Result(own.status(), own.unknown_input_keys()));
            if (combineResults(parents).status().equals("UNKNOWN")) result.add(new ResourceProtocol.Requirement("template:" + template.template_version() + ":field:" + field.field_id(),
                    field.node_id(), field.field_id(), "APPLICABILITY_UNKNOWN", List.of()));
        }
        return List.copyOf(result);
    }
    private static TemplateApplicabilityProtocol.ConditionResult requireCondition(Map<String,TemplateApplicabilityProtocol.ConditionResult> values, String scope, String id) {
        return Objects.requireNonNull(values.get(scope + ":" + id));
    }
    static TemplateApplicabilityProtocol.Result combine(List<TemplateApplicabilityProtocol.ConditionResult> values) {
        return combineResults(values.stream().map(value -> new TemplateApplicabilityProtocol.Result(value.status(), value.unknown_input_keys())).toList());
    }
    static TemplateApplicabilityProtocol.Result combineResults(List<TemplateApplicabilityProtocol.Result> values) {
        var unknown = new java.util.LinkedHashSet<String>();
        for (var value : values) {
            if (value.status().equals("NOT_APPLICABLE")) return new TemplateApplicabilityProtocol.Result("NOT_APPLICABLE", List.of());
            if (value.status().equals("UNKNOWN")) unknown.addAll(value.unknown_input_keys());
            else if (!value.status().equals("APPLICABLE")) mismatch();
        }
        return new TemplateApplicabilityProtocol.Result(unknown.isEmpty() ? "APPLICABLE" : "UNKNOWN", List.copyOf(unknown));
    }
    private static Map<String,Object> values(EnterpriseAccess.Admission admission) {
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id()); return values;
    }
    private static <T> T checked(ThrowingSupplier<T> work) { return work.get((message, failure) -> new IllegalStateException(message, failure)); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
