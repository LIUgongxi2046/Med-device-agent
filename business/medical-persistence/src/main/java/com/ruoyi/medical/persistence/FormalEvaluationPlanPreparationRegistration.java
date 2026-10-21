package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.ruoyi.medical.persistence.FormalEvaluationPlanPreparationLedger.*;

/** Registers only an actual original PUT/recovery proof. No run, invocation, model result,
 * professional completion, evaluation settlement or readable historical proof is manufactured. */
public final class FormalEvaluationPlanPreparationRegistration {
    private final JdbcTemplate db;
    private final FormalEvaluationPlanPreparationLedger ledger;
    private final FormalEvaluationPlanPreparationSchema schema;
    private final DependencyManifestSchema dependencies;
    private final PermissionSchema json;

    public FormalEvaluationPlanPreparationRegistration(DataSource data, FormalEvaluationPlanPreparationLedger ledger,
            FormalEvaluationPlanPreparationSchema schema, DependencyManifestSchema dependencies, PermissionSchema json) {
        this(new JdbcTemplate(data), ledger, schema, dependencies, json);
    }

    FormalEvaluationPlanPreparationRegistration(JdbcTemplate db, FormalEvaluationPlanPreparationLedger ledger, FormalEvaluationPlanPreparationSchema schema,
            DependencyManifestSchema dependencies, PermissionSchema json) {
        this.db = db;
        db.setQueryTimeout(5);
        this.ledger = ledger;
        this.schema = schema;
        this.dependencies = dependencies;
        this.json = json;
    }
    /** Data only: consumers still need independent current088 authority and actual object GET. */
    public record Receipt(String operationId, String writeId, String attemptId, String executionId, CommonProtocol.ArtifactRef artifact,
            String objectVersion, String receiptSha256) {
    }

