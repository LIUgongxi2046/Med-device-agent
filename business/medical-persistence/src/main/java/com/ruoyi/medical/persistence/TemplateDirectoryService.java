package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.pagehelper.PageHelper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.ConfigurationDiscoveryMapper;
import java.util.HashMap;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class TemplateDirectoryService {
    private final EnterpriseAccess access;
    private final ConfigurationDiscoveryMapper mapper;
    private final TemplateVersionStore store;
    private final ResourceReadAuthority authority;
    private final ConfigurationDiscoverySchema schema;
    private final ConfigurationDiscoveryCursor cursors;
    private final PermissionSchema json;

    public TemplateDirectoryService(EnterpriseAccess access, ConfigurationDiscoveryMapper mapper, TemplateVersionStore store,
            ResourceReadAuthority authority, ConfigurationDiscoverySchema schema, ConfigurationDiscoveryCursor cursors, PermissionSchema json) {
        this.access = access; this.mapper = mapper; this.store = store; this.authority = authority;
        this.schema = schema; this.cursors = cursors; this.json = json;
    }

    public JsonNode list(ApplicationSessionIdentity.Verified subject, String context, ConfigurationDiscoverySchema.Query query) {
        schema.validate(query);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            if (!admission.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            var values = new HashMap<String,Object>(); values.put("userId", admission.actorId()); values.put("q", query.q());
            String clock = Objects.requireNonNull(mapper.authorizationClock(values));
            var identity = json.object().put("kind", "TEMPLATE").put("actor", admission.actorId()).put("session", subject.sessionId())
                .put("limit", query.limit()).put("authorization_clock", clock);
            identity.set("q", json.tree(query.q())); identity.set("context", json.tree(admission.context()));
            String binding = DigestUtils.sha256Hex(json.canonical(identity));
            String after = cursors.after(query.after(), binding), lastReturned = null, next = null;
            var items = json.object().arrayNode(); int scanned = 0; boolean finished = false;
            while (!finished && scanned < ConfigurationDiscoveryService.MAXIMUM_SCAN) {
                int size = Math.min(ConfigurationDiscoveryService.BATCH_SIZE, ConfigurationDiscoveryService.MAXIMUM_SCAN - scanned);
                values.put("after", after);
                var candidates = PageHelper.startPage(1, size + 1, false).<ConfigurationDiscoveryService.Candidate>doSelectPageInfo(() -> mapper.templates(values)).getList();
                for (var candidate : candidates.subList(0, Math.min(size, candidates.size()))) {
                    if (after != null && after.compareTo(candidate.id()) >= 0) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
                    after = candidate.id(); scanned++;
                    var scope = new PermissionProtocol.Scope(admission.context().tenant_id(), null, "TEMPLATE", candidate.id());
                    boolean reviewing=authority.find(admission,scope,"template.approve",TemplateDependencyAuthority.PURPOSE).isPresent();
                    if(!reviewing&&authority.find(admission,scope,"template.read",TemplateDependencyAuthority.PURPOSE).isEmpty())continue;
                    var stored=reviewing?store.approvalTarget(admission,candidate.id()):store.historicalTarget(admission,candidate.id(),TemplateDependencyAuthority.PURPOSE);
                    if (items.size() == query.limit()) { next = cursors.next(Objects.requireNonNull(lastReturned), binding); finished = true; break; }
                    // 目录按实际读取或核准权限提供版本摘要，完整定义另行核验全部依赖。
                    items.add(json.object().put("tenant_id", stored.tenant_id()).put("template_version", stored.template_version())
                        .put("document_type", stored.content().document_type()).put("application_path", stored.content().application_path())
                        .put("ivd", stored.content().ivd()).put("status", stored.status()).put("revision", stored.revision())
                        .put("reason", stored.reason()).put("created_by", stored.created_by()).put("created_at", stored.created_at()).put("content_sha256", stored.content_sha256()));
                    lastReturned = candidate.id();
                }
                if (candidates.size() <= size) finished = true;
                else if (!finished && scanned == ConfigurationDiscoveryService.MAXIMUM_SCAN) next = cursors.next(Objects.requireNonNull(after), binding);
            }
            if (!clock.equals(mapper.authorizationClock(values))) throw new TenantAccessException(ErrorCode.CURSOR_INVALID);
            var result = json.object().put("schema_version", "1.0.0").put("tenant_id", admission.context().tenant_id());
            result.set("current_context", json.tree(admission.context())); result.set("items", items); result.set("next_after", json.tree(next));
            return schema.envelope("TemplateList", result);
        }).get());
    }
}
