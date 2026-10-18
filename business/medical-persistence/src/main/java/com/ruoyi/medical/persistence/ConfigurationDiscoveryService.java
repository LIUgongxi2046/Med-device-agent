package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.pagehelper.PageHelper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.ConfigurationDiscoveryMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** Bounded, permission-filtered discovery. Candidate IDs, hidden counts and credentials never leave this service. */
@Component
@org.springframework.boot.context.properties.EnableConfigurationProperties(ConfigurationDiscoveryPolicy.class)
public final class ConfigurationDiscoveryService {
    static final int BATCH_SIZE = 100;
    static final int MAXIMUM_SCAN = 500;
    public record Candidate(String id, String versionId) { }
    private final EnterpriseAccess access;
    private final ConfigurationDiscoveryMapper mapper;
    private final ModelConfigurationService models;
    private final RuleVersionStore rules;
    private final RuleVersionSchema ruleSchema;
    private final ConfigurationDiscoverySchema schema;
    private final ConfigurationDiscoveryCursor cursors;
    private final PermissionSchema json;

    public ConfigurationDiscoveryService(EnterpriseAccess access, ConfigurationDiscoveryMapper mapper,
            ModelConfigurationService models, RuleVersionStore rules, RuleVersionSchema ruleSchema,
            ConfigurationDiscoverySchema schema, ConfigurationDiscoveryCursor cursors, PermissionSchema json) {
        this.access = access; this.mapper = mapper; this.models = models; this.rules = rules; this.ruleSchema = ruleSchema;
        this.schema = schema; this.cursors = cursors; this.json = json;
    }

    public JsonNode models(ApplicationSessionIdentity.Verified subject, String context, ConfigurationDiscoverySchema.Query query) {
        return list(subject, context, query, true, false);
    }
    public JsonNode rules(ApplicationSessionIdentity.Verified subject, String context, ConfigurationDiscoverySchema.Query query) {
        return list(subject, context, query, false, false);
    }
    public JsonNode simulationModels(ApplicationSessionIdentity.Verified subject, String context, ConfigurationDiscoverySchema.Query query) {
        return list(subject, context, query, true, true);
    }
    private JsonNode list(ApplicationSessionIdentity.Verified subject, String context, ConfigurationDiscoverySchema.Query query, boolean model, boolean simulation) {
        schema.validate(query);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            if (!admission.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            var values = new HashMap<String,Object>(); values.put("userId", subject.user().user_id()); values.put("q", query.q()); values.put("mode", simulation ? "SIMULATION" : "EXTERNAL");
            String authorizationClock = Objects.requireNonNull(mapper.authorizationClock(values));
            String binding = binding(admission, query, model, simulation, authorizationClock), after = cursors.after(query.after(), binding);
            var items = json.object().arrayNode(); String next = null, lastReturned = null; int scanned = 0;
            boolean finished = false;
            while (!finished && scanned < MAXIMUM_SCAN) {
                int size = Math.min(BATCH_SIZE, MAXIMUM_SCAN - scanned); values.put("after", after);
                var candidates = candidates(values, model, size + 1);
                if (candidates.size() > size + 1) mismatch();
                String previous = after;
                for (var candidate : candidates) {
                    if (candidate == null) mismatch(); WireValues.uuid(candidate.id()); WireValues.uuid(candidate.versionId());
                    if (previous != null && previous.compareTo(candidate.id()) >= 0) mismatch(); previous = candidate.id();
                    if (!model && !candidate.id().equals(candidate.versionId())) mismatch();
                }
                for (var candidate : candidates.subList(0, Math.min(size, candidates.size()))) {
                    scanned++; after = candidate.id(); JsonNode view;
                    try {
                        view = model ? (simulation ? models.read(admission, candidate.versionId(), true) : models.read(admission, candidate.versionId())).required("data")
                                : ruleSchema.readView(rules.read(admission, candidate.versionId()), UUID.randomUUID().toString()).required("data");
                    } catch (TenantAccessException denied) {
                        // Dependency authorization is part of the original domain read, too. Never return a partially readable row.
                        if (denied.code() == ErrorCode.ACCESS_DENIED || denied.code() == ErrorCode.OBJECT_UNAVAILABLE) continue;
                        throw denied;
                    }
                    var target = model ? view : view.required("rule");
                    if (!candidate.id().equals(target.required(model ? "config_id" : "rule_version").textValue())
                            || !candidate.versionId().equals(target.required(model ? "model_config_revision" : "rule_version").textValue())
                            || !admission.context().tenant_id().equals(target.required("tenant_id").textValue())
                            || model && !candidate.versionId().equals(view.required("current_model_revision").textValue())) mismatch();
                    if (items.size() == query.limit()) { next = cursors.next(Objects.requireNonNull(lastReturned), binding); finished = true; break; }
                    items.add(view); lastReturned = candidate.id();
                }
                if (finished) break;
                if (candidates.size() <= size) finished = true;
                else if (scanned == MAXIMUM_SCAN) next = cursors.next(Objects.requireNonNull(after), binding);
            }
            var result = json.object(); result.put("schema_version", "1.0.0"); result.put("tenant_id", admission.context().tenant_id());
            result.set("current_context", json.tree(admission.context())); result.set("items", items); result.set("next_after", json.tree(next));
            if (!authorizationClock.equals(mapper.authorizationClock(values))) throw new TenantAccessException(ErrorCode.CURSOR_INVALID);
            return schema.envelope(model ? (simulation ? "SimulationModelList" : "ModelList") : "RuleList", result);
        }).get());
    }

    private List<Candidate> candidates(Map<String,Object> values, boolean model, int count) {
        // Original framework pagination, with counting explicitly off. No hidden-row total is computed.
        return PageHelper.startPage(1, count, false).<Candidate>doSelectPageInfo(() -> {
            if (model) mapper.models(values); else mapper.rules(values);
        }).getList();
    }
    private String binding(EnterpriseAccess.Admission admission, ConfigurationDiscoverySchema.Query query, boolean model, boolean simulation, String authorizationClock) throws IOException {
        var value = json.object(); value.put("kind", model ? (simulation ? "SIMULATION_MODEL" : "MODEL") : "RULE"); value.put("limit", query.limit()); value.set("q", json.tree(query.q()));
        value.put("actor", admission.subject().user().user_id()); value.put("session", admission.subject().sessionId());
        value.put("identity_mode", admission.subject().binding().view().mode()); value.put("context_id", admission.contextId()); value.put("authorization_clock", authorizationClock);
        value.set("context", json.tree(admission.context())); return DigestUtils.sha256Hex(json.canonical(value));
    }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
