package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Durable immutable EVAL-only operation and single physical-attempt producer. A crash after
 * attempt insertion yields recovery-only even when no PUT reached storage. It never retries PUT. */
public final class FormalEvaluationPlanPreparationLedger {
    private final JdbcTemplate db;
    private final FormalEvaluationPlanPreparationSchema schema;
    private final PermissionSchema json;

    public FormalEvaluationPlanPreparationLedger(DataSource data, FormalEvaluationPlanPreparationSchema schema, PermissionSchema json) {
        this(new JdbcTemplate(data), schema, json);
    }

    FormalEvaluationPlanPreparationLedger(JdbcTemplate db, FormalEvaluationPlanPreparationSchema schema, PermissionSchema json) {
        this.db = db;
        db.setQueryTimeout(5);
        this.schema = schema;
        this.json = json;
    }

    record Row(Map<String, Object> columns, JsonNode intent) {
        Row {
            columns = Collections.unmodifiableMap(new LinkedHashMap<>(columns));
            intent = intent.deepCopy();
        }

        @Override
        public JsonNode intent() {
            return intent.deepCopy();
        }
    }

    public static final class Attempt implements RuntimeObjectAttempt {
        private final FormalEvaluationPlanPreparationLedger owner;
        private final Row row;
        private final String attempt;
        private final boolean prior;
        private final java.util.concurrent.atomic.AtomicBoolean claimed = new java.util.concurrent.atomic.AtomicBoolean();
        private Attempt(FormalEvaluationPlanPreparationLedger owner, Row row, String attempt, boolean prior) {
            this.owner = owner;
            this.row = row;
            this.attempt = attempt;
            this.prior = prior;
        }

        public String tenantId() {
            return text(row.columns(), "tenant_id");
        }

        public String actorId() {
            return text(row.columns(), "created_by");
        }

        public String expectedObjectKey() {
            return objectKey(tenantId(), executionId(), writeIntent().writeId(), artifactId());
        }

        public S3ObjectWriter.WriteIntent writeIntent() {
            return new S3ObjectWriter.WriteIntent(text(row.columns(), "write_id"), text(row.intent(), "object_key"),
                    text(row.columns(), "payload_sha256"), Integer.toString(bytes().length), "application/json");
        }

        public byte[] bytes() {
            return text(row.columns(), "canonical_payload").getBytes(StandardCharsets.UTF_8);
        }

        public boolean priorAttempt() {
            return prior;
        }

        public TransferMode claimTransferMode() {
            return prior || !claimed.compareAndSet(false, true) ? TransferMode.RECOVER_ONLY : TransferMode.INITIAL_WRITE;
        }

        public String executionId() {
            return text(row.columns(), "execution_id");
        }

        public String artifactId() {
            return text(row.columns(), "artifact_id");
        }

        public String operationId() {
            return text(row.columns(), "operation_id");
        }

        public String attemptId() {
            return attempt;
        }
    }

    Attempt prepare(EnterpriseAccess.Admission a, String key, FormalEvaluationPlanPreparationAuthority.Prepared proof) throws Exception {
        current(a);
        FormalEvaluationPlanPreparationSchema.key(key);
        var checked = proof.current(a);
        if (!checked.writing() || checked.bytes() == null) denied();
        if (db.queryForList("SELECT user_id FROM tenant_member WHERE tenant_id=? AND user_id=? FOR UPDATE", checked.tenant(),
                checked.actor()).size() != 1) denied();
        String hash = schema.requestHash(checked.tenant(), checked.actor(), checked.context(), checked.original().body());
        var rows = rows(a, key);
        Row row;
        if (rows.isEmpty()) {
            var other = db.queryForList("SELECT write_id FROM formal_evaluation_plan_preparation_intent WHERE tenant_id=? AND execution_id=? AND sample_id=? AND repeat_index=? AND node_kind=? LIMIT 2",
                    checked.tenant(), checked.original().request().executionId(), checked.node().cell().sampleId(), checked.node().cell().repeatIndex(),
                    checked.node().nodeKind());
            if (!other.isEmpty()) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            // A stray operation is corruption, never permission to replace its original request.
            if (!operations(a, key).isEmpty()) bad();
            row = create(a, key, hash, checked);
        } else {
            row = verify(a, one(rows));
            if (!hash.equals(row.columns().get("request_sha256"))) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }

        requireProof(row, checked);
        var attempt = attempt(a, row, true);
        proof.current(a);
        return attempt;
    }

