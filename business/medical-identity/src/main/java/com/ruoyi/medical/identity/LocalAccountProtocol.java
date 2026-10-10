package com.ruoyi.medical.identity;

import java.util.List;

public final class LocalAccountProtocol {
    private LocalAccountProtocol() { }
    public enum Action {
        CREATE("LOCAL-02", "LocalAccountCreate"), STATE("LOCAL-03", "LocalAccountState"),
        PROFILE("LOCAL-04", "LocalAccountProfile"), RESET("LOCAL-05", "LocalAccountReset"),
        CREDENTIAL("LOCAL-06", "LocalAccountCredential"), OWN_PROFILE("LOCAL-07", "Request");
        final String id;
        final String definition;
        Action(String id, String definition) { this.id = id; this.definition = definition; }
    }
    public record AccountItem(String user_id, String username, String display_name, String status, String platform_role,
                              String revision, String credential_revision) { }
    public record Page(String schema_version, List<AccountItem> items, String next_cursor) {
        public Page { items = List.copyOf(items); }
    }
    public record Mutation(String schema_version, String operation_id, AccountItem account, boolean reauthentication_required) { }
    public record Audit(String schema_version, String resource_kind, String user_id, String revision,
                        String credential_revision, String status) { }
}