    Receipt register(EnterpriseAccess.Admission a, FormalEvaluationPlanPreparationAuthority.Prepared proof, FormalEvaluationPlanPreparationLedger.Attempt attempt,
            RuntimeObjectTransport.VerifiedTransfer physical) throws Exception {
        var row = ledger.require(a, attempt, proof);
        var p = proof.current(a);
        if (!p.writing()) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var c = row.columns();
        if (physical == null || physical.originalAttempt() != attempt) bad();
        var object = physical.reference();
        var intent = attempt.writeIntent();
        if (!intent.objectKey().equals(object.objectKey()) || !intent.sha256().equals(object.sha256())
                || !intent.sizeBytes().equals(object.sizeBytes()) || !intent.mediaType().equals(object.mediaType())
                || !physical.rawSha256().equals(object.sha256()) || object.objectVersion() == null || object.objectVersion().isBlank()
                || !Arrays.equals(attempt.bytes(), p.bytes())) bad();
        var old = read(a, proof, attempt);
        if (old.isPresent()) {
            if (!old.get().objectVersion().equals(object.objectVersion())) bad();
            return old.get();
        }

        String set = id(), audit = id(), event = id();
        LocalDateTime now = ledger.now();
        var manifest = new ArtifactRegistryProtocol.DependencyManifest("1.0.0", p.tenant(), p.project(), set, p.dependencies().members());
        String manifestJson = schema.canonical(manifest), manifestHash = schema.hash(manifest);
        version(p.tenant(), p.project(), set, "DEPENDENCY_ACCESS_SET", manifestHash);
        version(p.tenant(), p.project(), attempt.artifactId(), "ARTIFACT", object.sha256());
        var access = meta(row, now, false);
        access.put("access_set_id", set);
        access.put("manifest_sha256", manifestHash);
        access.put("content_sha256", manifestHash);
        ledger.insert("dependency_access_set", access);
        var m = meta(row, now, true);
        m.put("schema_manifest_sha256", dependencies.manifestSha256());
        m.put("access_set_id", set);
        m.put("manifest_json", manifestJson);
        m.put("manifest_sha256", manifestHash);
        m.put("content_sha256", manifestHash);
        m.put("member_count", manifest.members().size());
        ledger.insert("dependency_access_manifest", m);
        for (var member : manifest.members()) {
            var values = meta(row, now, false);
            values.remove("project_id");
            values.put("access_set_id", set);
            values.put("upstream_version_id", member.upstreamVersionId());
            values.put("rights_revision", member.rightsRevision());
            ledger.insert("dependency_access_member", values);
        }

        var artifact = meta(row, now, true);
        artifact.put("schema_manifest_sha256", text(row.intent(), "payload_schema_sha256"));
        artifact.put("artifact_id", attempt.artifactId());
        artifact.put("run_id", null);
        artifact.put("document_revision", null);
        artifact.put("artifact_kind", FormalEvaluationPlanPreparationSchema.KIND);
        artifact.put("object_ref", StoredObjectReferences.encode(object));
        artifact.put("object_key", object.objectKey());
        artifact.put("object_version", object.objectVersion());
        artifact.put("sha256", object.sha256());
        artifact.put("size_bytes", object.sizeBytes());
        artifact.put("media_type", object.mediaType());
        artifact.put("dependency_access_set_id", set);
        artifact.put("content_sha256", object.sha256());
        ledger.insert("artifact", artifact);
        var binding = meta(row, now, false);
        binding.put("artifact_id", attempt.artifactId());
        binding.put("resource_kind", "EVALUATION");
        binding.put("resource_id", p.evaluation());
        binding.put("schema_ref", FormalEvaluationPlanPreparationSchema.PAYLOAD_REF);
        binding.put("schema_document_sha256", text(row.intent(), "payload_schema_sha256"));
        binding.put("dependency_access_set_id", set);
        binding.put("dependency_manifest_sha256", manifestHash);
        ledger.insert("artifact_content_binding", binding);
        for (var parent : p.dependencies().roots()) ledger.insert("lineage_edge", Map.of("tenant_id", p.tenant(), "consumer_version_id",
                attempt.artifactId(), "upstream_version_id", parent.version_id(), "relation_kind", "EVALUATION_PLAN_PREPARATION",
                "actor_id", p.actor(), "created_at", now));
        var ref = reference(attempt.artifactId(), object);
        var document = json.object().put("schema_version", "1.0.0").put("kind", FormalEvaluationPlanPreparationSchema.KIND).put("operation_id",
                attempt.operationId()).put("write_id", intent.writeId()).put("attempt_id", attempt.attemptId()).put("execution_id",
                attempt.executionId()).put("evaluation_id", p.evaluation()).put("artifact_id", attempt.artifactId()).put("intent_sha256",
                text(c, "intent_sha256")).put("created_at", instant(now)).put("created_by", p.actor()).put("classification",
                p.dependencies().classification());
        document.set("object_ref", json.tree(object));
        document.set("artifact_ref", json.tree(ref));
        document.set("dependency_manifest", json.tree(manifest));
        document.set("parents", json.tree(p.dependencies().roots()));
        String receiptJson = schema.canonical(document), receiptHash = schema.hash(document);
        var registered = meta(row, now, true);
        registered.put("write_id", intent.writeId());
        registered.put("attempt_id", attempt.attemptId());
        registered.put("operation_id", attempt.operationId());
        registered.put("execution_id", attempt.executionId());
        registered.put("artifact_id", attempt.artifactId());
        registered.put("object_version", object.objectVersion());
        registered.put("payload_sha256", object.sha256());
        registered.put("receipt_json", receiptJson);
        registered.put("receipt_sha256", receiptHash);
        registered.put("audit_id", audit);
        registered.put("event_id", event);
        ledger.journal(row, "evaluation.plan_preparation.registered", audit, event, intent.writeId(), 3, receiptJson, now);
        ledger.insert("formal_evaluation_plan_preparation_registered", registered);
        changed(db.update("UPDATE operation_record SET state='COMPLETED',http_status=201,response_ref=?,revision=3,updated_at=? WHERE tenant_id=? AND operation_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? AND request_sha256=? AND state='PROCESSING' AND revision=2",
                receiptJson, now, p.tenant(), attempt.operationId(), p.actor(), FormalEvaluationPlanPreparationSchema.OPERATION,
                c.get("idempotency_key"), c.get("request_sha256")));
        proof.current(a);
        return read(a, proof, attempt).orElseThrow(() -> new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
    }

    Optional<Receipt> read(EnterpriseAccess.Admission a, FormalEvaluationPlanPreparationAuthority.Prepared proof, FormalEvaluationPlanPreparationLedger.Attempt attempt) throws Exception {
        var row=ledger.require(a,attempt,proof); var p=proof.current(a);
        var result=readVerified(row,attempt,new ReadIdentity(p.tenant(),p.actor(),p.project(),p.evaluation(),p.dependencies()));
        proof.current(a); return result;
    }
    /** Original metadata/journal verification only. The caller separately supplies current read authority. */
    Optional<Receipt> readStored(EnterpriseAccess.Admission a, FormalEvaluationPlanPreparationLedger.Attempt attempt) throws Exception {
        var row=ledger.requireStored(a,attempt);var c=row.columns();var stable=row.intent().required("stable_provenance");
        var roots=new ArrayList<PermissionProtocol.Basis>();
        for(var n:stable.required("roots"))roots.add(new PermissionProtocol.Basis(text(n,"version_id"),text(n,"content_sha256")));
        var members=new ArrayList<ArtifactRegistryProtocol.MemberRow>();
        for(var n:stable.required("members"))members.add(new ArtifactRegistryProtocol.MemberRow(text(n,"upstream_version_id"),text(n,"rights_revision")));
        var deps=new FormalEvaluationPlanPreparationDependencies.Checked(roots,members,text(stable,"classification"),stable.required("dependencies"));
        return readVerified(row,attempt,new ReadIdentity(text(c,"tenant_id"),text(c,"created_by"),text(c,"project_id"),text(c,"evaluation_id"),deps));
    }
    private record ReadIdentity(String tenant,String actor,String project,String evaluation,FormalEvaluationPlanPreparationDependencies.Checked dependencies) { }
    private Optional<Receipt> readVerified(Row row,FormalEvaluationPlanPreparationLedger.Attempt attempt,ReadIdentity p) throws Exception {
        var c = row.columns();
        var results = db.queryForList("SELECT * FROM formal_evaluation_plan_preparation_registered WHERE tenant_id=? AND write_id=? LIMIT 2",
                p.tenant(), attempt.writeIntent().writeId());
        var op = one(db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? LIMIT 2",
                p.tenant(), attempt.operationId()));
        if (results.isEmpty()) {
            if ("COMPLETED".equals(op.get("state"))) bad();
            return Optional.empty();
        }

        var registered = one(results);
        ledger.verifyMetadata(row, registered);
        var now = time(registered, "created_at");
        if (now.isBefore(time(c, "created_at")) || now.isAfter(ledger.now()) || !attempt.attemptId().equals(registered.get("attempt_id"))
                || !attempt.operationId().equals(registered.get("operation_id")) || !attempt.executionId().equals(registered.get("execution_id"))
                || !attempt.artifactId().equals(registered.get("artifact_id")) || !attempt.writeIntent().sha256().equals(registered.get("payload_sha256"))) bad();
        var document = schema.read(text(registered, "receipt_json"));
        if (!document.isObject() || document.size() != 16 || !schema.canonical(document).equals(registered.get("receipt_json"))
                || !schema.hash(document).equals(registered.get("receipt_sha256"))) bad();
        var o = document.required("object_ref");
        var object = new InterfaceProtocol.ObjectRef(text(o, "object_key"), text(o, "object_version"), text(o, "sha256"),
                text(o, "size_bytes"), text(o, "media_type"));
        var intent = attempt.writeIntent();
        if (!intent.objectKey().equals(object.objectKey()) || !intent.sha256().equals(object.sha256())
                || !intent.sizeBytes().equals(object.sizeBytes()) || !intent.mediaType().equals(object.mediaType())
                || !registered.get("object_version").equals(object.objectVersion())) bad();
        var ref = reference(attempt.artifactId(), object);
        var manifest = document.required("dependency_manifest");
        String set = text(manifest, "access_set_id"), manifestHash = schema.hash(manifest);
        var expectedManifest = new ArtifactRegistryProtocol.DependencyManifest("1.0.0", p.tenant(), p.project(), set,
                p.dependencies().members());
        if (!json.tree(expectedManifest).equals(manifest) || !json.tree(p.dependencies().roots()).equals(document.required("parents"))
                || !json.tree(ref).equals(document.required("artifact_ref"))) bad();
        if (!"1.0.0".equals(text(document, "schema_version")) || !FormalEvaluationPlanPreparationSchema.KIND.equals(text(document,
                "kind")) || !attempt.operationId().equals(text(document, "operation_id")) || !intent.writeId().equals(text(document,
                "write_id")) || !attempt.attemptId().equals(text(document, "attempt_id")) || !attempt.executionId().equals(text(document,
                "execution_id")) || !p.evaluation().equals(text(document, "evaluation_id")) || !attempt.artifactId().equals(text(document,
                "artifact_id")) || !c.get("intent_sha256").equals(text(document, "intent_sha256")) || !p.actor().equals(text(document,
                "created_by")) || !p.dependencies().classification().equals(text(document, "classification"))
                || !instant(now).equals(text(document, "created_at"))) bad();
        var artifact = one(db.queryForList("SELECT * FROM artifact WHERE tenant_id=? AND artifact_id=? LIMIT 2", p.tenant(),
                attempt.artifactId()));
        var expectedArtifact = meta(row, now, true);
        expectedArtifact.put("schema_manifest_sha256", text(row.intent(), "payload_schema_sha256"));
        expectedArtifact.put("artifact_id", attempt.artifactId());
        expectedArtifact.put("run_id", null);
        expectedArtifact.put("document_revision", null);
        expectedArtifact.put("artifact_kind", FormalEvaluationPlanPreparationSchema.KIND);
        expectedArtifact.put("object_key", object.objectKey());
        expectedArtifact.put("object_version", object.objectVersion());
        expectedArtifact.put("sha256", object.sha256());
        expectedArtifact.put("size_bytes", object.sizeBytes());
        expectedArtifact.put("media_type", object.mediaType());
        expectedArtifact.put("dependency_access_set_id", set);
        expectedArtifact.put("content_sha256", object.sha256());
        sameColumns(expectedArtifact, artifact);
        StoredObjectReferences.decode(text(artifact, "object_ref"), object);
        var expectedBinding = meta(row, now, false);
        expectedBinding.put("artifact_id", attempt.artifactId());
        expectedBinding.put("resource_kind", "EVALUATION");
        expectedBinding.put("resource_id", p.evaluation());
        expectedBinding.put("schema_ref", FormalEvaluationPlanPreparationSchema.PAYLOAD_REF);
        expectedBinding.put("schema_document_sha256", text(row.intent(), "payload_schema_sha256"));
        expectedBinding.put("dependency_access_set_id", set);
        expectedBinding.put("dependency_manifest_sha256", manifestHash);
        sameColumns(expectedBinding, one(db.queryForList("SELECT * FROM artifact_content_binding WHERE tenant_id=? AND artifact_id=? LIMIT 2",
                p.tenant(), attempt.artifactId())));
        var expectedSet = meta(row, now, false);
        expectedSet.put("access_set_id", set);
        expectedSet.put("manifest_sha256", manifestHash);
        expectedSet.put("content_sha256", manifestHash);
        sameColumns(expectedSet, one(db.queryForList("SELECT * FROM dependency_access_set WHERE tenant_id=? AND access_set_id=? LIMIT 2",
                p.tenant(), set)));
        var expectedManifestRow = meta(row, now, true);
        expectedManifestRow.put("schema_manifest_sha256", dependencies.manifestSha256());
        expectedManifestRow.put("access_set_id", set);
        expectedManifestRow.put("manifest_sha256", manifestHash);
        expectedManifestRow.put("content_sha256", manifestHash);
        expectedManifestRow.put("member_count", p.dependencies().members().size());
        var actualManifest = one(db.queryForList("SELECT * FROM dependency_access_manifest WHERE tenant_id=? AND access_set_id=? LIMIT 2",
                p.tenant(), set));
        sameColumns(expectedManifestRow, actualManifest);
        if (!manifest.equals(schema.read(text(actualManifest, "manifest_json")))) bad();
        var members = db.queryForList("SELECT * FROM dependency_access_member WHERE tenant_id=? AND access_set_id=? ORDER BY upstream_version_id LIMIT 513",
                p.tenant(), set);
        if (members.size() != p.dependencies().members().size()) bad();
        for (int i = 0; i<members.size(); i++) {
            var member = p.dependencies().members().get(i);
            var expected = meta(row, now, false);
            expected.remove("project_id");
            expected.put("access_set_id", set);
            expected.put("upstream_version_id", member.upstreamVersionId());
            expected.put("rights_revision", member.rightsRevision());
            sameColumns(expected, members.get(i));
        }

        var parents = db.queryForList("SELECT * FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? ORDER BY upstream_version_id LIMIT 513",
                p.tenant(), attempt.artifactId());
        if (parents.size() != p.dependencies().roots().size()) bad();
        for (int i = 0; i<parents.size(); i++) {
            var expected = new LinkedHashMap<String, Object>();
            expected.put("tenant_id", p.tenant());
            expected.put("consumer_version_id", attempt.artifactId());
            expected.put("upstream_version_id", p.dependencies().roots().get(i).version_id());
            expected.put("relation_kind", "EVALUATION_PLAN_PREPARATION");
            expected.put("actor_id", p.actor());
            expected.put("decision_ref", null);
            expected.put("created_at", now);
            sameColumns(expected, parents.get(i));
        }

        for (var v : List.of(Map.entry(attempt.artifactId(), object.sha256()), Map.entry(set, manifestHash))) sameColumns(Map.of("tenant_id",
                p.tenant(), "version_id", v.getKey(), "owner_project_id", p.project(), "entity_kind", v.getKey().equals(set) ? "DEPENDENCY_ACCESS_SET" : "ARTIFACT",
                "content_sha256", v.getValue()), one(db.queryForList("SELECT * FROM version_registry WHERE tenant_id=? AND version_id=? LIMIT 2",
                p.tenant(), v.getKey())));
        ledger.requireJournal(row, "evaluation.plan_preparation.registered", text(registered, "audit_id"), text(registered,
                "event_id"), intent.writeId(), 3, text(registered, "receipt_json"), now);
        if (!"COMPLETED".equals(op.get("state")) || number(op, "revision") != 3 || number(op, "http_status") != 201
                || !now.equals(time(op, "updated_at")) || !text(registered, "receipt_json").equals(schema.canonical(schema.read(text(op,
                "response_ref"))))) bad();
        return Optional.of(new Receipt(attempt.operationId(), intent.writeId(), attempt.attemptId(), attempt.executionId(),
                ref, object.objectVersion(), text(registered, "receipt_sha256")));
    }

