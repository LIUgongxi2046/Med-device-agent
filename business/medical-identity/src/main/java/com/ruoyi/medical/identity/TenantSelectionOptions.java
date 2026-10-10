package com.ruoyi.medical.identity;

import java.util.List;

public record TenantSelectionOptions(String schema_version, String user_id, String expected_context_revision,
        String selected_tenant_id, List<BusinessSessionProtocol.Member> memberships) {
    public TenantSelectionOptions { memberships = List.copyOf(memberships); }
}
