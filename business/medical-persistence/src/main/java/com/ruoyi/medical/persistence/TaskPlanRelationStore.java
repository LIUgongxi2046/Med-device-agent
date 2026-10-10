package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ArtifactBindings;
import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.TaskMapper;
import com.ruoyi.medical.persistence.mapper.TaskPlanRelationMapper;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class TaskPlanRelationStore {
    private static final String STATEMENT = "com.ruoyi.medical.persistence.mapper.TaskPlanRelationMapper.basis";
    private static final String PURPOSE = "PLAN_MAINTENANCE";
    private final TaskPlanRelationMapper mapper;
    private final TaskMapper tasks;
    private final ResourceReadAuthority authority;
    private final TaskInputSchema schema;
    private final ScopeSnapshotSchema snapshots;
    private final PermissionSchema json;
    private final IdentityDirectoryPolicy identity;
    private final SchemaCatalogue shared;

    public TaskPlanRelationStore(TaskPlanRelationMapper mapper, TaskMapper tasks, ResourceReadAuthority authority,
            TaskInputSchema schema, ScopeSnapshotSchema snapshots, PermissionSchema json, IdentityDirectoryPolicy identity)
            throws IOException, NoSuchAlgorithmException {
        this.mapper = mapper; this.tasks = tasks; this.authority = authority; this.schema = schema;
        this.snapshots = snapshots; this.json = json; this.identity = identity; shared = SchemaCatalogue.fromClasspath();
    }

    TaskPlanProjection.Projection save(EnterpriseAccess.Admission admission, String projectId, String taskId,
            String planRevision, TaskPlanPolicy.Validation validation, boolean confirmed) throws IOException {
        var values = scope(admission, projectId, taskId, planRevision);
        String action = confirmed ? "plan.confirm" : "plan.write";
        var scope = new PermissionProtocol.Scope(admission.context().tenant_id(), projectId, "TASK", taskId);
        var grant = authority.require(admission, scope, action, PURPOSE);
        var basis = basis(values);
        if (!(confirmed ? "CONFIRMED" : "DRAFT").equals(basis.status())
                || !basis.createdBy().equals(admission.subject().user().user_id())) denied();
        var candidate = candidate(basis);
        if (!candidate.equals(validation.candidate()) || !basis.contentHash().equals(validation.contentSha256())
                || !json.canonicalObject(candidate).equals(validation.canonicalContent())) mismatch();
        var evidence = evidence(admission, basis);
        var questionIds = identities(values, candidate);
        var created = new HashSet<String>();
        for (var question : candidate.questions()) {
            if (!questionIds.containsKey(question.questionKey())) {
                questionIds.put(question.questionKey(), UUID.randomUUID().toString());
                created.add(question.questionKey());
            }
        }
        var projection = TaskPlanProjection.project(validation, questionIds);
        var sections = index(candidate.sections(), ArtifactProtocol.PlanSection::sectionId);
        var questions = index(candidate.questions(), ArtifactProtocol.ResearchQuestion::questionKey);
        var questionSections = index(projection.questions(), TaskPlanProjection.Question::questionId);
        values.put("templateVersion", basis.templateVersion()); values.put("now", basis.createdAt());
        values.put("manifest", basis.manifest()); values.put("classification", basis.classification());
        values.put("evidenceScope", json.canonical(evidence));
        for (var section : projection.sections()) {
            values.put("item", section); values.put("requirements", json.canonicalObject(sections.get(section.sectionId()).requirementNodeIds()));
            one(mapper.insertSection(values));
        }
        insert(values, projection.sectionNodes(), mapper::insertSectionNode);
        insert(values, projection.sectionRules(), mapper::insertSectionRule);
        insert(values, projection.sectionDependencies(), mapper::insertSectionDependency);
        for (var question : projection.questions()) {
            values.put("item", question);
            if (created.contains(question.questionKey())) {
                values.put("completionRules", json.canonicalObject(questions.get(question.questionKey()).completionRuleIds()));
                one(mapper.insertQuestionIdentity(values));
            }
            one(mapper.insertQuestion(values));
        }
        for (var node : projection.questionNodes()) {
            values.put("item", node); values.put("sectionId", questionSections.get(node.questionId()).sectionId());
            one(mapper.insertQuestionNode(values));
        }
        insert(values, projection.questionRules(), mapper::insertQuestionRule);
        insert(values, projection.questionDependencies(), mapper::insertQuestionDependency);
        if (!questionIds.equals(identities(values, candidate))) mismatch();
        verify(values, basis, evidence, projection);
        if (!basis.equals(basis(values)) || !grant.equals(authority.require(admission, scope, action, PURPOSE))) {
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        }
        return projection;
    }

    TaskPlanProjection.Projection read(EnterpriseAccess.Admission admission, String projectId, String taskId,
            String planRevision, TaskPlanPolicy.Validation validation) throws IOException {
        var values = scope(admission, projectId, taskId, planRevision);
        authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), projectId, "TASK", taskId),
                "task.read", PURPOSE);
        var basis = basis(values);
        if (!candidate(basis).equals(validation.candidate()) || !basis.contentHash().equals(validation.contentSha256())) mismatch();
        var projection = TaskPlanProjection.project(validation, identities(values, validation.candidate()));
        verify(values, basis, evidence(admission, basis), projection);
        return projection;
    }

    private TaskPlanProjection.Basis basis(Map<String,Object> values) {
        var rows = mapper.basis(values); if (rows.size() != 1) mismatch(); var basis = rows.get(0);
        if (!TenantScope.requireCurrent().tenantId().equals(basis.tenantId()) || !values.get("projectId").equals(basis.projectId())
                || !values.get("taskId").equals(basis.taskId()) || !values.get("planRevision").equals(basis.planRevision())
                || !"1.0.0".equals(basis.schemaVersion()) || !schema.manifestSha256().equals(basis.manifest())
                || !identity.classification().equals(basis.classification()) || basis.createdAt() == null
                || !List.of("DRAFT", "CONFIRMED", "SUPERSEDED").contains(basis.status())) mismatch();
        for (String id : List.of(basis.taskRevision(), basis.templateVersion(), basis.snapshotId(), basis.profileRevision(), basis.createdBy())) WireValues.uuid(id);
        WireValues.hash(basis.contentHash()); WireValues.hash(basis.registryHash());
        if (!basis.contentHash().equals(basis.registryHash())) mismatch();
        return basis;
    }

    private ArtifactProtocol.PlanCandidate candidate(TaskPlanProjection.Basis basis) throws IOException {
        var binding = TypedSchemaBindings.bind(shared, ArtifactBindings.PLAN_CANDIDATE, schema.read(basis.contentJson()));
        if (!binding.payload().schemaRef().equals(basis.schemaRef())
                || !DigestUtils.sha256Hex(binding.payload().canonicalBytes()).equals(basis.contentHash())
                || !basis.templateVersion().equals(binding.read().templateVersion())
                || !basis.profileRevision().equals(binding.read().profileRevision())) mismatch();
        return binding.read();
    }

    private JsonNode evidence(EnterpriseAccess.Admission admission, TaskPlanProjection.Basis basis) throws IOException {
        String user = admission.subject().user().user_id();
        var rows = tasks.snapshot(user, basis.projectId(), basis.snapshotId());
        if (rows.size() != 1) mismatch(); var snapshot = rows.get(0);
        if (!basis.tenantId().equals(snapshot.tenantId()) || !basis.projectId().equals(snapshot.projectId())
                || !basis.snapshotId().equals(snapshot.snapshotId()) || !snapshot.manifestHash().equals(snapshot.registryHash())) mismatch();
        snapshots.require(snapshot.manifest(), snapshot.manifestHash(), basis.tenantId(), basis.projectId(), basis.snapshotId(),
                basis.profileRevision(), snapshot.authEpoch(), snapshot.governanceEpoch(), null,
                tasks.members(user, basis.projectId(), basis.snapshotId()));
        return schema.read(snapshot.manifest());
    }

    private Map<String,String> identities(Map<String,Object> values, ArtifactProtocol.PlanCandidate candidate) {
        values.put("questionKeys", candidate.questions().stream().map(ArtifactProtocol.ResearchQuestion::questionKey).toList());
        var expected = new HashSet<>(candidate.questions().stream().map(ArtifactProtocol.ResearchQuestion::questionKey).toList());
        var result = new LinkedHashMap<String,String>(); var ids = new HashSet<String>();
        for (var row : mapper.identities(values)) {
            WireValues.uuid(row.questionId()); WireValues.hash(row.keySha256());
            if (!values.get("taskId").equals(row.taskId()) || !expected.contains(row.questionKey())
                    || !DigestUtils.sha256Hex(row.questionKey()).equals(row.keySha256()) || !ids.add(row.questionId())
                    || result.putIfAbsent(row.questionKey(), row.questionId()) != null) mismatch();
        }
        return result;
    }

    private void verify(Map<String,Object> values, TaskPlanProjection.Basis basis, JsonNode evidence,
            TaskPlanProjection.Projection projection) throws IOException {
        var nodeGroups = new HashMap<String,java.util.ArrayList<String>>();
        for (var section : projection.sections()) nodeGroups.put(section.sectionId(), new java.util.ArrayList<>());
        for (var node : projection.sectionNodes()) nodeGroups.get(node.sectionId()).add(node.nodeId());
        var expectedSections = new LinkedHashMap<String,JsonNode>();
        for (var section : projection.sections()) {
            var value = object(section); value.put("template_version", basis.templateVersion());
            value.set("requirement_node_ids", json.tree(nodeGroups.get(section.sectionId()))); value.set("evidence_scope", evidence);
            expectedSections.put(section.sectionId(), value);
        }
        compare(basis, mapper.sections(values), expectedSections);
        var nodes = new LinkedHashMap<String,JsonNode>();
        for (var node : projection.sectionNodes()) {
            var value = object(node); value.put("template_version", basis.templateVersion());
            nodes.put(node.sectionId() + ":" + node.nodeId(), value);
        }
        compare(basis, mapper.sectionNodes(values), nodes);
        compare(basis, mapper.sectionRules(values), expected(projection.sectionRules(), item -> item.sectionId() + ":" + item.ruleId()));
        compare(basis, mapper.sectionDependencies(values), expected(projection.sectionDependencies(), item -> item.sectionId() + ":" + item.dependsOnSectionId()));
        compare(basis, mapper.questions(values), expected(projection.questions(), TaskPlanProjection.Question::questionId));
        var questions = index(projection.questions(), TaskPlanProjection.Question::questionId);
        var questionNodes = new LinkedHashMap<String,JsonNode>();
        for (var node : projection.questionNodes()) {
            var value = object(node); value.put("section_id", questions.get(node.questionId()).sectionId());
            questionNodes.put(node.questionId() + ":" + node.nodeId(), value);
        }
        compare(basis, mapper.questionNodes(values), questionNodes);
        compare(basis, mapper.questionRules(values), expected(projection.questionRules(), item -> item.questionId() + ":" + item.ruleId()));
        compare(basis, mapper.questionDependencies(values), expected(projection.questionDependencies(), item -> item.questionId() + ":" + item.dependsOnQuestionId()));
    }

    private void compare(TaskPlanProjection.Basis basis, List<TemplateProtocol.Projection> rows, Map<String,JsonNode> expected) throws IOException {
        if (rows.size() != expected.size()) mismatch(); var ids = new HashSet<String>();
        for (var row : rows) {
            if (!ids.add(row.id()) || !expected.containsKey(row.id()) || !expected.get(row.id()).equals(schema.read(row.content()))
                    || !basis.schemaVersion().equals(row.schemaVersion()) || !basis.manifest().equals(row.manifest())
                    || !basis.createdBy().equals(row.createdBy()) || !basis.createdAt().equals(row.createdAt())
                    || !basis.classification().equals(row.classification())) mismatch();
        }
    }

    private <T> Map<String,JsonNode> expected(List<T> rows, Function<T,String> key) throws IOException {
        var result = new LinkedHashMap<String,JsonNode>();
        for (T row : rows) if (result.putIfAbsent(key.apply(row), json.tree(row)) != null) mismatch();
        return result;
    }

    private ObjectNode object(Object value) throws IOException { return (ObjectNode) json.tree(value); }
    private static <T> Map<String,T> index(List<T> rows, Function<T,String> key) {
        var result = new HashMap<String,T>();
        for (T row : rows) if (result.putIfAbsent(key.apply(row), row) != null) mismatch();
        return result;
    }
    private static <T> void insert(Map<String,Object> values, List<T> rows, Function<Map<String,Object>,Integer> writer) {
        for (T row : rows) { values.put("item", row); one(writer.apply(values)); }
    }
    private Map<String,Object> scope(EnterpriseAccess.Admission admission, String project, String task, String plan) {
        TenantScope.requireSqlAccess(STATEMENT); Objects.requireNonNull(admission);
        WireValues.uuid(project); WireValues.uuid(task); WireValues.uuid(plan);
        if (!TenantScope.requireCurrent().tenantId().equals(admission.context().tenant_id())
                || !TenantScope.requireCurrent().subjectId().equals(admission.subject().user().user_id())) denied();
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id());
        values.put("projectId", project); values.put("taskId", task); values.put("planRevision", plan);
        TenantScope.requireSubjectParameter(STATEMENT, values); return values;
    }
    private static void one(int count) { if (count != 1) mismatch(); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
