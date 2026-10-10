package com.ruoyi.medical.identity;

import java.util.List;

public final class BusinessSessionProtocol {
    private BusinessSessionProtocol() { }
    public record User(String user_id, String username, String display_name, String platform_role) { }
    public record Member(String tenant_id, String member_id, String status, String valid_from, String valid_to, String revision) { }
    public record Context(String tenant_id, String context_revision, String auth_epoch, String governance_epoch) { }
    public record SelectionRequest(String tenant_id, String expected_context_revision) { }
    public record SelectionReceipt(String schema_version, String operation_id, Context current_context) { }
    public record SelectionAudit(String schema_version, String resource_kind, String tenant_id, String subject_id,
                                 String session_id, String context_revision) { }
    public record SelectionEvent(String schema_version, String event_type, String operation_id, String subject_id,
                                 String session_id, Context current_context) { }
    public record Action(String action, String project_id, String resource_kind, String resource_id) { }
    public record Csrf(String header_name, String token) { }
    public record InvitationEntry(String method, String path_template) { }
    public record Session(String schema_version, User user, List<Member> memberships, Context current_context,
                          Csrf csrf, List<Action> action_summary, InvitationEntry invitation_entry) {
        public Session { memberships = List.copyOf(memberships); action_summary = List.copyOf(action_summary); }
    }
}
