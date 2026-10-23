package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class AccountWorkspaceSelection {
    private final BusinessSessionDirectory directory;
    private final AccountWorkspaceBinding bindings;
    private final TenantSelectionService selections;

    public AccountWorkspaceSelection(BusinessSessionDirectory directory, AccountWorkspaceBinding bindings, TenantSelectionService selections) {
        this.directory = directory; this.bindings = bindings; this.selections = selections;
    }

    void initialize(ApplicationSessionIdentity.Verified subject) throws IOException {
        var state = directory.read(subject);
        String saved = bindings.tenant(subject.user().user_id());
        if (state.context() != null) {
            if (saved == null) bindings.remember(subject.user().user_id(), state.context().tenant_id());
            return;
        }
        var active = state.memberships().stream().filter(member -> member.status().equals("ACTIVE")).toList();
        if (active.isEmpty()) return;
        // 已撤销的默认组织需要本人重新选择，不自动转入其他组织。
        if (saved != null && active.stream().noneMatch(member -> member.tenant_id().equals(saved))) return;
        String target = saved == null ? active.get(0).tenant_id() : saved;
        String key = UUID.nameUUIDFromBytes(("account-workspace:" + subject.sessionId()).getBytes(StandardCharsets.UTF_8)).toString();
        selections.select(subject, new BusinessSessionProtocol.SelectionRequest(target, "0"), "0", key);
    }
}