    private Map<String, Object> meta(Row row, LocalDateTime now, boolean schemaFields) {
        var c = row.columns();
        return ledger.metadata(text(c, "tenant_id"), text(c, "project_id"), text(c, "created_by"), text(c, "classification"),
                now, schemaFields);
    }

    private void version(String tenant, String project, String id, String kind, String hash) {
        ledger.insert("version_registry", Map.of("tenant_id", tenant, "owner_project_id", project, "version_id", id,
                "entity_kind", kind, "content_sha256", hash));
    }

    static CommonProtocol.ArtifactRef reference(String artifact, InterfaceProtocol.ObjectRef object) throws Exception {
        String range = ObjectContentPaging.rangeDigest(ObjectContentPaging.objectDigest(object), 0, Long.parseLong(object.sizeBytes()));
        return new CommonProtocol.ArtifactRef(artifact, object.sha256(), FormalEvaluationPlanPreparationSchema.PAYLOAD_REF,
                new CommonProtocol.Coverage(range, range, true, null));
    }

    static void sameColumns(Map<String, Object> expected, Map<String, Object> actual) {
        for (var e : expected.entrySet()) {
            Object found = actual.get(e.getKey()), want = e.getValue();
            if (want instanceof LocalDateTime t) {
                if (!t.equals(time(actual, e.getKey()))) bad();
            } else if (want instanceof Number || "size_bytes".equals(e.getKey())) {
                if (found == null || new java.math.BigDecimal(want.toString()).compareTo(new java.math.BigDecimal(found.toString())) != 0) bad();
            } else if (!actual.containsKey(e.getKey()) || !Objects.equals(want, found)) bad();
        }
    }
}
