package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.BudgetMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TenantWatermarkMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class BudgetService {
    private final EnterpriseAccess access;
    private final BudgetMapper mapper;
    private final PermissionMapper permissions;
    private final TenantWatermarkMapper watermarks;
    private final PermissionSchema json;
    private final BudgetSchema schema;
    private final BudgetHistorySchema historySchema;
    private final IdentityDirectoryPolicy policy;
    private final JdbcTemplate jdbc;

    public BudgetService(EnterpriseAccess access, BudgetMapper mapper, PermissionMapper permissions, TenantWatermarkMapper watermarks, PermissionSchema json,
            BudgetSchema schema, BudgetHistorySchema historySchema, IdentityDirectoryPolicy policy, @Qualifier("masterDataSource") DataSource master) {
        this.access = access; this.mapper = mapper; this.permissions = permissions; this.watermarks = watermarks; this.json = json;
        this.schema = schema; this.historySchema = historySchema; this.policy = policy; jdbc = new JdbcTemplate(master);
    }

    public JsonNode list(ApplicationSessionIdentity.Verified subject, String context, BudgetProtocol.Query query) {
        return access.execute(subject, admission -> checked(() -> {
            current(admission, context); var values = values(admission); var grants = mapper.grants(values);
            var readers = eligible(admission, grants, "budget.read", "BUDGET_READING");
            if (readers.isEmpty()) denied(ErrorCode.ACCESS_DENIED);
            var scopes = readers.stream().map(BudgetService::scope).toList();
            if (query.project() != null && scopes.stream().noneMatch(scope -> scope.permits(query.project(), null))) {
                // 指定单一预算的许可仍可用于对应项目列表，SQL继续限制预算身份。
                if (scopes.stream().noneMatch(scope -> scope.kind().equals("BUDGET")
                        && (scope.projectId() == null || scope.projectId().equals(query.project())))) denied(ErrorCode.ACCESS_DENIED);
            }
            values.put("scopes", scopes); values.put("limit", query.limit()+1); values.put("after", query.after()); values.put("filterProject", query.project());
            var ids = mapper.accounts(values); var count = Objects.requireNonNull(mapper.count(values));
            var rows = new ArrayList<BudgetProtocol.Account>();
            for (String id : ids.subList(0, Math.min(query.limit(), ids.size()))) rows.add(account(values, id, false));
            for (String project : rows.stream().map(BudgetProtocol.Account::projectId).filter(Objects::nonNull).distinct().sorted().toList()) project(project, false);
            var items = json.object().arrayNode();
            for (var discovered : rows) {
                var row = account(values, discovered.budgetId(), true);
                if (!Objects.equals(row.projectId(), discovered.projectId())) throw new IllegalStateException("BUDGET_PROJECT_CHANGED");
                require(admission, grants, row.projectId(), row.budgetId(), "budget.read", "BUDGET_READING");
                items.add(item(values, row, admission, grants));
            }
            var result = json.object(); result.put("schema_version", "1.0.0"); result.set("budgets", items); result.put("total", count.toString());
            result.set("next_after", json.tree(ids.size() > query.limit() ? ids.get(query.limit()-1) : null));
            return schema.envelope("Listing", result, UUID.randomUUID().toString(), null);
        }));
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject, String context, String id) {
        return access.execute(subject, admission -> checked(() -> {
            current(admission, context); var values = values(admission); var grants = mapper.grants(values);
            if (eligible(admission, grants, "budget.read", "BUDGET_READING").isEmpty()) denied(ErrorCode.ACCESS_DENIED);
            var row = load(values, id, false);
            require(admission, grants, row.projectId(), id, "budget.read", "BUDGET_READING");
            var result = item(values, row, admission, grants);
            return schema.envelope("Account", result, UUID.randomUUID().toString(), row.revision().toString());
        }));
    }

    public JsonNode history(ApplicationSessionIdentity.Verified subject, String context, String id, BudgetProtocol.Query query) {
        return access.execute(subject, admission -> checked(() -> {
            current(admission, context); var values = values(admission); var grants = mapper.grants(values);
            if (eligible(admission, grants, "budget.read", "BUDGET_READING").isEmpty()) denied(ErrorCode.ACCESS_DENIED);
            var account = load(values, id, false);
            require(admission, grants, account.projectId(), id, "budget.read", "BUDGET_READING");
            approval(values, account);
            values.put("limit", query.limit()+1); values.put("after", query.after());
            var ids = mapper.history(values); var count = Objects.requireNonNull(mapper.historyCount(values));
            PermissionSchema.revision(count.toString());
            if (count.compareTo(BigInteger.valueOf(ids.size())) < 0 || ids.size() > query.limit()+1
                    || ids.stream().distinct().count() != ids.size()) throw new IllegalStateException("BUDGET_HISTORY_COUNT_INVALID");
            var entries = json.object().arrayNode(); String previous = query.after();
            for (String version : ids) {
                if (previous != null && version.compareTo(previous) <= 0) throw new IllegalStateException("BUDGET_HISTORY_ORDER_INVALID");
                previous = version;
            }
            for (String version : ids.subList(0, Math.min(query.limit(), ids.size()))) entries.add(historyItem(values, account, version));
            var result = historyIdentity(account); result.set("approvals", entries); result.put("total", count.toString());
            result.set("next_after", json.tree(ids.size() > query.limit() ? ids.get(query.limit()-1) : null));
            return historySchema.envelope("Listing", result);
        }));
    }

    public JsonNode historyRevision(ApplicationSessionIdentity.Verified subject, String context, String id, String version) {
        return access.execute(subject, admission -> checked(() -> {
            current(admission, context); var values = values(admission); var grants = mapper.grants(values);
            if (eligible(admission, grants, "budget.read", "BUDGET_READING").isEmpty()) denied(ErrorCode.ACCESS_DENIED);
            var account = load(values, id, false);
            require(admission, grants, account.projectId(), id, "budget.read", "BUDGET_READING");
            approval(values, account);
            return historySchema.envelope("Entry", historyItem(values, account, version));
        }));
    }

    private com.fasterxml.jackson.databind.node.ObjectNode historyIdentity(BudgetProtocol.Account account) {
        var value = json.object(); value.put("schema_version", "1.0.0"); value.put("tenant_id", account.tenantId());
        value.set("project_id", json.tree(account.projectId())); value.put("budget_id", account.budgetId());
        value.put("current_budget_revision", account.approvalId()); value.put("account_revision", account.revision().toString()); return value;
    }
    private JsonNode historyItem(Map<String,Object> values, BudgetProtocol.Account account, String version) throws IOException {
        var row = approval(values, account, version, ErrorCode.OBJECT_UNAVAILABLE); var value = historyIdentity(account);
        value.put("budget_revision", version); value.put("currency", row.currency()); value.set("approval", schema.read(row.definition()));
        value.put("content_sha256", row.sha256()); value.put("approver_id", row.approverId()); value.put("approved_at", time(row.createdAt()));
        value.put("is_current", account.approvalId().equals(version)); value.set("allowed_actions", json.tree(List.of("budget.read")));
        historySchema.verify("Entry", value); return value;
    }

    public JsonNode create(ApplicationSessionIdentity.Verified subject, String context, String key, byte[] bytes) {
        return access.execute(subject, admission -> checked(() -> {
            var values = values(admission); var grants = mapper.grants(values);
            if (eligible(admission, grants, "budget.approve", "BUDGET_APPROVAL").isEmpty()) denied(ErrorCode.ACCESS_DENIED);
            var request = schema.create(bytes);
            var witness = require(admission, grants, request.project_id(), null, "budget.approve", "BUDGET_APPROVAL");
            request(values, admission, "BD-02", "/api/budgets", context, key, request);
            var previous = mapper.currentOperation(values);
            if (!previous.isEmpty()) return replay(admission, values, one(previous, ErrorCode.IDEMPOTENCY_CONFLICT), "CREATE", null, grants);
            current(admission, context); project(request.project_id(), true); basis(request.approval_basis(), request.project_id());
            deadline(request.limits(), (LocalDateTime) values.get("now"));
            String id = UUID.randomUUID().toString(); values.put("budgetId", id); values.put("projectId", request.project_id()); values.put("currency", request.currency());
            prepare(values, admission, request.limits(), request.approval_basis(), request.reason(), witness, null);
            requireOne(mapper.insertAccount(values)); requireOne(mapper.insertVersion(values)); requireOne(mapper.insertApproval(values)); requireOne(mapper.initializeApproval(values));
            var stored = account(values, id, true); var actual = approval(values, stored);
            if (!stored.revision().equals(BigInteger.ONE) || stored.spent().signum() != 0 || stored.reserved().signum() != 0
                    || stored.spentTokens().signum() != 0 || stored.reservedTokens().signum() != 0
                    || stored.spentCalls().signum() != 0 || stored.reservedCalls().signum() != 0
                    || !stored.createdBy().equals(subject.user().user_id()) || !stored.createdAt().equals(values.get("now"))) {
                throw new IllegalStateException("BUDGET_CREATE_READ_MISMATCH");
            }
            return complete(admission, values, "CREATE", stored, actual, json.tree(null), json.tree(request));
        }));
    }

    public JsonNode decide(ApplicationSessionIdentity.Verified subject, String context, String key, String id, byte[] bytes) {
        return access.execute(subject, admission -> checked(() -> {
            var values = values(admission); var grants = mapper.grants(values);
            if (eligible(admission, grants, "budget.read", "BUDGET_READING").isEmpty()
                    || eligible(admission, grants, "budget.approve", "BUDGET_APPROVAL").isEmpty()) denied(ErrorCode.ACCESS_DENIED);
            var before = load(values, id, false);
            require(admission, grants, before.projectId(), id, "budget.read", "BUDGET_READING");
            var witness = require(admission, grants, before.projectId(), id, "budget.approve", "BUDGET_APPROVAL");
            var request = schema.decide(bytes); request(values, admission, "BD-04", "/api/budgets/" + id + "/decisions", context, key, request);
            var previous = mapper.currentOperation(values);
            if (!previous.isEmpty()) return replay(admission, values, one(previous, ErrorCode.IDEMPOTENCY_CONFLICT), "APPROVE", id, grants);
            current(admission, context); project(before.projectId(), true); var beforeValue = item(values, before, admission, grants);
            if (!before.revision().toString().equals(request.expected_revision()) || !before.approvalId().equals(request.based_on_budget_revision())) denied(ErrorCode.REVISION_CONFLICT);
            if (!before.currency().equals(request.currency())) denied(ErrorCode.CONTENT_MISMATCH);
            PermissionSchema.next(request.expected_revision()); basis(request.approval_basis(), before.projectId());
            deadline(request.limits(), (LocalDateTime) values.get("now"));
            requireLimit(request.limits().amount(), before.spent(), before.reserved());
            requireCount(request.limits().tokens(), before.spentTokens(), before.reservedTokens());
            requireCount(request.limits().calls(), before.spentCalls(), before.reservedCalls());
            values.put("expected", request.expected_revision()); values.put("oldApproval", before.approvalId());
            values.put("projectId", before.projectId()); values.put("currency", before.currency());
            prepare(values, admission, request.limits(), request.approval_basis(), request.reason(), witness, before.approvalId());
            requireOne(mapper.insertVersion(values)); requireOne(mapper.insertApproval(values)); requireOne(mapper.advanceApproval(values));
            var stored = account(values, id, true); var actual = approval(values, stored);
            if (!stored.revision().toString().equals(PermissionSchema.next(request.expected_revision()))
                    || stored.spent().compareTo(before.spent()) != 0 || stored.reserved().compareTo(before.reserved()) != 0
                    || !stored.spentTokens().equals(before.spentTokens()) || !stored.reservedTokens().equals(before.reservedTokens())
                    || !stored.spentCalls().equals(before.spentCalls()) || !stored.reservedCalls().equals(before.reservedCalls())) {
                throw new IllegalStateException("BUDGET_APPROVAL_READ_MISMATCH");
            }
            return complete(admission, values, "APPROVE", stored, actual, beforeValue, json.tree(request));
        }));
    }

    private BudgetProtocol.Account load(Map<String,Object> values, String id, boolean writing) {
        var discovered = account(values, id, false); project(discovered.projectId(), writing);
        var row = account(values, id, true);
        if (!Objects.equals(row.projectId(), discovered.projectId())) throw new IllegalStateException("BUDGET_PROJECT_CHANGED"); return row;
    }
    private BudgetProtocol.Account account(Map<String,Object> values, String id, boolean lock) {
        values.put("budgetId", id); values.put("lock", lock); var row = one(mapper.account(values), ErrorCode.OBJECT_UNAVAILABLE);
        if (!row.tenantId().equals(TenantScope.requireCurrent().tenantId()) || !row.budgetId().equals(id)
                || row.approvalId() == null || row.revision().signum() <= 0 || !row.classification().equals(policy.classification())) {
            throw new IllegalStateException("BUDGET_ACCOUNT_INVALID");
        }
        BudgetSchema.money(row.spent().toPlainString()); BudgetSchema.money(row.reserved().toPlainString());
        for (var count : List.of(row.spentTokens(), row.reservedTokens(), row.spentCalls(), row.reservedCalls(), row.revision())) PermissionSchema.revision(count.toString());
        return row;
    }
    private BudgetProtocol.Revision approval(Map<String,Object> values, BudgetProtocol.Account account) throws IOException {
        return approval(values, account, account.approvalId(), ErrorCode.CONTENT_MISMATCH);
    }
    private BudgetProtocol.Revision approval(Map<String,Object> values, BudgetProtocol.Account account, String version, ErrorCode missing) throws IOException {
        values.put("approvalId", version); values.put("projectId", account.projectId());
        var row = one(mapper.approval(values), missing); var definition = schema.approval(row.definition());
        String actualHash = DigestUtils.sha256Hex(json.canonicalObject(definition)); String registry = one(mapper.registry(values), ErrorCode.CONTENT_MISMATCH);
        if (!version.equals(row.approvalId()) || version.equals(definition.based_on_budget_revision()) || !Objects.equals(account.projectId(), row.projectId())
                || !account.currency().equals(row.currency()) || !account.budgetId().equals(row.budgetId())
                || !row.schemaVersion().equals("1.0.0") || !row.manifest().equals(schema.manifestSha256())
                || !row.sha256().equals(actualHash) || !registry.equals(actualHash) || !row.classification().equals(account.classification())
                || !definition.tenant_id().equals(account.tenantId()) || !Objects.equals(definition.project_id(), account.projectId())
                || !definition.budget_id().equals(account.budgetId()) || !definition.currency().equals(account.currency())
                || !definition.approver_id().equals(row.approverId()) || !definition.reason().equals(row.reason()) || !row.createdBy().equals(row.approverId())) denied(ErrorCode.CONTENT_MISMATCH);
        basis(definition.approval_basis(), account.projectId()); return row;
    }
    private JsonNode item(Map<String,Object> values, BudgetProtocol.Account account, EnterpriseAccess.Admission admission,
            List<PermissionProtocol.GrantRow> grants) throws IOException {
        var row = approval(values, account); var value = json.object(); value.put("schema_version", "1.0.0"); value.put("tenant_id", account.tenantId());
        value.set("project_id", json.tree(account.projectId())); value.put("budget_id", account.budgetId()); value.put("budget_revision", account.approvalId());
        value.put("currency", account.currency()); value.put("spent", account.spent().toPlainString()); value.put("reserved", account.reserved().toPlainString());
        value.put("spent_tokens", account.spentTokens().toString()); value.put("reserved_tokens", account.reservedTokens().toString());
        value.put("spent_calls", account.spentCalls().toString()); value.put("reserved_calls", account.reservedCalls().toString());
        value.set("approval", schema.read(row.definition())); value.put("content_sha256", row.sha256()); value.put("approver_id", row.approverId());
        value.put("approved_at", time(row.createdAt())); value.put("created_by", account.createdBy()); value.put("created_at", time(account.createdAt()));
        value.put("updated_at", time(account.updatedAt())); value.put("revision", account.revision().toString()); var actions = json.object().arrayNode();
        for (String action : List.of("budget.read", "budget.approve")) {
            String purpose = action.equals("budget.read") ? "BUDGET_READING" : "BUDGET_APPROVAL";
            if (eligible(admission, grants, action, purpose).stream().map(BudgetService::scope).anyMatch(scope -> scope.permits(account.projectId(), account.budgetId()))) actions.add(action);
        }
        value.set("allowed_actions", actions); schema.verify("Account", value); return value;
    }

    private void prepare(Map<String,Object> values, EnterpriseAccess.Admission admission, BudgetProtocol.Limits limits,
            PermissionProtocol.Basis basis, String reason, PermissionProtocol.GrantRow witness, String oldApproval) throws IOException {
        var content = new BudgetProtocol.Approval("1.0.0", admission.context().tenant_id(), (String) values.get("projectId"),
                (String) values.get("budgetId"), (String) values.get("currency"), limits, basis,
                admission.subject().user().user_id(), reason, witness.grantId(), witness.revision().toString(), oldApproval);
        schema.verify("Approval", json.tree(content)); values.put("approvalId", UUID.randomUUID().toString());
        values.put("definition", json.canonicalObject(content)); values.put("contentHash", DigestUtils.sha256Hex((String) values.get("definition")));
        values.put("reason", reason);
    }
    private JsonNode complete(EnterpriseAccess.Admission admission, Map<String,Object> values, String decision,
            BudgetProtocol.Account stored, BudgetProtocol.Revision approval, JsonNode before, JsonNode request) throws IOException {
        if (!stored.approvalId().equals(values.get("approvalId")) || !approval.sha256().equals(values.get("contentHash"))
                || !json.canonical(schema.read(approval.definition())).equals(values.get("definition"))) throw new IllegalStateException("BUDGET_DEFINITION_READ_MISMATCH");
        values.put("governanceEpoch", admission.context().governance_epoch()); values.put("authEpoch", admission.context().auth_epoch());
        String governance = PermissionSchema.next(admission.context().governance_epoch()), context = PermissionSchema.next(admission.context().context_revision());
        requireOne(mapper.advanceGovernance(values));
        requireOne(jdbc.update("""
                UPDATE platform_session_context SET context_revision=?,governance_epoch=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
                WHERE context_id=? AND session_id=? AND user_id=? AND tenant_id=? AND context_revision=? AND auth_epoch=? AND governance_epoch=?
                """, context, governance, admission.contextId(), admission.subject().sessionId(), admission.subject().user().user_id(), admission.context().tenant_id(),
                admission.context().context_revision(), admission.context().auth_epoch(), admission.context().governance_epoch()));
        var epochs = Objects.requireNonNull(watermarks.lockCurrentTenant());
        var contexts = jdbc.query("""
                SELECT tenant_id,context_revision,auth_epoch,governance_epoch FROM platform_session_context
                WHERE context_id=? AND session_id=? AND user_id=? AND tenant_id=?
                """, (row, ordinal) -> new BusinessSessionProtocol.Context(row.getString(1), row.getString(2), row.getString(3), row.getString(4)),
                admission.contextId(), admission.subject().sessionId(), admission.subject().user().user_id(), admission.context().tenant_id());
        if (!epochs.authEpoch().toString().equals(admission.context().auth_epoch()) || !epochs.governanceEpoch().toString().equals(governance)
                || contexts.size() != 1 || !contexts.get(0).equals(new BusinessSessionProtocol.Context(admission.context().tenant_id(),
                        context, admission.context().auth_epoch(), governance))) throw new IllegalStateException("BUDGET_CONTEXT_READ_MISMATCH");
        var current = json.object(); current.put("tenant_id", admission.context().tenant_id()); current.put("context_revision", context);
        current.put("auth_epoch", admission.context().auth_epoch()); current.put("governance_epoch", governance);
        String operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString(); var receipt = json.object();
        receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", operationId); receipt.put("decision", decision);
        receipt.put("tenant_id", stored.tenantId()); receipt.set("project_id", json.tree(stored.projectId())); receipt.put("budget_id", stored.budgetId());
        receipt.put("budget_revision", stored.approvalId()); receipt.put("revision", stored.revision().toString()); receipt.put("currency", stored.currency());
        receipt.put("content_sha256", approval.sha256()); receipt.set("current_context", current);
        var response = schema.envelope("Receipt", receipt, requestId, stored.revision().toString());
        values.put("operationId", operationId); values.put("httpStatus", decision.equals("CREATE") ? 201 : 200); values.put("response", json.canonical(response));
        requireOne(mapper.saveOperation(values));
        var audit = json.object(); audit.put("schema_version", "1.0.0"); audit.put("operation_id", operationId); audit.set("request", request);
        audit.set("before", before); audit.set("after", receipt); audit.set("approval", schema.read(approval.definition()));
        String event = decision.equals("CREATE") ? "budget.created" : "budget.approved";
        String auditId = UUID.randomUUID().toString(), eventId = UUID.randomUUID().toString();
        requireOne(permissions.saveAudit(admission.subject().user().user_id(), stored.projectId(), auditId, event,
                json.canonical(audit), requestId, schema.manifestSha256(), policy.classification()));
        var payload = receipt.deepCopy(); payload.set("approval", schema.read(approval.definition()));
        requireOne(permissions.saveEvent(admission.subject().user().user_id(), eventId, stored.budgetId(), stored.revision().toString(), event, json.canonical(payload)));
        values.put("auditId", auditId); values.put("eventId", eventId); values.put("eventType", event); values.put("requestId", requestId); values.put("resulting", stored.revision().toString());
        if (!schema.read(one(mapper.audit(values), ErrorCode.CONTENT_MISMATCH)).equals(audit)
                || !schema.read(one(mapper.event(values), ErrorCode.CONTENT_MISMATCH)).equals(payload)) throw new IllegalStateException("BUDGET_AUDIT_EVENT_READ_MISMATCH");
        var operation = one(mapper.currentOperation(values), ErrorCode.CONTENT_MISMATCH);
        if (!operation.operationId().equals(operationId) || !operation.requestSha256().equals(values.get("requestHash"))
                || !operation.targetId().equals(stored.budgetId()) || !schema.receipt(operation.responseRef()).equals(response)) throw new IllegalStateException("BUDGET_OPERATION_READ_MISMATCH");
        return response;
    }

    private JsonNode replay(EnterpriseAccess.Admission admission, Map<String,Object> values, SelectionOperationRow saved,
            String decision, String id, List<PermissionProtocol.GrantRow> grants) throws IOException {
        if (!saved.requestSha256().equals(values.get("requestHash"))) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!saved.state().equals("COMPLETED") || !saved.httpStatus().equals(decision.equals("CREATE") ? 201 : 200)
                || !saved.schemaVersion().equals("1.0.0") || !saved.schemaManifestSha256().equals(schema.manifestSha256())) throw new IllegalStateException("BUDGET_OPERATION_INVALID");
        var response = schema.receipt(saved.responseRef()); var receipt = response.required("data");
        if (!saved.operationId().equals(receipt.required("operation_id").textValue()) || !saved.targetId().equals(receipt.required("budget_id").textValue())
                || !decision.equals(receipt.required("decision").textValue()) || !admission.context().tenant_id().equals(receipt.required("tenant_id").textValue())
                || id != null && !id.equals(saved.targetId())) throw new IllegalStateException("BUDGET_OPERATION_INVALID");
        var row = load(values, saved.targetId(), false);
        require(admission, grants, row.projectId(), row.budgetId(), "budget.approve", "BUDGET_APPROVAL");
        if (decision.equals("APPROVE")) require(admission, grants, row.projectId(), row.budgetId(), "budget.read", "BUDGET_READING");
        var approval = approval(values, row);
        if (!row.revision().toString().equals(receipt.required("revision").textValue()) || !row.approvalId().equals(receipt.required("budget_revision").textValue())
                || !row.currency().equals(receipt.required("currency").textValue()) || !json.tree(row.projectId()).equals(receipt.required("project_id"))
                || !approval.sha256().equals(receipt.required("content_sha256").textValue())) denied(ErrorCode.SCOPE_STALE);
        return response;
    }

    private List<PermissionProtocol.GrantRow> eligible(EnterpriseAccess.Admission admission, List<PermissionProtocol.GrantRow> grants,
            String action, String purpose) throws IOException {
        var result = new ArrayList<PermissionProtocol.GrantRow>(); String tenant = admission.context().tenant_id();
        for (var grant : grants) {
            var condition = json.condition(grant.condition());
            if (!grant.status().equals("ACTIVE") || !grant.action().equals(action) || !grant.subjectId().equals(admission.subject().user().user_id())
                    || !grant.schemaVersion().equals("1.0.0") || !grant.manifest().equals(json.manifestSha256())
                    || !condition.identity_modes().contains(admission.subject().binding().view().mode())
                    || condition.purposes() != null && !condition.purposes().contains(purpose)) continue;
            boolean supported = switch (grant.resourceKind()) {
                case "ENTERPRISE" -> grant.projectId() == null && tenant.equals(grant.resourceId());
                case "PROJECT" -> grant.projectId() != null && grant.projectId().equals(grant.resourceId());
                case "BUDGET" -> true;
                default -> false;
            };
            if (supported) result.add(grant);
        }
        return List.copyOf(result);
    }
    private PermissionProtocol.GrantRow require(EnterpriseAccess.Admission admission, List<PermissionProtocol.GrantRow> grants,
            String project, String budget, String action, String purpose) throws IOException {
        for (var grant : eligible(admission, grants, action, purpose)) if (scope(grant).permits(project, budget)) return grant;
        throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
    private static BudgetProtocol.Scope scope(PermissionProtocol.GrantRow grant) { return new BudgetProtocol.Scope(grant.projectId(), grant.resourceKind(), grant.resourceId()); }
    private void project(String projectId, boolean writing) {
        if (projectId == null) return;
        String status = one(permissions.projectStatus(projectId), ErrorCode.OBJECT_UNAVAILABLE);
        if (writing && !List.of("PENDING", "ACTIVE").contains(status)) denied(ErrorCode.ACCESS_DENIED);
    }
    private void basis(PermissionProtocol.Basis basis, String project) {
        var row = one(permissions.basis(basis.version_id()), ErrorCode.CONTENT_MISMATCH);
        if (!row.sha256().equals(basis.content_sha256()) || row.ownerProjectId() != null && !row.ownerProjectId().equals(project)) denied(ErrorCode.CONTENT_MISMATCH);
    }
    private static void deadline(BudgetProtocol.Limits limits, LocalDateTime now) {
        if (!PermissionAuthority.time(limits.deadline_at()).isAfter(now)) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }
    static void requireLimit(String limit, BigDecimal spent, BigDecimal reserved) {
        if (BudgetSchema.money(limit).compareTo(spent.add(reserved)) < 0) denied(ErrorCode.BUDGET_EXCEEDED);
    }
    static void requireCount(String limit, BigInteger spent, BigInteger reserved) {
        if (PermissionSchema.revision(limit).compareTo(spent.add(reserved)) < 0) denied(ErrorCode.BUDGET_EXCEEDED);
    }
    private Map<String,Object> values(EnterpriseAccess.Admission admission) {
        var result = new HashMap<String,Object>(); result.put("userId", admission.subject().user().user_id());
        result.put("classification", policy.classification()); result.put("manifest", schema.manifestSha256());
        result.put("now", Objects.requireNonNull(permissions.databaseTime())); return result;
    }
    private void request(Map<String,Object> values, EnterpriseAccess.Admission admission, String operation, String path, String context, String key, Object request) throws IOException {
        values.put("operation", operation); values.put("key", key); values.put("requestHash", schema.hash(path, request, context, admission.subject().sessionId()));
    }
    private static String time(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC).toString(); }
    private static void current(EnterpriseAccess.Admission admission, String header) { if (!admission.context().context_revision().equals(header)) denied(ErrorCode.CONTEXT_CHANGED); }
    private static <T> T checked(ThrowingSupplier<T> work) {
        return work.get((message, failure) -> failure instanceof com.fasterxml.jackson.core.JsonProcessingException
                ? new SchemaRejection(ErrorCode.SCHEMA_INVALID) : new IllegalStateException(message, failure));
    }
    private static <T> T one(List<T> values, ErrorCode code) { if (values.size() != 1) denied(code); return Objects.requireNonNull(values.get(0)); }
    private static void requireOne(int count) { if (count != 1) denied(ErrorCode.REVISION_CONFLICT); }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
}