    Attempt recover(EnterpriseAccess.Admission a, String key, FormalEvaluationPlanPreparationAuthority.Prepared proof) throws Exception {
        current(a);
        FormalEvaluationPlanPreparationSchema.key(key);
        var checked = proof.current(a);
        if (!checked.writing()) denied();
        var originals = rows(a, key);
        if (originals.isEmpty()) throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);
        var row = verify(a, one(originals));
        if (!schema.requestHash(checked.tenant(), checked.actor(), checked.context(), checked.original().body()).equals(row.columns().get("request_sha256"))) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        requireProof(row, checked);
        return attempt(a, row, false);
    }

    /** Metadata lookup only. This does not create anything or require current execution authority. */
    Optional<Attempt> findOriginal(EnterpriseAccess.Admission a, String key, FormalEvaluationPlanPreparationSchema.Original original) throws Exception {
        current(a); FormalEvaluationPlanPreparationSchema.key(key);
        var originals = rows(a, key); if (originals.isEmpty()) return Optional.empty();
        var row = verify(a, one(originals));
        if (!schema.canonical(schema.read(original.body())).equals(schema.canonical(schema.read(text(row.intent(), "original_body")))))
            throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        return Optional.of(attempt(a, row, false));
    }
    Row requireStored(EnterpriseAccess.Admission a, Attempt attempt) throws Exception {
        current(a); if(attempt==null||attempt.owner!=this||!a.actorId().equals(attempt.actorId())) denied();
        var row=verify(a,one(rows(a,text(attempt.row.columns(),"idempotency_key"))));
        if(!schema.canonical(row.columns()).equals(schema.canonical(attempt.row.columns())))bad();
        if(!attempt(a,row,false).attemptId().equals(attempt.attemptId()))bad();return row;
    }

    Optional<Attempt> find(EnterpriseAccess.Admission a, String key, FormalEvaluationPlanPreparationAuthority.Prepared proof) throws Exception {
        current(a); FormalEvaluationPlanPreparationSchema.key(key);
        var checked = proof.current(a); var originals = rows(a, key);
        if (originals.isEmpty()) return Optional.empty();
        var row = verify(a, one(originals));
        if (!schema.requestHash(checked.tenant(), checked.actor(), checked.context(), checked.original().body()).equals(row.columns().get("request_sha256")))
            throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        requireProof(row, checked); return Optional.of(attempt(a, row, false));
    }

    Row require(EnterpriseAccess.Admission a, Attempt attempt, FormalEvaluationPlanPreparationAuthority.Prepared proof) throws Exception {
        current(a);
        if (attempt == null || attempt.owner != this || !a.actorId().equals(attempt.actorId())) denied();
        var row = verify(a, one(rows(a, text(attempt.row.columns(), "idempotency_key"))));
        if (!schema.canonical(row.columns()).equals(schema.canonical(attempt.row.columns()))) bad();
        var actual = attempt(a, row, false);
        if (!actual.attemptId().equals(attempt.attemptId())) bad();
        requireProof(row, proof.current(a));
        return row;
    }
    /** The original deadline applies to every guard around the initial PUT. Recovery may only GET. */
    void requireTransfer(EnterpriseAccess.Admission a, Attempt attempt, FormalEvaluationPlanPreparationAuthority.Prepared proof) throws Exception {
        var row = require(a, attempt, proof);
        if (!attempt.priorAttempt() && !now().isBefore(time(row.intent(), "deadline"))) throw new TenantAccessException(ErrorCode.ACTION_STALE);
    }

    private Row create(EnterpriseAccess.Admission a, String key, String requestHash, FormalEvaluationPlanPreparationAuthority.Checked p) throws Exception {
        String write = id(), operation = id(), artifact = id(), audit = id(), event = id();
        var now = now();
        var intent = json.object().put("schema_version", "1.0.0").put("tenant_id", p.tenant()).put("project_id", p.project()).put("created_by",
                p.actor()).put("operation_id", operation).put("write_id", write).put("artifact_id", artifact).put("execution_id",
                p.original().request().executionId()).put("evaluation_id", p.evaluation()).put("idempotency_key", key).put("context_revision",
                p.context()).put("original_body", p.original().body()).put("request_sha256", requestHash).put("object_key",
                objectKey(p.tenant(), p.original().request().executionId(), write, artifact)).put("payload_sha256", WireValues.sha256(p.bytes())).put("payload_size",
                Integer.toString(p.bytes().length)).put("payload_schema_ref", FormalEvaluationPlanPreparationSchema.PAYLOAD_REF).put("payload_schema_sha256",
                text(p.evidence(), "plan_schema_sha256")).put("classification", p.dependencies().classification()).put("created_at",
                instant(now)).put("deadline", instant(now.plusMinutes(5))).put("audit_id", audit).put("event_id", event).put("schema_manifest_sha256",
                schema.manifest());
        intent.set("node", json.tree(p.node()));
        intent.set("context_binding", json.tree(p.contextBinding()));
        intent.set("stable_provenance", schema.read(p.stable()));
        intent.set("original_evidence", p.evidence());
        var row = metadata(p.tenant(), p.project(), p.actor(), p.dependencies().classification(), now, true);
        row.put("write_id", write);
        row.put("operation_id", operation);
        row.put("artifact_id", artifact);
        row.put("execution_id", p.original().request().executionId());
        row.put("evaluation_id", p.evaluation());
        row.put("sample_id", p.node().cell().sampleId());
        row.put("repeat_index", p.node().cell().repeatIndex());
        row.put("node_kind", p.node().nodeKind());
        row.put("context_version", p.contextBinding().version_id());
        row.put("context_sha256", p.contextBinding().content_sha256());
        row.put("source_input_version", p.node().cell().input().version_id());
        row.put("source_input_sha256", p.node().cell().input().content_sha256());
        row.put("idempotency_key", key);
        row.put("request_sha256", requestHash);
        row.put("canonical_intent", schema.canonical(intent));
        row.put("intent_sha256", schema.hash(intent));
        row.put("canonical_payload", new String(p.bytes(), StandardCharsets.UTF_8));
        row.put("payload_sha256", WireValues.sha256(p.bytes()));
        row.put("audit_id", audit);
        row.put("event_id", event);
        changed(db.update("INSERT INTO operation_record (tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,http_status,response_ref,target_id,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES (?,?,?,?,?,?,'PROCESSING',202,NULL,?,'1.0.0',?,?,?,?,1,?)",
                p.tenant(), operation, p.actor(), FormalEvaluationPlanPreparationSchema.OPERATION, key, requestHash, artifact,
                schema.manifest(), p.actor(), now, p.dependencies().classification(), now));
        var result = new Row(row, intent);
        journal(result, "evaluation.plan_preparation.prepared", audit, event, write, 1, anchor(result), now);
        insert("formal_evaluation_plan_preparation_intent", row);
        return verify(a, one(rows(a, key)));
    }

    private Attempt attempt(EnterpriseAccess.Admission a, Row row, boolean create) throws Exception {
        var c = row.columns();
        var records = db.queryForList("SELECT * FROM formal_evaluation_plan_preparation_attempt WHERE tenant_id=? AND write_id=? LIMIT 2 FOR UPDATE",
                c.get("tenant_id"), c.get("write_id"));
        var op = one(operations(a, text(c, "idempotency_key")));
        boolean prior = !records.isEmpty();
        if (!prior) {
            if (!create || number(op, "revision") != 1 || !"PROCESSING".equals(op.get("state"))) bad();
            var now = now();
            if (!now.isBefore(time(row.intent(), "deadline"))) throw new TenantAccessException(ErrorCode.ACTION_STALE);
            String attempt = id(), audit = id(), event = id();
            var record = metadata(text(c, "tenant_id"), text(c, "project_id"), text(c, "created_by"), text(c, "classification"),
                    now, true);
            record.put("write_id", c.get("write_id"));
            record.put("attempt_id", attempt);
            record.put("operation_id", c.get("operation_id"));
            record.put("intent_sha256", c.get("intent_sha256"));
            record.put("audit_id", audit);
            record.put("event_id", event);
            String evidence = attemptAnchor(row, attempt, now);
            journal(row, "evaluation.plan_preparation.attempted", audit, event, text(c, "write_id"), 2, evidence, now);
            insert("formal_evaluation_plan_preparation_attempt", record);
            changed(db.update("UPDATE operation_record SET revision=2,updated_at=? WHERE tenant_id=? AND operation_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? AND state='PROCESSING' AND revision=1",
                    now, c.get("tenant_id"), c.get("operation_id"), c.get("created_by"), FormalEvaluationPlanPreparationSchema.OPERATION,
                    c.get("idempotency_key")));
            var verified = attempt(a, row, false);
            return new Attempt(this, row, verified.attemptId(), false);
        }

        var record = one(records);
        verifyMetadata(row, record);
        var created = time(record, "created_at");
        if (!c.get("operation_id").equals(record.get("operation_id")) || !c.get("intent_sha256").equals(record.get("intent_sha256"))
                || created.isBefore(time(c, "created_at")) || !created.isBefore(time(row.intent(), "deadline"))
                || created.isAfter(now()) || number(op, "revision") < 2 || time(op, "updated_at").isBefore(created)
                || "PROCESSING".equals(op.get("state")) && !time(op, "updated_at").equals(created)) bad();
        requireJournal(row, "evaluation.plan_preparation.attempted", text(record, "audit_id"), text(record, "event_id"),
                text(c, "write_id"), 2, attemptAnchor(row, text(record, "attempt_id"), created), created);
        return new Attempt(this, row, text(record, "attempt_id"), true);
    }

    private Row verify(EnterpriseAccess.Admission a, Map<String, Object> c) throws Exception {
        var n = schema.read(text(c, "canonical_intent"));
        if (!n.isObject() || n.size() != 28 || !schema.canonical(n).equals(c.get("canonical_intent"))
                || !schema.hash(n).equals(c.get("intent_sha256"))) bad();
        var row = new Row(c, n);
        if (!a.context().tenant_id().equals(c.get("tenant_id")) || !a.actorId().equals(c.get("created_by"))) denied();
        for (String f : List.of("tenant_id", "project_id", "created_by", "operation_id", "write_id", "artifact_id",
                "execution_id", "evaluation_id", "idempotency_key", "request_sha256", "payload_sha256", "classification",
                "audit_id", "event_id", "schema_version", "schema_manifest_sha256")) if (!text(c, f).equals(text(n, f))) bad();
        if (!"1.0.0".equals(c.get("schema_version")) || !schema.manifest().equals(c.get("schema_manifest_sha256"))
                || !instant(time(c, "created_at")).equals(text(n, "created_at")) || !time(c, "created_at").plusMinutes(5).equals(time(n,
                "deadline")) || time(c, "created_at").isAfter(now())) bad();
        var original = schema.request(text(n, "original_body").getBytes(StandardCharsets.UTF_8));
        var request = original.request();
        if (!request.executionId().equals(c.get("execution_id")) || !request.start().evaluationId().equals(c.get("evaluation_id"))
                || !request.sampleId().equals(c.get("sample_id")) || request.repeatIndex() != number(c, "repeat_index")
                || !request.nodeKind().equals(c.get("node_kind"))) bad();
        if (!schema.requestHash(text(c, "tenant_id"), text(c, "created_by"), text(n, "context_revision"), original.body()).equals(c.get("request_sha256"))) bad();
        var node = n.required("node");
        if (!node.required("cell").required("sample_id").asText().equals(c.get("sample_id")) || node.required("cell").required("repeat_index").intValue() != number(c,
                "repeat_index") || !node.required("node_kind").asText().equals(c.get("node_kind"))) bad();
        if (!node.required("cell").required("input").required("version_id").asText().equals(c.get("source_input_version"))
                || !node.required("cell").required("input").required("content_sha256").asText().equals(c.get("source_input_sha256"))
                || !n.required("context_binding").required("version_id").asText().equals(c.get("context_version"))
                || !n.required("context_binding").required("content_sha256").asText().equals(c.get("context_sha256"))) bad();
        byte[] payload = text(c, "canonical_payload").getBytes(StandardCharsets.UTF_8);
        if (!WireValues.sha256(payload).equals(c.get("payload_sha256")) || !Integer.toString(payload.length).equals(text(n,
                "payload_size")) || payload.length < 1 || payload.length > FormalEvaluationPlanPreparationSchema.MAXIMUM_BYTES
                || !schema.canonical(schema.read(text(c, "canonical_payload"))).equals(c.get("canonical_payload"))
                || !objectKey(text(c, "tenant_id"), text(c, "execution_id"), text(c, "write_id"), text(c, "artifact_id")).equals(text(n,
                "object_key"))) bad();
        if (!FormalEvaluationPlanPreparationSchema.PAYLOAD_REF.equals(text(n, "payload_schema_ref")) || !c.get("payload_sha256").equals(n.required("original_evidence").required("plan_sha256").asText())
                || !FormalEvaluationPlanPreparationDependencies.stableEvidence(n.required("original_evidence")).equals(
                    FormalEvaluationPlanPreparationDependencies.coreEvidence(n.required("stable_provenance")))) bad();
        var catalogue = SchemaCatalogue.fromClasspath();
        if (!catalogue.schemaDigest("artifacts").equals(text(n, "payload_schema_sha256"))) bad();
        catalogue.bindPublished(FormalEvaluationPlanPreparationSchema.PAYLOAD_REF, text(n, "payload_schema_sha256"), payload);
        var op = one(operations(a, text(c, "idempotency_key")));
        verifyMetadata(row, op);
        if (!c.get("operation_id").equals(op.get("operation_id")) || !c.get("created_by").equals(op.get("subject_id"))
                || !FormalEvaluationPlanPreparationSchema.OPERATION.equals(op.get("operation_name")) || !c.get("request_sha256").equals(op.get("request_sha256"))
                || !c.get("artifact_id").equals(op.get("target_id")) || !time(c, "created_at").equals(time(op, "created_at"))
                || time(op, "updated_at").isBefore(time(c, "created_at")) || time(op, "updated_at").isAfter(now())
                || op.get("error_code") != null) bad();
        if ("PROCESSING".equals(op.get("state"))) {
            if (!Set.of(1L, 2L).contains(number(op, "revision")) || number(op, "http_status") != 202
                    || op.get("response_ref") != null || number(op, "revision") == 1 && !time(op, "updated_at").equals(time(c,
                    "created_at"))) bad();
        } else if (!"COMPLETED".equals(op.get("state")) || number(op, "revision") != 3 || number(op, "http_status") != 201
                || op.get("response_ref") == null) bad();
        requireJournal(row, "evaluation.plan_preparation.prepared", text(c, "audit_id"), text(c, "event_id"), text(c, "write_id"),
                1, anchor(row), time(c, "created_at"));
        return row;
    }

    private void requireProof(Row row, FormalEvaluationPlanPreparationAuthority.Checked p) throws Exception {
        var c = row.columns();
        var intent = row.intent();
        if (p.writing() && !p.evidence().required("artifact_registration").equals(intent.required("original_evidence").required("artifact_registration")))
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        if (!p.tenant().equals(c.get("tenant_id")) || !p.actor().equals(c.get("created_by")) || !p.context().equals(text(intent,
                "context_revision")) || !p.project().equals(c.get("project_id")) || !schema.canonical(schema.read(p.original().body())).equals(schema.canonical(schema.read(text(intent,
                "original_body")))) || !Arrays.equals(p.bytes(), text(c, "canonical_payload").getBytes(StandardCharsets.UTF_8))
                || !p.stable().equals(schema.canonical(intent.required("stable_provenance"))) || !p.dependencies().classification().equals(c.get("classification"))
                || !json.tree(p.node()).equals(intent.required("node")) || !json.tree(p.contextBinding()).equals(intent.required("context_binding"))) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
    }

    private List<Map<String, Object>> rows(EnterpriseAccess.Admission a, String key) {
        return db.queryForList("SELECT * FROM formal_evaluation_plan_preparation_intent WHERE tenant_id=? AND created_by=? AND idempotency_key=? LIMIT 2 FOR UPDATE",
                a.context().tenant_id(), a.actorId(), key);
    }

    private List<Map<String, Object>> operations(EnterpriseAccess.Admission a, String key) {
        return db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? LIMIT 2 FOR UPDATE",
                a.context().tenant_id(), a.actorId(), FormalEvaluationPlanPreparationSchema.OPERATION, key);
    }

    private String anchor(Row row) throws Exception {
        return schema.canonical(Map.of("schema_version", "1.0.0", "kind", "EVALUATION_PLAN_PREPARATION_PREPARED", "operation_id",
                row.columns().get("operation_id"), "write_id", row.columns().get("write_id"), "intent_sha256", row.columns().get("intent_sha256"),
                "payload_sha256", row.columns().get("payload_sha256")));
    }

    private String attemptAnchor(Row row, String attempt, LocalDateTime at) throws Exception {
        var event = (ObjectNode) schema.read(anchor(row));
        event.put("kind", "EVALUATION_PLAN_PREPARATION_ATTEMPTED").put("attempt_id", attempt).put("attempted_at", instant(at));
        return schema.canonical(event);
    }

    void journal(Row row, String action, String audit, String event, String aggregate, int revision, String body, LocalDateTime now) {
        var c = row.columns();
        changed(db.update("INSERT INTO audit_event (tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",
                c.get("tenant_id"), c.get("project_id"), audit, c.get("created_by"), action, body, c.get("operation_id"),
                c.get("write_id"), schema.manifest(), c.get("created_by"), now, c.get("classification")));
        changed(db.update("INSERT INTO outbox_event (tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES (?,?,?,?,?,?,?)",
                c.get("tenant_id"), event, aggregate, revision, action, body, now));
    }

    void requireJournal(Row row, String action, String audit, String event, String aggregate, int revision, String body,
            LocalDateTime now) throws Exception {
        var c = row.columns();
        var a = one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2", c.get("tenant_id"),
                audit));
        var e = one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2", c.get("tenant_id"),
                event));
        verifyMetadata(row, a);
        if (!c.get("project_id").equals(a.get("project_id")) || !c.get("created_by").equals(a.get("subject_id"))
                || !action.equals(a.get("action")) || !"SUCCESS".equals(a.get("result")) || a.get("reason_code") != null
                || !c.get("operation_id").equals(a.get("request_id")) || !c.get("write_id").equals(a.get("trace_id"))
                || !now.equals(time(a, "created_at")) || !body.equals(schema.canonical(schema.read(text(a, "resource_ref"))))) bad();
        if (!aggregate.equals(e.get("aggregate_id")) || number(e, "aggregate_revision") != revision || !action.equals(e.get("event_type"))
                || !now.equals(time(e, "occurred_at")) || !body.equals(schema.canonical(schema.read(text(e, "payload"))))) bad();
    }

    void verifyMetadata(Row row, Map<String, Object> actual) {
        for (String f : List.of("tenant_id", "created_by", "classification", "schema_version", "schema_manifest_sha256")) if (!row.columns().get(f).equals(actual.get(f))) bad();
        if (actual.containsKey("project_id") && !row.columns().get("project_id").equals(actual.get("project_id"))) bad();
    }

    Map<String, Object> metadata(String tenant, String project, String actor, String classification, LocalDateTime now,
            boolean schemaFields) {
        var m = new LinkedHashMap<String, Object>();
        m.put("tenant_id", tenant);
        m.put("project_id", project);
        m.put("created_by", actor);
        m.put("created_at", now);
        m.put("classification", classification);
        if (schemaFields) {
            m.put("schema_version", "1.0.0");
            m.put("schema_manifest_sha256", schema.manifest());
        }

        return m;
    }

    void insert(String table, Map<String, Object> m) {
        var keys = new ArrayList<>(m.keySet());
        // Map.of iteration order can vary between JVMs. Keep SQL and its bound values
        // in the same stable column order for reproducible registration evidence.
        Collections.sort(keys);
        String columns = String.join(",", keys);
        String placeholders = String.join(",", Collections.nCopies(keys.size(), "?"));
        Object[] values = keys.stream().map(m::get).toArray();
        changed(db.update("INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")", values));
    }

    LocalDateTime now() {
        return one(db.query("SELECT UTC_TIMESTAMP(6)", (r, n) -> r.getTimestamp(1).toLocalDateTime()));
    }

    static String objectKey(String tenant, String execution, String write, String artifact) {
        for (String id : List.of(tenant, execution, write, artifact)) WireValues.uuid(id);
        return "evaluation-plan/" + tenant + "/" + execution + "/" + write + "/" + artifact;
    }

    static String text(Map<String, Object> v, String k) {
        if (v.get(k) instanceof String s) return s;
        bad();
        return null;
    }

    static String text(JsonNode n, String k) {
        var v = n.required(k);
        if (v.isTextual()) return v.textValue();
        bad();
        return null;
    }

    static LocalDateTime time(Map<String, Object> v, String k) {
        var x = v.get(k);
        if (x instanceof LocalDateTime t) return t;
        if (x instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        bad();
        return null;
    }

    static LocalDateTime time(JsonNode n, String k) {
        return LocalDateTime.ofInstant(Instant.parse(text(n, k)), ZoneOffset.UTC);
    }

    static long number(Map<String, Object> m, String k) {
        if (m.get(k) instanceof Number n) return new java.math.BigDecimal(n.toString()).longValueExact();
        bad();
        return 0;
    }

    static String instant(LocalDateTime t) {
        return t.toInstant(ZoneOffset.UTC).toString();
    }

    static String id() {
        return UUID.randomUUID().toString();
    }

    static <T > T one(List<T> v) {
        if (v.size() != 1 || v.get(0) == null) bad();
        return v.get(0);
    }

    static void changed(int n) {
        if (n != 1) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
    }

    static void current(EnterpriseAccess.Admission a) {
        FormalEvaluationExecutionAdmission.current(a);
    }

    static void bad() {
        throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    }

    static void denied() {
        throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
}
